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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.DeviceFeature;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/** A {@link TargetingDimensionMatcher} that provides matching on device features. */
public class DeviceFeatureMatcher
    extends TargetingDimensionMatcher<ImmutableList<DeviceFeatureTargeting>> {

  private final ImmutableSet<String> deviceFeatures;

  public DeviceFeatureMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
    deviceFeatures = ImmutableSet.copyOf(deviceSpec.getDeviceFeaturesList());
  }

  @Override
  public boolean matchesTargeting(ImmutableList<DeviceFeatureTargeting> targetingValue) {
    ImmutableSet<String> requiredFeatureSet =
        targetingValue.stream()
            .map(DeviceFeatureTargeting::getRequiredFeature)
            .map(DeviceFeature::getFeatureName)
            .filter(featureName -> !featureName.equals(CONDITIONAL_MODULES_OPEN_GL_NAME))
            .collect(toImmutableSet());
    return Sets.difference(requiredFeatureSet, deviceFeatures).isEmpty();
  }

  @Override
  protected ImmutableList<DeviceFeatureTargeting> getTargetingValue(
      ModuleTargeting moduleTargeting) {
    return ImmutableList.copyOf(moduleTargeting.getDeviceFeatureTargetingList());
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !getDeviceSpec().getDeviceFeaturesList().isEmpty();
  }

  @Override
  protected void checkDeviceCompatibleInternal(
      ImmutableList<DeviceFeatureTargeting> targetingValue) {}
}
