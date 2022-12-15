/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.shards;

import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.model.SourceStampConstants.STAMP_SOURCE_METADATA_KEY;
import static com.android.tools.build.bundletool.model.SourceStampConstants.STAMP_TYPE_METADATA_KEY;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.LDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toScreenDensity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.StandaloneConfig;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.SourceStampConstants.StampType;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.Component;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ShardedApksFacadeTest {
  private static final Version BUNDLETOOL_VERSION = BundleToolVersion.getCurrentVersion();
  private static final ApkOptimizations DEFAULT_APK_OPTIMIZATIONS =
      ApkOptimizations.getDefaultOptimizationsForVersion(BUNDLETOOL_VERSION);
  private static final DeviceSpec DEVICE_SPEC =
      mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US"));
  private static final BundleModuleName BASE_MODULE_NAME = BundleModuleName.create("base");
  private static final BundleModuleName VR_MODULE_NAME = BundleModuleName.create("vr");

  @Inject ShardedApksFacade shardedApksFacade;

  @DataPoints("standaloneSplitTypes")
  public static final ImmutableSet<SplitType> STANDALONE_SPLIT_TYPES =
      ImmutableSet.of(SplitType.STANDALONE, SplitType.SYSTEM);

  @Before
  public void setUp() {
    TestComponent.useTestModule(this, TestModule.builder().build());
  }

  @Test
  @Theory
  public void simpleMultipleModules(
      @FromDataPoints("standaloneSplitTypes") SplitType standaloneSplitType) throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().withDeviceSpec(DEVICE_SPEC).build());

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("vr")
                .addFile("assets/test.txt")
                .setManifest(androidManifestForFeature("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        standaloneSplitType.equals(SplitType.STANDALONE)
            ? shardedApksFacade.generateSplits(bundleModule, DEFAULT_APK_OPTIMIZATIONS)
            : shardedApksFacade.generateSystemSplits(
                bundleModule,
                ImmutableSet.of(BASE_MODULE_NAME, VR_MODULE_NAME),
                DEFAULT_APK_OPTIMIZATIONS);

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit moduleSplit = moduleSplits.get(0);
    assertThat(moduleSplit.getSplitType()).isEqualTo(standaloneSplitType);
    assertThat(getEntriesPaths(moduleSplit))
        .containsExactly("assets/test.txt", "assets/leftover.txt");
    assertThat(moduleSplit.getVariantTargeting()).isEqualTo(variantMinSdkTargeting(1));
  }

  @Test
  @Theory
  public void singleModule_withNativeLibsAndDensity(
      @FromDataPoints("standaloneSplitTypes") SplitType standaloneSplitType) throws Exception {
    TestComponent.useTestModule(
        this,
        standaloneSplitType.equals(SplitType.SYSTEM)
            ? TestModule.builder().withDeviceSpec(DEVICE_SPEC).build()
            : TestModule.builder().build());

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                // Add some density-specific resources.
                .addFile("res/drawable-ldpi/image.jpg")
                .addFile("res/drawable-mdpi/image.jpg")
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addDrawableResourceForMultipleDensities(
                            "image",
                            ImmutableMap.of(
                                LDPI_VALUE,
                                "res/drawable-ldpi/image.jpg",
                                MDPI_VALUE,
                                "res/drawable-mdpi/image.jpg"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits;

    if (standaloneSplitType.equals(SplitType.SYSTEM)) {
      moduleSplits =
          shardedApksFacade.generateSystemSplits(
              bundleModule, ImmutableSet.of(BASE_MODULE_NAME), DEFAULT_APK_OPTIMIZATIONS);
      assertThat(moduleSplits).hasSize(1); // x86, mdpi split
    } else {
      moduleSplits = shardedApksFacade.generateSplits(bundleModule, DEFAULT_APK_OPTIMIZATIONS);
      assertThat(moduleSplits).hasSize(14); // 7 (density), 2 (abi) splits
    }
    assertThat(moduleSplits.stream().map(ModuleSplit::getSplitType).collect(toImmutableSet()))
        .containsExactly(standaloneSplitType);
  }

  @Test
  @Theory
  public void singleModule_withNativeLibsAndDensity_strip64bitNativeLibs(
      @FromDataPoints("standaloneSplitTypes") SplitType standaloneSplitType) throws Exception {
    Optimizations optimizations =
        Optimizations.newBuilder()
            .setStandaloneConfig(StandaloneConfig.newBuilder().setStrip64BitLibraries(true))
            .build();
    TestComponent.useTestModule(
        this,
        standaloneSplitType.equals(SplitType.SYSTEM)
            ? TestModule.builder()
                .withDeviceSpec(DEVICE_SPEC)
                .withBundleConfig(BundleConfig.newBuilder().setOptimizations(optimizations))
                .build()
            : TestModule.builder()
                .withBundleConfig(BundleConfig.newBuilder().setOptimizations(optimizations))
                .build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                // Add some density-specific resources.
                .addFile("res/drawable-ldpi/image.jpg")
                .addFile("res/drawable-mdpi/image.jpg")
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addDrawableResourceForMultipleDensities(
                            "image",
                            ImmutableMap.of(
                                LDPI_VALUE,
                                "res/drawable-ldpi/image.jpg",
                                MDPI_VALUE,
                                "res/drawable-mdpi/image.jpg"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits;

    if (standaloneSplitType.equals(SplitType.SYSTEM)) {
      moduleSplits =
          shardedApksFacade.generateSystemSplits(
              bundleModule, ImmutableSet.of(BASE_MODULE_NAME), DEFAULT_APK_OPTIMIZATIONS);
      assertThat(moduleSplits).hasSize(1); // x86, mdpi split
    } else {
      moduleSplits = shardedApksFacade.generateSplits(bundleModule, DEFAULT_APK_OPTIMIZATIONS);
      assertThat(moduleSplits).hasSize(7); // 7 (density), 1 (abi) split
    }
    // Verify that the only ABI is x86.
    ImmutableSet<Abi> abiTargetings =
        moduleSplits.stream()
            .map(ModuleSplit::getApkTargeting)
            .map(ApkTargeting::getAbiTargeting)
            .map(AbiTargeting::getValueList)
            .flatMap(List::stream)
            .collect(toImmutableSet());
    assertThat(abiTargetings).containsExactly(toAbi(X86));
    // And ABI has no alternatives.
    ImmutableSet<Abi> abiAlternatives =
        moduleSplits.stream()
            .map(ModuleSplit::getApkTargeting)
            .map(ApkTargeting::getAbiTargeting)
            .map(AbiTargeting::getAlternativesList)
            .flatMap(List::stream)
            .collect(toImmutableSet());
    assertThat(abiAlternatives).isEmpty();
    assertThat(moduleSplits.stream().map(ModuleSplit::getSplitType).collect(toImmutableSet()))
        .containsExactly(standaloneSplitType);
  }

  @Test
  public void singleModule_withNativeLibsAndDensityWithDeviceSpec_64bitNativeLibsDisabled()
      throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US")))
            .withBundleConfig(
                BundleConfig.newBuilder()
                    .setOptimizations(
                        Optimizations.newBuilder()
                            .setStandaloneConfig(
                                StandaloneConfig.newBuilder().setStrip64BitLibraries(true))))
            .build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                // Add some density-specific resources.
                .addFile("res/drawable-ldpi/image.jpg")
                .addFile("res/drawable-mdpi/image.jpg")
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addDrawableResourceForMultipleDensities(
                            "image",
                            ImmutableMap.of(
                                LDPI_VALUE,
                                "res/drawable-ldpi/image.jpg",
                                MDPI_VALUE,
                                "res/drawable-mdpi/image.jpg"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        shardedApksFacade.generateSystemSplits(
            /* modules= */ bundleModule,
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            DEFAULT_APK_OPTIMIZATIONS);

    assertThat(moduleSplits).hasSize(1); // 1 (density), 1 (abi) split
    ModuleSplit moduleSplit = moduleSplits.get(0);
    assertThat(moduleSplit.getApkTargeting().getAbiTargeting()).isEqualTo(abiTargeting(X86));
    assertThat(moduleSplit.getApkTargeting().getScreenDensityTargeting().getValueList())
        .containsExactly(toScreenDensity(DensityAlias.MDPI));
    assertThat(moduleSplits.stream().map(ModuleSplit::getSplitType).collect(toImmutableSet()))
        .containsExactly(SplitType.SYSTEM);
    assertThat(moduleSplit.isMasterSplit()).isTrue();
  }

  @Test
  public void singleModule_withNativeLibsAndLanguagesWithDeviceSpec() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withDeviceSpec(
                mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("es")))
            .build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("lib/x86/libsome.so")
                .addFile("lib/x86_64/libsome.so")
                .addFile("assets/languages#lang_es/image.jpg")
                .addFile("assets/languages#lang_fr/image.jpg")
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .setAssetsConfig(
                    assets(
                        targetedAssetsDirectory(
                            "assets/languages#lang_es",
                            assetsDirectoryTargeting(languageTargeting("es"))),
                        targetedAssetsDirectory(
                            "assets/languages#lang_fr",
                            assetsDirectoryTargeting(languageTargeting("fr")))))
                .setResourceTable(
                    new ResourceTableBuilder()
                        .addPackage("com.test.app")
                        .addStringResourceForMultipleLocales(
                            "text",
                            ImmutableMap.of(
                                /* default locale */ "", "hello", "es", "hola", "fr", "bonjour"))
                        .build())
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        shardedApksFacade.generateSystemSplits(
            /* modules= */ bundleModule,
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            DEFAULT_APK_OPTIMIZATIONS);

    assertThat(moduleSplits).hasSize(2); // fused, es split
    ImmutableMap<Boolean, ModuleSplit> splitByMasterSplitTypeMap =
        Maps.uniqueIndex(moduleSplits, ModuleSplit::isMasterSplit);

    ModuleSplit fusedSplit = splitByMasterSplitTypeMap.get(true);
    assertThat(fusedSplit.getApkTargeting())
        .isEqualTo(
            mergeApkTargeting(
                apkAbiTargeting(X86, ImmutableSet.of(X86_64)), apkLanguageTargeting("es")));
    assertThat(fusedSplit.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(fusedSplit.isMasterSplit()).isTrue();
    // fr strings missing from resource table.
    assertThat(fusedSplit.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales(
                    "text", ImmutableMap.of(/* default locale */ "", "hello", "es", "hola"))
                .build());
    assertThat(extractPaths(fusedSplit.getEntries()))
        .containsExactly("lib/x86/libsome.so", "assets/languages#lang_es/image.jpg");
    assertThat(fusedSplit.getAndroidManifest().getSplitId()).isEmpty();

    ModuleSplit frLangSplit = splitByMasterSplitTypeMap.get(false);
    assertThat(frLangSplit.getApkTargeting()).isEqualTo(apkLanguageTargeting("fr"));
    assertThat(frLangSplit.getVariantTargeting()).isEqualTo(fusedSplit.getVariantTargeting());
    assertThat(frLangSplit.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(frLangSplit.isMasterSplit()).isFalse();
    assertThat(frLangSplit.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales("text", ImmutableMap.of("fr", "bonjour"))
                .build());
    assertThat(extractPaths(frLangSplit.getEntries()))
        .containsExactly("assets/languages#lang_fr/image.jpg");
    assertThat(frLangSplit.getAndroidManifest().getSplitId()).hasValue("config.fr");
  }

  @Test
  public void multipleModule_withLanguages() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withDeviceSpec(
                mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("fr")))
            .build());
    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/languages#lang_es/image.jpg")
                .addFile("assets/languages#lang_fr/image.jpg")
                .setAssetsConfig(
                    assets(
                        targetedAssetsDirectory(
                            "assets/languages#lang_es",
                            assetsDirectoryTargeting(languageTargeting("es"))),
                        targetedAssetsDirectory(
                            "assets/languages#lang_fr",
                            assetsDirectoryTargeting(languageTargeting("fr")))))
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("vr")
                .addFile("assets/vr/languages#lang_es/image.jpg")
                .addFile("assets/vr/languages#lang_fr/image.jpg")
                .addFile("assets/vr/languages#lang_it/image.jpg")
                .setAssetsConfig(
                    assets(
                        targetedAssetsDirectory(
                            "assets/vr/languages#lang_es",
                            assetsDirectoryTargeting(languageTargeting("es"))),
                        targetedAssetsDirectory(
                            "assets/vr/languages#lang_fr",
                            assetsDirectoryTargeting(languageTargeting("fr"))),
                        targetedAssetsDirectory(
                            "assets/vr/languages#lang_it",
                            assetsDirectoryTargeting(languageTargeting("it")))))
                .setManifest(androidManifestForFeature("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        shardedApksFacade.generateSystemSplits(
            /* modules= */ bundleModule,
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, VR_MODULE_NAME),
            DEFAULT_APK_OPTIMIZATIONS);

    assertThat(moduleSplits).hasSize(3); // fused, base-es, vr-es, vr-it splits
    ImmutableMap<String, ModuleSplit> splitBySplitIdMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getAndroidManifest().getSplitId().orElse(""));

    assertThat(splitBySplitIdMap.keySet()).containsExactly("", "config.es", "config.it");

    ModuleSplit fusedSplit = splitBySplitIdMap.get("");
    assertThat(fusedSplit.getApkTargeting()).isEqualTo(apkLanguageTargeting("fr"));
    assertThat(fusedSplit.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(fusedSplit.isMasterSplit()).isTrue();
    assertThat(extractPaths(fusedSplit.getEntries()))
        .containsExactly(
            "assets/languages#lang_fr/image.jpg", "assets/vr/languages#lang_fr/image.jpg");

    ModuleSplit esBaseSplit = splitBySplitIdMap.get("config.es");
    assertThat(esBaseSplit.getApkTargeting()).isEqualTo(apkLanguageTargeting("es"));
    assertThat(esBaseSplit.getVariantTargeting()).isEqualTo(fusedSplit.getVariantTargeting());
    assertThat(esBaseSplit.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(esBaseSplit.isMasterSplit()).isFalse();
    assertThat(extractPaths(esBaseSplit.getEntries()))
        .containsExactly(
            "assets/languages#lang_es/image.jpg", "assets/vr/languages#lang_es/image.jpg");

    ModuleSplit itBaseSplit = splitBySplitIdMap.get("config.it");
    assertThat(itBaseSplit.getApkTargeting()).isEqualTo(apkLanguageTargeting("it"));
    assertThat(itBaseSplit.getVariantTargeting()).isEqualTo(fusedSplit.getVariantTargeting());
    assertThat(itBaseSplit.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(itBaseSplit.isMasterSplit()).isFalse();
    assertThat(extractPaths(itBaseSplit.getEntries()))
        .containsExactly("assets/vr/languages#lang_it/image.jpg");
  }

  @Test
  public void testStandaloneApk_addsStamp() throws Exception {
    String stampSource = "https://www.example.com";
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withSourceStamp(
                SourceStamp.builder()
                    .setSource(stampSource)
                    .setSigningConfiguration(
                        SigningConfiguration.builder()
                            .setSignerConfig(mock(PrivateKey.class), mock(X509Certificate.class))
                            .build())
                    .build())
            .build());

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("vr")
                .addFile("assets/test.txt")
                .setManifest(androidManifestForFeature("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        shardedApksFacade.generateSplits(bundleModule, DEFAULT_APK_OPTIMIZATIONS);

    assertThat(moduleSplits).hasSize(1);
    ModuleSplit moduleSplit = getOnlyElement(moduleSplits);
    assertThat(moduleSplit.getAndroidManifest().getMetadataValue(STAMP_TYPE_METADATA_KEY))
        .hasValue(StampType.STAMP_TYPE_STANDALONE_APK.toString());
    assertThat(moduleSplit.getAndroidManifest().getMetadataValue(STAMP_SOURCE_METADATA_KEY))
        .hasValue(stampSource);
  }

  private static ImmutableSet<String> getEntriesPaths(ModuleSplit moduleSplit) {
    return moduleSplit.getEntries().stream()
        .map(moduleEntry -> moduleEntry.getPath().toString())
        .collect(toImmutableSet());
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(ShardedApksFacadeTest test);

    static void useTestModule(ShardedApksFacadeTest testInstance, TestModule testModule) {
      DaggerShardedApksFacadeTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
