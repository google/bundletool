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
import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DEFAULT_DENSITY_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.HDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.LDPI_VALUE;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.MDPI_VALUE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMainActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.xmlAttribute;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toScreenDensity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.fail;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Config.SplitsConfig;
import com.android.bundle.Config.StandaloneConfig;
import com.android.bundle.Config.StandaloneConfig.DexMergingStrategy;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.splitters.RuntimeEnabledSdkTableInjector;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.protobuf.Message;
import dagger.Component;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StandaloneApksGeneratorTest {

  private static final ApkOptimizations NO_DIMENSIONS =
      ApkOptimizations.builder()
          .setSplitDimensions(ImmutableSet.of())
          .setStandaloneDimensions(ImmutableSet.of())
          .build();

  private static final VariantTargeting VARIANT_TARGETING_WITH_SDK_1 = variantSdkTargeting(1);

  private static final ImmutableSet<DensityAlias> ALL_DENSITIES =
      ImmutableSet.of(
          DensityAlias.LDPI,
          DensityAlias.MDPI,
          DensityAlias.TVDPI,
          DensityAlias.HDPI,
          DensityAlias.XHDPI,
          DensityAlias.XXHDPI,
          DensityAlias.XXXHDPI);
  private static final ApkTargeting LDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.LDPI, Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.LDPI)));
  private static final ApkTargeting MDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.MDPI, Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.MDPI)));
  private static final ApkTargeting TVDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.TVDPI, Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.TVDPI)));
  private static final ApkTargeting HDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.HDPI, Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.HDPI)));
  private static final ApkTargeting XHDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.XHDPI, Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.XHDPI)));
  private static final ApkTargeting XXHDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.XXHDPI,
          Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.XXHDPI)));
  private static final ApkTargeting XXXHDPI_TARGETING =
      apkDensityTargeting(
          DensityAlias.XXXHDPI,
          Sets.difference(ALL_DENSITIES, ImmutableSet.of(DensityAlias.XXXHDPI)));

  private static final BundleMetadata BUNDLE_METADATA_WITH_TRANSPARENCY =
      BundleMetadata.builder()
          .addFile(
              BundleMetadata.BUNDLETOOL_NAMESPACE,
              BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
              ByteSource.empty())
          .build();

  private static final BundleConfig.Builder bundleConfigNodexmergestrategy =
      BundleConfig.newBuilder()
          .setOptimizations(
              Optimizations.newBuilder()
                  .setStandaloneConfig(
                      StandaloneConfig.newBuilder()
                          .setDexMergingStrategy(DexMergingStrategy.NEVER_MERGE)));

  @Inject StandaloneApksGenerator standaloneApksGenerator;

  @Before
  public void setUp() {
    TestComponent.useTestModule(this, TestModule.builder().build());
  }

  @Test
  public void shardByNoDimension_producesOneApk() throws Exception {
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
            .setNativeConfig(
                nativeLibraries(
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), NO_DIMENSIONS);

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting()).isEqualTo(VARIANT_TARGETING_WITH_SDK_1);
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);
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
  public void shardByNoDimension_keepMultipleTcfTargetedDirectories() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/data#tcf_etc1/file.txt")
            .addFile("assets/data#tcf_atc/file.txt")
            .addFile("lib/x86/libtest.so")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/data#tcf_etc1",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(
                                TextureCompressionFormatAlias.ETC1_RGB8,
                                ImmutableSet.of(TextureCompressionFormatAlias.ATC)))),
                    targetedAssetsDirectory(
                        "assets/data#tcf_atc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(
                                TextureCompressionFormatAlias.ATC,
                                ImmutableSet.of(TextureCompressionFormatAlias.ETC1_RGB8))))))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), NO_DIMENSIONS);

    assertThat(shards).hasSize(1);
    assertThat(extractPaths(shards.get(0).getEntries()))
        .containsExactly(
            "assets/data#tcf_etc1/file.txt",
            "assets/data#tcf_atc/file.txt",
            "lib/x86/libtest.so",
            "root/license.dat");
  }


  @Test
  public void removeSplitNameFromShard() throws Exception {
    XmlNode manifest =
        androidManifest(
            "com.test.app",
            withMainActivity("MainActivity"),
            withSplitNameActivity("FooActivity", "foo"));
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(manifest)
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), NO_DIMENSIONS);

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    ImmutableList<XmlElement> activities =
        fatShard
            .getAndroidManifest()
            .getManifestRoot()
            .getElement()
            .getChildElement("application")
            .getChildrenElements(ACTIVITY_ELEMENT_NAME)
            .map(XmlProtoElement::getProto)
            .collect(toImmutableList());
    assertThat(activities).hasSize(2);
    XmlElement activityElement = activities.get(1);
    assertThat(activityElement.getAttributeList())
        .containsExactly(
            xmlAttribute(ANDROID_NAMESPACE_URI, "name", NAME_RESOURCE_ID, "FooActivity"));
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting()).isEqualTo(VARIANT_TARGETING_WITH_SDK_1);

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);
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
  public void shardByAbi_havingSingleAbi_producesOneApk() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86_64/libtest.so")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualTo(apkAbiTargeting(AbiAlias.X86_64));
    assertThat(fatShard.getVariantTargeting())
        .isEqualTo(
            mergeVariantTargeting(variantAbiTargeting(X86_64), VARIANT_TARGETING_WITH_SDK_1));

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);

    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86_64/libtest.so",
            "res/drawable/image.jpg",
            "res/drawable-mdpi/image.jpg",
            "root/license.dat");
  }

  @Test
  public void shardByAbi_havingManyAbis_producesManyApks() throws Exception {
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(3);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

    VariantTargeting armVariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(ARMEABI, ImmutableSet.of(X86, X86_64)),
            VARIANT_TARGETING_WITH_SDK_1);
    VariantTargeting x86VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(X86, ImmutableSet.of(ARMEABI, X86_64)),
            VARIANT_TARGETING_WITH_SDK_1);
    VariantTargeting x64VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(X86_64, ImmutableSet.of(ARMEABI, X86)),
            VARIANT_TARGETING_WITH_SDK_1);

    assertThat(
            shards.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(armVariantTargeting, x64VariantTargeting, x86VariantTargeting);
    for (ModuleSplit shard : shards) {
      assertThat(extractPaths(shard.getEntries()))
          .containsAtLeast(
              "assets/file.txt",
              "dex/classes.dex",
              "res/drawable/image.jpg",
              "res/drawable-mdpi/image.jpg",
              "root/license.dat");
    }
    ApkTargeting armTargeting = apkAbiTargeting(ARMEABI, ImmutableSet.of(X86, X86_64));
    ApkTargeting x86Targeting = apkAbiTargeting(X86, ImmutableSet.of(ARMEABI, X86_64));
    ApkTargeting x64Targeting = apkAbiTargeting(X86_64, ImmutableSet.of(ARMEABI, X86));
    ImmutableMap<ApkTargeting, ModuleSplit> shardsByTargeting =
        Maps.uniqueIndex(shards, ModuleSplit::getApkTargeting);
    assertThat(shardsByTargeting.keySet())
        .containsExactly(armTargeting, x86Targeting, x64Targeting);
    assertThat(extractPaths(shardsByTargeting.get(armTargeting).getEntries()))
        .contains("lib/armeabi/libtest.so");
    assertThat(extractPaths(shardsByTargeting.get(armTargeting).getEntries()))
        .containsNoneOf("lib/x86/libtest.so", "lib/x86_64/libtest.so");
    assertThat(extractPaths(shardsByTargeting.get(x86Targeting).getEntries()))
        .contains("lib/x86/libtest.so");
    assertThat(extractPaths(shardsByTargeting.get(x86Targeting).getEntries()))
        .containsNoneOf("lib/armeabi/libtest.so", "lib/x86_64/libtest.so");
    assertThat(extractPaths(shardsByTargeting.get(x64Targeting).getEntries()))
        .contains("lib/x86_64/libtest.so");
    assertThat(extractPaths(shardsByTargeting.get(x64Targeting).getEntries()))
        .containsNoneOf("lib/armeabi/libtest.so", "lib/x86/libtest.so");
  }

  @Test
  public void shardByAbi_havingManyAbis_producesManyApks_withTransparency() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withBundleConfig(bundleConfigNodexmergestrategy)
            .withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY)
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(3);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

    VariantTargeting armVariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(ARMEABI, ImmutableSet.of(X86, X86_64)),
            VARIANT_TARGETING_WITH_SDK_1);
    VariantTargeting x86VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(X86, ImmutableSet.of(ARMEABI, X86_64)),
            VARIANT_TARGETING_WITH_SDK_1);
    VariantTargeting x64VariantTargeting =
        mergeVariantTargeting(
            variantAbiTargeting(X86_64, ImmutableSet.of(ARMEABI, X86)),
            VARIANT_TARGETING_WITH_SDK_1);

    assertThat(
            shards.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(armVariantTargeting, x64VariantTargeting, x86VariantTargeting);
    for (ModuleSplit shard : shards) {
      assertThat(extractPaths(shard.getEntries()))
          .containsAtLeast(
              "assets/file.txt",
              "dex/classes.dex",
              "res/drawable/image.jpg",
              "res/drawable-mdpi/image.jpg",
              "root/license.dat",
              "META-INF/code_transparency_signed.jwt");
    }
  }

  @Test
  public void shardByAbi_assetsAbiTargetingIsIgnored() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/data/file.txt")
            .addFile("assets/data#tcf_atc/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-mdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/data",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(
                                ImmutableSet.of(),
                                ImmutableSet.of(TextureCompressionFormatAlias.ATC)))),
                    targetedAssetsDirectory(
                        "assets/data#tcf_atc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(TextureCompressionFormatAlias.ATC)))))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly(
            "assets/data/file.txt",
            "assets/data#tcf_atc/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "lib/x86_64/libtest.so",
            "res/drawable/image.jpg",
            "res/drawable-mdpi/image.jpg",
            "root/license.dat");
  }

  @Test
  public void shardByDensity_havingNoResources_producesOneApk() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit shard = shards.get(0);
    assertThat(shard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(shard.getVariantTargeting()).isEqualTo(VARIANT_TARGETING_WITH_SDK_1);
    assertThat(shard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(shard.getResourceTable()).isEmpty();
    assertThat(extractPaths(shard.getEntries()))
        .containsExactly(
            "assets/file.txt", "dex/classes.dex", "lib/x86/libtest.so", "root/license.dat");
  }

  @Test
  public void shardByDensity_havingNonDensityResources_producesOneApk() throws Exception {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addFileResourceForMultipleConfigs(
                "drawable",
                "image",
                ImmutableMap.of(
                    Configuration.getDefaultInstance(),
                    "res/drawable/image.jpg",
                    locale("de"),
                    "res/drawable-de/image.jpg"))
            .build();

    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("res/drawable/image.jpg")
            .addFile("res/drawable-de/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setResourceTable(resourceTable)
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit shard = shards.get(0);
    assertThat(extractPaths(shard.getEntries()))
        .containsExactly(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "res/drawable/image.jpg",
            "res/drawable-de/image.jpg",
            "root/license.dat");
    assertThat((Message) shard.getResourceTable().get())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(resourceTable);
    assertThat(shard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(shard.getVariantTargeting()).isEqualTo(VARIANT_TARGETING_WITH_SDK_1);
    assertThat(shard.getSplitType()).isEqualTo(SplitType.STANDALONE);

    ImmutableList<ApkTargeting> targetings =
        shards.stream().map(ModuleSplit::getApkTargeting).collect(toImmutableList());
    assertThat(targetings).containsNoDuplicates();
    assertThat(targetings)
        .ignoringRepeatedFieldOrder()
        .containsExactly(ApkTargeting.getDefaultInstance());
  }

  @Test
  public void shardByDensity_havingOnlyOneDensityResource_producesSingleApk() throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/x86/libtest.so")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .setResourceTable(
                new ResourceTableBuilder()
                    .addPackage("com.test.app")
                    .addDrawableResourceForMultipleDensities(
                        "image", ImmutableMap.of(HDPI_VALUE, "res/drawable-hdpi/image.jpg"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit shard = shards.get(0);
    assertThat(extractPaths(shard.getEntries()))
        .containsAtLeast(
            "assets/file.txt",
            "dex/classes.dex",
            "lib/x86/libtest.so",
            "root/license.dat",
            "res/drawable-hdpi/image.jpg");
    assertThat(shard.getResourceTable().get())
        .containsResource("com.test.app:drawable/image")
        .withConfigSize(1);
    assertThat(shard.getApkTargeting()).isEqualToDefaultInstance();
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting()).isEqualTo(VARIANT_TARGETING_WITH_SDK_1);

    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);

    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly("assets/file.txt", "dex/classes.dex", "root/license.dat");
  }

  @Test
  public void shardByAbiAndDensity_havingOneAbiAndSomeDensityResource_producesManyApks()
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    // 7 shards: {x86} x {LDPI, MDPI, ..., XXXHDPI}.
    assertThat(shards).hasSize(7);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

    for (ModuleSplit shard : shards) {
      assertThat(extractPaths(shard.getEntries()))
          .containsAtLeast(
              "assets/file.txt", "dex/classes.dex", "lib/x86/libtest.so", "root/license.dat");
      // The MDPI shard(s) would match both hdpi and ldpi variant of the resource.
      if (shard
          .getApkTargeting()
          .getScreenDensityTargeting()
          .getValueList()
          .contains(toScreenDensity(DensityAlias.MDPI))) {
        assertThat(shard.getResourceTable().get())
            .containsResource("com.test.app:drawable/image")
            .withConfigSize(2);
      } else {
        assertThat(shard.getResourceTable().get())
            .containsResource("com.test.app:drawable/image")
            .withConfigSize(1);
      }
    }
    assertThat(shards.stream().map(ModuleSplit::getApkTargeting).collect(toImmutableList()))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            mergeApkTargeting(apkAbiTargeting(X86), LDPI_TARGETING),
            mergeApkTargeting(apkAbiTargeting(X86), MDPI_TARGETING),
            mergeApkTargeting(apkAbiTargeting(X86), HDPI_TARGETING),
            mergeApkTargeting(apkAbiTargeting(X86), XHDPI_TARGETING),
            mergeApkTargeting(apkAbiTargeting(X86), XXHDPI_TARGETING),
            mergeApkTargeting(apkAbiTargeting(X86), XXXHDPI_TARGETING),
            mergeApkTargeting(apkAbiTargeting(X86), TVDPI_TARGETING));
  }

  @Test
  public void shardByAbiAndDensity_havingOneAbiAndSomeDensityResource_produceManyApks_transparency()
      throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withBundleConfig(bundleConfigNodexmergestrategy)
            .withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY)
            .build());
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    // 7 shards: {x86} x {LDPI, MDPI, ..., XXXHDPI}.
    assertThat(shards).hasSize(7);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

    for (ModuleSplit shard : shards) {
      assertThat(extractPaths(shard.getEntries()))
          .containsAtLeast(
              "assets/file.txt",
              "dex/classes.dex",
              "lib/x86/libtest.so",
              "root/license.dat",
              "META-INF/code_transparency_signed.jwt");
    }
  }

  @Test
  public void shardByAbiAndDensity_havingManyAbisAndSomeResource_producesManyApks()
      throws Exception {
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("assets/file.txt")
            .addFile("dex/classes.dex")
            .addFile("lib/armeabi/libtest.so")
            .addFile("lib/x86/libtest.so")
            .addFile("lib/x86_64/libtest.so")
            .addFile("res/drawable-ldpi/image.jpg")
            .addFile("res/drawable-hdpi/image.jpg")
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
                            LDPI_VALUE,
                            "res/drawable-ldpi/image.jpg",
                            HDPI_VALUE,
                            "res/drawable-hdpi/image.jpg"))
                    .build())
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(3 * 7);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

    ApkTargeting armTargeting = apkAbiTargeting(ARMEABI, ImmutableSet.of(X86, X86_64));
    ApkTargeting x86Targeting = apkAbiTargeting(X86, ImmutableSet.of(ARMEABI, X86_64));
    ApkTargeting x64Targeting = apkAbiTargeting(X86_64, ImmutableSet.of(ARMEABI, X86));
    assertThat(shards.stream().map(ModuleSplit::getApkTargeting).collect(toImmutableList()))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            mergeApkTargeting(armTargeting, LDPI_TARGETING),
            mergeApkTargeting(armTargeting, MDPI_TARGETING),
            mergeApkTargeting(armTargeting, HDPI_TARGETING),
            mergeApkTargeting(armTargeting, XHDPI_TARGETING),
            mergeApkTargeting(armTargeting, XXHDPI_TARGETING),
            mergeApkTargeting(armTargeting, XXXHDPI_TARGETING),
            mergeApkTargeting(armTargeting, TVDPI_TARGETING),
            mergeApkTargeting(x86Targeting, LDPI_TARGETING),
            mergeApkTargeting(x86Targeting, MDPI_TARGETING),
            mergeApkTargeting(x86Targeting, HDPI_TARGETING),
            mergeApkTargeting(x86Targeting, XHDPI_TARGETING),
            mergeApkTargeting(x86Targeting, XXHDPI_TARGETING),
            mergeApkTargeting(x86Targeting, XXXHDPI_TARGETING),
            mergeApkTargeting(x86Targeting, TVDPI_TARGETING),
            mergeApkTargeting(x64Targeting, LDPI_TARGETING),
            mergeApkTargeting(x64Targeting, MDPI_TARGETING),
            mergeApkTargeting(x64Targeting, HDPI_TARGETING),
            mergeApkTargeting(x64Targeting, XHDPI_TARGETING),
            mergeApkTargeting(x64Targeting, XXHDPI_TARGETING),
            mergeApkTargeting(x64Targeting, XXXHDPI_TARGETING),
            mergeApkTargeting(x64Targeting, TVDPI_TARGETING));

    // Check files not specific to ABI nor screen density.
    for (ModuleSplit shard : shards) {
      assertThat(extractPaths(shard.getEntries()))
          .containsAtLeast("assets/file.txt", "dex/classes.dex", "root/license.dat");
    }

    // Check resources.
    for (ModuleSplit shard : shards) {
      DensityAlias density =
          shard.getApkTargeting().getScreenDensityTargeting().getValue(0).getDensityAlias();
      switch (density) {
        case LDPI:
          assertThat(extractPaths(shard.findEntriesUnderPath("res")))
              .containsExactly("res/drawable-ldpi/image.jpg");
          assertThat(shard.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(LDPI);
          break;

        case MDPI:
          // MDPI is a special case because the bucket encompasses devices that could serve either
          // the LDPI or the HDPI resource, so both resources are present.
          assertThat(extractPaths(shard.findEntriesUnderPath("res")))
              .containsExactly("res/drawable-ldpi/image.jpg", "res/drawable-hdpi/image.jpg");
          assertThat(shard.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(LDPI, HDPI);
          break;

        case TVDPI:
        case HDPI:
        case XHDPI:
        case XXHDPI:
        case XXXHDPI:
          assertThat(extractPaths(shard.findEntriesUnderPath("res")))
              .containsExactly("res/drawable-hdpi/image.jpg");
          assertThat(shard.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(HDPI);
          break;

        default:
          fail("Unexpected screen targeting density: " + density);
      }
    }

    // Check native libraries.
    for (ModuleSplit shard : shards) {
      switch (shard.getApkTargeting().getAbiTargeting().getValue(0).getAlias()) {
        case ARMEABI:
          assertThat(extractPaths(shard.getEntries())).contains("lib/armeabi/libtest.so");
          assertThat(extractPaths(shard.getEntries()))
              .containsNoneOf("lib/x86/libtest.so", "lib/x86_64/libtest.so");
          break;
        case X86:
          assertThat(extractPaths(shard.getEntries())).contains("lib/x86/libtest.so");
          assertThat(extractPaths(shard.getEntries()))
              .containsNoneOf("lib/armeabi/libtest.so", "lib/x86_64/libtest.so");
          break;
        case X86_64:
          assertThat(extractPaths(shard.getEntries())).contains("lib/x86_64/libtest.so");
          assertThat(extractPaths(shard.getEntries()))
              .containsNoneOf("lib/armeabi/libtest.so", "lib/x86/libtest.so");
          break;
        default:
          fail("Unexpected ABI targeting in: " + shard.getApkTargeting());
      }
    }
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule), NO_DIMENSIONS);

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
  public void manyModulesShardByNoDimension_producesFatApk_withTransparency() throws Exception {
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withBundleConfig(bundleConfigNodexmergestrategy)
            .withBundleMetadata(BUNDLE_METADATA_WITH_TRANSPARENCY)
            .build());
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule), NO_DIMENSIONS);

    assertThat(shards).hasSize(1);
    ModuleSplit fatApk = shards.get(0);
    assertThat(extractPaths(fatApk.getEntries()))
        .containsExactly(
            "lib/x86_64/libtest1.so",
            "lib/x86_64/libtest2.so",
            "res/drawable-ldpi/image1.jpg",
            "res/drawable-ldpi/image2.jpg",
            "META-INF/code_transparency_signed.jwt");
  }

  @Test
  public void manyModulesShardByAbi_havingSingleAbi_producesOneApk() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule featureModule1 =
        new BundleModuleBuilder("feature1")
            .addFile("lib/x86_64/lib1.so")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .build();
    BundleModule featureModule2 =
        new BundleModuleBuilder("feature2")
            .addFile("lib/x86_64/lib2.so")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule1, featureModule2),
            standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    ModuleSplit shard = shards.get(0);
    assertThat(shard.getApkTargeting()).isEqualTo(apkAbiTargeting(X86_64));
    assertThat(shard.getVariantTargeting())
        .isEqualTo(
            mergeVariantTargeting(variantAbiTargeting(X86_64), VARIANT_TARGETING_WITH_SDK_1));
    assertThat(shard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(extractPaths(shard.getEntries()))
        .containsExactly(
            "dex/classes.dex", "lib/x86_64/lib1.so", "lib/x86_64/lib2.so", "root/license.dat");
  }

  @Test
  public void manyModulesShardByAbi_havingManyAbis_producesManyApks() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule featureModule1 =
        new BundleModuleBuilder("feature1")
            .addFile("lib/x86/lib1.so")
            .addFile("lib/x86_64/lib1.so")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .build();
    BundleModule featureModule2 =
        new BundleModuleBuilder("feature2")
            .addFile("lib/x86/lib2.so")
            .addFile("lib/x86_64/lib2.so")
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86)),
                    targetedNativeDirectory("lib/x86_64", nativeDirectoryTargeting(X86_64))))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule1, featureModule2),
            standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(2);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);
    assertThat(
            shards.stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(
            mergeVariantTargeting(
                VARIANT_TARGETING_WITH_SDK_1, variantAbiTargeting(X86_64, ImmutableSet.of(X86))),
            mergeVariantTargeting(
                VARIANT_TARGETING_WITH_SDK_1, variantAbiTargeting(X86, ImmutableSet.of(X86_64))));

    ImmutableMap<ApkTargeting, ModuleSplit> shardsByTargeting =
        Maps.uniqueIndex(shards, ModuleSplit::getApkTargeting);
    ApkTargeting x86Targeting = apkAbiTargeting(X86, ImmutableSet.of(X86_64));
    ApkTargeting x64Targeting = apkAbiTargeting(X86_64, ImmutableSet.of(X86));
    assertThat(shardsByTargeting.keySet()).containsExactly(x86Targeting, x64Targeting);
    assertThat(extractPaths(shardsByTargeting.get(x86Targeting).getEntries()))
        .containsExactly(
            "dex/classes.dex", "lib/x86/lib1.so", "lib/x86/lib2.so", "root/license.dat");
    assertThat(extractPaths(shardsByTargeting.get(x64Targeting).getEntries()))
        .containsExactly(
            "dex/classes.dex", "lib/x86_64/lib1.so", "lib/x86_64/lib2.so", "root/license.dat");
  }

  @Test
  public void manyModulesShardByDensity_havingNoResources_producesOneApk() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("assets/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule),
            standaloneApkOptimizations(OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly("assets/file.txt", "dex/classes.dex", "root/license.dat");
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
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule),
            standaloneApkOptimizations(OptimizationDimension.SCREEN_DENSITY));

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
  public void manyModulesShardByAbiAndDensity_havingNoAbiAndNoResources_producesOneApk()
      throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule featureModule =
        new BundleModuleBuilder("feature")
            .addFile("assets/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly("assets/file.txt", "dex/classes.dex", "root/license.dat");
  }

  @Test
  public void manyModulesShardByAbiAndDensity_havingManyAbisAndSomeResource_producesManyApks()
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
            .setManifest(androidManifest("com.test.app"))
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/armeabi", nativeDirectoryTargeting(ARMEABI)),
                    targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(X86))))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, featureModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(2 * 7);
    assertThat(shards.stream().map(ModuleSplit::getSplitType).distinct().collect(toImmutableSet()))
        .containsExactly(SplitType.STANDALONE);

    ApkTargeting armTargeting = apkAbiTargeting(ARMEABI, ImmutableSet.of(X86));
    ApkTargeting x86Targeting = apkAbiTargeting(X86, ImmutableSet.of(ARMEABI));
    assertThat(shards.stream().map(ModuleSplit::getApkTargeting).collect(toImmutableList()))
        .ignoringRepeatedFieldOrder()
        .containsExactly(
            mergeApkTargeting(armTargeting, LDPI_TARGETING),
            mergeApkTargeting(armTargeting, MDPI_TARGETING),
            mergeApkTargeting(armTargeting, HDPI_TARGETING),
            mergeApkTargeting(armTargeting, XHDPI_TARGETING),
            mergeApkTargeting(armTargeting, XXHDPI_TARGETING),
            mergeApkTargeting(armTargeting, XXXHDPI_TARGETING),
            mergeApkTargeting(armTargeting, TVDPI_TARGETING),
            mergeApkTargeting(x86Targeting, LDPI_TARGETING),
            mergeApkTargeting(x86Targeting, MDPI_TARGETING),
            mergeApkTargeting(x86Targeting, HDPI_TARGETING),
            mergeApkTargeting(x86Targeting, XHDPI_TARGETING),
            mergeApkTargeting(x86Targeting, XXHDPI_TARGETING),
            mergeApkTargeting(x86Targeting, XXXHDPI_TARGETING),
            mergeApkTargeting(x86Targeting, TVDPI_TARGETING));

    // Check files not specific to ABI nor screen density.
    for (ModuleSplit shard : shards) {
      assertThat(extractPaths(shard.getEntries()))
          .containsAtLeast("assets/file.txt", "dex/classes.dex", "root/license.dat");
    }

    // Check resources.
    for (ModuleSplit shard : shards) {
      DensityAlias density =
          shard
              .getApkTargeting()
              .getScreenDensityTargeting()
              .getValueList()
              .get(0)
              .getDensityAlias();
      switch (density) {
        case LDPI:
        case MDPI:
          assertThat(extractPaths(shard.findEntriesUnderPath("res/")))
              .containsExactly("res/drawable/image.jpg");
          assertThat(shard.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(Configuration.getDefaultInstance());
          break;

        case TVDPI:
          // TVDPI is a special case because the bucket encompasses devices that could serve either
          // the MDPI or the HDPI resource, so both resources are present.
          assertThat(extractPaths(shard.findEntriesUnderPath("res/")))
              .containsExactly("res/drawable/image.jpg", "res/drawable-hdpi/image.jpg");
          assertThat(shard.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(Configuration.getDefaultInstance(), HDPI);
          break;

        case HDPI:
        case XHDPI:
        case XXHDPI:
        case XXXHDPI:
          assertThat(extractPaths(shard.findEntriesUnderPath("res/")))
              .containsExactly("res/drawable-hdpi/image.jpg");
          assertThat(shard.getResourceTable().get())
              .containsResource("com.test.app:drawable/image")
              .onlyWithConfigs(HDPI);
          break;

        default:
          fail("Unexpected screen density targeting: " + density);
      }
    }

    // Check native libraries.
    for (ModuleSplit shard : shards) {
      switch (shard.getApkTargeting().getAbiTargeting().getValue(0).getAlias()) {
        case ARMEABI:
          assertThat(extractPaths(shard.getEntries())).contains("lib/armeabi/libtest.so");
          assertThat(extractPaths(shard.getEntries()))
              .containsNoneOf("lib/x86/libtest.so", "lib/x86_64/libtest.so");
          break;
        case X86:
          assertThat(extractPaths(shard.getEntries())).contains("lib/x86/libtest.so");
          assertThat(extractPaths(shard.getEntries()))
              .containsNoneOf("lib/armeabi/libtest.so", "lib/x86_64/libtest.so");
          break;
        default:
          fail("Unexpected ABI targeting in: " + shard.getApkTargeting());
      }
    }
  }

  @Test
  public void shardAssetModules() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .addFile("root/license.dat")
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule assetModule =
        new BundleModuleBuilder("upfront_asset")
            .addFile("assets/file.txt")
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(baseModule, assetModule),
            standaloneApkOptimizations(
                OptimizationDimension.ABI, OptimizationDimension.SCREEN_DENSITY));

    assertThat(shards).hasSize(1);
    ModuleSplit fatShard = shards.get(0);
    assertThat(fatShard.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(fatShard.getVariantTargeting()).isEqualTo(VARIANT_TARGETING_WITH_SDK_1);
    assertThat(fatShard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(extractPaths(fatShard.getEntries()))
        .containsExactly("assets/file.txt", "dex/classes.dex", "root/license.dat");
  }

  @Test
  public void standaloneApks_injectBinaryArtProfiles() throws Exception {
    byte[] content = new byte[] {1, 4, 3, 2};
    TestComponent.useTestModule(
        this,
        TestModule.builder()
            .withBundleMetadata(
                BundleMetadata.builder()
                    .addFile(
                        "com.android.tools.build.profiles",
                        "baseline.prof",
                        ByteSource.wrap(content))
                    .build())
            .build());
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    assertThat(shards).hasSize(1);
    assertThat(shards.get(0).getSplitType()).isEqualTo(SplitType.STANDALONE);
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
  public void bundleHasdSdkDeps_sdkTableConfigInjected() {
    RuntimeEnabledSdkConfig runtimeEnabledSdkConfig =
        RuntimeEnabledSdkConfig.newBuilder()
            .addRuntimeEnabledSdk(
                RuntimeEnabledSdk.newBuilder()
                    .setPackageName("com.test.sdk")
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setCertificateDigest("certdigest"))
            .build();
    BundleModule bundleModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app"))
            .setRuntimeEnabledSdkConfig(runtimeEnabledSdkConfig)
            .build();
    AppBundle appBundle = new AppBundleBuilder().addModule(bundleModule).build();
    TestComponent.useTestModule(this, TestModule.builder().withAppBundle(appBundle).build());

    ImmutableList<ModuleSplit> shards =
        standaloneApksGenerator.generateStandaloneApks(
            ImmutableList.of(bundleModule), standaloneApkOptimizations(OptimizationDimension.ABI));

    shards.forEach(
        shard ->
            assertThat(
                    shard.getEntries().stream()
                        .filter(
                            entry ->
                                entry
                                    .getPath()
                                    .equals(
                                        ZipPath.create(
                                            RuntimeEnabledSdkTableInjector
                                                .RUNTIME_ENABLED_SDK_TABLE_FILE_PATH))))
                .hasSize(1));
  }

  private static ApkOptimizations standaloneApkOptimizations(OptimizationDimension... dimensions) {
    return ApkOptimizations.builder()
        .setSplitDimensions(ImmutableSet.of())
        .setStandaloneDimensions(ImmutableSet.copyOf(dimensions))
        .build();
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(StandaloneApksGeneratorTest test);

    static void useTestModule(StandaloneApksGeneratorTest testInstance, TestModule testModule) {
      DaggerStandaloneApksGeneratorTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
