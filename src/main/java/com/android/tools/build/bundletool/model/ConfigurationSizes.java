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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;

/** Holder of min and max size maps for each {@link SizeConfiguration}. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ConfigurationSizes {

  public abstract ImmutableMap<SizeConfiguration, Long> getMinSizeConfigurationMap();

  public abstract ImmutableMap<SizeConfiguration, Long> getMaxSizeConfigurationMap();

  public static ConfigurationSizes create(
      ImmutableMap<SizeConfiguration, Long> minSizeConfigurationMap,
      ImmutableMap<SizeConfiguration, Long> maxSizeConfigurationMap) {
    return new AutoValue_ConfigurationSizes(minSizeConfigurationMap, maxSizeConfigurationMap);
  }
}
