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
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.DeviceGroupTargeting;
import java.util.Collections;

/**
 * A {@link TargetingDimensionMatcher} that provides apk matching on a set of device groups.
 *
 * <p>Device groups are an artificial concept and they are explicitly defined in the {@link
 * DeviceSpec}.
 */
public class DeviceGroupApkMatcher extends TargetingDimensionMatcher<DeviceGroupTargeting> {

  public DeviceGroupApkMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  public boolean matchesTargeting(DeviceGroupTargeting targetingValue) {
    if (targetingValue.equals(DeviceGroupTargeting.getDefaultInstance())) {
      return true;
    }
    return !Collections.disjoint(
        targetingValue.getValueList(), getDeviceSpec().getDeviceGroupsList());
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    // Always true, because groups empty list is considered present.
    return true;
  }

  @Override
  protected void checkDeviceCompatibleInternal(DeviceGroupTargeting targetingValue) {}

  @Override
  protected DeviceGroupTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getDeviceGroupTargeting();
  }
}
