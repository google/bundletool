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

import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkRuntimeSupported;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Devices.SdkRuntime;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class SdkRuntimeMatcherTest {

  // The first element is whether the device supports the SDK runtime.
  // The second element is whether the SDK runtime is targeted.
  @DataPoints("matchingCombinations")
  public static final ImmutableList<ImmutableList<Boolean>> MATCHING_DEVICE_TARGETING_COMBINATIONS =
      ImmutableList.of(
          ImmutableList.of(true, true),
          ImmutableList.of(true, false),
          ImmutableList.of(false, false));

  @Test
  @Theory
  public void matchingDeviceAndTargetCombinations_allMatch(
      @FromDataPoints("matchingCombinations") ImmutableList<Boolean> deviceTargetingPair) {
    boolean sdkRuntimeSupported = deviceTargetingPair.get(0);
    boolean sdkRuntimeTargeted = deviceTargetingPair.get(1);
    SdkRuntimeMatcher matcher = new SdkRuntimeMatcher(sdkRuntimeSupported(sdkRuntimeSupported));
    SdkRuntimeTargeting targeting =
        SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(sdkRuntimeTargeted).build();

    boolean matched = matcher.matchesTargeting(targeting);

    assertThat(matched).isTrue();
  }

  @Test
  public void sdkRuntimeTargeted_deviceDoesNotSupportSdkRuntime_noMatch() {
    SdkRuntimeMatcher sdkRuntimeMatcher = new SdkRuntimeMatcher(DeviceSpec.getDefaultInstance());

    assertThat(
            sdkRuntimeMatcher.matchesTargeting(
                SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true).build()))
        .isFalse();
  }

  @Test
  public void sdkRuntimeSpecified_deviceDimensionPresentReturnsTrue() {
    SdkRuntimeMatcher sdkRuntimeMatcher =
        new SdkRuntimeMatcher(
            DeviceSpec.newBuilder()
                .setSdkRuntime(SdkRuntime.newBuilder().setSupported(false))
                .build());

    assertThat(sdkRuntimeMatcher.isDeviceDimensionPresent()).isTrue();
  }

  @Test
  public void sdkRuntimeUnspecified_deviceDimensionPresentReturnsFalse() {
    SdkRuntimeMatcher sdkRuntimeMatcher = new SdkRuntimeMatcher(DeviceSpec.getDefaultInstance());

    assertThat(sdkRuntimeMatcher.isDeviceDimensionPresent()).isFalse();
  }
}
