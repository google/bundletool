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

import static java.util.function.Predicate.isEqual;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.targeting.ScreenDensitySelector;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/** A {@link TargetingDimensionMatcher} that provides matching on screen density. */
public final class ScreenDensityMatcher extends TargetingDimensionMatcher<ScreenDensityTargeting> {

  public ScreenDensityMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
  }

  @Override
  public boolean matchesTargeting(ScreenDensityTargeting targeting) {
    ImmutableList<ScreenDensity> allDensities =
        ImmutableList.<ScreenDensity>builder()
            .addAll(targeting.getValueList())
            .addAll(targeting.getAlternativesList())
            .build();

    if (allDensities.isEmpty()) {
      return true;
    }
    int bestMatchingDensity =
        new ScreenDensitySelector()
            .selectBestDensity(
                Iterables.transform(allDensities, ResourcesUtils::convertToDpi),
                getDeviceSpec().getScreenDensity());

    return targeting
        .getValueList()
        .stream()
        .map(ResourcesUtils::convertToDpi)
        .anyMatch(isEqual(bestMatchingDensity));
  }

  @Override
  protected ScreenDensityTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getScreenDensityTargeting();
  }

  @Override
  protected ScreenDensityTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return variantTargeting.getScreenDensityTargeting();
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return getDeviceSpec().getScreenDensity() != 0;
  }

  @Override
  protected void checkDeviceCompatibleInternal(ScreenDensityTargeting targetingValue) {}
}
