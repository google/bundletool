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

package com.android.tools.build.bundletool.device;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import java.util.stream.Stream;

/** A {@link TargetingDimensionMatcher} that provides matching on SDK version. */
public final class SdkVersionMatcher extends TargetingDimensionMatcher<SdkVersionTargeting> {

  private final int deviceSdkVersion;

  public SdkVersionMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
    deviceSdkVersion = deviceSpec.getSdkVersion();
  }

  @Override
  public boolean matchesTargeting(SdkVersionTargeting targeting) {
    checkArgument(
        targeting.getValueCount() <= 1,
        "Found more than one SDK version value in the variant targeting.");

    SdkVersion sdkValue =
        targeting.getValueCount() == 0 ? SdkVersion.getDefaultInstance() : targeting.getValue(0);

    if (!matchesDeviceSdk(sdkValue, deviceSdkVersion)) {
      return false;
    }

    // Check if there is a better match among alternatives.
    for (SdkVersion alternativeSdkValue : targeting.getAlternativesList()) {
      if (isBetterSdkMatch(
          /* candidate= */ alternativeSdkValue, /* contestedValue= */ sdkValue, deviceSdkVersion)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns if the candidate sdk targeting better matches the device sdk version.
   *
   * @param candidate candidate sdk targeting that we analyze.
   * @param contestedValue the targeting value we are trying to contest.
   * @param deviceSdkVersion the device sdk version
   * @return whether candidate matches better the device sdk version.
   */
  private boolean isBetterSdkMatch(
      SdkVersion candidate, SdkVersion contestedValue, int deviceSdkVersion) {
    if (!matchesDeviceSdk(candidate, deviceSdkVersion)) {
      return false;
    }
    return candidate.hasMin() && candidate.getMin().getValue() > contestedValue.getMin().getValue();
  }

  private boolean matchesDeviceSdk(SdkVersion value, int deviceSdkVersion) {
    return !value.hasMin() || value.getMin().getValue() <= deviceSdkVersion;
  }

  @Override
  protected SdkVersionTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getSdkVersionTargeting();
  }

  @Override
  protected SdkVersionTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return variantTargeting.getSdkVersionTargeting();
  }

  @Override
  protected SdkVersionTargeting getTargetingValue(ModuleTargeting moduleTargeting) {
    return moduleTargeting.getSdkVersionTargeting();
  }

  @Override
  protected void checkDeviceCompatibleInternal(SdkVersionTargeting targeting) {
    SdkVersion sdkValue =
        targeting.getValueCount() == 0 ? SdkVersion.getDefaultInstance() : targeting.getValue(0);

    boolean anyMatch =
        Stream.concat(Stream.of(sdkValue), targeting.getAlternativesList().stream())
            .anyMatch(sdkVal -> matchesDeviceSdk(sdkVal, deviceSdkVersion));

    if (!anyMatch) {
      throw CommandExecutionException.builder()
          .withMessage(
              "The app doesn't support SDK version of the device: (%d).",
              getDeviceSpec().getSdkVersion())
          .build();
    }
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return deviceSdkVersion != 0;
  }
}
