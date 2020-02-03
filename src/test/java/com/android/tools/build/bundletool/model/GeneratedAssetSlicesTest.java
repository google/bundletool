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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GeneratedAssetSlicesTest {
  @Test
  public void fromModuleSplits_empty() {
    GeneratedAssetSlices generatedAssetSlices =
        GeneratedAssetSlices.fromModuleSplits(ImmutableList.of());
    assertThat(generatedAssetSlices.size()).isEqualTo(0);
    assertThat(generatedAssetSlices.getAssetSlices()).isEmpty();
  }

  @Test
  public void fromModuleSplits_assetSlicesAndOtherSplits() {
    ModuleSplit baseSplit =
        ModuleSplit.builder()
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
            .setEntries(ImmutableList.of())
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .setModuleName(BundleModuleName.create("base"))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();
    ModuleSplit assetSlice =
        ModuleSplit.builder()
            .setAndroidManifest(
                AndroidManifest.create(androidManifestForAssetModule("com.test.app")))
            .setEntries(ImmutableList.of())
            .setMasterSplit(true)
            .setSplitType(SplitType.ASSET_SLICE)
            .setModuleName(BundleModuleName.create("some_assets"))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();

    ImmutableList<ModuleSplit> splits = ImmutableList.of(baseSplit, assetSlice);
    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(splits);
    assertThat(generatedApks.size()).isEqualTo(1);
    assertThat(generatedApks.getSplitApks()).containsExactly(baseSplit);

    GeneratedAssetSlices generatedAssetSlices = GeneratedAssetSlices.fromModuleSplits(splits);
    assertThat(generatedAssetSlices.size()).isEqualTo(1);
    assertThat(generatedAssetSlices.getAssetSlices()).containsExactly(assetSlice);
  }

  @Test
  public void fromBuilder() {
    ModuleSplit assetSlice =
        ModuleSplit.builder()
            .setAndroidManifest(
                AndroidManifest.create(androidManifestForAssetModule("com.test.app")))
            .setEntries(ImmutableList.of())
            .setMasterSplit(true)
            .setSplitType(SplitType.ASSET_SLICE)
            .setModuleName(BundleModuleName.create("some_assets"))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .build();
    GeneratedAssetSlices generatedAssetSlices =
        GeneratedAssetSlices.builder().setAssetSlices(ImmutableList.of(assetSlice)).build();
    assertThat(generatedAssetSlices.size()).isEqualTo(1);
    assertThat(generatedAssetSlices.getAssetSlices()).containsExactly(assetSlice);
  }
}
