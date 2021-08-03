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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Streams;
import com.google.protobuf.Int32Value;

/**
 * A {@link TargetingDimensionMatcher} that provides APK matching on device tier.
 *
 * <p>Device tier is an artificial concept and it is explicitly defined in the {@link DeviceSpec}.
 */
public class DeviceTierApkMatcher extends TargetingDimensionMatcher<DeviceTierTargeting> {

  public DeviceTierApkMatcher(DeviceSpec deviceSpec) {
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

    ImmutableSet<Integer> values =
        targeting.getValueList().stream().map(Int32Value::getValue).collect(toImmutableSet());
    ImmutableSet<Integer> alternatives =
        targeting.getAlternativesList().stream()
            .map(Int32Value::getValue)
            .collect(toImmutableSet());

    SetView<Integer> intersection = Sets.intersection(values, alternatives);
    checkArgument(
        intersection.isEmpty(),
        "Expected targeting values and alternatives to be mutually exclusive, but both contain: %s",
        intersection);

    if (!getDeviceSpec().hasDeviceTier()) {
      throw InvalidDeviceSpecException.builder()
          .withUserMessage(
              "The bundle uses device tier targeting, but no device tier was specified.")
          .build();
    }
    if (values.contains(getDeviceSpec().getDeviceTier().getValue())) {
      return true;
    }
    return false;
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return getDeviceSpec().hasDeviceTier();
  }

  @Override
  protected void checkDeviceCompatibleInternal(DeviceTierTargeting targeting) {
    if (targeting.equals(DeviceTierTargeting.getDefaultInstance())) {
      return;
    }
    ImmutableSet<Integer> valuesAndAlternatives =
        Streams.concat(
                targeting.getValueList().stream().map(Int32Value::getValue),
                targeting.getAlternativesList().stream().map(Int32Value::getValue))
            .collect(toImmutableSet());
    checkArgument(
        valuesAndAlternatives.contains(getDeviceSpec().getDeviceTier().getValue()),
        "The specified device tier '%s' does not match any of the available values: %s.",
        getDeviceSpec().getDeviceTier().getValue(),
        valuesAndAlternatives.stream().map(i -> i.toString()).collect(joining(", ")));
  }
}
