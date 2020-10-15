/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * A {@link TargetingDimensionMatcher} that provides matching on device tier.
 *
 * <p>Device tier is an artificial concept and it is explicitly defined in the {@link DeviceSpec}.
 */
public class DeviceTierMatcher extends TargetingDimensionMatcher<DeviceTierTargeting> {

  public DeviceTierMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  protected DeviceTierTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getDeviceTierTargeting();
  }

  @Override
  protected DeviceTierTargeting getTargetingValue(VariantTargeting variantTargeting) {
    // Device tier is not propagated to the variant targeting.
    return DeviceTierTargeting.getDefaultInstance();
  }

  @Override
  public boolean matchesTargeting(DeviceTierTargeting targeting) {
    // If there is no targeting, by definition the targeting is matched.
    if (targeting.equals(DeviceTierTargeting.getDefaultInstance())) {
      return true;
    }

    ImmutableSet<String> values = ImmutableSet.copyOf(targeting.getValueList());
    ImmutableSet<String> alternatives = ImmutableSet.copyOf(targeting.getAlternativesList());

    SetView<String> intersection = Sets.intersection(values, alternatives);
    checkArgument(
        intersection.isEmpty(),
        "Expected targeting values and alternatives to be mutually exclusive, but both contain: %s",
        intersection);

    if (getDeviceSpec().getDeviceTier().isEmpty()) {
      throw InvalidDeviceSpecException.builder()
          .withUserMessage(
              "The bundle uses device tier targeting, but no device tier was specified.")
          .build();
    }
    if (values.contains(getDeviceSpec().getDeviceTier())) {
      return true;
    }
    return false;
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !getDeviceSpec().getDeviceTier().isEmpty();
  }

  @Override
  protected void checkDeviceCompatibleInternal(DeviceTierTargeting targeting) {
    if (targeting.equals(DeviceTierTargeting.getDefaultInstance())) {
      return;
    }
    ImmutableSet<String> valuesAndAlternatives =
        ImmutableSet.<String>builder()
            .addAll(targeting.getValueList())
            .addAll(targeting.getAlternativesList())
            .build();
    checkArgument(
        valuesAndAlternatives.contains(getDeviceSpec().getDeviceTier()),
        "The specified device tier '%s' does not match any of the available values: %s.",
        getDeviceSpec().getDeviceTier(),
        String.join(", ", valuesAndAlternatives));
  }
}
