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

import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceFeatureTargetingList;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceFeatureMatcherTest {

  @Test
  public void deviceFeaturePresent_match() {
    DeviceSpec deviceSpec =
        deviceFeatures(
            "com.software.soft.feature1",
            "com.hardware.cool.feature",
            "com.hardware.even.cooler.feature");
    DeviceFeatureMatcher matcher = new DeviceFeatureMatcher(deviceSpec);

    assertThat(matcher.matchesTargeting(deviceFeatureTargetingList("com.hardware.cool.feature")))
        .isTrue();
    assertThat(
            matcher.matchesTargeting(
                deviceFeatureTargetingList(
                    "com.software.soft.feature1", "com.hardware.even.cooler.feature")))
        .isTrue();
    assertThat(matcher.matchesTargeting(ImmutableList.of())).isTrue();
  }

  @Test
  public void skipOpenGlFeature() {
    DeviceSpec deviceSpec = deviceFeatures("com.hardware.cool.feature");

    DeviceFeatureMatcher matcher = new DeviceFeatureMatcher(deviceSpec);
    ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetings =
        ImmutableList.of(
            deviceFeatureTargeting(OpenGlFeatureMatcher.CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30000),
            deviceFeatureTargeting("com.hardware.cool.feature"));

    assertThat(matcher.matchesTargeting(deviceFeatureTargetings)).isTrue();
  }

  @Test
  public void deviceFeatureAbsent_noMatch() {
    DeviceSpec deviceSpec = deviceFeatures("com.hardware.other.feature");
    DeviceFeatureMatcher matcher = new DeviceFeatureMatcher(deviceSpec);

    assertThat(matcher.matchesTargeting(deviceFeatureTargetingList("com.hardware.cool.feature")))
        .isFalse();
    assertThat(
            matcher.matchesTargeting(
                deviceFeatureTargetingList(
                    "com.hardware.other.feature", "com.software.soft.feature1")))
        .isFalse();
  }
}
