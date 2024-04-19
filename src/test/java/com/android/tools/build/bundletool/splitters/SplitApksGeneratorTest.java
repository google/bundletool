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

import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_SPLIT_TYPES_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_SPLIT_TYPES_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_TYPES_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_TYPES_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_U_API_VERSION;
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
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.FeatureModulesConfigProto.FeatureModulesCustomConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
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
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import dagger.Component;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
  public void simpleMultipleModules_withRequiredSplitTypes() throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().build());
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
    assertThat(getRequiredSplitTypes(baseModule)).containsExactly("test__module");
    assertThat(getProvidedSplitTypes(baseModule)).isEmpty();

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(getRequiredSplitTypes(testModule)).isEmpty();
    assertThat(getProvidedSplitTypes(testModule)).containsExactly("test__module");

    assertConsistentRequiredSplitTypes(moduleSplits);
  }

  @Test
  public void simpleMultipleModules_withRequiredSplitTypes_experimentalTPlusVariant()
      throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().build());
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
            bundleModule,
            ApkGenerationConfiguration.builder().setEnableRequiredSplitTypes(true).build());

    assertThat(moduleSplits).hasSize(4);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(
            moduleSplits,
            split ->
                String.format(
                    "%s:%s",
                    split.getModuleName().getName(),
                    split
                        .getVariantTargeting()
                        .getSdkVersionTargeting()
                        .getValue(0)
                        .getMin()
                        .getValue()));

    assertThat(moduleSplitMap.keySet()).containsExactly("base:21", "test:21", "base:33", "test:33");

    ModuleSplit baseModule = moduleSplitMap.get("base:21");
    assertThat(baseModule).isNotNull();
    assertThat(getRequiredSplitTypes(baseModule)).containsExactly("test__module");
    assertThat(getProvidedSplitTypes(baseModule)).isEmpty();

    ModuleSplit testModule = moduleSplitMap.get("test:21");
    assertThat(testModule).isNotNull();
    assertThat(getRequiredSplitTypes(testModule)).isEmpty();
    assertThat(getProvidedSplitTypes(testModule)).containsExactly("test__module");

    // TODO(b/199376532): Remove once system required split type attributes are enabled.
    ModuleSplit baseModuleTPlus = moduleSplitMap.get("base:33");
    assertThat(baseModuleTPlus).isNotNull();
    assertThat(getRequiredSplitTypes(baseModuleTPlus)).containsExactly("test__module");
    assertThat(getProvidedSplitTypes(baseModuleTPlus)).isEmpty();
    ModuleSplit testModuleTPlus = moduleSplitMap.get("test:33");
    assertThat(testModuleTPlus).isNotNull();
    assertThat(getRequiredSplitTypes(testModuleTPlus)).isEmpty();
    assertThat(getProvidedSplitTypes(testModuleTPlus)).containsExactly("test__module");

    assertConsistentRequiredSplitTypes(moduleSplits);
  }

  @Test
  public void simpleMultipleModules_withoutRequiredSplitTypes() throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().build());
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
            bundleModule,
            ApkGenerationConfiguration.builder().setEnableRequiredSplitTypes(false).build());

    assertThat(moduleSplits).hasSize(2);
    for (ModuleSplit moduleSplit : moduleSplits) {
      assertThat(
              moduleSplit
                  .getAndroidManifest()
                  .getManifestElement()
                  .getAndroidAttribute(SPLIT_TYPES_RESOURCE_ID))
          .isEmpty();
      assertThat(
              moduleSplit
                  .getAndroidManifest()
                  .getManifestElement()
                  .getAndroidAttribute(REQUIRED_SPLIT_TYPES_RESOURCE_ID))
          .isEmpty();
    }
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
    assertThat(getRequiredSplitTypes(baseLModule)).containsExactly("test__module");
    assertThat(getProvidedSplitTypes(baseLModule)).isEmpty();

    ModuleSplit testLModule = getModuleSplit(moduleSplits, lVariantTargeting, "test");
    assertThat(testLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testLModule.getEntries())).containsExactly("assets/test.txt");
    assertThat(getRequiredSplitTypes(testLModule)).isEmpty();
    assertThat(getProvidedSplitTypes(testLModule)).containsExactly("test__module");

    ModuleSplit baseMModule = getModuleSplit(moduleSplits, mVariantTargeting, "base");
    assertThat(baseMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseMModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(getForceUncompressed(baseMModule, "lib/x86_64/libsome.so")).isTrue();
    assertThat(getRequiredSplitTypes(baseMModule)).containsExactly("test__module");
    assertThat(getProvidedSplitTypes(baseMModule)).isEmpty();

    ModuleSplit testMModule = getModuleSplit(moduleSplits, mVariantTargeting, "test");
    assertThat(testMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testMModule.getEntries())).containsExactly("assets/test.txt");
    assertThat(getRequiredSplitTypes(testMModule)).isEmpty();
    assertThat(getProvidedSplitTypes(testMModule)).containsExactly("test__module");

    assertConsistentRequiredSplitTypes(ImmutableList.of(baseLModule, testLModule));
    assertConsistentRequiredSplitTypes(ImmutableList.of(baseMModule, testMModule));
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
    assertThat(getRequiredSplitTypes(mainSplitOfBaseModule))
        .containsExactly("base__abi", "test__module");
    assertThat(getProvidedSplitTypes(mainSplitOfBaseModule)).isEmpty();

    ModuleSplit abiSplitOfBaseModule =
        getModuleSplit(moduleSplits, minSdkLWithAbiTargeting, /* moduleName= */ "base");
    assertThat(extractPaths(abiSplitOfBaseModule.getEntries()))
        .containsExactly("lib/x86_64/libsome.so");
    assertThat(getRequiredSplitTypes(abiSplitOfBaseModule)).isEmpty();
    assertThat(getProvidedSplitTypes(abiSplitOfBaseModule)).containsExactly("base__abi");

    ModuleSplit testModule =
        getModuleSplit(moduleSplits, minSdkLTargeting, /* moduleName= */ "test");
    assertThat(extractPaths(testModule.getEntries())).containsExactly("assets/test.txt");
    assertThat(getRequiredSplitTypes(testModule)).isEmpty();
    assertThat(getProvidedSplitTypes(testModule)).containsExactly("test__module");

    assertConsistentRequiredSplitTypes(moduleSplits);
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
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION));
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
                .get(sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION))
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
                    .setSdkModulesConfig(SdkModulesConfig.getDefaultInstance())
                    .setResourcesPackageId(2)
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
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION));
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
    assertThat(moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION))).hasSize(1);
    assertThat(
            moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION)).stream()
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
                    .setResourcesPackageId(2)
                    .build())
            .addModule(
                new BundleModuleBuilder("comtestsdk")
                    .setModuleType(ModuleType.SDK_DEPENDENCY_MODULE)
                    .setSdkModulesConfig(SdkModulesConfig.getDefaultInstance())
                    .setResourcesPackageId(2)
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
                .setEnableRequiredSplitTypes(false)
                .build());

    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION));
    ImmutableMap<VariantTargeting, Collection<ModuleSplit>> moduleSplitMap =
        moduleSplits.stream()
            .collect(toImmutableListMultimap(ModuleSplit::getVariantTargeting, Function.identity()))
            .asMap();
    assertThat(
            moduleSplitMap.get(lPlusVariantTargeting()).stream()
                .map(ModuleSplit::getModuleName)
                .map(BundleModuleName::getName)
                .distinct())
        .containsExactly("base", "comtestsdk");
    ImmutableSet<ModuleSplit> sdkModuleSplits =
        moduleSplitMap.get(lPlusVariantTargeting()).stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("comtestsdk"))
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
            moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION)).stream()
                .map(ModuleSplit::getModuleName)
                .map(BundleModuleName::getName)
                .distinct())
        .containsExactly("base");
    ImmutableSet<ModuleSplit> sdkRuntimeVariantSplits =
        moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION)).stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("base"))
            .collect(toImmutableSet());
    // 1 main split + 7 config splits: 1 per each screen density.
    assertThat(sdkRuntimeVariantSplits).hasSize(8);

    for (ModuleSplit moduleSplit : moduleSplits) {
      assertThat(
              moduleSplit
                  .getAndroidManifest()
                  .getManifestElement()
                  .getAndroidAttribute(SPLIT_TYPES_RESOURCE_ID))
          .isEmpty();
      assertThat(
              moduleSplit
                  .getAndroidManifest()
                  .getManifestElement()
                  .getAndroidAttribute(REQUIRED_SPLIT_TYPES_RESOURCE_ID))
          .isEmpty();
    }
  }

  @Test
  public void
      appBundleWithSdkDependencyModuleAndDensityTargeting_noDensitySplitsForSdkModule_requiredSplitTypesSet() {
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
                    .setResourcesPackageId(2)
                    .build())
            .addModule(
                new BundleModuleBuilder("comtestsdk")
                    .setModuleType(ModuleType.SDK_DEPENDENCY_MODULE)
                    .setSdkModulesConfig(SdkModulesConfig.getDefaultInstance())
                    .setResourcesPackageId(2)
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
            lPlusVariantTargeting(), sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION));
    ImmutableMap<VariantTargeting, Collection<ModuleSplit>> moduleSplitMap =
        moduleSplits.stream()
            .collect(toImmutableListMultimap(ModuleSplit::getVariantTargeting, Function.identity()))
            .asMap();

    // L+ SDK module
    Collection<ModuleSplit> lPlusVariantSplits = moduleSplitMap.get(lPlusVariantTargeting());
    ImmutableSet<ModuleSplit> lPlusSdkModuleSplits =
        lPlusVariantSplits.stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("comtestsdk"))
            .collect(toImmutableSet());
    ImmutableSet<ModuleSplit> lPlusBaseModuleSplits =
        lPlusVariantSplits.stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("base"))
            .collect(toImmutableSet());

    // SDK just provides itself
    ModuleSplit lPlusSdkModuleSplit = Iterables.getOnlyElement(lPlusSdkModuleSplits);
    assertThat(getRequiredSplitTypes(lPlusSdkModuleSplit)).isEmpty();
    assertThat(getProvidedSplitTypes(lPlusSdkModuleSplit)).containsExactly("comtestsdk__module");

    // Base module requires density and the SDK
    ModuleSplit lPlusBaseModuleSplit =
        lPlusBaseModuleSplits.stream()
            .filter(moduleSplit -> !moduleSplit.getApkTargeting().hasScreenDensityTargeting())
            .collect(onlyElement());
    assertThat(getRequiredSplitTypes(lPlusBaseModuleSplit))
        .containsExactly("base__density", "comtestsdk__module");
    assertThat(getProvidedSplitTypes(lPlusBaseModuleSplit)).isEmpty();

    // Density splits provide density
    ImmutableSet<ModuleSplit> lPlusBaseModuleDensitySplits =
        lPlusBaseModuleSplits.stream()
            .filter(moduleSplit -> moduleSplit.getApkTargeting().hasScreenDensityTargeting())
            .collect(toImmutableSet());
    lPlusBaseModuleDensitySplits.forEach(
        split -> {
          assertThat(getRequiredSplitTypes(split)).isEmpty();
          assertThat(getProvidedSplitTypes(split)).containsExactly("base__density");
        });

    assertConsistentRequiredSplitTypes(lPlusVariantSplits);

    // T+ runtime enabled
    Collection<ModuleSplit> tPlusVariantSplits =
        moduleSplitMap.get(sdkRuntimeVariantTargeting(ANDROID_U_API_VERSION));
    ImmutableSet<ModuleSplit> tPlusSdkModuleSplits =
        lPlusVariantSplits.stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("comtestsdk"))
            .collect(toImmutableSet());
    ImmutableSet<ModuleSplit> tPlusBaseModuleSplits =
        lPlusVariantSplits.stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("base"))
            .collect(toImmutableSet());

    // SDK just provides itself
    ModuleSplit tPlusSdkModuleSplit = Iterables.getOnlyElement(tPlusSdkModuleSplits);
    assertThat(getRequiredSplitTypes(tPlusSdkModuleSplit)).isEmpty();
    assertThat(getProvidedSplitTypes(tPlusSdkModuleSplit)).containsExactly("comtestsdk__module");

    // Base module requires density and the SDK
    ModuleSplit tPlusBaseModuleSplit =
        tPlusBaseModuleSplits.stream()
            .filter(moduleSplit -> !moduleSplit.getApkTargeting().hasScreenDensityTargeting())
            .collect(onlyElement());
    assertThat(getRequiredSplitTypes(tPlusBaseModuleSplit))
        .containsExactly("base__density", "comtestsdk__module");
    assertThat(getProvidedSplitTypes(tPlusBaseModuleSplit)).isEmpty();

    // Density splits provide density
    ImmutableSet<ModuleSplit> tPlusBaseModuleDensitySplits =
        tPlusBaseModuleSplits.stream()
            .filter(moduleSplit -> moduleSplit.getApkTargeting().hasScreenDensityTargeting())
            .collect(toImmutableSet());
    tPlusBaseModuleDensitySplits.forEach(
        split -> {
          assertThat(getRequiredSplitTypes(split)).isEmpty();
          assertThat(getProvidedSplitTypes(split)).containsExactly("base__density");
        });

    assertConsistentRequiredSplitTypes(tPlusVariantSplits);
  }

  @Test
  public void appBundleWithAllSplitTargeting_requiredSplitTypesSet() {
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
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest("com.test.app"))
                    .setResourceTable(appResourceTable)
                    .setResourcesPackageId(2)
                    .addFile("assets/languages#lang_es/strings.xml")
                    .addFile("assets/textures#tcf_atc/textures.dat")
                    .addFile("assets/textures#tier_0/textures.dat")
                    .addFile("assets/content#countries_latam/strings.xml")
                    .setAssetsConfig(
                        assets(
                            targetedAssetsDirectory(
                                "assets/languages#lang_es",
                                assetsDirectoryTargeting(languageTargeting("es"))),
                            targetedAssetsDirectory(
                                "assets/textures#tcf_atc",
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC))),
                            targetedAssetsDirectory(
                                "assets/textures#tier_0",
                                assetsDirectoryTargeting(deviceTierTargeting(/* value= */ 0))),
                            targetedAssetsDirectory(
                                "assets/content#countries_latam",
                                assetsDirectoryTargeting(countrySetTargeting("latam")))))
                    .setNativeConfig(
                        nativeLibraries(
                            targetedNativeDirectory(
                                "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                    .build())
            .addModule(
                new BundleModuleBuilder("test")
                    .addFile("assets/test.txt")
                    .setManifest(androidManifest("com.test.app"))
                    .addFile("assets/languages#lang_es/strings.xml")
                    .addFile("assets/textures#tcf_atc/textures.dat")
                    .addFile("assets/textures#tier_0/textures.dat")
                    .addFile("assets/content#countries_latam/strings.xml")
                    .setAssetsConfig(
                        assets(
                            targetedAssetsDirectory(
                                "assets/languages#lang_es",
                                assetsDirectoryTargeting(languageTargeting("es"))),
                            targetedAssetsDirectory(
                                "assets/textures#tcf_atc",
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC))),
                            targetedAssetsDirectory(
                                "assets/textures#tier_0",
                                assetsDirectoryTargeting(deviceTierTargeting(/* value= */ 0))),
                            targetedAssetsDirectory(
                                "assets/content#countries_latam",
                                assetsDirectoryTargeting(countrySetTargeting("latam")))))
                    .setNativeConfig(
                        nativeLibraries(
                            targetedNativeDirectory(
                                "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                    .build())
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            appBundle.getModules().values().asList(),
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(
                    ImmutableSet.of(
                        OptimizationDimension.SCREEN_DENSITY,
                        OptimizationDimension.ABI,
                        OptimizationDimension.LANGUAGE,
                        OptimizationDimension.TEXTURE_COMPRESSION_FORMAT,
                        OptimizationDimension.DEVICE_TIER,
                        OptimizationDimension.COUNTRY_SET))
                .build());

    ModuleSplit baseModule = getModuleSplit(moduleSplits, "base", Optional.empty());
    assertThat(getProvidedSplitTypes(baseModule)).isEmpty();
    assertThat(getRequiredSplitTypes(baseModule))
        .containsExactly(
            "base__abi",
            "base__countries",
            "base__density",
            "base__textures",
            "base__tier",
            "test__module");

    ModuleSplit testModule = getModuleSplit(moduleSplits, "test", Optional.empty());
    assertThat(getProvidedSplitTypes(testModule)).containsExactly("test__module");
    assertThat(getRequiredSplitTypes(testModule))
        .containsExactly("test__abi", "test__countries", "test__textures", "test__tier");

    ImmutableMap<String, String> expectedProvidedSplitTypes =
        ImmutableMap.of(
            "base.countries_latam",
            "base__countries",
            "base.ldpi",
            "base__density",
            "base.x86_64",
            "base__abi",
            "base.atc",
            "base__textures",
            "base.tier_0",
            "base__tier",
            "test.countries_latam",
            "test__countries",
            "test.x86_64",
            "test__abi",
            "test.atc",
            "test__textures",
            "test.tier_0",
            "test__tier");

    expectedProvidedSplitTypes.forEach(
        (splitId, splitType) -> {
          List<String> parts = Splitter.on(".").splitToList(splitId);
          String moduleName = parts.get(0);
          String suffix = parts.get(1);
          ModuleSplit moduleSplit = getModuleSplit(moduleSplits, moduleName, Optional.of(suffix));
          assertThat(getProvidedSplitTypes(moduleSplit)).containsExactly(splitType);
          assertThat(getRequiredSplitTypes(moduleSplit)).isEmpty();
        });

    ModuleSplit languageSplit = getModuleSplit(moduleSplits, "base", Optional.of("es"));
    assertThat(getProvidedSplitTypes(languageSplit)).isEmpty();

    assertConsistentRequiredSplitTypes(moduleSplits);
  }

  @Test
  public void appBundle_withAllSplitTargeting_andFeatureModulesConfig() {
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
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest("com.test.app"))
                    .setResourceTable(appResourceTable)
                    .setResourcesPackageId(2)
                    .addFile("assets/languages#lang_es/strings.xml")
                    .addFile("assets/textures#tcf_atc/textures.dat")
                    .addFile("assets/textures#tier_0/textures.dat")
                    .addFile("assets/content#countries_latam/strings.xml")
                    .setAssetsConfig(
                        assets(
                            targetedAssetsDirectory(
                                "assets/languages#lang_es",
                                assetsDirectoryTargeting(languageTargeting("es"))),
                            targetedAssetsDirectory(
                                "assets/textures#tcf_atc",
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC))),
                            targetedAssetsDirectory(
                                "assets/textures#tier_0",
                                assetsDirectoryTargeting(deviceTierTargeting(/* value= */ 0))),
                            targetedAssetsDirectory(
                                "assets/content#countries_latam",
                                assetsDirectoryTargeting(countrySetTargeting("latam")))))
                    .setNativeConfig(
                        nativeLibraries(
                            targetedNativeDirectory(
                                "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                    .build())
            .addModule(
                new BundleModuleBuilder("test")
                    .addFile("assets/test.txt")
                    .setManifest(androidManifest("com.test.app"))
                    .addFile("assets/languages#lang_es/strings.xml")
                    .addFile("assets/textures#tcf_atc/textures.dat")
                    .addFile("assets/textures#tier_0/textures.dat")
                    .addFile("assets/content#countries_latam/strings.xml")
                    .setAssetsConfig(
                        assets(
                            targetedAssetsDirectory(
                                "assets/languages#lang_es",
                                assetsDirectoryTargeting(languageTargeting("es"))),
                            targetedAssetsDirectory(
                                "assets/textures#tcf_atc",
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC))),
                            targetedAssetsDirectory(
                                "assets/textures#tier_0",
                                assetsDirectoryTargeting(deviceTierTargeting(/* value= */ 0))),
                            targetedAssetsDirectory(
                                "assets/content#countries_latam",
                                assetsDirectoryTargeting(countrySetTargeting("latam")))))
                    .setNativeConfig(
                        nativeLibraries(
                            targetedNativeDirectory(
                                "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                    .build())
            .build();
    FeatureModulesCustomConfig featureModulesCustomConfig =
        FeatureModulesCustomConfig.newBuilder().addDisableConfigSplitsModules("test").build();
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withAppBundle(appBundle)
            .withFeatureModulesCustomConfig(Optional.of(featureModulesCustomConfig))
            .build());

    ImmutableList<ModuleSplit> moduleSplits =
        splitApksGenerator.generateSplits(
            appBundle.getModules().values().asList(),
            ApkGenerationConfiguration.builder()
                .setOptimizationDimensions(
                    ImmutableSet.of(
                        OptimizationDimension.SCREEN_DENSITY,
                        OptimizationDimension.ABI,
                        OptimizationDimension.LANGUAGE,
                        OptimizationDimension.TEXTURE_COMPRESSION_FORMAT,
                        OptimizationDimension.DEVICE_TIER,
                        OptimizationDimension.COUNTRY_SET))
                .build());

    ImmutableList<ModuleSplit> testModuleSplits =
        moduleSplits.stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("test"))
            .collect(toImmutableList());
    ImmutableList<ModuleSplit> baseModuleSplits =
        moduleSplits.stream()
            .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals("base"))
            .collect(toImmutableList());
    assertThat(testModuleSplits).hasSize(1);
    assertThat(baseModuleSplits.size()).isGreaterThan(1);
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

  private static ModuleSplit getModuleSplit(
      ImmutableList<ModuleSplit> moduleSplits, String moduleName, Optional<String> suffix) {
    return moduleSplits.stream()
        .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals(moduleName))
        .filter(
            moduleSplit ->
                suffix.isEmpty()
                    ? moduleSplit.getSuffix().isEmpty()
                    : moduleSplit.getSuffix().equals(suffix.get()))
        .findFirst()
        .get();
  }

  private static boolean getForceUncompressed(ModuleSplit moduleSplit, String path) {
    return moduleSplit.findEntry(path).get().getForceUncompressed();
  }

  /**
   * Given a list of splits, assert that the provided and required split types are internally
   * consistent.
   */
  private static void assertConsistentRequiredSplitTypes(Collection<ModuleSplit> moduleSplits) {
    ImmutableSet.Builder<String> requiredSplitTypes = ImmutableSet.builder();
    for (ModuleSplit moduleSplit : moduleSplits) {
      requiredSplitTypes.addAll(getRequiredSplitTypes(moduleSplit));
    }

    ImmutableSet.Builder<String> providedSplitTypes = ImmutableSet.builder();
    for (ModuleSplit moduleSplit : moduleSplits) {
      providedSplitTypes.addAll(getProvidedSplitTypes(moduleSplit));
    }

    assertThat(requiredSplitTypes.build()).containsExactlyElementsIn(providedSplitTypes.build());
  }

  private static ImmutableList<String> getRequiredSplitTypes(ModuleSplit moduleSplit) {
    String value =
        moduleSplit
            .getAndroidManifest()
            .getManifestElement()
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, REQUIRED_SPLIT_TYPES_ATTRIBUTE_NAME)
            .orElse(
                XmlProtoAttribute.create(
                    DISTRIBUTION_NAMESPACE_URI, REQUIRED_SPLIT_TYPES_ATTRIBUTE_NAME))
            .getValueAsString();
    if (value.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(value.split(","));
  }

  private static ImmutableList<String> getProvidedSplitTypes(ModuleSplit moduleSplit) {
    String value =
        moduleSplit
            .getAndroidManifest()
            .getManifestElement()
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, SPLIT_TYPES_ATTRIBUTE_NAME)
            .get()
            .getValueAsString();
    if (value.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(value.split(","));
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
