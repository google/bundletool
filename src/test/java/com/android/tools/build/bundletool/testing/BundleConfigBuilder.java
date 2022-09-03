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

package com.android.tools.build.bundletool.testing;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.ResourceOptimizations;
import com.android.bundle.Config.ResourceOptimizations.SparseEncoding;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.StandaloneConfig.DexMergingStrategy;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.bundle.Config.UnsignedEmbeddedApkConfig;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Helper to create {@link BundleConfig} instances in tests. */
public class BundleConfigBuilder {
  private final BundleConfig.Builder builder;

  /** Creates a builder with the initial state identical to the given config. */
  public BundleConfigBuilder(BundleConfig bundleConfig) {
    this.builder = bundleConfig.toBuilder();
  }

  /** Creates default builder instance with minimal defaults pre-set. */
  public static BundleConfigBuilder create() {
    return new BundleConfigBuilder(BundleConfig.getDefaultInstance())
        // Populate required fields with sensible defaults.
        .setVersion(BundleToolVersion.getCurrentVersion().toString());
  }

  public BundleConfigBuilder addSplitDimension(SplitDimension.Value splitDimension) {
    return addSplitDimension(splitDimension, /* negate= */ false);
  }

  public BundleConfigBuilder addSplitDimension(
      SplitDimension.Value splitDimension, boolean negate) {
    return addSplitDimension(
        SplitDimension.newBuilder().setValue(splitDimension).setNegate(negate).build());
  }

  public BundleConfigBuilder addSplitDimension(
      SplitDimension.Value splitDimension,
      boolean negate,
      boolean stripSuffix,
      String defaultSuffix) {
    return addSplitDimension(
        SplitDimension.newBuilder()
            .setValue(splitDimension)
            .setNegate(negate)
            .setSuffixStripping(
                SuffixStripping.newBuilder()
                    .setEnabled(stripSuffix)
                    .setDefaultSuffix(defaultSuffix)
                    .build())
            .build());
  }

  public BundleConfigBuilder addSplitDimension(SplitDimension splitDimension) {
    builder.getOptimizationsBuilder().getSplitsConfigBuilder().addSplitDimension(splitDimension);
    return this;
  }

  public BundleConfigBuilder setUncompressNativeLibraries(boolean enabled) {
    builder.getOptimizationsBuilder().getUncompressNativeLibrariesBuilder().setEnabled(enabled);
    return this;
  }

  public BundleConfigBuilder setUncompressDexFiles(boolean enabled) {
    builder.getOptimizationsBuilder().getUncompressDexFilesBuilder().setEnabled(enabled);
    return this;
  }

  @CanIgnoreReturnValue
  public BundleConfigBuilder setUncompressDexFilesForVariant(
      UncompressedDexTargetSdk uncompressedDexTargetSdk) {
    builder
        .getOptimizationsBuilder()
        .getUncompressDexFilesBuilder()
        .setUncompressedDexTargetSdk(uncompressedDexTargetSdk);
    return this;
  }

  public BundleConfigBuilder setSparseEncodingForSdk32() {
    builder.setOptimizations(
        Optimizations.newBuilder()
            .setResourceOptimizations(
                ResourceOptimizations.newBuilder()
                    .setSparseEncoding(SparseEncoding.VARIANT_FOR_SDK_32)
                    .build())
            .build());
    return this;
  }

  public BundleConfigBuilder setStoreArchive(boolean enabled) {
    builder.getOptimizationsBuilder().getStoreArchiveBuilder().setEnabled(enabled);
    return this;
  }

  public BundleConfigBuilder setInjectLocaleConfig(boolean enabled) {
    builder.getLocalesBuilder().setInjectLocaleConfig(enabled);
    return this;
  }

  public BundleConfigBuilder setDexMergingStrategy(DexMergingStrategy dexMergingStrategy) {
    builder
        .getOptimizationsBuilder()
        .getStandaloneConfigBuilder()
        .setDexMergingStrategy(dexMergingStrategy);
    return this;
  }

  public BundleConfigBuilder addUncompressedGlob(String uncompressedGlob) {
    builder.getCompressionBuilder().addUncompressedGlob(uncompressedGlob);
    return this;
  }

  public BundleConfigBuilder addResourcePinnedToMasterSplit(int resourceId) {
    builder.getMasterResourcesBuilder().addResourceIds(resourceId);
    return this;
  }

  public BundleConfigBuilder addStandaloneDimension(SplitDimension.Value standaloneDimension) {
    return addStandaloneDimension(standaloneDimension, /* negate= */ false);
  }

  public BundleConfigBuilder addStandaloneDimension(
      SplitDimension.Value standaloneDimension, boolean negate) {
    return addStandaloneDimension(
        SplitDimension.newBuilder().setValue(standaloneDimension).setNegate(negate).build());
  }

  public BundleConfigBuilder addStandaloneDimension(SplitDimension standaloneDimension) {
    builder
        .getOptimizationsBuilder()
        .getStandaloneConfigBuilder()
        .addSplitDimension(standaloneDimension);
    return this;
  }

  public BundleConfigBuilder addUnsignedEmbeddedApkConfig(String path) {
    builder.addUnsignedEmbeddedApkConfig(UnsignedEmbeddedApkConfig.newBuilder().setPath(path));
    return this;
  }

  public BundleConfigBuilder clearCompression() {
    builder.clearCompression();
    return this;
  }

  public BundleConfigBuilder clearOptimizations() {
    builder.clearOptimizations();
    return this;
  }

  public BundleConfigBuilder clearVersion() {
    builder.getBundletoolBuilder().clearVersion();
    return this;
  }

  public BundleConfigBuilder setVersion(String versionString) {
    builder.setBundletool(Bundletool.newBuilder().setVersion(versionString));
    return this;
  }

  public BundleConfig build() {
    return builder.build();
  }
}
