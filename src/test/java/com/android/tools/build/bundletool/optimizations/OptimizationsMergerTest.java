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
package com.android.tools.build.bundletool.optimizations;

import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OptimizationsMergerTest {

  private static final Version BUNDLE_TOOL_VERSION = Version.of("0.2.0");
  private static final ImmutableSet<OptimizationDimension> DEFAULT_SPLIT_DIMENSIONS =
      ApkOptimizations.getDefaultOptimizationsForVersion(BUNDLE_TOOL_VERSION).getSplitDimensions();
  private static final ImmutableSet<OptimizationDimension> DEFAULT_STANDALONE_DIMENSIONS =
      ApkOptimizations.getDefaultOptimizationsForVersion(BUNDLE_TOOL_VERSION)
          .getStandaloneDimensions();

  @Before
  public void setUp() {
    // Some of the tests rely on this.
    checkState(DEFAULT_SPLIT_DIMENSIONS.equals(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE)));
  }

  @Test
  public void mergeOptimizations_onlyDefaults() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(createBundleConfigBuilder().clearOptimizations().build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_overridesTakePrecedence() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .addSplitDimension(SplitDimension.Value.ABI)
                    .build(),
                /* optimizationsOverride= */ ImmutableSet.of(SCREEN_DENSITY));

    assertThat(apkOptimizations.getSplitDimensions()).containsExactly(SCREEN_DENSITY);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).containsExactly(SCREEN_DENSITY);
  }

  @Test
  public void mergeOptimizations_overridesTakePrecedence_withUncompressNativeLibs() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .setUncompressNativeLibraries(true)
                    .build(),
                /* optimizationsOverride= */ ImmutableSet.of(SCREEN_DENSITY));

    assertThat(apkOptimizations.getSplitDimensions()).containsExactly(SCREEN_DENSITY);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isTrue();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).containsExactly(SCREEN_DENSITY);
  }

  @Test
  public void mergeOptimizations_withUncompressDexFiles() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .setUncompressDexFiles(true)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isTrue();
    assertThat(apkOptimizations.getUncompressedDexTargetSdk())
        .isEqualTo(UncompressedDexTargetSdk.UNSPECIFIED);
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_withUncompressDexFiles_withUncompressedDexTargetSdk() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .setUncompressDexFiles(true)
                    .setUncompressDexFilesForVariant(UncompressedDexTargetSdk.SDK_31)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isTrue();
    assertThat(apkOptimizations.getUncompressedDexTargetSdk())
        .isEqualTo(UncompressedDexTargetSdk.SDK_31);
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_withUncompressDexFilesForSVariant() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .setUncompressDexFilesForVariant(UncompressedDexTargetSdk.SDK_31)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getUncompressedDexTargetSdk())
        .isEqualTo(UncompressedDexTargetSdk.SDK_31);
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_bundleConfigRemovesOneDimension() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .addSplitDimension(SplitDimension.Value.ABI, /* negate= */ true)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions())
        .isEqualTo(Sets.difference(DEFAULT_SPLIT_DIMENSIONS, ImmutableSet.of(ABI)));
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions())
        .isEqualTo(Sets.difference(DEFAULT_STANDALONE_DIMENSIONS, ImmutableSet.of(ABI)));
  }

  @Test
  public void mergeOptimizations_bundleConfigSameAsDefaults() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .addSplitDimension(SplitDimension.Value.ABI)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_afterVersion_0_6_0_uncompressNativeLibsNotSet() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder().setVersion("0.6.0").clearOptimizations().build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isTrue();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_afterVersion_0_6_0_enabledUncompressNativeLibs() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .setVersion("0.6.0")
                    .clearOptimizations()
                    .setUncompressNativeLibraries(/* enabled= */ true)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isTrue();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_afterVersion_0_6_0_disabledUncompressNativeLibs() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .setVersion("0.6.0")
                    .clearOptimizations()
                    .setUncompressNativeLibraries(/* enabled= */ false)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions()).isEqualTo(DEFAULT_STANDALONE_DIMENSIONS);
  }

  @Test
  public void mergeOptimizations_bundleConfigAddsOneStandaloneDimension() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .addStandaloneDimension(SplitDimension.Value.LANGUAGE)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions())
        .isEqualTo(Sets.union(DEFAULT_STANDALONE_DIMENSIONS, ImmutableSet.of(LANGUAGE)));
  }

  @Test
  public void mergeOptimizations_bundleConfigRemovesOneStandaloneDimension() {
    ApkOptimizations apkOptimizations =
        new OptimizationsMerger()
            .mergeWithDefaults(
                createBundleConfigBuilder()
                    .clearOptimizations()
                    .addStandaloneDimension(SplitDimension.Value.ABI, /* negate= */ true)
                    .build());

    assertThat(apkOptimizations.getSplitDimensions()).isEqualTo(DEFAULT_SPLIT_DIMENSIONS);
    assertThat(apkOptimizations.getUncompressNativeLibraries()).isFalse();
    assertThat(apkOptimizations.getUncompressDexFiles()).isFalse();
    assertThat(apkOptimizations.getStandaloneDimensions())
        .isEqualTo(Sets.difference(DEFAULT_STANDALONE_DIMENSIONS, ImmutableSet.of(ABI)));
  }

  private static BundleConfigBuilder createBundleConfigBuilder() {
    return BundleConfigBuilder.create().setVersion(BUNDLE_TOOL_VERSION.toString());
  }
}
