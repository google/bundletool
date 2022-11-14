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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Streams;

/**
 * A {@link TargetingDimensionMatcher} that provides APK matching on country set.
 *
 * <p>Country Set is a user attribute and it is explicitly defined in the {@link DeviceSpec}.
 */
public class CountrySetApkMatcher extends TargetingDimensionMatcher<CountrySetTargeting> {

  public CountrySetApkMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  protected CountrySetTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getCountrySetTargeting();
  }

  @Override
  protected CountrySetTargeting getTargetingValue(VariantTargeting variantTargeting) {
    // Country set is not propagated to the variant targeting.
    return CountrySetTargeting.getDefaultInstance();
  }

  @Override
  public boolean matchesTargeting(CountrySetTargeting targeting) {
    // If there is no targeting, by definition the targeting is matched.
    if (targeting.equals(CountrySetTargeting.getDefaultInstance())) {
      return true;
    }

    ImmutableSet<String> values = ImmutableSet.copyOf(targeting.getValueList());
    ImmutableSet<String> alternatives = ImmutableSet.copyOf(targeting.getAlternativesList());

    SetView<String> intersection = Sets.intersection(values, alternatives);
    checkArgument(
        intersection.isEmpty(),
        "Expected targeting values and alternatives to be mutually exclusive, but both contain: %s",
        intersection);

    // case where country set is not specified in device spec and default suffix is "".
    if (!getDeviceSpec().hasCountrySet() || getDeviceSpec().getCountrySet().getValue().isEmpty()) {
      return values.isEmpty() && !alternatives.isEmpty();
    }

    return values.contains(getDeviceSpec().getCountrySet().getValue());
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return getDeviceSpec().hasCountrySet();
  }

  @Override
  protected void checkDeviceCompatibleInternal(CountrySetTargeting targeting) {
    if (targeting.equals(CountrySetTargeting.getDefaultInstance())) {
      return;
    }
    if (!getDeviceSpec().hasCountrySet() || getDeviceSpec().getCountrySet().getValue().isEmpty()) {
      // If no country set is specified in device spec, fallback or default suffix targeting
      // apks are used.
      return;
    }
    ImmutableSet<String> valuesAndAlternatives =
        Streams.concat(targeting.getValueList().stream(), targeting.getAlternativesList().stream())
            .collect(toImmutableSet());
    checkArgument(
        valuesAndAlternatives.contains(getDeviceSpec().getCountrySet().getValue()),
        "The specified country set '%s' does not match any of the available values: %s.",
        getDeviceSpec().getCountrySet().getValue(),
        String.join(", ", valuesAndAlternatives));
  }
}
