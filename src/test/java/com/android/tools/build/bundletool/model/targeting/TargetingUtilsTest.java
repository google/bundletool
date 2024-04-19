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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getAlternativeTargeting;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMaxSdk;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMinSdk;
import static com.android.tools.build.bundletool.model.utils.TargetingNormalizer.normalizeAssetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeTextureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
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
  public void getDimensions_deviceTier() {
    assertThat(
            TargetingUtils.getTargetingDimensions(assetsDirectoryTargeting(deviceTierTargeting(0))))
        .containsExactly(TargetingDimension.DEVICE_TIER);
  }

  @Test
  public void getDimensions_countrySet() {
    assertThat(
            TargetingUtils.getTargetingDimensions(
                assetsDirectoryTargeting(countrySetTargeting("latam"))))
        .containsExactly(TargetingDimension.COUNTRY_SET);
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
                            ImmutableSet.of())))))
        .containsExactly(TargetingDimension.ABI, TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
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
  public void extractDimensionTargeting_countrySetTexture() {
    AssetsDirectoryTargeting targeting =
        AssetsDirectoryTargeting.newBuilder()
            .setCountrySet(countrySetTargeting("latam"))
            .setTextureCompressionFormat(
                textureCompressionTargeting(TextureCompressionFormatAlias.ASTC))
            .setDeviceTier(deviceTierTargeting(2))
            .build();

    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.COUNTRY_SET))
        .hasValue(
            AssetsDirectoryTargeting.newBuilder()
                .setCountrySet(countrySetTargeting("latam"))
                .build());
    assertThat(
            TargetingUtils.extractDimensionTargeting(
                targeting, TargetingDimension.TEXTURE_COMPRESSION_FORMAT))
        .hasValue(
            AssetsDirectoryTargeting.newBuilder()
                .setTextureCompressionFormat(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ASTC))
                .build());
    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.DEVICE_TIER))
        .hasValue(
            AssetsDirectoryTargeting.newBuilder().setDeviceTier(deviceTierTargeting(2)).build());
    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.ABI))
        .isEmpty();
    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.LANGUAGE))
        .isEmpty();
  }

  @Test
  public void extractDimensionTargeting_empty() {
    AssetsDirectoryTargeting targeting = AssetsDirectoryTargeting.getDefaultInstance();

    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.COUNTRY_SET))
        .isEmpty();
    assertThat(
            TargetingUtils.extractDimensionTargeting(
                targeting, TargetingDimension.TEXTURE_COMPRESSION_FORMAT))
        .isEmpty();
    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.DEVICE_TIER))
        .isEmpty();
    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.ABI))
        .isEmpty();
    assertThat(TargetingUtils.extractDimensionTargeting(targeting, TargetingDimension.LANGUAGE))
        .isEmpty();
  }

  @Test
  public void getAssetDirectories() {
    BundleModule module =
        new BundleModuleBuilder("a")
            .addFile("assets/img1#countries_latam#tcf_astc/image.jpg")
            .addFile("assets/img1#tcf_astc/image.jpg")
            .addFile("assets/img1#tcf_pvrtc/image.jpg")
            .addFile("assets/img1/image.jpg")
            .addFile("assets/file.txt")
            .addFile("foo/bar/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    assertThat(TargetingUtils.getAssetDirectories(module))
        .containsExactly(
            ZipPath.create("assets/img1#countries_latam#tcf_astc"),
            ZipPath.create("assets/img1#tcf_astc"),
            ZipPath.create("assets/img1#tcf_pvrtc"),
            ZipPath.create("assets/img1"),
            ZipPath.create("assets"));
  }

  @Test
  public void getAlternativeTargeting_withSingleDimensionTargeting_countrySet() {
    AssetsDirectoryTargeting latamTargeting =
        assetsDirectoryTargeting(countrySetTargeting("latam"));
    AssetsDirectoryTargeting seaTargeting = assetsDirectoryTargeting(countrySetTargeting("sea"));
    AssetsDirectoryTargeting fallbackTargeting = AssetsDirectoryTargeting.getDefaultInstance();

    assertThat(
            getAlternativeTargeting(
                /* targeting= */ latamTargeting,
                /* allTargeting= */ ImmutableList.of(
                    latamTargeting, seaTargeting, fallbackTargeting)))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                assetsDirectoryTargeting(
                    alternativeCountrySetTargeting(/* alternatives= */ ImmutableList.of("sea")))));
    assertThat(
            getAlternativeTargeting(
                /* targeting= */ fallbackTargeting,
                /* allTargeting= */ ImmutableList.of(
                    latamTargeting, seaTargeting, fallbackTargeting)))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                assetsDirectoryTargeting(
                    alternativeCountrySetTargeting(
                        /* alternatives= */ ImmutableList.of("sea", "latam")))));
  }

  @Test
  public void getAlternativeTargeting_withSingleDimensionTargeting_deviceTier() {
    AssetsDirectoryTargeting tier0Targeting = assetsDirectoryTargeting(deviceTierTargeting(0));
    AssetsDirectoryTargeting tier1Targeting = assetsDirectoryTargeting(deviceTierTargeting(1));
    AssetsDirectoryTargeting tier2Targeting = assetsDirectoryTargeting(deviceTierTargeting(2));

    assertThat(
            getAlternativeTargeting(
                /* targeting= */ tier2Targeting,
                /* allTargeting= */ ImmutableList.of(
                    tier0Targeting, tier1Targeting, tier2Targeting)))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                assetsDirectoryTargeting(
                    alternativeDeviceTierTargeting(/* alternatives= */ ImmutableList.of(0, 1)))));
    assertThat(
            getAlternativeTargeting(
                /* targeting= */ tier0Targeting,
                /* allTargeting= */ ImmutableList.of(
                    tier0Targeting, tier1Targeting, tier2Targeting)))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                assetsDirectoryTargeting(
                    alternativeDeviceTierTargeting(/* alternatives= */ ImmutableList.of(1, 2)))));
  }

  @Test
  public void getAlternativeTargeting_withSingleDimensionTargeting_tcf() {
    AssetsDirectoryTargeting astcTargeting =
        assetsDirectoryTargeting(textureCompressionTargeting(TextureCompressionFormatAlias.ASTC));
    AssetsDirectoryTargeting pvrtcTargeting =
        assetsDirectoryTargeting(textureCompressionTargeting(TextureCompressionFormatAlias.PVRTC));
    AssetsDirectoryTargeting fallbackTargeting = AssetsDirectoryTargeting.getDefaultInstance();

    assertThat(
            getAlternativeTargeting(
                /* targeting= */ astcTargeting,
                /* allTargeting= */ ImmutableList.of(
                    astcTargeting, pvrtcTargeting, fallbackTargeting)))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                assetsDirectoryTargeting(
                    alternativeTextureCompressionTargeting(TextureCompressionFormatAlias.PVRTC))));
    assertThat(
            getAlternativeTargeting(
                /* targeting= */ fallbackTargeting,
                /* allTargeting= */ ImmutableList.of(
                    astcTargeting, pvrtcTargeting, fallbackTargeting)))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                assetsDirectoryTargeting(
                    alternativeTextureCompressionTargeting(
                        TextureCompressionFormatAlias.ASTC, TextureCompressionFormatAlias.PVRTC))));
  }

  @Test
  public void getAlternativeTargeting_withNestedDimensionTargeting_countrySetAndTcf() {
    AssetsDirectoryTargeting latamAstcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of("latam"),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.ASTC));
    AssetsDirectoryTargeting latamPvrtcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of("latam"),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.PVRTC));
    AssetsDirectoryTargeting latamTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of("latam"), /* tcfValue= */ ImmutableList.of());
    AssetsDirectoryTargeting astcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of(),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.ASTC));
    AssetsDirectoryTargeting pvrtcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of(),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.PVRTC));
    AssetsDirectoryTargeting fallbackTargeting = AssetsDirectoryTargeting.getDefaultInstance();
    ImmutableList<AssetsDirectoryTargeting> allTargeting =
        ImmutableList.of(
            latamAstcTargeting,
            latamPvrtcTargeting,
            latamTargeting,
            astcTargeting,
            pvrtcTargeting,
            fallbackTargeting);

    assertThat(getAlternativeTargeting(latamAstcTargeting, allTargeting))
        .isEqualTo(
            AssetsDirectoryTargeting.newBuilder()
                .setTextureCompressionFormat(
                    TextureCompressionFormatTargeting.newBuilder()
                        .addAlternatives(
                            TextureCompressionFormat.newBuilder()
                                .setAlias(TextureCompressionFormatAlias.PVRTC)))
                .build());
    assertThat(getAlternativeTargeting(fallbackTargeting, allTargeting))
        .isEqualTo(
            normalizeAssetsDirectoryTargeting(
                AssetsDirectoryTargeting.newBuilder()
                    .setCountrySet(
                        alternativeCountrySetTargeting(
                            /* alternatives= */ ImmutableList.of("latam")))
                    .setTextureCompressionFormat(
                        alternativeTextureCompressionTargeting(
                            TextureCompressionFormatAlias.ASTC,
                            TextureCompressionFormatAlias.PVRTC))
                    .build()));
  }

  @Test
  public void getAssetsDirectoryTargetingByDimension() {
    AssetsDirectoryTargeting latamAstcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of("latam"),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.ASTC));
    AssetsDirectoryTargeting latamPvrtcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of("latam"),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.PVRTC));
    AssetsDirectoryTargeting latamTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of("latam"), /* tcfValue= */ ImmutableList.of());
    AssetsDirectoryTargeting astcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of(),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.ASTC));
    AssetsDirectoryTargeting pvrtcTargeting =
        constructNestedTargetingWithCountrySetAndTcf(
            /* countrySetValue= */ ImmutableList.of(),
            /* tcfValue= */ ImmutableList.of(TextureCompressionFormatAlias.PVRTC));
    AssetsDirectoryTargeting fallbackTargeting = AssetsDirectoryTargeting.getDefaultInstance();
    ImmutableList<AssetsDirectoryTargeting> allTargeting =
        ImmutableList.of(
            latamAstcTargeting,
            latamPvrtcTargeting,
            latamTargeting,
            astcTargeting,
            pvrtcTargeting,
            fallbackTargeting);
    ImmutableMultimap<TargetingDimension, AssetsDirectoryTargeting>
        expectedAssetsDirectoryTargetingByDimension =
            ImmutableMultimap.<TargetingDimension, AssetsDirectoryTargeting>builder()
                .put(
                    TargetingDimension.COUNTRY_SET,
                    assetsDirectoryTargeting(countrySetTargeting("latam")))
                .put(TargetingDimension.COUNTRY_SET, AssetsDirectoryTargeting.getDefaultInstance())
                .put(
                    TargetingDimension.TEXTURE_COMPRESSION_FORMAT,
                    assetsDirectoryTargeting(
                        textureCompressionTargeting(TextureCompressionFormatAlias.ASTC)))
                .put(
                    TargetingDimension.TEXTURE_COMPRESSION_FORMAT,
                    assetsDirectoryTargeting(
                        textureCompressionTargeting(TextureCompressionFormatAlias.PVRTC)))
                .put(
                    TargetingDimension.TEXTURE_COMPRESSION_FORMAT,
                    AssetsDirectoryTargeting.getDefaultInstance())
                .build();

    ImmutableMultimap<TargetingDimension, AssetsDirectoryTargeting>
        assetsDirectoryTargetingByDimension =
            TargetingUtils.getAssetsDirectoryTargetingByDimension(allTargeting);

    assertThat(assetsDirectoryTargetingByDimension)
        .isEqualTo(expectedAssetsDirectoryTargetingByDimension);
  }

  private static AssetsDirectoryTargeting constructNestedTargetingWithCountrySetAndTcf(
      ImmutableList<String> countrySetValue,
      ImmutableList<TextureCompressionFormatAlias> tcfValue) {
    AssetsDirectoryTargeting.Builder targetingBuilder = AssetsDirectoryTargeting.newBuilder();
    if (!countrySetValue.isEmpty()) {
      targetingBuilder.setCountrySet(
          CountrySetTargeting.newBuilder().addAllValue(countrySetValue).build());
    }
    if (!tcfValue.isEmpty()) {
      targetingBuilder.setTextureCompressionFormat(
          TextureCompressionFormatTargeting.newBuilder()
              .addAllValue(
                  tcfValue.stream()
                      .map(
                          tcfAlias ->
                              TextureCompressionFormat.newBuilder().setAlias(tcfAlias).build())
                      .collect(toImmutableList()))
              .build());
    }
    return targetingBuilder.build();
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
