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

import static com.android.tools.build.bundletool.model.OptimizationDimension.COUNTRY_SET;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AssetModuleSplitterTest {

  private static final String MODULE_NAME = "test_module";

  private static final BundleModule BASE_MODULE =
      new BundleModuleBuilder("base")
          .addFile("dex/classes.dex")
          .setManifest(androidManifest("com.test.app"))
          .build();

  private static final AppBundle APP_BUNDLE = new AppBundleBuilder().addModule(BASE_MODULE).build();

  @Test
  public void singleSlice() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder(MODULE_NAME)
            .addFile("assets/image.jpg")
            .addFile("assets/image2.jpg")
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    assertThat(testModule.getModuleType()).isEqualTo(ModuleType.ASSET_MODULE);
    ImmutableList<ModuleSplit> slices =
        new AssetModuleSplitter(
                testModule, ApkGenerationConfiguration.getDefaultInstance(), APP_BUNDLE)
            .splitModule();

    assertThat(slices).hasSize(1);
    ModuleSplit masterSlice = slices.get(0);
    assertThat(masterSlice.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(masterSlice.isMasterSplit()).isTrue();
    assertThat(masterSlice.getAndroidManifest().getSplitId()).hasValue(MODULE_NAME);
    assertThat(masterSlice.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(masterSlice.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(extractPaths(masterSlice.getEntries()))
        .containsExactly("assets/image.jpg", "assets/image2.jpg");
  }


  @Test
  public void slicesByCountrySet() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder(MODULE_NAME)
            .addFile("assets/images/image.jpg")
            .addFile("assets/images#countries_latam/image.jpg")
            .addFile("assets/images#countries_sea/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#countries_latam",
                        assetsDirectoryTargeting(
                            countrySetTargeting(
                                ImmutableList.of("latam"), ImmutableList.of("sea")))),
                    targetedAssetsDirectory(
                        "assets/images#countries_sea",
                        assetsDirectoryTargeting(
                            countrySetTargeting(
                                ImmutableList.of("sea"), ImmutableList.of("latam")))),
                    targetedAssetsDirectory(
                        "assets/images",
                        assetsDirectoryTargeting(
                            alternativeCountrySetTargeting(ImmutableList.of("latam", "sea"))))))
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    assertThat(testModule.getModuleType()).isEqualTo(ModuleType.ASSET_MODULE);
    ImmutableList<ModuleSplit> slices =
        new AssetModuleSplitter(
                testModule,
                ApkGenerationConfiguration.builder()
                    .setOptimizationDimensions(ImmutableSet.of(COUNTRY_SET))
                    .build(),
                APP_BUNDLE)
            .splitModule();

    assertThat(slices).hasSize(4);

    ImmutableMap<ApkTargeting, ModuleSplit> slicesByTargeting =
        Maps.uniqueIndex(slices, ModuleSplit::getApkTargeting);

    assertThat(slicesByTargeting.keySet())
        .containsExactly(
            ApkTargeting.getDefaultInstance(),
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("latam"), ImmutableList.of("sea"))),
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("sea"), ImmutableList.of("latam"))),
            apkCountrySetTargeting(
                alternativeCountrySetTargeting(ImmutableList.of("latam", "sea"))));

    ModuleSplit masterSplit = slicesByTargeting.get(ApkTargeting.getDefaultInstance());
    assertThat(masterSplit.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(masterSplit.isMasterSplit()).isTrue();
    assertThat(masterSplit.getAndroidManifest().getSplitId()).hasValue(MODULE_NAME);
    assertThat(masterSplit.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(masterSplit.getEntries()).isEmpty();

    ModuleSplit latamSplit =
        slicesByTargeting.get(
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("latam"), ImmutableList.of("sea"))));
    assertThat(latamSplit.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(latamSplit.isMasterSplit()).isFalse();
    assertThat(latamSplit.getAndroidManifest().getSplitId())
        .hasValue(MODULE_NAME + ".config.countries_latam");
    assertThat(latamSplit.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(extractPaths(latamSplit.getEntries()))
        .containsExactly("assets/images#countries_latam/image.jpg");

    ModuleSplit seaSplit =
        slicesByTargeting.get(
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("sea"), ImmutableList.of("latam"))));
    assertThat(seaSplit.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(seaSplit.isMasterSplit()).isFalse();
    assertThat(seaSplit.getAndroidManifest().getSplitId())
        .hasValue(MODULE_NAME + ".config.countries_sea");
    assertThat(seaSplit.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(extractPaths(seaSplit.getEntries()))
        .containsExactly("assets/images#countries_sea/image.jpg");

    ModuleSplit restOfWorldSplit =
        slicesByTargeting.get(
            apkCountrySetTargeting(
                alternativeCountrySetTargeting(ImmutableList.of("latam", "sea"))));
    assertThat(restOfWorldSplit.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(restOfWorldSplit.isMasterSplit()).isFalse();
    assertThat(restOfWorldSplit.getAndroidManifest().getSplitId())
        .hasValue(MODULE_NAME + ".config.other_countries");
    assertThat(restOfWorldSplit.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(extractPaths(restOfWorldSplit.getEntries()))
        .containsExactly("assets/images/image.jpg");
  }

  @Test
  public void singleSlice_updatesMinSdkVersionFromBaseModule_flagEnabled() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(23)))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder(MODULE_NAME)
            .addFile("assets/image.jpg")
            .addFile("assets/image2.jpg")
            .setManifest(androidManifestForAssetModule("com.test.app", withInstallTimeDelivery()))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder().addModule(baseModule).addModule(testModule).build();

    ImmutableList<ModuleSplit> slices =
        new AssetModuleSplitter(
                testModule,
                ApkGenerationConfiguration.builder()
                    .setEnableBaseModuleMinSdkAsDefaultTargeting(true)
                    .build(),
                appBundle)
            .splitModule();

    assertThat(slices).hasSize(1);
    ModuleSplit masterSlice = slices.get(0);
    assertThat(masterSlice.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(masterSlice.isMasterSplit()).isTrue();
    assertThat(masterSlice.getAndroidManifest().getSplitId()).hasValue(MODULE_NAME);
    assertThat(masterSlice.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(masterSlice.getApkTargeting()).isEqualTo(apkMinSdkTargeting(23));
    assertThat(extractPaths(masterSlice.getEntries()))
        .containsExactly("assets/image.jpg", "assets/image2.jpg");
  }

  @Test
  public void singleSlice_updatesMinSdkVersionFromBaseModule_flagDisabled() throws Exception {
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app", withMinSdkVersion(23)))
            .build();
    BundleModule testModule =
        new BundleModuleBuilder(MODULE_NAME)
            .addFile("assets/image.jpg")
            .addFile("assets/image2.jpg")
            .setManifest(androidManifestForAssetModule("com.test.app", withInstallTimeDelivery()))
            .build();
    AppBundle appBundle =
        new AppBundleBuilder().addModule(baseModule).addModule(testModule).build();

    ImmutableList<ModuleSplit> slices =
        new AssetModuleSplitter(
                testModule, ApkGenerationConfiguration.getDefaultInstance(), appBundle)
            .splitModule();

    assertThat(slices).hasSize(1);
    ModuleSplit masterSlice = slices.get(0);
    assertThat(masterSlice.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(masterSlice.isMasterSplit()).isTrue();
    assertThat(masterSlice.getAndroidManifest().getSplitId()).hasValue(MODULE_NAME);
    assertThat(masterSlice.getAndroidManifest().getHasCode()).hasValue(false);
    assertThat(masterSlice.getApkTargeting()).isEqualTo(apkMinSdkTargeting(21));
    assertThat(extractPaths(masterSlice.getEntries()))
        .containsExactly("assets/image.jpg", "assets/image2.jpg");
  }
}
