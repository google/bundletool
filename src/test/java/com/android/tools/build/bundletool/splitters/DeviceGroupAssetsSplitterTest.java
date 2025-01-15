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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDeviceGroupTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceGroupTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithDefaultTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.DeviceGroupTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DeviceGroupAssetsSplitter}. */
@RunWith(JUnit4.class)
public class DeviceGroupAssetsSplitterTest {

  private static final DeviceGroupTargeting TARGET_A_OF_ABC =
      deviceGroupTargeting(/* value= */ "a", /* alternatives= */ ImmutableList.of("b", "c"));
  private static final DeviceGroupTargeting TARGET_B_OF_ABC =
      deviceGroupTargeting(/* value= */ "b", /* alternatives= */ ImmutableList.of("a", "c"));
  private static final DeviceGroupTargeting TARGET_C_OF_ABC =
      deviceGroupTargeting(/* value= */ "c", /* alternatives= */ ImmutableList.of("a", "b"));

  @Test
  public void multipleDeviceGroupsAndUntargetedFile() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#group_a/image.jpg")
            .addFile("assets/images#group_b/image.jpg")
            .addFile("assets/images#group_c/image.jpg")
            .addFile("assets/file.txt")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#group_a", assetsDirectoryTargeting(TARGET_A_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images#group_b", assetsDirectoryTargeting(TARGET_B_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images#group_c", assetsDirectoryTargeting(TARGET_C_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    ImmutableCollection<ModuleSplit> assetsSplits =
        DeviceGroupAssetsSplitter.create(/* stripTargetingSuffix= */ false).split(baseSplit);

    assertThat(assetsSplits).hasSize(4);
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/file.txt");

    ImmutableList<ModuleSplit> groupASplits =
        getSplitsWithTargetingEqualTo(assetsSplits, apkDeviceGroupTargeting(TARGET_A_OF_ABC));
    assertThat(groupASplits).hasSize(1);
    assertThat(extractPaths(groupASplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#group_a/image.jpg");

    ImmutableList<ModuleSplit> groupBSplits =
        getSplitsWithTargetingEqualTo(assetsSplits, apkDeviceGroupTargeting(TARGET_B_OF_ABC));
    assertThat(groupBSplits).hasSize(1);
    assertThat(extractPaths(groupBSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#group_b/image.jpg");

    ImmutableList<ModuleSplit> groupCSplits =
        getSplitsWithTargetingEqualTo(assetsSplits, apkDeviceGroupTargeting(TARGET_C_OF_ABC));
    assertThat(groupCSplits).hasSize(1);
    assertThat(extractPaths(groupCSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#group_c/image.jpg");
  }

  @Test
  public void multipleDeviceGroups_withSuffixStripping() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#group_a/image.jpg")
            .addFile("assets/images#group_b/image.jpg")
            .addFile("assets/images#group_c/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#group_a", assetsDirectoryTargeting(TARGET_A_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images#group_b", assetsDirectoryTargeting(TARGET_B_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images#group_c", assetsDirectoryTargeting(TARGET_C_OF_ABC))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    ImmutableCollection<ModuleSplit> assetsSplits =
        DeviceGroupAssetsSplitter.create(/* stripTargetingSuffix= */ true).split(baseSplit);

    assertThat(assetsSplits).hasSize(4);
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY))).isEmpty();

    ImmutableList<ModuleSplit> groupASplits =
        getSplitsWithTargetingEqualTo(assetsSplits, apkDeviceGroupTargeting(TARGET_A_OF_ABC));
    assertThat(groupASplits).hasSize(1);
    assertThat(extractPaths(groupASplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");

    ImmutableList<ModuleSplit> groupBSplits =
        getSplitsWithTargetingEqualTo(assetsSplits, apkDeviceGroupTargeting(TARGET_B_OF_ABC));
    assertThat(groupBSplits).hasSize(1);
    assertThat(extractPaths(groupBSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");

    ImmutableList<ModuleSplit> groupCSplits =
        getSplitsWithTargetingEqualTo(assetsSplits, apkDeviceGroupTargeting(TARGET_C_OF_ABC));
    assertThat(groupCSplits).hasSize(1);
    assertThat(extractPaths(groupCSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");
  }

  @Test
  public void multipleDeviceGroups_withUngroupedFile_throws() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#group_a/image.jpg")
            .addFile("assets/images#group_b/image.jpg")
            .addFile("assets/images#group_c/image.jpg")
            .addFile("assets/images/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#group_a", assetsDirectoryTargeting(TARGET_A_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images#group_b", assetsDirectoryTargeting(TARGET_B_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images#group_c", assetsDirectoryTargeting(TARGET_C_OF_ABC)),
                    targetedAssetsDirectory(
                        "assets/images",
                        assetsDirectoryTargeting(
                            DeviceGroupTargeting.newBuilder()
                                .addAllAlternatives(ImmutableList.of("a", "b"))
                                .build()))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    assertThrows(
        IllegalStateException.class,
        () -> DeviceGroupAssetsSplitter.create(/* stripTargetingSuffix= */ false).split(baseSplit));
  }
}
