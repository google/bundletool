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

import static com.android.tools.build.bundletool.device.OpenGlFeatureMatcher.CONDITIONAL_MODULES_OPEN_GL_NAME;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceGroups;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeModuleTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleDeviceGroupsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ModuleTargeting;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleMatcherTest {

  @Test
  public void matchesModuleTargeting_noConditions() {
    DeviceSpec deviceSpec = mergeSpecs(deviceWithSdk(22), deviceFeatures("feature1"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpec);

    assertThat(moduleMatcher.matchesModuleTargeting(ModuleTargeting.getDefaultInstance())).isTrue();
  }

  @Test
  public void matchesModuleTargeting_positive() {
    ModuleTargeting targeting =
        mergeModuleTargeting(
            moduleMinSdkVersionTargeting(21),
            moduleFeatureTargeting("feature1"),
            moduleFeatureTargeting(CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30001),
            moduleDeviceGroupsTargeting("highRam"));
    DeviceSpec deviceSpec =
        mergeSpecs(
            deviceWithSdk(22),
            deviceFeatures("feature1", "feature2", "reqGlEsVersion=0x30002"),
            deviceGroups("highRam"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpec);

    assertThat(moduleMatcher.matchesModuleTargeting(targeting)).isTrue();
  }

  @Test
  public void matchesModuleTargeting_negative_sdkTooLow() {
    ModuleTargeting targeting =
        mergeModuleTargeting(moduleMinSdkVersionTargeting(21), moduleFeatureTargeting("feature1"));
    DeviceSpec deviceSpecLowSdk =
        mergeSpecs(deviceWithSdk(19), deviceFeatures("feature1", "feature2"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpecLowSdk);

    assertThat(moduleMatcher.matchesModuleTargeting(targeting)).isFalse();
  }

  @Test
  public void matchesModuleTargeting_negative_featureNotPresent() {
    ModuleTargeting targeting =
        mergeModuleTargeting(moduleMinSdkVersionTargeting(21), moduleFeatureTargeting("feature1"));
    DeviceSpec deviceSpecNoFeature = mergeSpecs(deviceWithSdk(21), deviceFeatures("feature2"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpecNoFeature);

    assertThat(moduleMatcher.matchesModuleTargeting(targeting)).isFalse();
  }

  @Test
  public void matchesModuleTargeting_negative_openGlVersionTooLow() {
    ModuleTargeting targeting =
        mergeModuleTargeting(
            moduleMinSdkVersionTargeting(21),
            moduleFeatureTargeting(CONDITIONAL_MODULES_OPEN_GL_NAME, 0x30001));
    DeviceSpec deviceSpec = mergeSpecs(deviceWithSdk(22), deviceFeatures("reqGlEsVersion=0x30000"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpec);

    assertThat(moduleMatcher.matchesModuleTargeting(targeting)).isFalse();
  }

  @Test
  public void matchesModuleTargeting_negative_wrongDeviceTier() {
    ModuleTargeting targeting =
        mergeModuleTargeting(
            moduleMinSdkVersionTargeting(21),
            moduleFeatureTargeting("feature1"),
            moduleDeviceGroupsTargeting("highRam"));
    DeviceSpec deviceSpec = mergeSpecs(deviceWithSdk(22), deviceGroups("lowRam"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpec);

    assertThat(moduleMatcher.matchesModuleTargeting(targeting)).isFalse();
  }

  @Test
  public void matchesModuleTargeting_specWithoutSdk_positive() {
    ModuleTargeting targeting = ModuleTargeting.getDefaultInstance();
    DeviceSpec deviceSpec = mergeSpecs(deviceFeatures("feature1"));
    ModuleMatcher moduleMatcher = new ModuleMatcher(deviceSpec);

    assertThat(moduleMatcher.matchesModuleTargeting(targeting)).isTrue();
  }
}
