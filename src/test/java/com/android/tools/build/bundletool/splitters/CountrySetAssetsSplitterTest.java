/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithDefaultTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CountrySetAssetsSplitter}. */
@RunWith(JUnit4.class)
public class CountrySetAssetsSplitterTest {

  @Test
  public void multipleCountrySetsAndUntargetedFile() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#countries_latam/image.jpg")
            .addFile("assets/images#countries_sea/image.jpg")
            .addFile("assets/images/image.jpg")
            .addFile("assets/file.txt")
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
                            alternativeCountrySetTargeting(ImmutableList.of("latam", "sea")))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    ImmutableCollection<ModuleSplit> assetsSplits =
        CountrySetAssetsSplitter.create(/* stripTargetingSuffix= */ false).split(baseSplit);

    assertThat(assetsSplits).hasSize(4);
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/file.txt");

    ImmutableList<ModuleSplit> latamSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("latam"), ImmutableList.of("sea"))));
    assertThat(latamSplits).hasSize(1);
    assertThat(extractPaths(latamSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#countries_latam/image.jpg");

    ImmutableList<ModuleSplit> seaSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("sea"), ImmutableList.of("latam"))));
    assertThat(seaSplits).hasSize(1);
    assertThat(extractPaths(seaSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#countries_sea/image.jpg");

    ImmutableList<ModuleSplit> restOfWorldSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                alternativeCountrySetTargeting(ImmutableList.of("latam", "sea"))));
    assertThat(restOfWorldSplits).hasSize(1);
    assertThat(extractPaths(restOfWorldSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");
  }

  @Test
  public void countrySet_restOfWorldFolderMissing_ok() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
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
                                ImmutableList.of("sea"), ImmutableList.of("latam"))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    ImmutableCollection<ModuleSplit> assetsSplits =
        CountrySetAssetsSplitter.create(/* stripTargetingSuffix= */ false).split(baseSplit);

    assertThat(assetsSplits).hasSize(3);
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY))).isEmpty();

    ImmutableList<ModuleSplit> latamSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("latam"), ImmutableList.of("sea"))));
    assertThat(latamSplits).hasSize(1);
    assertThat(extractPaths(latamSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#countries_latam/image.jpg");

    ImmutableList<ModuleSplit> seaSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("sea"), ImmutableList.of("latam"))));
    assertThat(seaSplits).hasSize(1);
    assertThat(extractPaths(seaSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#countries_sea/image.jpg");
  }

  @Test
  public void multipleCountrySets_withSuffixStripping() {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#countries_latam/image.jpg")
            .addFile("assets/images#countries_sea/image.jpg")
            .addFile("assets/images/image.jpg")
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
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    ImmutableCollection<ModuleSplit> assetsSplits =
        CountrySetAssetsSplitter.create(/* stripTargetingSuffix= */ true).split(baseSplit);

    assertThat(assetsSplits).hasSize(4);
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY))).isEmpty();

    ImmutableList<ModuleSplit> latamSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("latam"), ImmutableList.of("sea"))));
    assertThat(latamSplits).hasSize(1);
    assertThat(extractPaths(latamSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");

    ImmutableList<ModuleSplit> seaSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                countrySetTargeting(ImmutableList.of("sea"), ImmutableList.of("latam"))));
    assertThat(seaSplits).hasSize(1);
    assertThat(extractPaths(seaSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");

    ImmutableList<ModuleSplit> restOfWorldSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkCountrySetTargeting(
                alternativeCountrySetTargeting(ImmutableList.of("latam", "sea"))));
    assertThat(restOfWorldSplits).hasSize(1);
    assertThat(extractPaths(restOfWorldSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images/image.jpg");
  }
}
