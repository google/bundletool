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

import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.DeviceFeature;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** A {@link TargetingDimensionMatcher} that provides matching on OpenGl version. */
public class OpenGlFeatureMatcher
    extends TargetingDimensionMatcher<ImmutableList<DeviceFeatureTargeting>> {

  private static final String OPEN_GL_DEVICE_NAME = "reqGlEsVersion";
  static final String CONDITIONAL_MODULES_OPEN_GL_NAME =
      "android.hardware.opengles.version";

  private final int supportedOpenGlVersion;

  public OpenGlFeatureMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
    supportedOpenGlVersion =
        deviceSpec.getDeviceFeaturesList().stream()
            .filter(feature -> feature.startsWith(OPEN_GL_DEVICE_NAME + "="))
            .map(feature -> feature.split("=", /* limit= */ 2)[1])
            .mapToInt(Integer::decode)
            .findFirst()
            .orElse(0);
  }

  @Override
  public boolean matchesTargeting(ImmutableList<DeviceFeatureTargeting> targetingValue) {
    Optional<Integer> maybeMinRequiredOpenGl =
        targetingValue.stream()
            .map(DeviceFeatureTargeting::getRequiredFeature)
            .filter(feature -> feature.getFeatureName().equals(CONDITIONAL_MODULES_OPEN_GL_NAME))
            .map(DeviceFeature::getFeatureVersion)
            .collect(toOptional());
    return maybeMinRequiredOpenGl
        .map(minRequiredOpenGl -> supportedOpenGlVersion >= minRequiredOpenGl)
        .orElse(true);
  }

  @Override
  protected void checkDeviceCompatibleInternal(
      ImmutableList<DeviceFeatureTargeting> targetingValue) {}

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !getDeviceSpec().getDeviceFeaturesList().isEmpty();
  }

  @Override
  protected ImmutableList<DeviceFeatureTargeting> getTargetingValue(
      ModuleTargeting moduleTargeting) {
    return ImmutableList.copyOf(moduleTargeting.getDeviceFeatureTargetingList());
  }
}
