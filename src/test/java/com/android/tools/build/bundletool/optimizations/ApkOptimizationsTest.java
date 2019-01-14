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
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.google.common.truth.Truth.assertThat;

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
                .build());
  }
}
