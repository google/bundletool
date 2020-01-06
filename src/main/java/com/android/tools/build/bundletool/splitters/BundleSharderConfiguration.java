/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.splitters;

import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/** Configuration to be passed to Bundle sharders. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class BundleSharderConfiguration {

  public abstract boolean getStrip64BitLibrariesFromShards();

  public abstract Optional<DeviceSpec> getDeviceSpec();

  /** The configuration of the suffixes for the different dimensions. */
  public abstract ImmutableMap<OptimizationDimension, SuffixStripping>
      getSuffixStrippings();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_BundleSharderConfiguration.Builder()
        .setStrip64BitLibrariesFromShards(false)
        .setSuffixStrippings(ImmutableMap.of());
  }

  public static BundleSharderConfiguration getDefaultInstance() {
    return BundleSharderConfiguration.builder().build();
  }

  public boolean splitByLanguage() {
    return getDeviceSpec().map(spec -> !spec.getSupportedLocalesList().isEmpty()).orElse(false);
  }

  /** Builder for the {@link BundleSharderConfiguration}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setStrip64BitLibrariesFromShards(boolean strip64BitLibrariesFromShards);

    public abstract Builder setDeviceSpec(Optional<DeviceSpec> deviceSpec);

    public abstract Builder setSuffixStrippings(
        ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings);

    abstract BundleSharderConfiguration build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  BundleSharderConfiguration() {}
}
