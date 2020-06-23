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

import static com.android.tools.build.bundletool.model.targeting.TargetingComparators.MULTI_ABI_ALIAS_COMPARATOR;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

/** A {@link TargetingDimensionMatcher} that provides matching on multiple ABI architectures. */
public class MultiAbiMatcher extends TargetingDimensionMatcher<MultiAbiTargeting> {

  public MultiAbiMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  public boolean matchesTargeting(MultiAbiTargeting targeting) {
    // Test if the targeting has anything specific for multi ABI.
    if (targeting.equals(MultiAbiTargeting.getDefaultInstance())) {
      return true;
    }

    ImmutableSet<ImmutableSet<AbiAlias>> valuesSet =
        targeting.getValueList().stream()
            .map(MultiAbiMatcher::abiAliases)
            .collect(toImmutableSet());

    ImmutableSet<AbiAlias> deviceAbis = deviceAbiAliases();

    if (valuesSet.stream().noneMatch(deviceAbis::containsAll)) {
      return false;
    }

    ImmutableSet<ImmutableSet<AbiAlias>> alternativesSet =
        targeting.getAlternativesList().stream()
            .map(MultiAbiMatcher::abiAliases)
            .collect(toImmutableSet());

    // There is a match only if there is no better alternative. A better alternative is contained
    // in the device's supported ABIs and is "greater" than all current targeting's values.
    return alternativesSet.stream()
        .noneMatch(
            alternative ->
                deviceAbis.containsAll(alternative)
                    && valuesSet.stream()
                        .allMatch(
                            value -> MULTI_ABI_ALIAS_COMPARATOR.compare(alternative, value) > 0));
  }

  @Override
  protected void checkDeviceCompatibleInternal(MultiAbiTargeting targeting) {
    if (targeting.equals(MultiAbiTargeting.getDefaultInstance())) {
      return;
    }

    ImmutableSet<ImmutableSet<AbiAlias>> valuesAndAlternativesSet =
        Streams.concat(
                targeting.getValueList().stream().map(MultiAbiMatcher::abiAliases),
                targeting.getAlternativesList().stream().map(MultiAbiMatcher::abiAliases))
            .collect(toImmutableSet());

    ImmutableSet<AbiAlias> deviceAbis = deviceAbiAliases();

    if (valuesAndAlternativesSet.stream().noneMatch(deviceAbis::containsAll)) {
      throw IncompatibleDeviceException.builder()
          .withUserMessage(
              "No set of ABI architectures that the app supports is contained in the ABI "
                  + "architecture set of the device. Device ABIs: %s, app ABIs: %s.",
              deviceAbis, valuesAndAlternativesSet)
          .build();
    }
  }

  @Override
  protected MultiAbiTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getMultiAbiTargeting();
  }

  @Override
  protected MultiAbiTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return variantTargeting.getMultiAbiTargeting();
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !getDeviceSpec().getSupportedAbisList().isEmpty();
  }

  private static ImmutableSet<AbiAlias> abiAliases(MultiAbi multiAbi) {
    return multiAbi.getAbiList().stream().map(Abi::getAlias).collect(toImmutableSet());
  }

  private ImmutableSet<AbiAlias> deviceAbiAliases() {
    return getDeviceSpec().getSupportedAbisList().stream()
        .map(
            abi ->
                AbiName.fromPlatformName(abi)
                    .orElseThrow(
                        () ->
                            InvalidCommandException.builder()
                                .withInternalMessage("Unrecognized ABI '%s' in device spec.", abi)
                                .build())
                    .toProto())
        .collect(toImmutableSet());
  }
}
