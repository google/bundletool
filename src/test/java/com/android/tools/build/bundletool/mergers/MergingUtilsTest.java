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

package com.android.tools.build.bundletool.mergers;

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.S3TC;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAlternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkTextureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.screenDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class MergingUtilsTest {

  @Test
  public void mergeShardTargetings_nonAbiNonDensityNonLanguageTargeting_throws() {
    ApkTargeting targeting = apkMinSdkTargeting(21);

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> MergingUtils.mergeShardTargetings(targeting, apkLanguageTargeting("en")));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expecting only ABI, screen density, language, texture compression format, device tier"
                + " and country set targeting");
  }

  @Test
  public void mergeShardTargetings_sdkTargetingSecondTargeting_throws() {
    ApkTargeting targeting = apkMinSdkTargeting(1);

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> MergingUtils.mergeShardTargetings(apkAbiTargeting(AbiAlias.X86), targeting));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expecting only ABI, screen density, language, texture compression format, device tier"
                + " and country set targeting");
  }

  @Test
  public void mergeShardTargetings_defaultInstances_ok() {
    ApkTargeting targeting = ApkTargeting.getDefaultInstance();

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting, targeting);

    assertThat(merged).isEqualTo(targeting);
    assertThat(merged.hasAbiTargeting()).isFalse();
    assertThat(merged.hasScreenDensityTargeting()).isFalse();
  }

  @Test
  public void mergeShardTargetings_equalAbis_ok() {
    ApkTargeting targeting = apkAbiTargeting(AbiAlias.X86);

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting, targeting);

    assertThat(merged).isEqualTo(targeting);
    assertThat(merged.hasScreenDensityTargeting()).isFalse();
  }

  @Test
  public void mergeShardTargetings_equalDensities_ok() {
    ApkTargeting targeting = apkDensityTargeting(DensityAlias.HDPI);

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting, targeting);

    assertThat(merged).isEqualTo(targeting);
    assertThat(merged.hasAbiTargeting()).isFalse();
  }

  @Test
  public void mergeShardTargetings_equalLanguages_ok() {
    ApkTargeting targeting = apkLanguageTargeting("en");

    assertThat(MergingUtils.mergeShardTargetings(targeting, targeting))
        .isEqualTo(apkLanguageTargeting("en"));
  }

  @Test
  public void mergeShardTargetings_equalTextureCompressionFormat_ok() {
    ApkTargeting targeting =
        apkTextureTargeting(textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8)));

    assertThat(MergingUtils.mergeShardTargetings(targeting, targeting))
        .isEqualTo(
            apkTextureTargeting(textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8))));
  }

  @Test
  public void mergeShardTargetings_equalDeviceTier_ok() {
    ApkTargeting targeting = apkDeviceTierTargeting(deviceTierTargeting(0, ImmutableList.of(1)));

    assertThat(MergingUtils.mergeShardTargetings(targeting, targeting)).isEqualTo(targeting);
  }

  @Test
  public void mergeShardTargetings_differentAbis_ok() {
    ApkTargeting targeting1 =
        apkAbiTargeting(AbiAlias.X86, ImmutableSet.of(AbiAlias.X86_64, AbiAlias.MIPS));
    ApkTargeting targeting2 =
        apkAbiTargeting(AbiAlias.X86_64, ImmutableSet.of(AbiAlias.X86, AbiAlias.MIPS));

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting1, targeting2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            apkAbiTargeting(
                ImmutableSet.of(AbiAlias.X86, AbiAlias.X86_64), ImmutableSet.of(AbiAlias.MIPS)));
  }

  @Test
  public void mergeShardTargetings_differentDensities_ok() {
    ApkTargeting targeting1 =
        apkDensityTargeting(
            DensityAlias.MDPI, ImmutableSet.of(DensityAlias.HDPI, DensityAlias.LDPI));
    ApkTargeting targeting2 =
        apkDensityTargeting(
            DensityAlias.HDPI, ImmutableSet.of(DensityAlias.MDPI, DensityAlias.LDPI));

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting1, targeting2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            apkDensityTargeting(
                ImmutableSet.of(DensityAlias.MDPI, DensityAlias.HDPI),
                ImmutableSet.of(DensityAlias.LDPI)));
  }

  @Test
  public void mergeShardTargetings_differentLanguages_ok() {
    ApkTargeting targeting1 = apkAlternativeLanguageTargeting("en", "jp");
    ApkTargeting targeting2 = apkLanguageTargeting("fr");

    assertThat(MergingUtils.mergeShardTargetings(targeting1, targeting2))
        .isEqualTo(apkLanguageTargeting(ImmutableSet.of("fr"), ImmutableSet.of("en", "jp")));
  }

  @Test
  public void mergeShardTargetings_differentTextureCompressionFormats_ok() {
    ApkTargeting targeting1 =
        apkTextureTargeting(textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8)));
    ApkTargeting targeting2 =
        apkTextureTargeting(textureCompressionTargeting(S3TC, ImmutableSet.of(ATC)));

    assertThat(MergingUtils.mergeShardTargetings(targeting1, targeting2))
        .isEqualTo(
            apkTextureTargeting(
                textureCompressionTargeting(S3TC, ImmutableSet.of(ETC1_RGB8, ATC))));
  }

  @Test
  public void mergeShardTargetings_differentDeviceTiers_ok() {
    ApkTargeting targeting1 = apkDeviceTierTargeting(deviceTierTargeting(0, ImmutableList.of(1)));
    ApkTargeting targeting2 = apkDeviceTierTargeting(deviceTierTargeting(0, ImmutableList.of(2)));

    assertThat(MergingUtils.mergeShardTargetings(targeting1, targeting2))
        .isEqualTo(apkDeviceTierTargeting(deviceTierTargeting(0, ImmutableList.of(1, 2))));
  }

  @Test
  public void mergeShardTargetings_firstAbiSecondDensity_ok() {
    ApkTargeting targeting1 = apkAbiTargeting(AbiAlias.X86);
    ApkTargeting targeting2 = apkDensityTargeting(DensityAlias.HDPI);

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting1, targeting2);

    assertThat(merged)
        .isEqualTo(
            ApkTargeting.newBuilder()
                .setAbiTargeting(abiTargeting(AbiAlias.X86))
                .setScreenDensityTargeting(screenDensityTargeting(DensityAlias.HDPI))
                .build());
  }

  @Test
  public void mergeShardTargetings_firstAbiSecondLanguage_ok() {
    ApkTargeting targeting1 = apkAbiTargeting(AbiAlias.X86);
    ApkTargeting targeting2 = apkLanguageTargeting("fr");

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting1, targeting2);

    assertThat(merged)
        .isEqualTo(
            ApkTargeting.newBuilder()
                .setAbiTargeting(abiTargeting(AbiAlias.X86))
                .setLanguageTargeting(languageTargeting("fr"))
                .build());
  }

  @Test
  public void mergeShardTargetings_oneSubsetOfTheOther_ok() {
    ApkTargeting targeting1 = apkAbiTargeting(AbiAlias.X86);
    ApkTargeting targeting2 = mergeApkTargeting(targeting1, apkDensityTargeting(DensityAlias.HDPI));

    ApkTargeting merged = MergingUtils.mergeShardTargetings(targeting1, targeting2);

    assertThat(merged).isEqualTo(targeting2);
  }
}
