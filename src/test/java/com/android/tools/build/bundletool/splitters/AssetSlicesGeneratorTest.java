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

import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withVersionCode;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AssetSlicesGeneratorTest {

  private static final String PACKAGE_NAME = "com.test.app";
  private static final int VERSION_CODE = 12341;
  private static final String VERSION_NAME = "12341";
  // Larger than 32 bit, to test the conversion.
  private static final long OVERRIDDEN_VERSION_CODE = 101010101010L;
  private static final String OVERRIDDEN_VERSION_NAME = "101010101010";

  @Test
  public void singleAssetModule() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            new BundleModuleBuilder("asset_module")
                .addFile("assets/some_asset.txt")
                .setManifest(androidManifestForAssetModule(PACKAGE_NAME))
                .build());

    ImmutableList<ModuleSplit> assetSlices =
        new AssetSlicesGenerator(
                appBundle, ApkGenerationConfiguration.getDefaultInstance(), Optional.empty())
            .generateAssetSlices();

    assertThat(assetSlices).hasSize(1);
    ModuleSplit assetSlice = assetSlices.get(0);

    assertThat(assetSlice.getModuleName().getName()).isEqualTo("asset_module");
    assertThat(assetSlice.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(assetSlice.isMasterSplit()).isTrue();
    assertThat(assetSlice.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(extractPaths(assetSlice.getEntries())).containsExactly("assets/some_asset.txt");
  }

  @Test
  public void upfrontAssetModule_addsVersionCode() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            new BundleModuleBuilder("asset_module")
                .setManifest(androidManifestForAssetModule(PACKAGE_NAME, withInstallTimeDelivery()))
                .build());

    ImmutableList<ModuleSplit> assetSlices =
        new AssetSlicesGenerator(
                appBundle, ApkGenerationConfiguration.getDefaultInstance(), Optional.empty())
            .generateAssetSlices();

    assertThat(assetSlices).hasSize(1);
    ModuleSplit assetSlice = assetSlices.get(0);
    assertThat(assetSlice.getAndroidManifest().getVersionCode()).hasValue(VERSION_CODE);
  }

  @Test
  public void upfrontAssetModule_doesNotOverrideVersionCode() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            new BundleModuleBuilder("asset_module")
                .setManifest(androidManifestForAssetModule(PACKAGE_NAME, withInstallTimeDelivery()))
                .build());

    ImmutableList<ModuleSplit> assetSlices =
        new AssetSlicesGenerator(
                appBundle,
                ApkGenerationConfiguration.getDefaultInstance(),
                Optional.of(OVERRIDDEN_VERSION_CODE))
            .generateAssetSlices();

    assertThat(assetSlices).hasSize(1);
    ModuleSplit assetSlice = assetSlices.get(0);
    assertThat(assetSlice.getAndroidManifest().getVersionCode()).hasValue(VERSION_CODE);
  }

  @Test
  public void onDemandAssetModule_overridesVersionCodeWhenFillingVersionName() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            new BundleModuleBuilder("asset_module")
                .setManifest(androidManifestForAssetModule(PACKAGE_NAME, withOnDemandDelivery()))
                .build());

    ImmutableList<ModuleSplit> assetSlices =
        new AssetSlicesGenerator(
                appBundle,
                ApkGenerationConfiguration.getDefaultInstance(),
                Optional.of(OVERRIDDEN_VERSION_CODE))
            .generateAssetSlices();

    assertThat(assetSlices).hasSize(1);
    ModuleSplit assetSlice = assetSlices.get(0);
    assertThat(assetSlice.getAndroidManifest().getVersionName()).hasValue(OVERRIDDEN_VERSION_NAME);
  }

  @Test
  public void onDemandAssetModule_addsVersionName() throws Exception {
    AppBundle appBundle =
        createAppBundle(
            new BundleModuleBuilder("asset_module")
                .setManifest(androidManifestForAssetModule(PACKAGE_NAME, withOnDemandDelivery()))
                .build());

    ImmutableList<ModuleSplit> assetSlices =
        new AssetSlicesGenerator(
                appBundle, ApkGenerationConfiguration.getDefaultInstance(), Optional.empty())
            .generateAssetSlices();

    assertThat(assetSlices).hasSize(1);
    ModuleSplit assetSlice = assetSlices.get(0);
    assertThat(assetSlice.getAndroidManifest().getVersionName()).hasValue(VERSION_NAME);
  }


  private static AppBundle createAppBundle(BundleModule... assetModules) throws Exception {
    AppBundleBuilder appBundleBuilder =
        new AppBundleBuilder()
            .addModule(
                new BundleModuleBuilder("base")
                    .setManifest(androidManifest(PACKAGE_NAME, withVersionCode(VERSION_CODE)))
                    .build());
    for (BundleModule assetModule : assetModules) {
      appBundleBuilder.addModule(assetModule);
    }
    return appBundleBuilder.build();
  }
}
