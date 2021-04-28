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

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.DeviceTierModuleTargeting;
import com.android.bundle.Targeting.ModuleTargeting;

/**
 * A {@link TargetingDimensionMatcher} that provides module matching on device tier.
 *
 * <p>Device tier is an artificial concept and it is explicitly defined in the {@link DeviceSpec}.
 */
public class DeviceTierModuleMatcher extends TargetingDimensionMatcher<DeviceTierModuleTargeting> {

  public DeviceTierModuleMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  public boolean matchesTargeting(DeviceTierModuleTargeting targetingValue) {
    if (targetingValue.equals(DeviceTierModuleTargeting.getDefaultInstance())) {
      return true;
    }
    return targetingValue.getValueList().contains(getDeviceSpec().getDeviceTier());
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !getDeviceSpec().getDeviceTier().isEmpty();
  }

  @Override
  protected void checkDeviceCompatibleInternal(DeviceTierModuleTargeting targetingValue) {}

  @Override
  protected DeviceTierModuleTargeting getTargetingValue(ModuleTargeting moduleTargeting) {
    return moduleTargeting.getDeviceTierTargeting();
  }
}
