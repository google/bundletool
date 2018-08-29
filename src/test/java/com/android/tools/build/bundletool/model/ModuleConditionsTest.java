/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeModuleTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.ModuleTargeting;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleConditionsTest {

  @Test
  public void toTargeting_emptyIfNoConditionsUsed() {
    ModuleConditions moduleConditions = ModuleConditions.builder().build();

    assertThat(moduleConditions.toTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void toTargeting_minSdkVersion() {
    ModuleConditions moduleConditions = ModuleConditions.builder().setMinSdkVersion(26).build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting).isEqualTo(moduleMinSdkVersionTargeting(26));
  }

  @Test
  public void toTargeting_deviceFeatureConditions() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .addDeviceFeatureCondition(DeviceFeatureCondition.create("com.feature1"))
            .addDeviceFeatureCondition(DeviceFeatureCondition.create("com.feature2"))
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeModuleTargeting(
                moduleFeatureTargeting("com.feature1"), moduleFeatureTargeting("com.feature2")));
  }

  @Test
  public void toTargeting_mixedConditions() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .addDeviceFeatureCondition(DeviceFeatureCondition.create("com.feature1"))
            .addDeviceFeatureCondition(DeviceFeatureCondition.create("com.feature2"))
            .setMinSdkVersion(24)
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeModuleTargeting(
                moduleFeatureTargeting("com.feature1"),
                moduleFeatureTargeting("com.feature2"),
                moduleMinSdkVersionTargeting(24)));
  }
}
