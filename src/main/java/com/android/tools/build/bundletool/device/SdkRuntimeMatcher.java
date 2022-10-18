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

import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.VariantTargeting;

/** A {@link TargetingDimensionMatcher} that provides matching on SDK runtime. */
public final class SdkRuntimeMatcher extends TargetingDimensionMatcher<SdkRuntimeTargeting> {

  public SdkRuntimeMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  public boolean matchesTargeting(SdkRuntimeTargeting targeting) {
    // A device will only fail to match an SdkRuntimeTargeting if it does not support the SDK
    // runtime and the SDK runtime is targeted.
    return !targeting.getRequiresSdkRuntime() || getDeviceSpec().getSdkRuntime().getSupported();
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return getDeviceSpec().hasSdkRuntime();
  }

  @Override
  protected void checkDeviceCompatibleInternal(SdkRuntimeTargeting targetingValue) {}

  @Override
  protected SdkRuntimeTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return variantTargeting.getSdkRuntimeTargeting();
  }

  boolean deviceSupportsSdkRuntime() {
    if (getDeviceSpec().hasSdkRuntime()) {
      return getDeviceSpec().getSdkRuntime().getSupported();
    }
    // SDK version of 0 means it has not been specified, which is often the case for unreleased
    // versions.
    return getDeviceSpec().getSdkVersion() == 0
        || getDeviceSpec().getSdkVersion() >= SDK_SANDBOX_MIN_VERSION;
  }
}
