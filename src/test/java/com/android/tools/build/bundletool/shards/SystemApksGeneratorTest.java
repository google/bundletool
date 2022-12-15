/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DEFAULT_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.HDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.LDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTitle;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.TEST_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTableWithTestLabel;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAlternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toScreenDensity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.splitters.RuntimeEnabledSdkTableInjector;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import dagger.Component;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SystemApksGeneratorTest {

  private static final DeviceSpec DEVICE_SPEC =
      mergeSpecs(sdkVersion(28), abis("x86"), density(DensityAlias.MDPI), locales("en-US"));

  private static final BundleModuleName FEATURE_MODULE_NAME = BundleModuleName.create("feature");
  private static final BundleModuleName VR_MODULE_NAME = BundleModuleName.create("vr");

  private static final BundleMetadata BUNDLE_METADATA_WITH_TRANSPARENCY =
      BundleMetadata.builder()
          .addFile(
              BundleMetadata.BUNDLETOOL_NAMESPACE,
              BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
              ByteSource.empty())
          .build();

  @Inject SystemApksGenerator systemApksGenerator;

  @Before
  public void setUp() {
    SystemApksGeneratorTest.TestComponent.useTestModule(
        this, TestModule.builder().withDeviceSpec(DEVICE_SPEC).build());
  }

  @Test
  public void shardByAbi_havingNoNativeTargeting_producesOneApk() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            DEFAULT_DENSITY_VALUE,
                            "res/drawable/image.jpg",
                            MDPI_VALUE,
                            "res/drawable-mdpi/image.jpg"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting()).isEqualTo(variantMinSdkTargeting(1));

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "lib/x86_64/libtest.so",
            "res/drawable/image.jpg",
            "res/drawable-mdpi/image.jpg",
            "root/license.dat");
  }

  @Test
  public void producesOneApk_withTransparency() throws Exception {
    SystemApksGeneratorTest.TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY)
            .withDeviceSpec(DEVICE_SPEC)
            .build());
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "META-INF/code_transparency_signed.jwt");
  }

  @Test
  public void shardByAbi_havingSingleAbi_producesOneApk() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            DEFAULT_DENSITY_VALUE,
                            "res/drawable/image.jpg",
                            MDPI_VALUE,
                            "res/drawable-mdpi/image.jpg"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualTo(apkAbiTargeting(X86));
    assertThat(fatShard.getVariantTargeting())
        .isEqualTo(mergeVariantTargeting(variantMinSdkTargeting(1), variantAbiTargeting(X86)));

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);

    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "res/drawable/image.jpg",
            "res/drawable-mdpi/image.jpg",
            "root/license.dat");
  }

  @Test
  public void shardByAbi_havingManyAbisForSystemShards_producesSingleApk() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("armeabi"), density(DensityAlias.MDPI), locales("en-US")))
            .build());

    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/armeabi/libtest.so")
            .addFile("lib/x86/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/armeabi", nativeDirectoryTargeting(ARMEABI)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            DEFAULT_DENSITY_VALUE,
                            "res/drawable/image.jpg",
                            MDPI_VALUE,
                            "res/drawable-mdpi/image.jpg"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit armeabiShard = shards.get(0);
    assertThat(armeabiShard.getApkTargeting())
        .isEqualTo(apkAbiTargeting(ARMEABI, ImmutableSet.of(X86, X86_64)));
    assertThat(armeabiShard.getVariantTargeting())
        .isEqualTo(
            mergeVariantTargeting(
                variantMinSdkTargeting(1),
                variantAbiTargeting(ARMEABI, ImmutableSet.of(X86, X86_64))));
    assertThat(armeabiShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(extractPaths(armeabiShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/armeabi/libtest.so",
            "res/drawable/image.jpg",
            "res/drawable-mdpi/image.jpg",
            "root/license.dat");
  }

  @Test
  public void shardByAbi_havingManyModulesWithLanguagesForSystemShards() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withDeviceSpec(
                mergeSpecs(
                    sdkVersion(28), abis("armeabi"), density(DensityAlias.MDPI), locales("es")))
            .build());
    BundleModule baseModule =
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
            .setManifest(androidManifest("com.app"))
            .setResourceTable(resourceTableWithTestLabel("Test feature"))
            .build();

    BundleModule vrModule =
        new BundleModuleBuilder("vr")
            .addFile("assets/vr/languages#lang_es/image.jpg")
            .addFile("assets/vr/languages#lang_fr/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/vr/languages#lang_es",
                        assetsDirectoryTargeting(languageTargeting("es"))),
                    targetedAssetsDirectory(
                        "assets/vr/languages#lang_fr",
                        assetsDirectoryTargeting(languageTargeting("fr")))))
            .setManifest(
                androidManifestForFeature(
                    "com.app",
                    withFusingAttribute(true),
                    withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID)))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, vrModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, VR_MODULE_NAME),
            splitOptimizations(
                OptimizationDimension.ABI,
                OptimizationDimension.LANGUAGE,
                OptimizationDimension.SCREEN_DENSITY));

    ModuleSplit fusedShard = shards.get(0);
    assertThat(fusedShard.isBaseModuleSplit()).isTrue();

    assertThat(extractPaths(fusedShard.getEntries()))
        .containsExactly(
            "assets/vr/languages#lang_es/image.jpg", "assets/languages#lang_es/image.jpg");

    ImmutableList<ModuleSplit> langSplits = shards.subList(1, shards.size());
    assertThat(langSplits).hasSize(1);
    ImmutableMap<String, ModuleSplit> langSplitsNameMap =
        Maps.uniqueIndex(langSplits, split -> split.getModuleName().getName());

    assertThat(langSplitsNameMap.keySet()).containsExactly("base");

    ModuleSplit frBaseSplit = langSplitsNameMap.get("base");
    assertThat(extractPaths(frBaseSplit.getEntries()))
        .containsExactly(
            "assets/languages#lang_fr/image.jpg", "assets/vr/languages#lang_fr/image.jpg");
    assertThat(frBaseSplit.getAndroidManifest().getSplitId()).hasValue("config.fr");
  }

  @Test
  public void producesTwoApks_withTransparency() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY)
            .withDeviceSpec(DEVICE_SPEC)
            .build());
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("assets/languages#lang_fr/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/languages#lang_fr",
                        assetsDirectoryTargeting(languageTargeting("fr")))))
            .setManifest(androidManifest("com.app"))
            .build();

    BundleModule vrModule =
        new BundleModuleBuilder("vr")
            .addFile("assets/vr/languages#lang_fr/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/vr/languages#lang_fr",
                        assetsDirectoryTargeting(languageTargeting("fr")))))
            .setManifest(
                androidManifestForFeature(
                    "com.app",
                    withFusingAttribute(true),
                    withTitle("@string/test_label", TEST_LABEL_RESOURCE_ID)))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, vrModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, VR_MODULE_NAME),
            splitOptimizations(OptimizationDimension.LANGUAGE));

    assertThat(shards).hasSize(2);
    
    ModuleSplit fusedShard = shards.get(0);
    assertThat(fusedShard.isBaseModuleSplit()).isTrue();
    assertThat(extractPaths(fusedShard.getEntries()))
        .containsExactly("META-INF/code_transparency_signed.jwt");

    ModuleSplit languageSplit = shards.get(1);
    assertThat(extractPaths(languageSplit.getEntries()))
        .containsExactly(
            "assets/languages#lang_fr/image.jpg", "assets/vr/languages#lang_fr/image.jpg");
  }

  @Test
  public void shardByAbiAndDensity_havingNoAbiAndNoResources_producesOneApk() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting()).isEqualTo(variantMinSdkTargeting(1));

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);

    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly("assets/file.txt", "dex/classes.dex", "root/license.dat");
  }

  @Test
  public void
      shardByAbiAndDensity_havingOneAbiAndSomeDensityResourceWithDeviceSpec_producesSingleApk()
          throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("res/drawable-ldpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            LDPI_VALUE,
                            "res/drawable-ldpi/image.jpg",
                            HDPI_VALUE,
                            "res/drawable-hdpi/image.jpg"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    // 1 shards: {x86} x {MDPI}.
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting().getAbiTargeting()).isEqualTo(abiTargeting(X86));
    assertThat(fatShard.getApkTargeting().getScreenDensityTargeting().getValueList())
        .containsExactly(toScreenDensity(DensityAlias.MDPI));
    assertThat(fatShard.getVariantTargeting().getAbiTargeting()).isEqualTo(abiTargeting(X86));
    assertThat(fatShard.getVariantTargeting().getScreenDensityTargeting().getValueList())
        .containsExactly(toScreenDensity(DensityAlias.MDPI));

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    // The MDPI shard would match both hdpi and ldpi variant of the resource.
    assertThat(fatShard.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(2);
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "res/drawable-hdpi/image.jpg",
            "res/drawable-ldpi/image.jpg",
            "root/license.dat");
  }

  @Test
  public void shardByAbiAndDensity_havingOneAbiAndOneDensityMultipleLanguageResourceWithDeviceSpec()
      throws Exception {
    TestComponent.useTestModule(
        this, TestModule.builder().withDeviceSpec(mergeSpecs(DEVICE_SPEC, locales("fr"))).build());
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("root/license.dat")
            .addFile("assets/languages#lang_es/image.jpg")
            .addFile("assets/languages#lang_fr/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
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
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(
                OptimizationDimension.ABI,
                OptimizationDimension.SCREEN_DENSITY,
                OptimizationDimension.LANGUAGE));

    // shard {x86} x {MDPI} x {fr}
    ModuleSplit fatShard = getSystemImageSplit(shards);
    assertThat(fatShard.getApkTargeting())
        .isEqualTo(mergeApkTargeting(apkAbiTargeting(X86), apkLanguageTargeting("fr")));
    assertThat(fatShard.getVariantTargeting())
        .isEqualTo(mergeVariantTargeting(variantAbiTargeting(X86), variantMinSdkTargeting(1)));
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    // es strings missing from resource table.
    assertThat(fatShard.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales(
                    "text", ImmutableMap.of(/* default locale */ "", "hello", "fr", "bonjour"))
                .build());
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "root/license.dat",
            "assets/languages#lang_fr/image.jpg");

    ModuleSplit esLangShard = Iterables.getOnlyElement(getAdditionalSplits(shards));

    assertThat(esLangShard.getApkTargeting()).isEqualTo(apkLanguageTargeting("es"));
    assertThat(esLangShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(esLangShard.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales("text", ImmutableMap.of("es", "hola"))
                .build());
    assertThat(extractPaths(esLangShard.getEntries()))
        .containsExactly("assets/languages#lang_es/image.jpg");
  }

  @Test
  public void
      shardByAbiAndDensity_multipleLanguageResourceAndDeviceSpecMissingLanguage_fallsBackToDefault()
          throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("root/license.dat")
            .addFile("assets/languages#lang_es/image.jpg")
            .addFile("assets/languages#lang_fr/image.jpg")
            .addFile("assets/languages/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/languages#lang_es",
                        assetsDirectoryTargeting(languageTargeting("es"))),
                    targetedAssetsDirectory(
                        "assets/languages#lang_fr",
                        assetsDirectoryTargeting(languageTargeting("fr"))),
                    targetedAssetsDirectory(
                        "assets/languages",
                        assetsDirectoryTargeting(alternativeLanguageTargeting("es", "fr")))))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addStringResourceForMultipleLocales(
                        "text",
                        ImmutableMap.of(
                            /* default locale */ "", "hello", "es", "hola", "fr", "bonjour"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(bundleModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations(
                OptimizationDimension.ABI,
                OptimizationDimension.SCREEN_DENSITY,
                OptimizationDimension.LANGUAGE));

    ModuleSplit fatShard = getSystemImageSplit(shards);
    assertThat(fatShard.getApkTargeting())
        .isEqualTo(
            mergeApkTargeting(apkAbiTargeting(X86), apkAlternativeLanguageTargeting("es", "fr")));
    assertThat(fatShard.getApkTargeting().getAbiTargeting()).isEqualTo(abiTargeting(X86));
    assertThat(fatShard.getApkTargeting().getScreenDensityTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting())
        .isEqualTo(mergeVariantTargeting(variantMinSdkTargeting(1), variantAbiTargeting(X86)));
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(fatShard.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales(
                    "text", ImmutableMap.of(/* default locale */ "", "hello"))
                .build());
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "root/license.dat",
            "assets/languages/image.jpg");

    ImmutableMap<LanguageTargeting, ModuleSplit> splitLanguageTargetingMap =
        Maps.uniqueIndex(
            getAdditionalSplits(shards), split -> split.getApkTargeting().getLanguageTargeting());

    assertThat(splitLanguageTargetingMap.keySet())
        .containsExactly(languageTargeting("es"), languageTargeting("fr"));

    ModuleSplit esLangShard = splitLanguageTargetingMap.get(languageTargeting("es"));
    assertThat(esLangShard.getApkTargeting()).isEqualTo(apkLanguageTargeting("es"));
    assertThat(esLangShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(esLangShard.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales("text", ImmutableMap.of("es", "hola"))
                .build());
    assertThat(extractPaths(esLangShard.getEntries()))
        .containsExactly("assets/languages#lang_es/image.jpg");

    ModuleSplit frLangShard = splitLanguageTargetingMap.get(languageTargeting("fr"));
    assertThat(frLangShard.getApkTargeting()).isEqualTo(apkLanguageTargeting("fr"));
    assertThat(frLangShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(frLangShard.getResourceTable().get())
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addStringResourceForMultipleLocales("text", ImmutableMap.of("fr", "bonjour"))
                .build());
    assertThat(extractPaths(frLangShard.getEntries()))
        .containsExactly("assets/languages#lang_fr/image.jpg");
  }

  @Test
  public void
      manyModulesShardByAbiAndDensity_havingManyAbisAndSomeResourceWithDeviceSpec_producesSingleApk()
          throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image",
                        ImmutableMap.of(
                            DEFAULT_DENSITY_VALUE,
                            "res/drawable/image.jpg",
                            HDPI_VALUE,
                            "res/drawable-hdpi/image.jpg"))
                    .build())
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("lib/armeabi/libtest.so")
            .addFile("lib/x86/libtest.so")
            .setManifest(androidManifestForFeature("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/armeabi", nativeDirectoryTargeting(ARMEABI)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, featureModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, FEATURE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    ModuleSplit fatShard = getSystemImageSplit(shards);
    assertThat(fatShard.getApkTargeting().getAbiTargeting())
        .isEqualTo(abiTargeting(X86, ImmutableSet.of(ARMEABI)));
    assertThat(fatShard.getApkTargeting().getScreenDensityTargeting().getValueList())
        .containsExactly(toScreenDensity(DensityAlias.MDPI));
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(fatShard.isBaseModuleSplit()).isTrue();
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "res/drawable/image.jpg",
            "root/license.dat");
    assertThat(shards).hasSize(1);
  }

  @Test
  public void manyModulesShardByDensity_havingOnlyOneDensityResource_producesSingleApk()
      throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("res/drawable-hdpi/image.jpg")
            .setManifest(androidManifest("com.test.app"))
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image",
                                fileReference("res/drawable-hdpi/image.jpg", HDPI))))))
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("res/drawable-hdpi/image2.jpg")
            .setManifest(androidManifestForFeature("com.test.app"))
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET + 1,
                        "com.test.app.split",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image2",
                                fileReference("res/drawable-hdpi/image2.jpg", HDPI))))))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, featureModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, FEATURE_MODULE_NAME),
            splitOptimizations(OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit shard = shards.get(0);
    assertThat(extractPaths(shard.findEntriesUnderPath("res")))
        .containsExactly("res/drawable-hdpi/image.jpg", "res/drawable-hdpi/image2.jpg");
    assertThat(shard.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(1);
    assertThat(shard.getResourceTable().get())
        .containsResource("com.test.app.split:drawable/image2")
        .withConfigSize(1);
    assertThat(shard.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void manyModulesShardByNoDimension_producesFatApk() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("lib/x86_64/libtest1.so")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .addFile("res/drawable-ldpi/image1.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET,
                        "com.test.app",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image1",
                                fileReference("res/drawable-ldpi/image1.jpg", LDPI))))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("lib/x86_64/libtest2.so")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .addFile("res/drawable-ldpi/image2.jpg")
            .setResourceTable(
                resourceTable(
                    pkg(
                        USER_PACKAGE_OFFSET + 1,
                        "com.test.app.split",
                        type(
                            0x01,
                            "drawable",
                            entry(
                                0x01,
                                "image2",
                                fileReference("res/drawable-ldpi/image2.jpg", LDPI))))))
            .setManifest(androidManifestForFeature("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, featureModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, FEATURE_MODULE_NAME),
            splitOptimizations());

    assertThat(shards).hasSize(1);
    ModuleSplit fatApk = shards.get(0);
    assertThat(extractPaths(fatApk.getEntries()))
        .containsExactly(
            "lib/x86_64/libtest1.so",
            "lib/x86_64/libtest2.so",
            "res/drawable-ldpi/image1.jpg",
            "res/drawable-ldpi/image2.jpg");
    assertThat(fatApk.getResourceTable()).isPresent();
    assertThat(fatApk.getResourceTable().get())
        .containsResource("com.test.app:drawable/image1")
        .onlyWithConfigs(LDPI);
    assertThat(fatApk.getResourceTable().get())
        .containsResource("com.test.app.split:drawable/image2")
        .onlyWithConfigs(LDPI);
  }

  @Test
  public void uncompressedNativeLibraries_enabled_singleApk() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("assets/images/img.png")
            .addFile("res/layout/default.xml")
            .addFile("lib/x86_64/libtest1.so")
            .addFile("lib/x86/libtest1.so")
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addFileResource("layout", "default", "res/layout/default.xml")
                    .build())
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setManifest(androidManifestForFeature("com.test.app"))
            .build();

    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("lib/x86_64/libtest2.so")
            .addFile("lib/x86/libtest2.so")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setManifest(androidManifestForFeature("com.test.app.feature"))
            .build();

    ApkOptimizations apkOptimizations =
        splitOptimizations(OptimizationDimension.ABI).toBuilder()
            .setUncompressNativeLibraries(true)
            .build();
    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, featureModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME, FEATURE_MODULE_NAME),
            apkOptimizations);

    assertThat(shards).hasSize(1);
    ModuleSplit fatApk = shards.get(0);
    assertThat(fatApk.getAndroidManifest().getExtractNativeLibsValue()).hasValue(false);
    assertThat(fatApk.findEntry("lib/x86/libtest1.so").map(ModuleEntry::getForceUncompressed))
        .hasValue(true);
    assertThat(fatApk.findEntry("lib/x86/libtest2.so").map(ModuleEntry::getForceUncompressed))
        .hasValue(true);
    assertThat(fatApk.findEntry("res/layout/default.xml").map(ModuleEntry::getForceUncompressed))
        .hasValue(false);
    assertThat(fatApk.findEntry("assets/images/img.png").map(ModuleEntry::getForceUncompressed))
        .hasValue(false);
    assertThat(fatApk.findEntry("lib/x86_64/libtest1.so")).isEmpty();
  }

  @Test
  public void uncompressedNativeLibraries_enabled_multiApks() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("lib/x86_64/libtest1.so")
            .addFile("lib/x86/libtest1.so")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setManifest(androidManifestForFeature("com.test.app"))
            .build();

    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("lib/x86_64/libtest2.so")
            .addFile("lib/x86/libtest2.so")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setManifest(androidManifestForFeature("com.test.app.feature"))
            .build();

    ApkOptimizations apkOptimizations =
        splitOptimizations(OptimizationDimension.ABI).toBuilder()
            .setUncompressNativeLibraries(true)
            .build();
    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule, featureModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            apkOptimizations);

    assertThat(shards).hasSize(3);
    ModuleSplit systemApk = getSystemImageSplit(shards);
    assertThat(systemApk.getAndroidManifest().getExtractNativeLibsValue()).hasValue(false);
    assertThat(systemApk.findEntry("lib/x86/libtest1.so").map(ModuleEntry::getForceUncompressed))
        .hasValue(true);

    ModuleSplit additionalNativeSplit =
        getAdditionalSplits(shards).stream()
            .filter(split -> split.getApkTargeting().hasAbiTargeting())
            .collect(onlyElement());
    assertThat(
            additionalNativeSplit
                .findEntry("lib/x86/libtest2.so")
                .map(ModuleEntry::getForceUncompressed))
        .hasValue(true);
  }

  @Test
  public void uncompressedNativeLibraries_disabled() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("lib/x86_64/libtest1.so")
            .addFile("lib/x86/libtest1.so")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .setManifest(androidManifestForFeature("com.test.app"))
            .build();

    ApkOptimizations apkOptimizations =
        splitOptimizations(OptimizationDimension.ABI).toBuilder()
            .setUncompressNativeLibraries(false)
            .build();
    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            apkOptimizations);

    assertThat(shards).hasSize(1);
    ModuleSplit fatApk = shards.get(0);
    assertThat(fatApk.getAndroidManifest().getExtractNativeLibsValue()).isEmpty();
    assertThat(fatApk.findEntry("lib/x86/libtest1.so").map(ModuleEntry::getForceUncompressed))
        .hasValue(false);
    assertThat(fatApk.findEntry("lib/x86_64/libtest1.so")).isEmpty();
  }

  @Test
  public void uncompressedDexFiles_enabled() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifestForFeature("com.test.app"))
            .build();

    ApkOptimizations apkOptimizations =
        splitOptimizations().toBuilder().setUncompressDexFiles(true).build();
    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            apkOptimizations);

    assertThat(shards).hasSize(1);
    ModuleSplit fatApk = shards.get(0);
    assertThat(fatApk.findEntry("dex/classes.dex").map(ModuleEntry::getForceUncompressed))
        .hasValue(true);
  }

  @Test
  public void uncompressedDexFiles_disabled() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifestForFeature("com.test.app"))
            .build();

    ApkOptimizations apkOptimizations =
        splitOptimizations().toBuilder().setUncompressDexFiles(false).build();
    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            apkOptimizations);

    assertThat(shards).hasSize(1);
    ModuleSplit fatApk = shards.get(0);
    assertThat(fatApk.findEntry("dex/classes.dex").map(ModuleEntry::getForceUncompressed))
        .hasValue(false);
  }

  @Test
  public void systemApks_injectBinaryArtProfiles() throws Exception {
    byte[] content = new byte[] {1, 4, 3, 2};
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withDeviceSpec(DEVICE_SPEC)
            .withBundleMetadata(
                BundleMetadata.builder()
                    .addFile(
                        "com.android.tools.build.profiles",
                        "baseline.prof",
                        ByteSource.wrap(content))
                    .build())
            .build());
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations());

    assertThat(shards).hasSize(1);
    assertThat(shards.get(0).getSplitType()).isEqualTo(SplitType.SYSTEM);
    assertThat(extractPaths(shards.get(0).getEntries()))
        .containsAtLeast("dex/classes.dex", "assets/dexopt/baseline.prof");

    byte[] actualContent =
        shards
            .get(0)
            .findEntry(ZipPath.create("assets/dexopt/baseline.prof"))
            .get()
            .getContent()
            .read();
    assertThat(actualContent).isEqualTo(content);
  }

  @Test
  public void appBundleHasRuntimeEnabledSdkDependencies_injectsRuntimeEnabledSdkTable()
      throws Exception {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest("certdigest"))
            .build();
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(baseModule).build();
    TestComponent.useTestModule(
        this, TestModule.builder().withDeviceSpec(DEVICE_SPEC).withAppBundle(appBundle).build());

    ImmutableList<ModuleSplit> shards =
        systemApksGenerator.generateSystemApks(
            /* modules= */ ImmutableList.of(baseModule),
            /* modulesToFuse= */ ImmutableSet.of(BASE_MODULE_NAME),
            splitOptimizations());

    assertThat(shards).hasSize(1);
    assertThat(
            shards
                .get(0)
                .findEntry(
                    ZipPath.create(
                        RuntimeEnabledSdkTableInjector.RUNTIME_ENABLED_SDK_TABLE_FILE_PATH)))
        .isPresent();
  }

  private static ApkOptimizations splitOptimizations(OptimizationDimension... dimensions) {
    return ApkOptimizations.builder()
        .setSplitDimensions(ImmutableSet.copyOf(dimensions))
        .setStandaloneDimensions(ImmutableSet.copyOf(dimensions))
        .build();
  }

  private static ModuleSplit getSystemImageSplit(ImmutableList<ModuleSplit> splits) {
    return splits.stream()
        .filter(split -> split.getModuleName().equals(BASE_MODULE_NAME) && split.isMasterSplit())
        .collect(onlyElement());
  }

  private static ImmutableList<ModuleSplit> getAdditionalSplits(ImmutableList<ModuleSplit> splits) {
    return splits.stream()
        .filter(
            not(split -> split.getModuleName().equals(BASE_MODULE_NAME) && split.isMasterSplit()))
        .collect(toImmutableList());
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(SystemApksGeneratorTest test);

    static void useTestModule(SystemApksGeneratorTest testInstance, TestModule testModule) {
      DaggerSystemApksGeneratorTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
