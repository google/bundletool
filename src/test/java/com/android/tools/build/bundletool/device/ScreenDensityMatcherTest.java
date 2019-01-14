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

package com.android.tools.build.bundletool.device;

import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DENSITY_ALIAS_TO_DPI_MAP;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithDensity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.screenDensityTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScreenDensityMatcherTest {

  @Test
  public void simpleMatching() {
    ScreenDensityMatcher densityMatcher =
        new ScreenDensityMatcher(lDeviceWithDensity(DensityAlias.XHDPI));

    // In the absence of alternatives, any density targeting should match our device.
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.XHDPI, ImmutableSet.of())))
        .isTrue();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.MDPI, ImmutableSet.of())))
        .isTrue();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.LDPI, ImmutableSet.of())))
        .isTrue();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.XXHDPI, ImmutableSet.of())))
        .isTrue();
  }

  @Test
  public void betterAlternative_exactDpiMatch() {
    ScreenDensityMatcher densityMatcher =
        new ScreenDensityMatcher(lDeviceWithDensity(DensityAlias.HDPI));

    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.MDPI, ImmutableSet.of(DensityAlias.HDPI))))
        .isFalse();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.XHDPI, ImmutableSet.of(DensityAlias.HDPI))))
        .isFalse();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(
                    DensityAlias.MDPI,
                    ImmutableSet.of(DensityAlias.HDPI, DensityAlias.LDPI, DensityAlias.XXHDPI))))
        .isFalse();
  }

  @Test
  public void betterAlternative_noDpiMatch() {
    ScreenDensityMatcher densityMatcher =
        new ScreenDensityMatcher(lDeviceWithDensity(DensityAlias.HDPI));

    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.LDPI, ImmutableSet.of(DensityAlias.XHDPI))))
        .isFalse();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.LDPI, ImmutableSet.of(DensityAlias.MDPI))))
        .isFalse();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(
                    DensityAlias.XXXHDPI,
                    ImmutableSet.of(DensityAlias.XXHDPI, DensityAlias.XHDPI))))
        .isFalse();
  }

  @Test
  public void worseAlternatives_exactDpiMatch() {
    ScreenDensityMatcher densityMatcher =
        new ScreenDensityMatcher(lDeviceWithDensity(DensityAlias.HDPI));

    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(
                    DensityAlias.HDPI, ImmutableSet.of(DensityAlias.LDPI, DensityAlias.XHDPI))))
        .isTrue();
  }

  @Test
  public void worseAlternatives_noDpiMatch() {
    ScreenDensityMatcher densityMatcher =
        new ScreenDensityMatcher(lDeviceWithDensity(DensityAlias.HDPI));

    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.XHDPI, ImmutableSet.of(DensityAlias.LDPI))))
        .isTrue();
    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(DensityAlias.XXHDPI, ImmutableSet.of(DensityAlias.XXXHDPI))))
        .isTrue();

    assertThat(
            densityMatcher.matchesTargeting(
                screenDensityTargeting(
                    DENSITY_ALIAS_TO_DPI_MAP.get(DensityAlias.HDPI) + 1,
                    ImmutableSet.of(
                        DensityAlias.MDPI,
                        DensityAlias.XHDPI,
                        DensityAlias.XHDPI,
                        DensityAlias.LDPI,
                        DensityAlias.XXXHDPI))))
        .isTrue();
  }
}
