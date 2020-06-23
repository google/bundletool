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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Streams;

/** A {@link TargetingDimensionMatcher} that provides matching on ABI architecture. */
public final class AbiMatcher extends TargetingDimensionMatcher<AbiTargeting> {

  public AbiMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  public boolean matchesTargeting(AbiTargeting targeting) {
    // Test if the targeting has anything specific for ABI.
    if (targeting.equals(AbiTargeting.getDefaultInstance())) {
      return true;
    }

    ImmutableSet<AbiAlias> valuesList =
        targeting.getValueList().stream().map(Abi::getAlias).collect(toImmutableSet());
    ImmutableSet<AbiAlias> alternativesList =
        targeting.getAlternativesList().stream().map(Abi::getAlias).collect(toImmutableSet());

    SetView<AbiAlias> intersection = Sets.intersection(valuesList, alternativesList);
    checkArgument(
        intersection.isEmpty(),
        "Expected targeting values and alternatives to be mutually exclusive, but both contain: %s",
        intersection);

    // The device spec ABI list is sorted in the order of preference for the device.
    // We try looking for a given abi in the values and alternatives.
    // If we find it in alternatives first, it means there is a better match.
    for (String abi : getDeviceSpec().getSupportedAbisList()) {
      AbiAlias abiAlias =
          AbiName.fromPlatformName(abi)
              .orElseThrow(
                  () ->
                      IncompatibleDeviceException.builder()
                          .withUserMessage("Unrecognized ABI '%s' in device spec.", abi)
                          .build())
              .toProto();
      if (valuesList.contains(abiAlias)) {
        return true;
      }
      if (alternativesList.contains(abiAlias)) {
        return false;
      }
    }
    // At this point we know that any device's abiAlias is not within values or alternatives.
    // The only viable scenario is when the split has no values and the semantic is to match
    // "all but alternatives".

    return valuesList.isEmpty();
  }

  @Override
  protected void checkDeviceCompatibleInternal(AbiTargeting targeting) {
    if (targeting.equals(AbiTargeting.getDefaultInstance())) {
      return;
    }

    ImmutableSet<String> valuesAndAlternativesSet =
        Streams.concat(
                targeting.getValueList().stream()
                    .map(Abi::getAlias)
                    .map(AbiName::fromProto)
                    .map(AbiName::getPlatformName),
                targeting.getAlternativesList().stream()
                    .map(Abi::getAlias)
                    .map(AbiName::fromProto)
                    .map(AbiName::getPlatformName))
            .collect(toImmutableSet());

    ImmutableSet<String> deviceAbis =
        getDeviceSpec().getSupportedAbisList().stream().collect(toImmutableSet());

    SetView<String> intersection = Sets.intersection(valuesAndAlternativesSet, deviceAbis);

    if (intersection.isEmpty()) {
      throw IncompatibleDeviceException.builder()
          .withUserMessage(
              "The app doesn't support ABI architectures of the device. "
                  + "Device ABIs: %s, app ABIs: %s.",
              getDeviceSpec().getSupportedAbisList(), valuesAndAlternativesSet)
          .build();
    }
  }

  @Override
  protected AbiTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getAbiTargeting();
  }

  @Override
  protected AbiTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return variantTargeting.getAbiTargeting();
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !getDeviceSpec().getSupportedAbisList().isEmpty();
  }
}
