/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.build.bundletool.optimizations;

import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.COUNTRY_SET;
import static com.android.tools.build.bundletool.model.OptimizationDimension.DEVICE_TIER;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApkOptimizationsTest {

  @Test
  public void getDefaultOptimizations_0_2_0_onlySplitsByAbiDensityAndLanguage() {
    ApkOptimizations defaultOptimizations =
        ApkOptimizations.getDefaultOptimizationsForVersion(Version.of("0.2.0"));
    assertThat(defaultOptimizations)
        .isEqualTo(
            ApkOptimizations.builder()
                .setSplitDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE))
                .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                .build());
  }

  @Test
  public void
      getDefaultOptimizations_0_6_0_onlySplitsByAbiDensityLanguageAndUncompressNativeLibs() {
    ApkOptimizations defaultOptimizations =
        ApkOptimizations.getDefaultOptimizationsForVersion(Version.of("0.6.0"));
    assertThat(defaultOptimizations)
        .isEqualTo(
            ApkOptimizations.builder()
                .setSplitDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE))
                .setUncompressNativeLibraries(true)
                .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                .build());
  }

  @Test
  public void
      getDefaultOptimizations_0_10_2_onlySplitsByAbiDensityTextureLanguageAndUncompressNativeLibs() {
    ApkOptimizations defaultOptimizations =
        ApkOptimizations.getDefaultOptimizationsForVersion(Version.of("0.10.2"));
    assertThat(defaultOptimizations)
        .isEqualTo(
            ApkOptimizations.builder()
                .setSplitDimensions(
                    ImmutableSet.of(ABI, SCREEN_DENSITY, TEXTURE_COMPRESSION_FORMAT, LANGUAGE))
                .setUncompressNativeLibraries(true)
                .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                .build());
  }

  @Test
  public void
      getDefaultOptimizations_1_11_3_onlySplitsByAbiDensityTextureLanguageAndUncompressNativeLibsUncompressedDex() {
    ApkOptimizations defaultOptimizations =
        ApkOptimizations.getDefaultOptimizationsForVersion(Version.of("1.11.3"));
    assertThat(defaultOptimizations)
        .isEqualTo(
            ApkOptimizations.builder()
                .setSplitDimensions(
                    ImmutableSet.of(ABI, SCREEN_DENSITY, TEXTURE_COMPRESSION_FORMAT, LANGUAGE))
                .setUncompressNativeLibraries(true)
                .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                .setUncompressDexFiles(true)
                .setUncompressedDexTargetSdk(UncompressedDexTargetSdk.SDK_31)
                .build());
  }

  @Test
  public void
      getDefaultOptimizations_1_13_2_onlySplitsByAbiDensityTextureLanguageUncompressNativeLibsUncompressedDexDeviceTiers() {
    ApkOptimizations defaultOptimizations =
        ApkOptimizations.getDefaultOptimizationsForVersion(Version.of("1.13.2"));
    assertThat(defaultOptimizations)
        .isEqualTo(
            ApkOptimizations.builder()
                .setSplitDimensions(
                    ImmutableSet.of(
                        ABI, SCREEN_DENSITY, TEXTURE_COMPRESSION_FORMAT, LANGUAGE, DEVICE_TIER))
                .setUncompressNativeLibraries(true)
                .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                .setUncompressDexFiles(true)
                .setUncompressedDexTargetSdk(UncompressedDexTargetSdk.SDK_31)
                .build());
  }

  @Test
  public void getSplitDimensionsForAssetModules_returnsDimensionsSupportedByAssetModules() {
    ApkOptimizations optimizations =
        ApkOptimizations.builder()
            .setSplitDimensions(
                ImmutableSet.of(
                    ABI, SCREEN_DENSITY, TEXTURE_COMPRESSION_FORMAT, DEVICE_TIER, COUNTRY_SET))
            .setStandaloneDimensions(ImmutableSet.of(ABI))
            .build();

    assertThat(optimizations.getSplitDimensionsForAssetModules())
        .containsExactly(TEXTURE_COMPRESSION_FORMAT, DEVICE_TIER, COUNTRY_SET);
  }
}
