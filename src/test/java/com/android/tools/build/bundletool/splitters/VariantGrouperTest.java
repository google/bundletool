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

import static com.android.tools.build.bundletool.testing.ModuleSplitUtils.createModuleSplitBuilder;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.InMemoryModuleEntry;
import com.android.tools.build.bundletool.utils.Versions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VariantGrouperTest {

  private static final byte[] DUMMY_CONTENT = new byte[1];

  @Test
  public void variantGrouper_singleVariantSingleSplit() {
    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_asset.txt", DUMMY_CONTENT)))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(lPlusVariantTargeting())
            .build();

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantSplitMap =
        new VariantGrouper().groupByVariant(ImmutableList.of(moduleSplit));

    assertThat(variantSplitMap).hasSize(1);
    assertThat(variantSplitMap).containsEntry(lPlusVariantTargeting(), moduleSplit);
  }

  @Test
  public void variantGrouper_singleVariantMultipleSplits() {
    ModuleSplit moduleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_asset.txt", DUMMY_CONTENT)))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(lPlusVariantTargeting())
            .build();
    ModuleSplit moduleSplit2 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_other_asset.txt", DUMMY_CONTENT)))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(lPlusVariantTargeting())
            .build();

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantSplitMap =
        new VariantGrouper().groupByVariant(ImmutableList.of(moduleSplit, moduleSplit2));

    assertThat(variantSplitMap).hasSize(2);
    assertThat(variantSplitMap).containsEntry(lPlusVariantTargeting(), moduleSplit);
    assertThat(variantSplitMap).containsEntry(lPlusVariantTargeting(), moduleSplit2);
  }

  @Test
  public void variantGrouper_MultipleVariantsMultipleSplitsComplementaryVariants() {
    VariantTargeting variantLPlusTargeting =
        variantMinSdkTargeting(Versions.ANDROID_L_API_VERSION, Versions.ANDROID_M_API_VERSION);
    VariantTargeting variantMPlusTargeting =
        variantMinSdkTargeting(Versions.ANDROID_M_API_VERSION, Versions.ANDROID_L_API_VERSION);
    ModuleSplit moduleSplit =
        createModuleSplitBuilder().setVariantTargeting(variantLPlusTargeting).build();
    ModuleSplit moduleSplit2 =
        createModuleSplitBuilder().setVariantTargeting(variantMPlusTargeting).build();

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantSplitMap =
        new VariantGrouper().groupByVariant(ImmutableList.of(moduleSplit, moduleSplit2));

    assertThat(variantSplitMap).hasSize(2);

    assertThat(variantSplitMap).containsEntry(variantLPlusTargeting, moduleSplit);
    assertThat(variantSplitMap).containsEntry(variantMPlusTargeting, moduleSplit2);
  }

  /**
   * This tests variant targetings which are not complementary (targetings are not alternatives of
   * each other), we expect some splits to appear multiple times in the result, as variants are
   * split into multiple sdk ranges.
   */
  @Test
  public void variantGrouper_MultipleVariantsMultipleSplitsSimple() {
    ModuleSplit moduleSplit =
        createModuleSplitBuilder().setVariantTargeting(lPlusVariantTargeting()).build();
    ModuleSplit moduleSplit2 =
        createModuleSplitBuilder()
            .setVariantTargeting(variantMinSdkTargeting(Versions.ANDROID_M_API_VERSION))
            .build();

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantSplitMap =
        new VariantGrouper().groupByVariant(ImmutableList.of(moduleSplit, moduleSplit2));

    VariantTargeting variantLPlusTargeting =
        variantMinSdkTargeting(Versions.ANDROID_L_API_VERSION, Versions.ANDROID_M_API_VERSION);
    VariantTargeting variantMPlusTargeting =
        variantMinSdkTargeting(Versions.ANDROID_M_API_VERSION, Versions.ANDROID_L_API_VERSION);
    assertThat(variantSplitMap).hasSize(3);

    assertThat(variantSplitMap)
        .containsEntry(
            variantLPlusTargeting, applyVariantTargeting(moduleSplit, variantLPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantMPlusTargeting, applyVariantTargeting(moduleSplit, variantMPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantMPlusTargeting, applyVariantTargeting(moduleSplit2, variantMPlusTargeting));
  }

  @Test
  public void variantGrouper_MultipleVariantsMultipleSplits() {

    ModuleSplit moduleSplit =
        createModuleSplitBuilder().setVariantTargeting(lPlusVariantTargeting()).build();

    ModuleSplit moduleSplit2 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/x86/liba.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .setVariantTargeting(lPlusVariantTargeting())
            .build();

    ModuleSplit moduleSplit3 =
        createModuleSplitBuilder()
            .setVariantTargeting(variantMinSdkTargeting(Versions.ANDROID_M_API_VERSION))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/x86/libb.so", DUMMY_CONTENT)))
            .build();

    ModuleSplit moduleSplit4 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("testModule/lib/x86/libc.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .setVariantTargeting(variantMinSdkTargeting(Versions.ANDROID_M_API_VERSION))
            .build();

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantSplitMap =
        new VariantGrouper()
            .groupByVariant(
                ImmutableList.of(moduleSplit, moduleSplit2, moduleSplit3, moduleSplit4));

    VariantTargeting variantLPlusTargeting =
        variantMinSdkTargeting(Versions.ANDROID_L_API_VERSION, Versions.ANDROID_M_API_VERSION);
    VariantTargeting variantMPlusTargeting =
        variantMinSdkTargeting(Versions.ANDROID_M_API_VERSION, Versions.ANDROID_L_API_VERSION);
    assertThat(variantSplitMap).hasSize(6);

    assertThat(variantSplitMap)
        .containsEntry(
            variantLPlusTargeting, applyVariantTargeting(moduleSplit, variantLPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantMPlusTargeting, applyVariantTargeting(moduleSplit, variantMPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantLPlusTargeting, applyVariantTargeting(moduleSplit2, variantLPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantMPlusTargeting, applyVariantTargeting(moduleSplit2, variantMPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantMPlusTargeting, applyVariantTargeting(moduleSplit3, variantMPlusTargeting));
    assertThat(variantSplitMap)
        .containsEntry(
            variantMPlusTargeting, applyVariantTargeting(moduleSplit4, variantMPlusTargeting));
  }

  private ModuleSplit applyVariantTargeting(
      ModuleSplit moduleSplit, VariantTargeting variantTargeting) {

    return moduleSplit.toBuilder().setVariantTargeting(variantTargeting).build();
  }
}
