/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.countrySet;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CountrySetApkMatcherTest {

  @Test
  public void matchesTargeting_matches() {
    CountrySetApkMatcher latamMatcher = new CountrySetApkMatcher(countrySet("latam"));
    CountrySetTargeting latamTargetingWithoutAlternatives = countrySetTargeting("latam");
    CountrySetTargeting latamTargetingWithAlternatives =
        countrySetTargeting("latam", ImmutableList.of("sea"));

    assertThat(latamMatcher.matchesTargeting(latamTargetingWithoutAlternatives)).isTrue();
    assertThat(latamMatcher.matchesTargeting(latamTargetingWithAlternatives)).isTrue();
  }

  @Test
  public void matchesTargeting_doesNotMatch() {
    CountrySetApkMatcher latamMatcher = new CountrySetApkMatcher(countrySet("latam"));
    CountrySetTargeting seaTargeting = countrySetTargeting("sea", ImmutableList.of("latam"));

    assertThat(latamMatcher.matchesTargeting(seaTargeting)).isFalse();
  }

  @Test
  public void matchesTargeting_overlappingValuesAndAlternatives_throws() {
    CountrySetApkMatcher matcher = new CountrySetApkMatcher(countrySet("latam"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                matcher.matchesTargeting(
                    countrySetTargeting("latam", ImmutableList.of("sea", "latam"))));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected targeting values and alternatives to be mutually exclusive, but both contain:"
                + " [latam]");
  }

  @Test
  public void getTargetingValue() {
    CountrySetApkMatcher latamMatcher = new CountrySetApkMatcher(countrySet("latam"));
    CountrySetTargeting latamTargeting = countrySetTargeting("latam", ImmutableList.of("sea"));
    ApkTargeting latamApkTargeting =
        ApkTargeting.newBuilder().setCountrySetTargeting(latamTargeting).build();

    assertThat(latamMatcher.getTargetingValue(latamApkTargeting)).isEqualTo(latamTargeting);
  }

  @Test
  public void isDimensionPresent() {
    DeviceSpec deviceSpecWithCountrySet = countrySet("latam");
    DeviceSpec deviceSpecWithoutCountrySet = abis("x86");

    assertThat(new CountrySetApkMatcher(deviceSpecWithCountrySet).isDeviceDimensionPresent())
        .isTrue();
    assertThat(new CountrySetApkMatcher(deviceSpecWithoutCountrySet).isDeviceDimensionPresent())
        .isFalse();
  }

  @Test
  public void checkDeviceCompatibleInternal_isCompatible() {
    CountrySetApkMatcher latamMatcher = new CountrySetApkMatcher(countrySet("latam"));
    CountrySetTargeting latamTargetingWithoutAlternatives = countrySetTargeting("latam");
    CountrySetTargeting latamTargetingWithAlternatives =
        countrySetTargeting("sea", ImmutableList.of("latam", "europe"));

    latamMatcher.checkDeviceCompatibleInternal(latamTargetingWithoutAlternatives);
    latamMatcher.checkDeviceCompatibleInternal(latamTargetingWithAlternatives);
  }

  @Test
  public void checkDeviceCompatibleInternal_isNotCompatible() {
    CountrySetApkMatcher latamMatcher = new CountrySetApkMatcher(countrySet("latam"));
    CountrySetTargeting targetingWithoutLatamInValuesAndAlternatives =
        countrySetTargeting("sea", ImmutableList.of("europe"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                latamMatcher.checkDeviceCompatibleInternal(
                    targetingWithoutLatamInValuesAndAlternatives));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The specified country set 'latam' does not match any of the available values: sea,"
                + " europe.");
  }
}
