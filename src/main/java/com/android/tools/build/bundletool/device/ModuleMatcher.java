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

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ModuleTargeting;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Calculates if a given device matches a module.
 *
 * <p>Restrictions are applied only for conditional modules.
 */
public class ModuleMatcher {

  private final ImmutableList<? extends TargetingDimensionMatcher<?>> moduleMatchers;

  public ModuleMatcher(
      SdkVersionMatcher sdkVersionMatcher,
      DeviceFeatureMatcher deviceFeatureMatcher,
      OpenGlFeatureMatcher openGlFeatureMatcher,
      DeviceGroupModuleMatcher deviceGroupModuleMatcher) {
    this.moduleMatchers =
        ImmutableList.of(
            sdkVersionMatcher,
            deviceFeatureMatcher,
            openGlFeatureMatcher,
            deviceGroupModuleMatcher);
  }

  @VisibleForTesting
  public ModuleMatcher(DeviceSpec deviceSpec) {
    this(
        new SdkVersionMatcher(deviceSpec),
        new DeviceFeatureMatcher(deviceSpec),
        new OpenGlFeatureMatcher(deviceSpec),
        new DeviceGroupModuleMatcher(deviceSpec));
  }

  /**
   * Returns if a conditional module can be installed on a device, or true for all other modules.
   */
  public boolean matchesModuleTargeting(ModuleTargeting targeting) {
    return moduleMatchers.stream()
        .allMatch(matcher -> matcher.getModuleTargetingPredicate().test(targeting));
  }
}
