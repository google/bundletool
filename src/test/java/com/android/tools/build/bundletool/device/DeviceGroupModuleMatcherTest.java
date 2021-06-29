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
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleDeviceGroupsTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ModuleTargeting;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceGroupModuleMatcherTest {

  @Test
  public void matchesTargeting_specifiedDeviceTier() {
    DeviceGroupModuleMatcher matcher =
        new DeviceGroupModuleMatcher(deviceGroups("highRam", "mediumRam"));

    assertThat(
            matcher
                .getModuleTargetingPredicate()
                .test(moduleDeviceGroupsTargeting("mediumRam", "googlePixel")))
        .isTrue();
    assertThat(matcher.getModuleTargetingPredicate().test(moduleDeviceGroupsTargeting("lowRam")))
        .isFalse();
    assertThat(matcher.getModuleTargetingPredicate().test(ModuleTargeting.getDefaultInstance()))
        .isTrue();
  }

  @Test
  public void matchesTargeting_noTierInDeviceSpec() {
    DeviceGroupModuleMatcher matcher =
        new DeviceGroupModuleMatcher(DeviceSpec.getDefaultInstance());

    assertThat(matcher.getModuleTargetingPredicate().test(moduleDeviceGroupsTargeting("highRam")))
        .isFalse();
    assertThat(matcher.getModuleTargetingPredicate().test(ModuleTargeting.getDefaultInstance()))
        .isTrue();
  }
}
