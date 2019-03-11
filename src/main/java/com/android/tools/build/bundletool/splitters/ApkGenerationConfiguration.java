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

package com.android.tools.build.bundletool.splitters;

import com.android.bundle.Targeting.Abi;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ResourceId;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;

/** Configuration to be passed to Module Splitters and Variant generators. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ApkGenerationConfiguration {

  public abstract ImmutableSet<OptimizationDimension> getOptimizationDimensions();

  public abstract boolean isForInstantAppVariants();

  public abstract boolean getEnableNativeLibraryCompressionSplitter();

  public abstract boolean getEnableDexCompressionSplitter();

  /** Whether to include 64-bit native library config splits. */
  public abstract boolean getInclude64BitLibs();

  /**
   * Returns a list of ABIs for placeholder libraries that should be populated for base modules
   * without native code. See {@link AbiPlaceholderInjector} for details.
   */
  public abstract ImmutableSet<Abi> getAbisForPlaceholderLibs();

  /** Resources that are pinned to the master split. */
  public abstract ImmutableSet<ResourceId> getMasterPinnedResources();

  /** Resources that are (transitively) reachable from AndroidManifest.xml of the base module. */
  public abstract ImmutableSet<ResourceId> getBaseManifestReachableResources();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_ApkGenerationConfiguration.Builder()
        .setForInstantAppVariants(false)
        .setEnableNativeLibraryCompressionSplitter(false)
        .setEnableDexCompressionSplitter(false)
        .setInclude64BitLibs(true)
        .setAbisForPlaceholderLibs(ImmutableSet.of())
        .setOptimizationDimensions(ImmutableSet.of())
        .setMasterPinnedResources(ImmutableSet.of())
        .setBaseManifestReachableResources(ImmutableSet.of());
  }

  public static ApkGenerationConfiguration getDefaultInstance() {
    return ApkGenerationConfiguration.builder().build();
  }

  /** Builder for the {@link ApkGenerationConfiguration}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setOptimizationDimensions(
        ImmutableSet<OptimizationDimension> optimizationDimensions);

    public abstract Builder setForInstantAppVariants(boolean forInstantAppVariants);

    public abstract Builder setEnableNativeLibraryCompressionSplitter(
        boolean enableNativeLibraryCompressionSplitter);

    public abstract Builder setEnableDexCompressionSplitter(boolean enableDexCompressionSplitter);

    public abstract Builder setAbisForPlaceholderLibs(ImmutableSet<Abi> abis);

    public abstract Builder setInclude64BitLibs(boolean shouldGenerate);

    public abstract Builder setMasterPinnedResources(ImmutableSet<ResourceId> resourceIds);

    public abstract Builder setBaseManifestReachableResources(ImmutableSet<ResourceId> resourceIds);

    public abstract ApkGenerationConfiguration build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  ApkGenerationConfiguration() {}
}
