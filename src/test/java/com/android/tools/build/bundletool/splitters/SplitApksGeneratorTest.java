/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_T_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import dagger.Component;
import java.util.Collection;
import java.util.function.Function;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SplitApksGeneratorTest {

  private static final BundleMetadata BUNDLE_METADATA_WITH_TRANSPARENCY =
      BundleMetadata.builder()
          .addFile(
              BundleMetadata.BUNDLETOOL_NAMESPACE,
              BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
              ByteSource.empty())
          .build();

  @Inject SplitApksGenerator splitApksGenerator;

  @Before
  public void setUp() throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().build());
  }

  @Test
  public void simpleMultipleModules() throws Exception {
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule, ApkGenerationConfiguration.getDefaultInstance());

    assertThat(moduleSplits).hasSize(2);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getModuleName().getName());

    ModuleSplit baseModule = moduleSplitMap.get("base");
    assertThat(baseModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseModule.getEntries())).containsExactly("assets/leftover.txt");
    assertThat(baseModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(testModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testModule.getEntries())).containsExactly("assets/test.txt");
    assertThat(testModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
  }

  @Test
  public void simpleMultipleModules_withTransparencyFile() throws Exception {
    TestComponent.useTestModule(
        this, TestModule.builder().withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY).build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule, ApkGenerationConfiguration.getDefaultInstance());

    assertThat(moduleSplits).hasSize(2);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getModuleName().getName());

    ModuleSplit baseModule = moduleSplitMap.get("base");
    assertThat(baseModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseModule.getEntries()))
        .containsExactly("assets/leftover.txt", "META-INF/code_transparency_signed.jwt");
    assertThat(baseModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(testModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testModule.getEntries())).containsExactly("assets/test.txt");
    assertThat(testModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
  }

  @Test
  public void multipleModules_withOnlyBaseModuleWithNativeLibraries() throws Exception {
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule,
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .build());

    VariantTargeting lVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_L_API_VERSION,
            /* alternativeSdkVersions...= */ ANDROID_M_API_VERSION);
    VariantTargeting mVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_M_API_VERSION,
            /* alternativeSdkVersions...= */ ANDROID_L_API_VERSION);

    // 2 splits for L and M variants.
    assertThat(moduleSplits).hasSize(4);
    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(lVariantTargeting, mVariantTargeting);

    ModuleSplit baseLModule = getModuleSplit(moduleSplits, lVariantTargeting, "base");
    assertThat(baseLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseLModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(getForceUncompressed(baseLModule, "lib/x86_64/libsome.so")).isFalse();

    ModuleSplit testLModule = getModuleSplit(moduleSplits, lVariantTargeting, "test");
    assertThat(testLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testLModule.getEntries())).containsExactly("assets/test.txt");

    ModuleSplit baseMModule = getModuleSplit(moduleSplits, mVariantTargeting, "base");
    assertThat(baseMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseMModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(getForceUncompressed(baseMModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testMModule = getModuleSplit(moduleSplits, mVariantTargeting, "test");
    assertThat(testMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testMModule.getEntries())).containsExactly("assets/test.txt");
  }

  @Test
  public void multipleModules_multipleVariants_withTransparency() throws Exception {
    TestComponent.useTestModule(
        this, TestModule.builder().withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY).build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule,
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(OptimizationDimension.ABI))
                .build());

    ApkTargeting minSdkLTargeting = apkMinSdkTargeting(/* minSdkVersion= */ ANDROID_L_API_VERSION);
    ApkTargeting minSdkLWithAbiTargeting =
        mergeApkTargeting(apkAbiTargeting(AbiAlias.X86_64), minSdkLTargeting);

    // 1 main and 1 ABI split for base module + test module.
    assertThat(moduleSplits).hasSize(3);
    assertThat(moduleSplits.stream().map(ModuleSplit::getApkTargeting).collect(toImmutableSet()))
        .containsExactly(minSdkLTargeting, minSdkLWithAbiTargeting);
    ModuleSplit mainSplitOfBaseModule =
        getModuleSplit(moduleSplits, minSdkLTargeting, /* moduleName= */ "base");
    assertThat(extractPaths(mainSplitOfBaseModule.getEntries()))
        .containsExactly("assets/leftover.txt", "META-INF/code_transparency_signed.jwt");
    ModuleSplit abiSplitOfBaseModule =
        getModuleSplit(moduleSplits, minSdkLWithAbiTargeting, /* moduleName= */ "base");
    assertThat(extractPaths(abiSplitOfBaseModule.getEntries()))
        .containsExactly("lib/x86_64/libsome.so");
    ModuleSplit testModule =
        getModuleSplit(moduleSplits, minSdkLTargeting, /* moduleName= */ "test");
    assertThat(extractPaths(testModule.getEntries())).containsExactly("assets/test.txt");
  }

  @Test
  public void multipleModules_withOnlyBaseModuleWithNativeLibrariesOtherModuleWithDexFiles()
      throws Exception {
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .addFile("dex/classes.dex")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule,
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setEnableDexCompressionSplitter(true)
                .build());

    VariantTargeting lVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_L_API_VERSION,
            /* alternativeSdkVersions...= */ ANDROID_M_API_VERSION,
            ANDROID_Q_API_VERSION);
    VariantTargeting mVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_M_API_VERSION,
            /* alternativeSdkVersions...= */ ANDROID_L_API_VERSION,
            ANDROID_Q_API_VERSION);
    VariantTargeting qVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_Q_API_VERSION,
            /* alternativeSdkVersions...= */ ANDROID_L_API_VERSION,
            ANDROID_M_API_VERSION);

    // 2 splits for L, M, P variants.
    assertThat(moduleSplits).hasSize(6);
    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(lVariantTargeting, mVariantTargeting, qVariantTargeting);

    ModuleSplit baseLModule = getModuleSplit(moduleSplits, lVariantTargeting, "base");
    assertThat(baseLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseLModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(getForceUncompressed(baseLModule, "lib/x86_64/libsome.so")).isFalse();

    ModuleSplit testLModule = getModuleSplit(moduleSplits, lVariantTargeting, "test");
    assertThat(testLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testLModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(getForceUncompressed(testLModule, "dex/classes.dex")).isFalse();

    ModuleSplit baseMModule = getModuleSplit(moduleSplits, mVariantTargeting, "base");
    assertThat(baseMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseMModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(getForceUncompressed(baseMModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testMModule = getModuleSplit(moduleSplits, mVariantTargeting, "test");
    assertThat(testMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testMModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(getForceUncompressed(testMModule, "dex/classes.dex")).isFalse();

    ModuleSplit basePModule = getModuleSplit(moduleSplits, qVariantTargeting, "base");
    assertThat(basePModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(basePModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(getForceUncompressed(basePModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testPModule = getModuleSplit(moduleSplits, qVariantTargeting, "test");
    assertThat(testPModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testPModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(getForceUncompressed(testPModule, "dex/classes.dex")).isTrue();
  }

  @Test
  public void
      multipleModules_withinstantAppOnlyBaseModuleWithNativeLibrariesOtherModuleWithDexFiles()
          throws Exception {
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .addFile("dex/classes.dex")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule,
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setEnableDexCompressionSplitter(true)
                .setForInstantAppVariants(true)
                .build());

    // 2 splits for L variant
    assertThat(moduleSplits).hasSize(2);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getModuleName().getName());

    ModuleSplit baseModule = moduleSplitMap.get("base");
    assertThat(baseModule.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(extractPaths(baseModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(baseModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(getForceUncompressed(baseModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(testModule.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(extractPaths(testModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(testModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(getForceUncompressed(testModule, "dex/classes.dex")).isFalse();
  }

  @Test
  public void
      multipleModules_instantAppOnlyBaseModuleWithNativeLibrariesOthersWithDexFiles_withTransparency()
          throws Exception {
    TestComponent.useTestModule(
        this, TestModule.builder().withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY).build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .addFile("dex/classes.dex")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            bundleModule,
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setEnableDexCompressionSplitter(true)
                .setForInstantAppVariants(true)
                .build());

    // 2 splits for L variant
    assertThat(moduleSplits).hasSize(2);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getModuleName().getName());

    ModuleSplit baseModule = moduleSplitMap.get("base");
    assertThat(baseModule.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(extractPaths(baseModule.getEntries()))
        .containsExactly(
            "assets/leftover.txt",
            "lib/x86_64/libsome.so",
            "META-INF/code_transparency_signed.jwt");
    assertThat(baseModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(getForceUncompressed(baseModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(testModule.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(extractPaths(testModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(testModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(getForceUncompressed(testModule, "dex/classes.dex")).isFalse();
  }

  @Test
  public void appBundleHasRuntimeEnabledSdkDeps_generatesSdkRuntimeVariant() {
    AppBundle appBundleWithRuntimeEnabledSdkDeps =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest("com.test.app"))
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName("com.test.sdk")
                                    .setVersionMajor(1)
                                    .setCertificateDigest("AA:BB:CC")
                                    .setResourcesPackageId(2))
                            .build())
                    .build())
            .build();
    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(appBundleWithRuntimeEnabledSdkDeps).build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            appBundleWithRuntimeEnabledSdkDeps.getModules().values().asList(),
            ApkGenerationConfiguration.getDefaultInstance());

    assertThat(moduleSplits).hasSize(2);
    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableList()))
        .containsExactly(
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION));
    ImmutableMap<VariantTargeting, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, ModuleSplit::getVariantTargeting);
    assertThat(
            moduleSplitMap
                .get(lPlusVariantTargeting())
                .getAndroidManifest()
                .getUsesSdkLibraryElements())
        .isEmpty();
    assertThat(
            moduleSplitMap
                .get(sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION))
                .getAndroidManifest()
                .getUsesSdkLibraryElements())
        .hasSize(1);
  }

  @Test
  public void appBundleWithSdkDependencyModule_sdkModuleIncludedInNonSdkRuntimeVariantOnly() {
    AppBundle appBundleWithRuntimeEnabledSdkDeps =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest("com.test.app"))
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName("com.test.sdk")
                                    .setVersionMajor(1)
                                    .setCertificateDigest("AA:BB:CC")
                                    .setResourcesPackageId(2))
                            .build())
                    .build())
            .addModule(
                new BundleModuleBuilder("comTestSdk")
                    .setModuleType(ModuleType.SDK_DEPENDENCY_MODULE)
                    .setManifest(androidManifest("com.test.sdk"))
                    .build())
            .build();
    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(appBundleWithRuntimeEnabledSdkDeps).build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            appBundleWithRuntimeEnabledSdkDeps.getModules().values().asList(),
            ApkGenerationConfiguration.getDefaultInstance());

    assertThat(moduleSplits).hasSize(3);
    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION));
    ImmutableMap<VariantTargeting, Collection<ModuleSplit>> moduleSplitMap =
        moduleSplits.stream()
            .collect(toImmutableListMultimap(ModuleSplit::getVariantTargeting, Function.identity()))
            .asMap();
    assertThat(moduleSplitMap.get(lPlusVariantTargeting())).hasSize(2);
    assertThat(
            moduleSplitMap.get(lPlusVariantTargeting()).stream()
                .map(ModuleSplit::getModuleName)
                .map(BundleModuleName::getName))
        .containsExactly("base", "comTestSdk");
    assertThat(moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION))).hasSize(1);
    assertThat(
            moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION)).stream()
                .map(ModuleSplit::getModuleName)
                .map(BundleModuleName::getName))
        .containsExactly("base");
  }

  @Test
  public void appBundleWithSdkDependencyModuleAndDensityTargeting_noDensitySplitsForSdkModule() {
    ResourceTable appResourceTable =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET,
                "com.test.app",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "title_image",
                        fileReference("res/drawable-hdpi/title_image.jpg", HDPI),
                        fileReference(
                            "res/drawable/title_image.jpg", Configuration.getDefaultInstance())))));
    ResourceTable sdkResourceTable =
        resourceTable(
            pkg(
                USER_PACKAGE_OFFSET + 1,
                "com.test.sdk",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x01,
                        "title_image",
                        fileReference("res/drawable-hdpi/title_image.jpg", HDPI),
                        fileReference(
                            "res/drawable/title_image.jpg", Configuration.getDefaultInstance())))));
    AppBundle appBundleWithRuntimeEnabledSdkDeps =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest("com.test.app"))
                    .setResourceTable(appResourceTable)
                    .setRuntimeEnabledSdkConfig(
                        RuntimeEnabledSdkConfig.newBuilder()
                            .addRuntimeEnabledSdk(
                                RuntimeEnabledSdk.newBuilder()
                                    .setPackageName("com.test.sdk")
                                    .setVersionMajor(1)
                                    .setCertificateDigest("AA:BB:CC")
                                    .setResourcesPackageId(2))
                            .build())
                    .build())
            .addModule(
                new BundleModuleBuilder("comTestSdk")
                    .setModuleType(ModuleType.SDK_DEPENDENCY_MODULE)
                    .setManifest(androidManifest("com.test.sdk"))
                    .setResourceTable(sdkResourceTable)
                    .build())
            .build();
    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(appBundleWithRuntimeEnabledSdkDeps).build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            appBundleWithRuntimeEnabledSdkDeps.getModules().values().asList(),
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(ImmutableSet.of(OptimizationDimension.SCREEN_DENSITY))
                .build());

    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION));
    ImmutableMap<VariantTargeting, Collection<ModuleSplit>> moduleSplitMap =
        moduleSplits.stream()
            .collect(toImmutableListMultimap(ModuleSplit::getVariantTargeting, Function.identity()))
            .asMap();
    assertThat(
            moduleSplitMap.get(lPlusVariantTargeting()).stream()
                .map(ModuleSplit::getModuleName)
                .map(BundleModuleName::getName)
                .distinct())
        .containsExactly("base", "comTestSdk");
    ImmutableSet<ModuleSplit> sdkModuleSplits =
        moduleSplitMap.get(lPlusVariantTargeting()).stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("comTestSdk"))
            .collect(toImmutableSet());
    // Only main split for SDK dependency module - no config splits.
    assertThat(sdkModuleSplits).hasSize(1);
    assertThat(
            Iterables.getOnlyElement(sdkModuleSplits).getApkTargeting().hasScreenDensityTargeting())
        .isFalse();
    ImmutableSet<ModuleSplit> nonSdkRuntimeVariantBaseModuleSplits =
        moduleSplitMap.get(lPlusVariantTargeting()).stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("base"))
            .collect(toImmutableSet());
    // 1 main split + 7 config splits: 1 per each screen density.
    assertThat(nonSdkRuntimeVariantBaseModuleSplits).hasSize(8);
    assertThat(
            moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION)).stream()
                .map(ModuleSplit::getModuleName)
                .map(BundleModuleName::getName)
                .distinct())
        .containsExactly("base");
    ImmutableSet<ModuleSplit> sdkRuntimeVariantSplits =
        moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION)).stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("base"))
            .collect(toImmutableSet());
    // 1 main split + 7 config splits: 1 per each screen density.
    assertThat(sdkRuntimeVariantSplits).hasSize(8);
  }

  private static ModuleSplit getModuleSplit(
      ImmutableList<ModuleSplit> moduleSplits,
      VariantTargeting variantTargeting,
      String moduleName) {
    return moduleSplits.stream()
        .filter(moduleSplit -> moduleSplit.getVariantTargeting().equals(variantTargeting))
        .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals(moduleName))
        .findFirst()
        .get();
  }

  private static ModuleSplit getModuleSplit(
      ImmutableList<ModuleSplit> moduleSplits, ApkTargeting apkTargeting, String moduleName) {
    return moduleSplits.stream()
        .filter(moduleSplit -> moduleSplit.getApkTargeting().equals(apkTargeting))
        .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals(moduleName))
        .findFirst()
        .get();
  }

  private static boolean getForceUncompressed(ModuleSplit moduleSplit, String path) {
    return moduleSplit.findEntry(path).get().getForceUncompressed();
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(SplitApksGeneratorTest test);

    static void useTestModule(SplitApksGeneratorTest testInstance, TestModule testModule) {
      DaggerSplitApksGeneratorTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
