/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceGroups;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceGroupTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceGroupApkMatcherTest {

  @Test
  public void matchesTargeting_specifiedDeviceGroup() {
    DeviceGroupApkMatcher matcher = new DeviceGroupApkMatcher(deviceGroups("highRam", "mediumRam"));

    assertThat(
            matcher.matchesTargeting(
                deviceGroupTargeting("mediumRam", ImmutableList.of("googlePixel"))))
        .isTrue();
    assertThat(matcher.matchesTargeting(deviceGroupTargeting("lowRam"))).isFalse();
    assertThat(matcher.getApkTargetingPredicate().test(ApkTargeting.getDefaultInstance())).isTrue();
  }

  @Test
  public void matchesTargeting_noDeviceGroupsInDeviceSpec() {
    DeviceGroupApkMatcher matcher = new DeviceGroupApkMatcher(DeviceSpec.getDefaultInstance());

    assertThat(matcher.matchesTargeting(deviceGroupTargeting("highRam"))).isFalse();
    assertThat(matcher.getApkTargetingPredicate().test(ApkTargeting.getDefaultInstance())).isTrue();
  }
}
