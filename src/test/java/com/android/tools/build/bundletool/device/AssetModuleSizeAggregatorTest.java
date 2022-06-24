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

package com.android.tools.build.bundletool.device;

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ASTC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC2;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createAssetSliceSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantTextureTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.android.tools.build.bundletool.commands.GetSizeCommand.GetSizeSubcommand;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssetModuleSizeAggregatorTest {

  private static final long ASSET_1_MAIN_SIZE = 1 << 0;
  private static final long ASSET_1_ETC2_SIZE = 1 << 1;
  private static final long ASSET_1_ASTC_SIZE = 1 << 2;
  private static final long ASSET_2_MAIN_SIZE = 1 << 3;
  private static final long ASSET_2_ETC2_SIZE = 1 << 4;
  private static final long ASSET_2_ASTC_SIZE = 1 << 5;
  private static final AssetSliceSet ASSET_MODULE_1 =
      createAssetSliceSet(
          "asset1",
          DeliveryType.INSTALL_TIME,
          createMasterApkDescription(
              apkSdkTargeting(sdkVersionFrom(21)), ZipPath.create("asset1-main.apk")),
          createApkDescription(
              mergeApkTargeting(
                  apkTextureTargeting(ETC2, ImmutableSet.of(ASTC)),
                  apkSdkTargeting(sdkVersionFrom(21))),
              ZipPath.create("asset1-tcf_etc2.apk"),
              /* isMasterSplit= */ false),
          createApkDescription(
              mergeApkTargeting(
                  apkTextureTargeting(ASTC, ImmutableSet.of(ETC2)),
                  apkSdkTargeting(sdkVersionFrom(21))),
              ZipPath.create("asset1-tcf_astc.apk"),
              /* isMasterSplit= */ false));
  private static final AssetSliceSet ASSET_MODULE_2 =
      createAssetSliceSet(
          "asset2",
          DeliveryType.INSTALL_TIME,
          createMasterApkDescription(
              apkSdkTargeting(sdkVersionFrom(21)), ZipPath.create("asset2-main.apk")),
          createApkDescription(
              mergeApkTargeting(
                  apkTextureTargeting(ETC2, ImmutableSet.of(ASTC)),
                  apkSdkTargeting(sdkVersionFrom(21))),
              ZipPath.create("asset2-tcf_etc2.apk"),
              /* isMasterSplit= */ false),
          createApkDescription(
              mergeApkTargeting(
                  apkTextureTargeting(ASTC, ImmutableSet.of(ETC2)),
                  apkSdkTargeting(sdkVersionFrom(21))),
              ZipPath.create("asset2-tcf_astc.apk"),
              /* isMasterSplit= */ false));
  private static final ImmutableMap<String, Long> SIZE_BY_APK_PATHS =
      ImmutableMap.<String, Long>builder()
          .put("asset1-main.apk", ASSET_1_MAIN_SIZE)
          .put("asset1-tcf_etc2.apk", ASSET_1_ETC2_SIZE)
          .put("asset1-tcf_astc.apk", ASSET_1_ASTC_SIZE)
          .put("asset2-main.apk", ASSET_2_MAIN_SIZE)
          .put("asset2-tcf_etc2.apk", ASSET_2_ETC2_SIZE)
          .put("asset2-tcf_astc.apk", ASSET_2_ASTC_SIZE)
          .build();

  private final GetSizeCommand.Builder getSizeCommand =
      GetSizeCommand.builder()
          .setApksArchivePath(Paths.get("test.apks"))
          .setGetSizeSubCommand(GetSizeSubcommand.TOTAL);

  @Test
  public void getSize_noAssetModules() throws Exception {
    ConfigurationSizes configurationSizes =
        new AssetModuleSizeAggregator(
                ImmutableList.of(),
                VariantTargeting.getDefaultInstance(),
                ImmutableMap.of(),
                getSizeCommand.build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 0L);
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 0L);
  }

  @Test
  public void getSize_singleAssetModule_noTargeting() throws Exception {
    ImmutableList<AssetSliceSet> assetModules =
        ImmutableList.of(
            createAssetSliceSet(
                "asset1",
                DeliveryType.INSTALL_TIME,
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("asset1-main.apk"))));
    VariantTargeting variantTargeting = VariantTargeting.getDefaultInstance();
    ImmutableMap<String, Long> sizeByApkPaths = ImmutableMap.of("asset1-main.apk", 10L);
    ConfigurationSizes configurationSizes =
        new AssetModuleSizeAggregator(
                assetModules, variantTargeting, sizeByApkPaths, getSizeCommand.build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 10L);
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(SizeConfiguration.getDefaultInstance(), 10L);
  }

  @Test
  public void getSize_multipleAssetModules_withTargeting() throws Exception {
    ImmutableList<AssetSliceSet> assetModules = ImmutableList.of(ASSET_MODULE_1, ASSET_MODULE_2);
    VariantTargeting variantTargeting = variantSdkTargeting(21);
    ConfigurationSizes configurationSizes =
        new AssetModuleSizeAggregator(
                assetModules,
                variantTargeting,
                SIZE_BY_APK_PATHS,
                getSizeCommand.setDimensions(ImmutableSet.of(TEXTURE_COMPRESSION_FORMAT)).build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder().setTextureCompressionFormat("etc2").build(),
            ASSET_1_MAIN_SIZE + ASSET_1_ETC2_SIZE + ASSET_2_MAIN_SIZE + ASSET_2_ETC2_SIZE,
            SizeConfiguration.builder().setTextureCompressionFormat("astc").build(),
            ASSET_1_MAIN_SIZE + ASSET_1_ASTC_SIZE + ASSET_2_MAIN_SIZE + ASSET_2_ASTC_SIZE);
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder().setTextureCompressionFormat("etc2").build(),
            ASSET_1_MAIN_SIZE + ASSET_1_ETC2_SIZE + ASSET_2_MAIN_SIZE + ASSET_2_ETC2_SIZE,
            SizeConfiguration.builder().setTextureCompressionFormat("astc").build(),
            ASSET_1_MAIN_SIZE + ASSET_1_ASTC_SIZE + ASSET_2_MAIN_SIZE + ASSET_2_ASTC_SIZE);
  }

  @Test
  public void getSize_multipleAssetModules_withDeviceSpecAndVariantTargeting() throws Exception {
    ImmutableList<AssetSliceSet> assetModules = ImmutableList.of(ASSET_MODULE_1, ASSET_MODULE_2);
    VariantTargeting variantTargeting = variantTextureTargeting(ETC2);
    ConfigurationSizes configurationSizes =
        new AssetModuleSizeAggregator(
                assetModules,
                variantTargeting,
                SIZE_BY_APK_PATHS,
                getSizeCommand
                    .setDimensions(ImmutableSet.of(TEXTURE_COMPRESSION_FORMAT, SDK))
                    .setDeviceSpec(sdkVersion(21))
                    .build())
            .getSize();
    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setTextureCompressionFormat("etc2")
                .setSdkVersion("21")
                .build(),
            ASSET_1_MAIN_SIZE + ASSET_1_ETC2_SIZE + ASSET_2_MAIN_SIZE + ASSET_2_ETC2_SIZE);
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setTextureCompressionFormat("etc2")
                .setSdkVersion("21")
                .build(),
            ASSET_1_MAIN_SIZE + ASSET_1_ETC2_SIZE + ASSET_2_MAIN_SIZE + ASSET_2_ETC2_SIZE);
  }
}
