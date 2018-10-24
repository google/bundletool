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

import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.compareManifestMutators;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkGraphicsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithDefaultTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link GraphicsApiAssetsSplitter}. */
@RunWith(JUnit4.class)
public class GraphicsApiAssetsSplitterTest {

  @Test
  public void singleSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/images#opengl_2.0/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(1);
    ModuleSplit split = assetsSplits.iterator().next();
    assertThat(split.getApkTargeting())
        .isEqualTo(apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(2))));
    assertThat(split.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(split.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(split.getEntries()))
        .containsExactly(
            "assets/images#opengl_2.0/image.jpg", "assets/images#opengl_2.0/image2.jpg");
  }

  @Test
  public void singleOpenGlAndDefaultSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2)))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(2);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries()))
        .containsExactly("assets/image2.jpg");
    List<ModuleSplit> gl2PlusSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits, apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(2))));
    assertThat(gl2PlusSplits).hasSize(1);
    assertThat(extractPaths(gl2PlusSplits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_2.0/image.jpg");
  }

  @Test
  public void multipleVersionsAndDefaultSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/images#opengl_3.0/image.jpg")
            .addFile("assets/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(2), ImmutableSet.of(openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/images#opengl_3.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(3), ImmutableSet.of(openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(3);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries()))
        .containsExactly("assets/image2.jpg");
    List<ModuleSplit> gl2Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(2), ImmutableSet.of(openGlVersionFrom(3)))));
    assertThat(gl2Splits).hasSize(1);
    assertThat(extractPaths(gl2Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_2.0/image.jpg");
    List<ModuleSplit> gl3Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(3), ImmutableSet.of(openGlVersionFrom(2)))));
    assertThat(gl3Splits).hasSize(1);
    assertThat(extractPaths(gl3Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_3.0/image.jpg");
  }

  @Test
  public void multipleVersionsWithOpenRanges() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_3.0/image.jpg")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/images#opengl_1.0/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_1.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(1),
                                ImmutableSet.of(openGlVersionFrom(2), openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(2),
                                ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/images#opengl_3.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(3),
                                ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(2)))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(3);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> gl1Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(1),
                    ImmutableSet.of(openGlVersionFrom(2), openGlVersionFrom(3)))));
    assertThat(gl1Splits).hasSize(1);
    assertThat(extractPaths(gl1Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_1.0/image.jpg");
    List<ModuleSplit> gl2Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(2),
                    ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(3)))));
    assertThat(gl2Splits).hasSize(1);
    assertThat(extractPaths(gl2Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_2.0/image.jpg");
    List<ModuleSplit> gl3Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(3),
                    ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(2)))));
    assertThat(gl3Splits).hasSize(1);
    assertThat(extractPaths(gl3Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_3.0/image.jpg");
  }

  @Test
  public void targetingWithDifferentAlternatives() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/images#opengl_3.0/image.jpg")
            .addFile("assets/other#opengl_1.0/image.jpg")
            .addFile("assets/other#opengl_2.0/image.jpg")
            .addFile("assets/other#opengl_3.0/image.jpg")
            .addFile("assets/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(2), ImmutableSet.of(openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/images#opengl_3.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(3), ImmutableSet.of(openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets/other#opengl_1.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(1),
                                ImmutableSet.of(openGlVersionFrom(2), openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/other#opengl_2.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(2),
                                ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/other#opengl_3.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(3),
                                ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    // Expecting 6 splits: 5 splits from the targeted directories (2 different alternative sets)
    // and the default split.
    assertThat(assetsSplits).hasSize(6);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries()))
        .containsExactly("assets/image2.jpg");
    List<ModuleSplit> gl1Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(1),
                    ImmutableSet.of(openGlVersionFrom(2), openGlVersionFrom(3)))));
    assertThat(gl1Splits).hasSize(1);
    assertThat(extractPaths(gl1Splits.get(0).getEntries()))
        .containsExactly("assets/other#opengl_1.0/image.jpg");

    List<ModuleSplit> gl2SplitsImages =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(2), ImmutableSet.of(openGlVersionFrom(3)))));
    assertThat(gl2SplitsImages).hasSize(1);
    assertThat(extractPaths(gl2SplitsImages.get(0).getEntries()))
        .containsExactly("assets/images#opengl_2.0/image.jpg");
    List<ModuleSplit> gl2SplitsOther =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(2),
                    ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(3)))));
    assertThat(gl2SplitsOther).hasSize(1);
    assertThat(extractPaths(gl2SplitsOther.get(0).getEntries()))
        .containsExactly("assets/other#opengl_2.0/image.jpg");

    List<ModuleSplit> gl3SplitsImages =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(3), ImmutableSet.of(openGlVersionFrom(2)))));
    assertThat(gl3SplitsImages).hasSize(1);
    assertThat(extractPaths(gl3SplitsImages.get(0).getEntries()))
        .containsExactly("assets/images#opengl_3.0/image.jpg");
    List<ModuleSplit> gl3SplitsOther =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            apkGraphicsTargeting(
                openGlVersionTargeting(
                    openGlVersionFrom(3),
                    ImmutableSet.of(openGlVersionFrom(1), openGlVersionFrom(2)))));
    assertThat(gl3SplitsOther).hasSize(1);
    assertThat(extractPaths(gl3SplitsOther.get(0).getEntries()))
        .containsExactly("assets/other#opengl_3.0/image.jpg");
  }

  /** Tests that the targeted directories having common OpenGL targeting are captured together. */
  @Test
  public void multipleDimensionsMixed() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/texture#tcf_atc/image.jpg")
            .addFile("assets/images#opengl_2.0/texture#tcf_etc1/image.jpg")
            .addFile("assets/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0/texture#tcf_atc",
                        mergeAssetsTargeting(
                            assetsDirectoryTargeting(
                                textureCompressionTargeting(
                                    TextureCompressionFormatAlias.ATC,
                                    ImmutableSet.of(TextureCompressionFormatAlias.ETC1_RGB8))),
                            assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0/texture#tcf_etc1",
                        mergeAssetsTargeting(
                            assetsDirectoryTargeting(
                                textureCompressionTargeting(
                                    TextureCompressionFormatAlias.ETC1_RGB8,
                                    ImmutableSet.of(TextureCompressionFormatAlias.ATC))),
                            assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(2);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(extractPaths(defaultSplits.get(0).getEntries()))
        .containsExactly("assets/image2.jpg");
    List<ModuleSplit> gl2PlusSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits, apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(2))));
    assertThat(gl2PlusSplits).hasSize(1);
    assertThat(extractPaths(gl2PlusSplits.get(0).getEntries()))
        .containsExactly(
            "assets/images#opengl_2.0/texture#tcf_atc/image.jpg",
            "assets/images#opengl_2.0/texture#tcf_etc1/image.jpg");
  }

  /** Tests that the targeted directory on orthogonal dimension goes to the default split. */
  @Test
  public void multipleDimensionsUnMixed() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/texture#tcf_atc/lib.zip")
            .addFile("assets/other/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        mergeAssetsTargeting(
                            assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets/texture#tcf_atc",
                        assetsDirectoryTargeting(
                            textureCompressionTargeting(TextureCompressionFormatAlias.ATC))),
                    targetedAssetsDirectory(
                        "assets/other", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    // Expecting 2 splits: one with openGL targeting and the default split.
    assertThat(assetsSplits).hasSize(2);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(
            extractPaths(
                defaultSplits
                    .stream()
                    .map(ModuleSplit::getEntries)
                    .flatMap(Collection::stream)
                    .collect(toImmutableList())))
        .containsExactly("assets/other/image2.jpg", "assets/texture#tcf_atc/lib.zip");
    List<ModuleSplit> gl2PlusSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits, apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(2))));
    assertThat(gl2PlusSplits).hasSize(1);
    assertThat(extractPaths(gl2PlusSplits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_2.0/image.jpg");
  }

  /** Tests that if the module split already contains targeting, it is retained. */
  @Test
  public void targetedSplit_multipleVersions() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/images#opengl_3.0/image.jpg")
            .addFile("assets/images/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(2), ImmutableSet.of(openGlVersionFrom(3))))),
                    targetedAssetsDirectory(
                        "assets/images#opengl_3.0",
                        assetsDirectoryTargeting(
                            openGlVersionTargeting(
                                openGlVersionFrom(3), ImmutableSet.of(openGlVersionFrom(2))))),
                    targetedAssetsDirectory(
                        "assets/images",
                        assetsDirectoryTargeting(
                            graphicsApiTargeting(
                                ImmutableSet.of(),
                                ImmutableSet.of(openGlVersionFrom(2), openGlVersionFrom(3)))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    // The input split already targets x86.
    ApkTargeting abiTargeting = apkAbiTargeting(ImmutableSet.of(AbiAlias.X86), ImmutableSet.of());
    ModuleSplit baseSplit =
        ModuleSplit.forAssets(testModule)
            .toBuilder()
            .setApkTargeting(abiTargeting)
            .setMasterSplit(false)
            .build();
    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(3);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> fallbackSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            mergeApkTargeting(
                abiTargeting,
                apkGraphicsTargeting(
                    graphicsApiTargeting(
                        ImmutableSet.of(),
                        ImmutableSet.of(openGlVersionFrom(2), openGlVersionFrom(3))))));
    assertThat(fallbackSplits).hasSize(1);
    assertThat(extractPaths(fallbackSplits.get(0).getEntries()))
        .containsExactly("assets/images/image2.jpg");
    List<ModuleSplit> gl2Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            mergeApkTargeting(
                abiTargeting,
                apkGraphicsTargeting(
                    openGlVersionTargeting(
                        openGlVersionFrom(2), ImmutableSet.of(openGlVersionFrom(3))))));
    assertThat(gl2Splits).hasSize(1);
    assertThat(extractPaths(gl2Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_2.0/image.jpg");
    List<ModuleSplit> gl3Splits =
        getSplitsWithTargetingEqualTo(
            assetsSplits,
            mergeApkTargeting(
                abiTargeting,
                apkGraphicsTargeting(
                    openGlVersionTargeting(
                        openGlVersionFrom(3), ImmutableSet.of(openGlVersionFrom(2))))));
    assertThat(gl3Splits).hasSize(1);
    assertThat(extractPaths(gl3Splits.get(0).getEntries()))
        .containsExactly("assets/images#opengl_3.0/image.jpg");
  }

  @Test
  public void masterSplitFlag_outputMasterSplitInheritsTrue() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2)))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    assertThat(baseSplit.isMasterSplit()).isTrue();

    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);

    assertThat(assetsSplits).hasSize(2);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(defaultSplits.get(0).isMasterSplit()).isTrue();
    List<ModuleSplit> gl2PlusSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits, apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(2))));
    assertThat(gl2PlusSplits).hasSize(1);
    assertThat(gl2PlusSplits.get(0).isMasterSplit()).isFalse();
  }

  @Test
  public void masterSplitFlag_outputMasterSplitInheritsFalse() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .addFile("assets/image2.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2)))),
                    targetedAssetsDirectory(
                        "assets", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit =
        ModuleSplit.forAssets(testModule).toBuilder().setMasterSplit(false).build();

    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);

    assertThat(assetsSplits).hasSize(2);
    assertThat(
            assetsSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    List<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(assetsSplits);
    assertThat(defaultSplits).hasSize(1);
    assertThat(defaultSplits.get(0).isMasterSplit()).isFalse();
    List<ModuleSplit> gl2PlusSplits =
        getSplitsWithTargetingEqualTo(
            assetsSplits, apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(2))));
    assertThat(gl2PlusSplits).hasSize(1);
    assertThat(gl2PlusSplits.get(0).isMasterSplit()).isFalse();
  }

  @Test
  public void inputSplitAlreadyTargetingOpenGl_throws() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2))))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit =
        ModuleSplit.forAssets(testModule)
            .toBuilder()
            .setApkTargeting(apkGraphicsTargeting(graphicsApiTargeting(openGlVersionFrom(1))))
            .setMasterSplit(false)
            .build();
    Throwable t =
        assertThrows(
            IllegalArgumentException.class,
            () -> GraphicsApiAssetsSplitter.create().split(baseSplit));
    assertThat(t).hasMessageThat().contains("Split is already targeting the splitting dimension.");
  }

  @Test
  public void manifestMutatorToRequireSplits_notRegistered_whenNoGraphicsApiSpecificAssets()
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
        GraphicsApiAssetsSplitter.create().split(baseSplit);

    assertThat(assetsSplits).hasSize(1);
    assertThat(assetsSplits.asList().get(0).getMasterManifestMutators()).isEmpty();
  }

  @Test
  public void manifestMutatorToRequireSplits_registered_whenGraphicsApiSpecificAssetsPresent()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/images#opengl_2.0/image.jpg")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/images#opengl_2.0",
                        assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2))))))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);

    Collection<ModuleSplit> assetsSplits = GraphicsApiAssetsSplitter.create().split(baseSplit);

    ImmutableList<ModuleSplit> configSplits =
        assetsSplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());

    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(
              compareManifestMutators(
                  configSplit.getMasterManifestMutators(), withSplitsRequired(true)))
          .isTrue();
    }
  }
}
