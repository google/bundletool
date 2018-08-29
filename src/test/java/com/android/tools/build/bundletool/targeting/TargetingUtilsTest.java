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

package com.android.tools.build.bundletool.targeting;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.utils.TargetingProtoUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingUtilsTest {

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
  public void matchModuleSplitWithVariants_simple() {

    ModuleSplit moduleSplit = createModuleSplit(VariantTargeting.getDefaultInstance());
    ImmutableList<VariantTargeting> variants =
        ImmutableList.of(VariantTargeting.getDefaultInstance());

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantsSplitsMap =
        TargetingUtils.matchModuleSplitWithVariants(variants, ImmutableList.of(moduleSplit));

    assertThat(variantsSplitsMap).hasSize(1);
    assertThat(variantsSplitsMap).containsEntry(variants.get(0), moduleSplit);
  }

  @Test
  public void matchModuleSplitWithVariants_singleModuleSplit() {

    ModuleSplit moduleSplit = createModuleSplit(variantTargetingFromSdkVersion(sdkVersionFrom(21)));
    ImmutableList<VariantTargeting> variants = createVariantsTargetingSdkVersions(21, 23, 25);

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantsSplitsMap =
        TargetingUtils.matchModuleSplitWithVariants(variants, ImmutableList.of(moduleSplit));

    assertThat(variantsSplitsMap).hasSize(3);
    assertThat(variantsSplitsMap).containsEntry(variants.get(0), moduleSplit);
    assertThat(variantsSplitsMap).containsEntry(variants.get(1), moduleSplit);
    assertThat(variantsSplitsMap).containsEntry(variants.get(2), moduleSplit);
  }

  @Test
  public void matchModuleSplitWithVariants_multipleModuleSplitDefaultVariantTargeting_throws() {

    ModuleSplit moduleSplit1 =
        createModuleSplit(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(25))));
    ModuleSplit moduleSplit2 =
        createModuleSplit(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(25), ImmutableSet.of(sdkVersionFrom(21))));

    ImmutableList<VariantTargeting> variants =
        ImmutableList.of(VariantTargeting.getDefaultInstance());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                TargetingUtils.matchModuleSplitWithVariants(
                    variants, ImmutableList.of(moduleSplit1, moduleSplit2)));

    assertThat(exception).hasMessageThat().contains("Partial overlap between the sdk ranges of");
  }

  @Test
  public void matchModuleSplitWithVariants_multipleModuleSplits() {

    ModuleSplit moduleSplit1 =
        createModuleSplit(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(25))));
    ModuleSplit moduleSplit2 =
        createModuleSplit(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(25), ImmutableSet.of(sdkVersionFrom(21))));

    ImmutableList<VariantTargeting> variants = createVariantsTargetingSdkVersions(21, 23, 25);

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantsSplitsMap =
        TargetingUtils.matchModuleSplitWithVariants(
            variants, ImmutableList.of(moduleSplit1, moduleSplit2));

    assertThat(variantsSplitsMap).hasSize(3);
    assertThat(variantsSplitsMap).containsEntry(variants.get(0), moduleSplit1);
    assertThat(variantsSplitsMap).containsEntry(variants.get(1), moduleSplit1);
    assertThat(variantsSplitsMap).containsEntry(variants.get(2), moduleSplit2);
  }

  @Test
  public void matchModuleSplitWithVariants_multipleModuleSplitsPartialOverlapTargeting_throws() {

    ModuleSplit moduleSplit1 =
        createModuleSplit(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(24))));
    ModuleSplit moduleSplit2 =
        createModuleSplit(
            variantTargetingFromSdkVersion(
                sdkVersionFrom(24), ImmutableSet.of(sdkVersionFrom(21))));

    ImmutableList<VariantTargeting> variants = createVariantsTargetingSdkVersions(21, 23, 25);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                TargetingUtils.matchModuleSplitWithVariants(
                    variants, ImmutableList.of(moduleSplit1, moduleSplit2)));

    assertThat(exception).hasMessageThat().contains("Partial overlap between the sdk ranges of");
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

  private static ModuleSplit createModuleSplit(VariantTargeting variantTargeting) {
    return ModuleSplit.builder()
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
        .setEntries(ImmutableList.of())
        .setMasterSplit(true)
        .setSplitType(SplitType.SPLIT)
        .setModuleName(BundleModuleName.create("base"))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(variantTargeting)
        .build();
  }

  private ImmutableList<VariantTargeting> createVariantsTargetingSdkVersions(int... sdkValues) {

    ImmutableSet<SdkVersion> sdkVersions =
        Arrays.stream(sdkValues)
            .mapToObj(TargetingProtoUtils::sdkVersionFrom)
            .collect(toImmutableSet());

    return sdkVersions
        .stream()
        .map(
            sdkVersion ->
                variantTargetingFromSdkVersion(
                    sdkVersion,
                    Sets.difference(sdkVersions, ImmutableSet.of(sdkVersion)).immutableCopy()))
        .collect(toImmutableList());
  }
}
