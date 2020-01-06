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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.excludeAssetsTargetingOtherValue;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMaxSdk;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMinSdk;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeTextureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.InMemoryModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingUtilsTest {

  private static final byte[] DUMMY_CONTENT = new byte[1];

  @Test
  public void getDimensions_noDimension() {
    assertThat(TargetingUtils.getTargetingDimensions(AssetsDirectoryTargeting.getDefaultInstance()))
        .isEmpty();
  }

  @Test
  public void getDimensions_graphicsApi() {
    assertThat(
            TargetingUtils.getTargetingDimensions(
                assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2, 3)))))
        .containsExactly(TargetingDimension.GRAPHICS_API);
  }

  @Test
  public void getDimensions_language() {
    assertThat(
            TargetingUtils.getTargetingDimensions(
                assetsDirectoryTargeting(languageTargeting("en"))))
        .containsExactly(TargetingDimension.LANGUAGE);
  }

  @Test
  public void getDimensions_textureCompressionFormat() {
    assertThat(
            TargetingUtils.getTargetingDimensions(
                assetsDirectoryTargeting(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ATC))))
        .containsExactly(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
  }

  @Test
  public void getDimensions_abi() {
    assertThat(
            TargetingUtils.getTargetingDimensions(
                assetsDirectoryTargeting(abiTargeting(AbiAlias.ARM64_V8A))))
        .containsExactly(TargetingDimension.ABI);
  }

  @Test
  public void getDimensions_multiple() {
    assertThat(
            TargetingUtils.getTargetingDimensions(
                mergeAssetsTargeting(
                    assetsDirectoryTargeting(
                        textureCompressionTargeting(TextureCompressionFormatAlias.ATC)),
                    assetsDirectoryTargeting(
                        abiTargeting(
                            ImmutableSet.of(AbiAlias.ARM64_V8A, AbiAlias.ARMEABI_V7A),
                            ImmutableSet.of())),
                    assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(3))))))
        .containsExactly(
            TargetingDimension.ABI,
            TargetingDimension.TEXTURE_COMPRESSION_FORMAT,
            TargetingDimension.GRAPHICS_API);
  }

  @Test
  public void generateAllVariantTargetings_singleVariant() {
    assertThat(
            TargetingUtils.generateAllVariantTargetings(
                ImmutableSet.of(variantTargetingFromSdkVersion(sdkVersionFrom(1)))))
        .containsExactly(variantTargetingFromSdkVersion(sdkVersionFrom(1)));
  }

  @Test
  public void generateAllVariantTargetings_disjointVariants() {
    assertThat(
            TargetingUtils.generateAllVariantTargetings(
                ImmutableSet.of(
                    variantTargetingFromSdkVersion(
                        sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
                    variantTargetingFromSdkVersion(
                        sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))))))
        .containsExactly(
            variantTargetingFromSdkVersion(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
            variantTargetingFromSdkVersion(
                sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))));
  }

  @Test
  public void generateAllVariantTargetings_overlappingVariants() {
    assertThat(
            TargetingUtils.generateAllVariantTargetings(
                ImmutableSet.of(
                    variantTargetingFromSdkVersion(
                        sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(25))),
                    variantTargetingFromSdkVersion(
                        sdkVersionFrom(25), ImmutableSet.of(sdkVersionFrom(21))),
                    variantTargetingFromSdkVersion(
                        sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
                    variantTargetingFromSdkVersion(
                        sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))))))
        .containsExactly(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23), sdkVersionFrom(25))),
            variantTargetingFromSdkVersion(
                sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21), sdkVersionFrom(25))),
            variantTargetingFromSdkVersion(
                sdkVersionFrom(25), ImmutableSet.of(sdkVersionFrom(21), sdkVersionFrom(23))));
  }

  @Test
  public void cropVariants_sdkRangeNoOverlap() {
    assertThat(
            TargetingUtils.cropVariantsWithAppSdkRange(
                ImmutableSet.of(
                    variantTargetingFromSdkVersion(sdkVersionFrom(21)),
                    variantTargetingFromSdkVersion(sdkVersionFrom(25))),
                Range.closed(15, 19)))
        .isEmpty();
  }

  @Test
  public void cropVariants_sdkRangeOverlaps() {
    assertThat(
            TargetingUtils.cropVariantsWithAppSdkRange(
                ImmutableSet.of(
                    variantTargetingFromSdkVersion(sdkVersionFrom(21)),
                    variantTargetingFromSdkVersion(sdkVersionFrom(25))),
                Range.closed(22, 26)))
        .containsExactly(
            variantTargetingFromSdkVersion(sdkVersionFrom(22)),
            variantTargetingFromSdkVersion(sdkVersionFrom(25)));
  }

  @Test
  public void cropVariants_sdkRangeOverlaps_noEmptyRanges() {
    assertThat(
            TargetingUtils.cropVariantsWithAppSdkRange(
                ImmutableSet.of(
                    variantTargetingFromSdkVersion(sdkVersionFrom(21)),
                    variantTargetingFromSdkVersion(sdkVersionFrom(25))),
                Range.closed(25, 26)))
        .containsExactly(variantTargetingFromSdkVersion(sdkVersionFrom(25)));
  }

  @Test
  public void minSdk_emptySdkTargeting() {
    assertThat(getMinSdk(SdkVersionTargeting.getDefaultInstance())).isEqualTo(1);
  }

  @Test
  public void minSdk_nonEmptySdkTargeting() {
    assertThat(
            getMinSdk(sdkVersionTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23)))))
        .isEqualTo(21);
  }

  @Test
  public void maxSdk_emptySdkTargeting() {
    assertThat(getMaxSdk(SdkVersionTargeting.getDefaultInstance())).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void maxSdk_nonEmptySdkTargeting() {
    assertThat(
            getMaxSdk(sdkVersionTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23)))))
        .isEqualTo(23);
    assertThat(
            getMaxSdk(sdkVersionTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21)))))
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void excludeAssetsTargetingOtherValue_variousTargeting() {
    ModuleSplit split =
        ModuleSplit.builder()
            // Unrelevant, default values for a split:
            .setModuleName(BundleModuleName.create("base"))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setMasterSplit(true)
            // Create entries for assets and their associated assets config with some targeting:
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "assets/textures/untargeted_texture.dat", DUMMY_CONTENT),
                    InMemoryModuleEntry.ofFile(
                        "assets/textures#tcf_etc1/etc1_texture.dat", DUMMY_CONTENT),
                    InMemoryModuleEntry.ofFile(
                        "assets/textures#tcf_atc/atc_texture.dat", DUMMY_CONTENT)))
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/textures",
                        assetsDirectoryTargeting(
                            alternativeTextureCompressionTargeting(ETC1_RGB8))),
                    targetedAssetsDirectory(
                        "assets/textures#tcf_etc1",
                        assetsDirectoryTargeting(textureCompressionTargeting(ETC1_RGB8))),
                    targetedAssetsDirectory(
                        "assets/textures#tcf_atc",
                        assetsDirectoryTargeting(textureCompressionTargeting(ATC)))))
            .build();

    ModuleSplit assetsRemovedSplit =
        excludeAssetsTargetingOtherValue(
            split, TargetingDimension.TEXTURE_COMPRESSION_FORMAT, "etc1");

    // Check that the ATC and untargeted sibling folders have been excluded
    assertThat(assetsRemovedSplit.getEntries()).hasSize(1);
    assertThat((Object) assetsRemovedSplit.getEntries().get(0).getPath())
        .isEqualTo(ZipPath.create("assets/textures#tcf_etc1/etc1_texture.dat"));

    assertThat(assetsRemovedSplit.getAssetsConfig().get().getDirectoryCount()).isEqualTo(1);
    assertThat(assetsRemovedSplit.getAssetsConfig().get().getDirectory(0).getPath())
        .isEqualTo("assets/textures#tcf_etc1");
  }

  private static VariantTargeting variantTargetingFromSdkVersion(SdkVersion values) {
    return variantTargetingFromSdkVersion(values, ImmutableSet.of());
  }

  private static VariantTargeting variantTargetingFromSdkVersion(
      SdkVersion values, ImmutableSet<SdkVersion> alternatives) {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(sdkVersionTargeting(values, alternatives))
        .build();
  }
}
