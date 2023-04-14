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

package com.android.tools.build.bundletool.model.utils;

import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.LDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.NODPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXXHDPI;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toScreenDensity;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.testing.ProtoFuzzer;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingNormalizerTest {
  @Test
  public void normalizeScreenDensityTargeting() {
    ApkTargeting targeting =
        apkDensityTargeting(
            ScreenDensityTargeting.newBuilder()
                .addAllValue(
                    ImmutableList.of(
                        toScreenDensity(LDPI),
                        toScreenDensity(MDPI),
                        toScreenDensity(HDPI),
                        toScreenDensity(150),
                        toScreenDensity(200)))
                .addAllAlternatives(
                    ImmutableList.of(
                        toScreenDensity(XHDPI),
                        toScreenDensity(XXXHDPI),
                        toScreenDensity(NODPI),
                        toScreenDensity(400),
                        toScreenDensity(800)))
                .build());

    ApkTargeting normalized = TargetingNormalizer.normalizeApkTargeting(targeting);

    assertThat(normalized)
        .isEqualTo(
            apkDensityTargeting(
                ScreenDensityTargeting.newBuilder()
                    .addAllValue(
                        ImmutableList.of(
                            toScreenDensity(LDPI),
                            toScreenDensity(150),
                            toScreenDensity(MDPI),
                            toScreenDensity(200),
                            toScreenDensity(HDPI)))
                    .addAllAlternatives(
                        ImmutableList.of(
                            toScreenDensity(XHDPI),
                            toScreenDensity(400),
                            toScreenDensity(XXXHDPI),
                            toScreenDensity(800),
                            toScreenDensity(NODPI)))
                    .build()));
  }

  @Test
  public void normalizeApkTargeting_allTargetingDimensionsAreHandled() {
    ApkTargeting apkTargeting = ProtoFuzzer.randomProtoMessage(ApkTargeting.class);
    ApkTargeting shuffledApkTargeting = ProtoFuzzer.shuffleRepeatedFields(apkTargeting);
    // Sanity-check that the testing data was generated alright.
    assertThat(apkTargeting).isNotEqualTo(shuffledApkTargeting);

    // The following check fails, if the normalizing logic forgets to handle some dimension.
    // This would typically happen when the targeting proto is extended by a new dimension.
    assertThat(TargetingNormalizer.normalizeApkTargeting(apkTargeting))
        .isEqualTo(TargetingNormalizer.normalizeApkTargeting(shuffledApkTargeting));
  }

  @Test
  public void normalizeVariantTargeting_allTargetingDimensionsAreHandled() {
    VariantTargeting variantTargeting = ProtoFuzzer.randomProtoMessage(VariantTargeting.class);
    VariantTargeting shuffledVariantTargeting = ProtoFuzzer.shuffleRepeatedFields(variantTargeting);
    // Sanity-check that the testing data was generated alright.
    assertThat(variantTargeting).isNotEqualTo(shuffledVariantTargeting);

    // The following check fails, if the normalizing logic forgets to handle some dimension.
    // This would typically happen when the targeting proto is extended by a new dimension.
    assertThat(TargetingNormalizer.normalizeVariantTargeting(variantTargeting))
        .isEqualTo(TargetingNormalizer.normalizeVariantTargeting(shuffledVariantTargeting));
  }

  @Test
  public void normalizeAssetsDirectoryTargeting_allTargetingDimensionsAreHandled() {
    AssetsDirectoryTargeting assetsDirectoryTargeting =
        ProtoFuzzer.randomProtoMessage(AssetsDirectoryTargeting.class);
    AssetsDirectoryTargeting shuffledAssetsDirectoryTargeting =
        ProtoFuzzer.shuffleRepeatedFields(assetsDirectoryTargeting);
    // Sanity-check that the testing data was generated alright.
    assertThat(assetsDirectoryTargeting).isNotEqualTo(shuffledAssetsDirectoryTargeting);

    // The following check fails, if the normalizing logic forgets to handle some dimension.
    // This would typically happen when the targeting proto is extended by a new dimension.
    assertThat(TargetingNormalizer.normalizeAssetsDirectoryTargeting(assetsDirectoryTargeting))
        .isEqualTo(
            TargetingNormalizer.normalizeAssetsDirectoryTargeting(
                shuffledAssetsDirectoryTargeting));
  }
}
