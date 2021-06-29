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
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleDeviceGroupsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleExcludeCountriesTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleIncludeCountriesTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMaxSdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinMaxSdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.ModuleTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleConditionsTest {

  @Test
  public void multipleDeviceFeatureConditions_sameFeatureName_throws() {
    ModuleConditions.Builder builder = ModuleConditions.builder();

    builder.addDeviceFeatureCondition(
        DeviceFeatureCondition.create("com.android.feature", /* version= */ Optional.of(1)));
    builder.addDeviceFeatureCondition(
        DeviceFeatureCondition.create("com.android.feature", /* version= */ Optional.of(2)));

    InvalidBundleException exception = assertThrows(InvalidBundleException.class, builder::build);

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The device feature condition on 'com.android.feature' "
                + "is present more than once.");
  }

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
  public void toTargeting_maxSdkVersion() {
    ModuleConditions moduleConditions = ModuleConditions.builder().setMaxSdkVersion(26).build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting).isEqualTo(moduleMaxSdkVersionTargeting(26));
  }

  @Test
  public void toTargeting_minMaxSdkVersions() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder().setMinSdkVersion(26).setMaxSdkVersion(28).build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting).isEqualTo(moduleMinMaxSdkVersionTargeting(26, 28));
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
  public void toTargeting_deviceFeatureVersionedConditions() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .addDeviceFeatureCondition(
                DeviceFeatureCondition.create("com.feature1", Optional.of(12)))
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(mergeModuleTargeting(moduleFeatureTargeting("com.feature1", 12)));
  }

  @Test
  public void toTargeting_deviceGroupsConditions() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .setDeviceGroupsCondition(
                DeviceGroupsCondition.create(ImmutableSet.of("group1", "group2")))
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(moduleDeviceGroupsTargeting("group1", "group2"));
  }

  @Test
  public void toTargeting_userCountriesCondition() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .setUserCountriesCondition(
                UserCountriesCondition.create(ImmutableList.of("PL", "US"), /* exclude= */ false))
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();

    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(mergeModuleTargeting(moduleIncludeCountriesTargeting("PL", "US")));
  }

  @Test
  public void toTargeting_exludeUserCountriesCondition() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .setUserCountriesCondition(
                UserCountriesCondition.create(ImmutableList.of("PL", "US"), /* exclude= */ true))
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();

    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(mergeModuleTargeting(moduleExcludeCountriesTargeting("PL", "US")));
  }

  @Test
  public void toTargeting_mixedConditions() {
    ModuleConditions moduleConditions =
        ModuleConditions.builder()
            .addDeviceFeatureCondition(DeviceFeatureCondition.create("com.feature1"))
            .addDeviceFeatureCondition(DeviceFeatureCondition.create("com.feature2"))
            .setMinSdkVersion(24)
            .setUserCountriesCondition(
                UserCountriesCondition.create(ImmutableList.of("FR"), /* exclude= */ false))
            .setDeviceGroupsCondition(DeviceGroupsCondition.create(ImmutableSet.of("group1")))
            .build();

    ModuleTargeting moduleTargeting = moduleConditions.toTargeting();
    assertThat(moduleTargeting)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            mergeModuleTargeting(
                moduleFeatureTargeting("com.feature1"),
                moduleFeatureTargeting("com.feature2"),
                moduleMinSdkVersionTargeting(24),
                moduleIncludeCountriesTargeting("FR"),
                moduleDeviceGroupsTargeting("group1")));
  }
}
