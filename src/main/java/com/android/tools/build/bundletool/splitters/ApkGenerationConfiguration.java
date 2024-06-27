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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_API_VERSION;

import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.bundle.Targeting.Abi;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ResourceId;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/** Configuration to be passed to Module Splitters and Variant generators. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ApkGenerationConfiguration {

  public abstract ImmutableSet<OptimizationDimension> getOptimizationDimensions();

  public abstract boolean isForInstantAppVariants();

  public abstract boolean getEnableUncompressedNativeLibraries();

  public abstract boolean getEnableDexCompressionSplitter();

  public abstract UncompressedDexTargetSdk getDexCompressionSplitterForTargetSdk();

  public abstract boolean getEnableSparseEncodingVariant();

  public abstract boolean isInstallableOnExternalStorage();

  public abstract boolean getEnableBaseModuleMinSdkAsDefaultTargeting();

  /**
   * Returns a list of ABIs for placeholder libraries that should be populated for base modules
   * without native code. See {@link AbiPlaceholderInjector} for details.
   */
  public abstract ImmutableSet<Abi> getAbisForPlaceholderLibs();

  /** Resources IDs that are pinned to the master split. */
  public abstract ImmutableSet<ResourceId> getMasterPinnedResourceIds();

  /** Resource names that are pinned to the master split. */
  public abstract ImmutableSet<String> getMasterPinnedResourceNames();

  /** Resources that are (transitively) reachable from AndroidManifest.xml of the base module. */
  public abstract ImmutableSet<ResourceId> getBaseManifestReachableResources();

  /** The configuration of the suffixes for the different dimensions. */
  public abstract ImmutableMap<OptimizationDimension, SuffixStripping> getSuffixStrippings();

  public boolean shouldStripTargetingSuffix(OptimizationDimension dimension) {
    return getSuffixStrippings().containsKey(dimension)
        && getSuffixStrippings().get(dimension).getEnabled();
  }

  public int getMinimalSdkTargetingForUncompressedDex() {
    switch (getDexCompressionSplitterForTargetSdk()) {
      case SDK_31:
        return ANDROID_S_API_VERSION;
      default:
        // Uncompressed dex are supported starting from Android P, but only starting from Android Q
        // the performance impact is negligible compared to a compressed dex.
        return ANDROID_Q_API_VERSION;
    }
  }

  /**
   * Minimum SDK version for which signing with v3 key rotation is intended to be performed.
   *
   * <p>Optional. Setting a value for this field will force an additional variant to be generated
   * which targets the specified SDK version.
   */
  public abstract Optional<Integer> getMinSdkForAdditionalVariantWithV3Rotation();

  public abstract boolean getEnableRequiredSplitTypes();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_ApkGenerationConfiguration.Builder()
        .setForInstantAppVariants(false)
        .setEnableUncompressedNativeLibraries(false)
        .setEnableDexCompressionSplitter(false)
        .setDexCompressionSplitterForTargetSdk(UncompressedDexTargetSdk.UNSPECIFIED)
        .setEnableSparseEncodingVariant(false)
        .setInstallableOnExternalStorage(false)
        .setEnableBaseModuleMinSdkAsDefaultTargeting(false)
        .setAbisForPlaceholderLibs(ImmutableSet.of())
        .setOptimizationDimensions(ImmutableSet.of())
        .setMasterPinnedResourceIds(ImmutableSet.of())
        .setMasterPinnedResourceNames(ImmutableSet.of())
        .setBaseManifestReachableResources(ImmutableSet.of())
        .setSuffixStrippings(ImmutableMap.of())
        .setEnableRequiredSplitTypes(false);
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

    public abstract Builder setInstallableOnExternalStorage(boolean installableOnExternalStorage);

    public abstract Builder setEnableUncompressedNativeLibraries(
        boolean enableUncompressedNativeLibraries);

    public abstract Builder setEnableDexCompressionSplitter(boolean enableDexCompressionSplitter);

    public abstract Builder setDexCompressionSplitterForTargetSdk(
        UncompressedDexTargetSdk uncompressedDexTargetSdk);

    public abstract Builder setEnableSparseEncodingVariant(boolean enableSparseEncoding);

    public abstract Builder setAbisForPlaceholderLibs(ImmutableSet<Abi> abis);

    public abstract Builder setMasterPinnedResourceIds(ImmutableSet<ResourceId> resourceIds);

    public abstract Builder setMasterPinnedResourceNames(ImmutableSet<String> resourceNames);

    public abstract Builder setBaseManifestReachableResources(ImmutableSet<ResourceId> resourceIds);

    public abstract Builder setSuffixStrippings(
        ImmutableMap<OptimizationDimension, SuffixStripping> suffixStripping);

    public abstract Builder setMinSdkForAdditionalVariantWithV3Rotation(
        int minSdkForAdditionalVariantWithV3Rotation);

    public abstract Builder setEnableBaseModuleMinSdkAsDefaultTargeting(
        boolean enableBaseModuleMinSdkAsDefaultTargeting);

    public abstract Builder setEnableRequiredSplitTypes(boolean enableRequiredSplitTypes);

    public abstract ApkGenerationConfiguration build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  ApkGenerationConfiguration() {}
}
