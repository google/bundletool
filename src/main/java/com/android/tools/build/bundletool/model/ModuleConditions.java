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

import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionTargeting;

import com.android.bundle.Targeting.DeviceFeature;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Encapsulates all {@link BundleModule} conditions. */
@AutoValue
public abstract class ModuleConditions {

  public abstract ImmutableList<DeviceFeatureCondition> getDeviceFeatureConditions();

  public abstract Optional<Integer> getMinSdkVersion();

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

    if (getMinSdkVersion().isPresent()) {
      moduleTargeting.setSdkVersionTargeting(
          sdkVersionTargeting(sdkVersionFrom(getMinSdkVersion().get())));
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

    public abstract ModuleConditions build();
  }
}
