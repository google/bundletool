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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.compareManifestMutators;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithDefaultTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TextureCompressionFormatAssetsSplitter}.
 *
 * <p>Note that since this implementation re-uses {@link AssetsDimensionSplitterFactory} it has only
 * basic sanity testing that the supplied getter and setter functions are correct.
 */
@RunWith(JUnit4.class)
public class TextureCompressionFormatAssetsSplitterTest {

  @Test
  public void multipleTexturesAndDefaultSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#tcf_etc1/image.jpg")
            .addFile("assets/images#tcf_3dc/image.jpg")
            .addFile("assets/file.txt")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#tcf_etc1",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(
                                TextureCompressionFormatAlias.ETC1_RGB8,
                                ImmutableSet.of(TextureCompressionFormatAlias.THREE_DC)))),
                    targetedAssetsDirectory(
                        "assets/images#tcf_3dc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(
                                TextureCompressionFormatAlias.THREE_DC,
                                ImmutableSet.of(TextureCompressionFormatAlias.ETC1_RGB8)))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits =
        TextureCompressionFormatAssetsSplitter.create(/* stripTargetingSuffix= */ false)
            .split(baseSplit);
    assertThat(assetsSplits).hasSize(3);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/file.txt");
    List<ModuleSplit> etc1Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkTextureTargeting(
                textureCompressionTargeting(
                    TextureCompressionFormatAlias.ETC1_RGB8,
                    ImmutableSet.of(TextureCompressionFormatAlias.THREE_DC))));
    assertThat(etc1Splits).hasSize(1);
    assertThat(extractPaths(etc1Splits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#tcf_etc1/image.jpg");
    List<ModuleSplit> threeDcSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkTextureTargeting(
                textureCompressionTargeting(
                    TextureCompressionFormatAlias.THREE_DC,
                    ImmutableSet.of(TextureCompressionFormatAlias.ETC1_RGB8))));
    assertThat(threeDcSplits).hasSize(1);
    assertThat(extractPaths(threeDcSplits.get(0).findEntriesUnderPath(ASSETS_DIRECTORY)))
        .containsExactly("assets/images#tcf_3dc/image.jpg");
  }


  @Test
  public void manifestMutatorToRequireSplits_notRegistered_whenNoTcfSpecificAssets()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/other/file.dat")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/other", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);

    ImmutableCollection<ModuleSplit> assetsSplits =
        TextureCompressionFormatAssetsSplitter.create(/* stripTargetingSuffix= */ false)
            .split(baseSplit);

    assertThat(assetsSplits).hasSize(1);
    assertThat(assetsSplits.asList().get(0).getMasterManifestMutators()).isEmpty();
  }

  @Test
  public void manifestMutatorToRequireSplits_registered_whenTcfSpecificAssetsPresent()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#tcf_etc1/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#tcf_etc1",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(
                                TextureCompressionFormatAlias.ETC1_RGB8,
                                ImmutableSet.of(TextureCompressionFormatAlias.THREE_DC))))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);

    ImmutableCollection<ModuleSplit> assetsSplits =
        TextureCompressionFormatAssetsSplitter.create(/* stripTargetingSuffix= */ false)
            .split(baseSplit);

    ImmutableList<ModuleSplit> configSplits =
        assetsSplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());

    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(
              compareManifestMutators(
                  configSplit.getMasterManifestMutators(), withSplitsRequired(/* value= */ true)))
          .isTrue();
    }
  }
}
