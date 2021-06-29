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

package com.android.tools.build.bundletool.model;

import com.android.bundle.Targeting.DeviceFeature;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.DeviceGroupModuleTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.UserCountriesTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.TargetingProtoUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Encapsulates all {@link BundleModule} conditions. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ModuleConditions {

  public abstract ImmutableList<DeviceFeatureCondition> getDeviceFeatureConditions();

  public abstract Optional<Integer> getMinSdkVersion();

  public abstract Optional<Integer> getMaxSdkVersion();

  public abstract Optional<UserCountriesCondition> getUserCountriesCondition();

  public abstract Optional<DeviceGroupsCondition> getDeviceGroupsCondition();

  public boolean isEmpty() {
    return toTargeting().equals(ModuleTargeting.getDefaultInstance());
  }

  public static Builder builder() {
    return new AutoValue_ModuleConditions.Builder();
  }

  public ModuleTargeting toTargeting() {
    ModuleTargeting.Builder moduleTargeting = ModuleTargeting.newBuilder();

    for (DeviceFeatureCondition condition : getDeviceFeatureConditions()) {
      DeviceFeature.Builder feature =
          DeviceFeature.newBuilder().setFeatureName(condition.getFeatureName());
      condition.getFeatureVersion().ifPresent(feature::setFeatureVersion);
      moduleTargeting.addDeviceFeatureTargeting(
          DeviceFeatureTargeting.newBuilder().setRequiredFeature(feature));
    }

    // Effective min SDK targeting in this case is:
    //   1) provided min SDK, if available
    //   2) 1, if min SDK is not provided, but max SDK is used
    //   3) nothing, if neither min SDK nor max SDK are used
    Optional<Integer> effectiveMinSdk =
        getMinSdkVersion().isPresent() ? getMinSdkVersion() : getMaxSdkVersion().map(unused -> 1);

    effectiveMinSdk
        .map(TargetingProtoUtils::sdkVersionFrom)
        .map(TargetingProtoUtils::sdkVersionTargeting)
        .ifPresent(moduleTargeting::setSdkVersionTargeting);

    // Sentinel alternatives are not inclusive, hence +1.
    getMaxSdkVersion()
        .map(sdk -> sdk + 1)
        .map(TargetingProtoUtils::sdkVersionFrom)
        .ifPresent(
            maxSdkVersion ->
                moduleTargeting.getSdkVersionTargetingBuilder().addAlternatives(maxSdkVersion));

    if (getUserCountriesCondition().isPresent()) {
      UserCountriesCondition condition = getUserCountriesCondition().get();
      moduleTargeting.setUserCountriesTargeting(
          UserCountriesTargeting.newBuilder()
              .addAllCountryCodes(condition.getCountries())
              .setExclude(condition.getExclude())
              .build());
    }

    if (getDeviceGroupsCondition().isPresent()) {
      moduleTargeting.setDeviceGroupTargeting(
          DeviceGroupModuleTargeting.newBuilder()
              .addAllValue(getDeviceGroupsCondition().get().getDeviceGroups())
              .build());
    }

    return moduleTargeting.build();
  }

  /** Builder for {@link ModuleConditions}. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract ImmutableList.Builder<DeviceFeatureCondition> deviceFeatureConditionsBuilder();

    public Builder addDeviceFeatureCondition(DeviceFeatureCondition deviceFeatureCondition) {
      deviceFeatureConditionsBuilder().add(deviceFeatureCondition);
      return this;
    }

    public abstract Builder setMinSdkVersion(int minSdkVersion);

    public abstract Builder setMaxSdkVersion(int maxSdkVersion);

    public abstract Builder setUserCountriesCondition(
        UserCountriesCondition userCountriesCondition);

    public abstract Builder setDeviceGroupsCondition(DeviceGroupsCondition deviceGroupsCondition);

    protected abstract ModuleConditions autoBuild();

    public ModuleConditions build() {
      ModuleConditions moduleConditions = autoBuild();

      Set<String> featureNames = new HashSet<>();
      for (DeviceFeatureCondition condition : moduleConditions.getDeviceFeatureConditions()) {
        if (!featureNames.add(condition.getFeatureName())) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "The device feature condition on '%s' is present more than once.",
                  condition.getFeatureName())
              .build();
        }
      }

      return moduleConditions;
    }
  }
}
