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

import static com.android.tools.build.bundletool.model.targeting.TargetedDirectorySegment.constructTargetingSegmentPath;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetedDirectorySegmentTest {

  @Test
  public void testTargeting_nokey_value_ok() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions()).isEmpty();
    assertThat(segment.getTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void testTargeting_tcf_astc() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_astc");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.ASTC)));
  }

  @Test
  public void testTargeting_tcf_atc() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_atc");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.ATC)));
  }

  @Test
  public void testTargeting_tcf_dxt1() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_dxt1");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.DXT1)));
  }

  @Test
  public void testTargeting_tcf_latc() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_latc");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.LATC)));
  }

  @Test
  public void testTargeting_tcf_paletted() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_paletted");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.PALETTED)));
  }

  @Test
  public void testTargeting_tcf_pvrtc() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_pvrtc");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.PVRTC)));
  }

  @Test
  public void testTargeting_tcf_etc1() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_etc1");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8)));
  }

  @Test
  public void testTargeting_tcf_etc2() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_etc2");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.ETC2)));
  }

  @Test
  public void testTargeting_tcf_s3tc() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_s3tc");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.S3TC)));
  }

  @Test
  public void testTargeting_tcf_3dc() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tcf_3dc");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions())
        .contains(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    assertThat(segment.getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.THREE_DC)));
  }

  @Test
  public void testTargeting_tcf_badValue() {
    assertThrows(
        InvalidBundleException.class, () -> TargetedDirectorySegment.parse("test#tcf_else"));
  }

  @Test
  public void testTargeting_badKey() {
    assertThrows(
        InvalidBundleException.class,
        () -> TargetedDirectorySegment.parse("test#unsupported_else"));
  }

  @Test
  public void testTargeting_deviceTier() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tier_1");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions()).contains(TargetingDimension.DEVICE_TIER);
    assertThat(segment.getTargeting()).isEqualTo(assetsDirectoryTargeting(deviceTierTargeting(1)));
  }

  @Test
  public void testTargeting_deviceTier_invalidTierName_notNumerical() {
    assertThrows(
        InvalidBundleException.class, () -> TargetedDirectorySegment.parse("test#tier_invalid"));
  }

  @Test
  public void testTargeting_deviceTier_invalidTierName_negativeNumber() {
    assertThrows(
        InvalidBundleException.class, () -> TargetedDirectorySegment.parse("test#tier_-1"));
  }

  @Test
  public void testTargeting_deviceTier_invalidTierName_notAnInt() {
    assertThrows(
        InvalidBundleException.class, () -> TargetedDirectorySegment.parse("test#tier_1.5"));
  }

  @Test
  public void testTargeting_countrySet() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#countries_latam");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions()).contains(TargetingDimension.COUNTRY_SET);
    assertThat(segment.getTargeting())
        .isEqualTo(assetsDirectoryTargeting(countrySetTargeting("latam")));
  }

  @Test
  public void testTargeting_countrySet_invalidCountrySetName_withSpecialChars() {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> TargetedDirectorySegment.parse("assets/test#countries_latam$%@"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Country set name should match the regex '^[a-zA-Z][a-zA-Z0-9_]*$' but got"
                + " 'latam$%@' for directory 'assets/test'.");
  }

  @Test
  public void testTargeting_countrySet_invalidCountrySetName_onlyNumerics() {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> TargetedDirectorySegment.parse("assets/test#countries_1234"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Country set name should match the regex '^[a-zA-Z][a-zA-Z0-9_]*$' but got"
                + " '1234' for directory 'assets/test'.");
  }

  @Test
  public void testFailsParsing_missingKey() {
    assertThrows(InvalidBundleException.class, () -> TargetedDirectorySegment.parse("bad#"));
    assertThrows(InvalidBundleException.class, () -> TargetedDirectorySegment.parse("bad#_2.0"));
  }

  @Test
  public void testFailsParsing_missingValue() {
    assertThrows(InvalidBundleException.class, () -> TargetedDirectorySegment.parse("bad#opengl"));
    assertThrows(
        InvalidBundleException.class, () -> TargetedDirectorySegment.parse("bad###opengl#"));
  }

  @Test
  public void testTargeting_language_ok() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#lang_en");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions()).contains(TargetingDimension.LANGUAGE);
    assertThat(segment.getTargeting()).isEqualTo(assetsDirectoryTargeting(languageTargeting("en")));
  }

  @Test
  public void testTargeting_languageThreeChars_ok() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#lang_fil");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions()).contains(TargetingDimension.LANGUAGE);
    assertThat(segment.getTargeting())
        .isEqualTo(assetsDirectoryTargeting(languageTargeting("fil")));
  }

  @Test
  public void testTargeting_upperCase_OK() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#lang_FR");
    assertThat(segment.getName()).isEqualTo("test");
    assertThat(segment.getTargetingDimensions()).contains(TargetingDimension.LANGUAGE);
    assertThat(segment.getTargeting()).isEqualTo(assetsDirectoryTargeting(languageTargeting("fr")));
  }

  @Test
  public void testTargeting_languageFourChars_throws() {
    assertThrows(
        InvalidBundleException.class, () -> TargetedDirectorySegment.parse("bad#lang_filo"));
  }

  @Test
  public void testTargeting_nokey_toPathIdempotent() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test");
    assertThat(segment.toPathSegment()).isEqualTo("test");
  }

  @Test
  public void testTargeting_tcf_toPathIdempotent() {
    TargetedDirectorySegment segment;
    segment = TargetedDirectorySegment.parse("test#tcf_astc");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_astc");
    segment = TargetedDirectorySegment.parse("test#tcf_atc");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_atc");
    segment = TargetedDirectorySegment.parse("test#tcf_dxt1");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_dxt1");
    segment = TargetedDirectorySegment.parse("test#tcf_etc1");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_etc1");
    segment = TargetedDirectorySegment.parse("test#tcf_etc2");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_etc2");
    segment = TargetedDirectorySegment.parse("test#tcf_latc");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_latc");
    segment = TargetedDirectorySegment.parse("test#tcf_paletted");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_paletted");
    segment = TargetedDirectorySegment.parse("test#tcf_pvrtc");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_pvrtc");
    segment = TargetedDirectorySegment.parse("test#tcf_s3tc");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_s3tc");
    segment = TargetedDirectorySegment.parse("test#tcf_3dc");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_3dc");
  }

  @Test
  public void testTargeting_language_toPathIdempotent() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#lang_fr");
    assertThat(segment.toPathSegment()).isEqualTo("test#lang_fr");
  }

  @Test
  public void testTargeting_deviceTier_toPathIdempotent() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#tier_2");
    assertThat(segment.toPathSegment()).isEqualTo("test#tier_2");

    segment = TargetedDirectorySegment.parse("test#tier_1");
    assertThat(segment.toPathSegment()).isEqualTo("test#tier_1");

    segment = TargetedDirectorySegment.parse("test#tier_0");
    assertThat(segment.toPathSegment()).isEqualTo("test#tier_0");
  }

  @Test
  public void testTargeting_countrySet_toPathIdempotent() {
    TargetedDirectorySegment segment = TargetedDirectorySegment.parse("test#countries_latam");
    assertThat(segment.toPathSegment()).isEqualTo("test#countries_latam");

    segment = TargetedDirectorySegment.parse("test#countries_sea");
    assertThat(segment.toPathSegment()).isEqualTo("test#countries_sea");
  }

  @Test
  public void testTargeting_nested_toPathIdempotent() {
    TargetedDirectorySegment segment =
        TargetedDirectorySegment.parse("test#countries_latam#tcf_astc");
    assertThat(segment.toPathSegment()).isEqualTo("test#countries_latam#tcf_astc");

    segment = TargetedDirectorySegment.parse("test#tcf_astc#countries_latam");
    assertThat(segment.toPathSegment()).isEqualTo("test#tcf_astc#countries_latam");
  }

  @Test
  public void testTargeting_nested_moreThanTwoDimension_throws() {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> TargetedDirectorySegment.parse("test#countries_latam#tcf_astc#tier_2"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "No directory should target more than two dimension. Found directory"
                + " 'test#countries_latam#tcf_astc#tier_2' targeting more than two dimension.");
  }

  @Test
  public void testTargeting_nested_dimensionOtherThanTcfTierCountriesUsed_throws() {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> TargetedDirectorySegment.parse("test#countries_latam#lang_en-US"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Targeting dimension 'LANGUAGE' should not be nested with other dimensions. Found"
                + " directory 'test#countries_latam#lang_en-US' which nests the dimension with"
                + " other dimensions.");
  }

  @Test
  public void testTargeting_invalidTargeting_throws() {
    InvalidBundleException exception =
        assertThrows(InvalidBundleException.class, () -> TargetedDirectorySegment.parse("#tier_2"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Cannot tokenize targeted directory '#tier_2'. Expecting either '<name>' or"
                + " '<name>#<key>_<value>' format.");
  }

  @Test
  public void testTargeting_nested_sameDimensionUsedMultipleTimes_throws() {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> TargetedDirectorySegment.parse("test#tier_2#tier_1"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "No directory should be targeted more than once on the same dimension. Found directory"
                + " 'test#tier_2#tier_1' targeted multiple times on same dimension.");
  }

  @Test
  public void testTargeting_nested_invalidKey_throws() {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> TargetedDirectorySegment.parse("test#tier_2#invalid_astc"));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Unrecognized key: 'invalid' used in targeting of directory"
                + " 'test#tier_2#invalid_astc'.");
  }

  @Test
  public void constructTargetingSegmentPath_allDimensionsPresent() {
    AssetsDirectoryTargeting targeting =
        AssetsDirectoryTargeting.newBuilder()
            .setCountrySet(countrySetTargeting("latam"))
            .setTextureCompressionFormat(
                textureCompressionTargeting(TextureCompressionFormatAlias.ASTC))
            .build();
    ImmutableList<TargetingDimension> targetingOrder =
        ImmutableList.of(
            TargetingDimension.TEXTURE_COMPRESSION_FORMAT, TargetingDimension.COUNTRY_SET);

    assertThat(constructTargetingSegmentPath(targeting, targetingOrder))
        .isEqualTo("#tcf_astc#countries_latam");
  }

  @Test
  public void constructTargetingSegmentPath_dimensionsMissing() {
    AssetsDirectoryTargeting targeting =
        AssetsDirectoryTargeting.newBuilder()
            .setCountrySet(countrySetTargeting("latam"))
            .setTextureCompressionFormat(
                textureCompressionTargeting(TextureCompressionFormatAlias.ASTC))
            .build();
    ImmutableList<TargetingDimension> targetingOrder =
        ImmutableList.of(
            TargetingDimension.TEXTURE_COMPRESSION_FORMAT,
            TargetingDimension.COUNTRY_SET,
            TargetingDimension.DEVICE_TIER);

    assertThat(constructTargetingSegmentPath(targeting, targetingOrder))
        .isEqualTo("#tcf_astc#countries_latam");
  }
}
