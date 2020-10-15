/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceTier;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceTierMatcherTest {

  @Test
  public void matchesTargeting_matches() {
    DeviceTierMatcher matcher = new DeviceTierMatcher(deviceTier("low"));

    assertThat(matcher.matchesTargeting(deviceTierTargeting("low"))).isTrue();
    assertThat(
            matcher.matchesTargeting(
                deviceTierTargeting("low", ImmutableList.of("medium", "high"))))
        .isTrue();
  }

  @Test
  public void matchesTargeting_doesNotMatch() {
    DeviceTierMatcher matcher = new DeviceTierMatcher(deviceTier("low"));

    assertThat(matcher.matchesTargeting(deviceTierTargeting("medium"))).isFalse();
    assertThat(
            matcher.matchesTargeting(
                deviceTierTargeting("medium", ImmutableList.of("low", "high"))))
        .isFalse();
  }

  @Test
  public void matchesTargeting_overlappingValuesAndAlternatives_throws() {
    DeviceTierMatcher matcher = new DeviceTierMatcher(deviceTier("low"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            matcher.matchesTargeting(deviceTierTargeting("low", ImmutableList.of("low", "high"))));
  }

  @Test
  public void getTargetingValue() {
    DeviceTierMatcher matcher = new DeviceTierMatcher(deviceTier("low"));

    DeviceTierTargeting targeting = deviceTierTargeting("low", ImmutableList.of("medium"));
    assertThat(
            matcher.getTargetingValue(
                ApkTargeting.newBuilder().setDeviceTierTargeting(targeting).build()))
        .isEqualTo(targeting);
  }

  @Test
  public void isDimensionPresent() {
    assertThat(new DeviceTierMatcher(deviceTier("low")).isDeviceDimensionPresent()).isTrue();
    assertThat(new DeviceTierMatcher(abis("x86")).isDeviceDimensionPresent()).isFalse();
  }

  @Test
  public void checkDeviceCompatibleInternal_isCompatible() {
    DeviceTierMatcher matcher = new DeviceTierMatcher(deviceTier("low"));

    matcher.checkDeviceCompatibleInternal(deviceTierTargeting("low"));
    matcher.checkDeviceCompatibleInternal(
        deviceTierTargeting("medium", ImmutableList.of("low", "high")));
  }

  @Test
  public void checkDeviceCompatibleInternal_isNotCompatible() {
    DeviceTierMatcher matcher = new DeviceTierMatcher(deviceTier("low"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            matcher.checkDeviceCompatibleInternal(
                deviceTierTargeting("medium", ImmutableList.of("high"))));
  }
}
