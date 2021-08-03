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
public class DeviceTierApkMatcherTest {

  @Test
  public void matchesTargeting_matches() {
    DeviceTierApkMatcher matcher = new DeviceTierApkMatcher(deviceTier(1));

    assertThat(matcher.matchesTargeting(deviceTierTargeting(1))).isTrue();
    assertThat(matcher.matchesTargeting(deviceTierTargeting(1, ImmutableList.of(0, 2, 3))))
        .isTrue();
  }

  @Test
  public void matchesTargeting_doesNotMatch() {
    DeviceTierApkMatcher matcher = new DeviceTierApkMatcher(deviceTier(1));

    assertThat(matcher.matchesTargeting(deviceTierTargeting(2))).isFalse();
    assertThat(matcher.matchesTargeting(deviceTierTargeting(2, ImmutableList.of(0, 1, 3))))
        .isFalse();
  }

  @Test
  public void matchesTargeting_overlappingValuesAndAlternatives_throws() {
    DeviceTierApkMatcher matcher = new DeviceTierApkMatcher(deviceTier(1));

    assertThrows(
        IllegalArgumentException.class,
        () -> matcher.matchesTargeting(deviceTierTargeting(1, ImmutableList.of(1, 3))));
  }

  @Test
  public void getTargetingValue() {
    DeviceTierApkMatcher matcher = new DeviceTierApkMatcher(deviceTier(1));

    DeviceTierTargeting targeting = deviceTierTargeting(1, ImmutableList.of(0, 2));
    assertThat(
            matcher.getTargetingValue(
                ApkTargeting.newBuilder().setDeviceTierTargeting(targeting).build()))
        .isEqualTo(targeting);
  }

  @Test
  public void isDimensionPresent() {
    assertThat(new DeviceTierApkMatcher(deviceTier(1)).isDeviceDimensionPresent()).isTrue();
    assertThat(new DeviceTierApkMatcher(abis("x86")).isDeviceDimensionPresent()).isFalse();
  }

  @Test
  public void checkDeviceCompatibleInternal_isCompatible() {
    DeviceTierApkMatcher matcher = new DeviceTierApkMatcher(deviceTier(1));

    matcher.checkDeviceCompatibleInternal(deviceTierTargeting(1));
    matcher.checkDeviceCompatibleInternal(deviceTierTargeting(2, ImmutableList.of(1, 3)));
  }

  @Test
  public void checkDeviceCompatibleInternal_isNotCompatible() {
    DeviceTierApkMatcher matcher = new DeviceTierApkMatcher(deviceTier(1));

    assertThrows(
        IllegalArgumentException.class,
        () -> matcher.checkDeviceCompatibleInternal(deviceTierTargeting(2, ImmutableList.of(3))));
  }
}
