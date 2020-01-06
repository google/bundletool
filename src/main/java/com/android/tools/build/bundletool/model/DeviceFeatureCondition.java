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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/** A {@link BundleModule} condition describing a certain device feature. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class DeviceFeatureCondition {

  public abstract String getFeatureName();

  public abstract Optional<Integer> getFeatureVersion();

  public static DeviceFeatureCondition create(String featureName) {
    return new AutoValue_DeviceFeatureCondition(featureName, Optional.empty());
  }

  public static DeviceFeatureCondition create(String featureName, Optional<Integer> version) {
    return new AutoValue_DeviceFeatureCondition(featureName, version);
  }
}
