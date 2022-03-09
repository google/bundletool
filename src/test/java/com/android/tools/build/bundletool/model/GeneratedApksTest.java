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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GeneratedApksTest {

  @Test
  public void builder_empty() {
    GeneratedApks generatedApks = GeneratedApks.builder().build();

    assertThat(generatedApks.size()).isEqualTo(0);
    assertThat(generatedApks.getSplitApks()).isEmpty();
    assertThat(generatedApks.getStandaloneApks()).isEmpty();
    assertThat(generatedApks.getInstantApks()).isEmpty();
    assertThat(generatedApks.getSystemApks()).isEmpty();
    assertThat(generatedApks.getArchivedApks()).isEmpty();
  }

  @Test
  public void fromModuleSplits_empty() {
    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(ImmutableList.of());

    assertThat(generatedApks.size()).isEqualTo(0);
    assertThat(generatedApks.getSplitApks()).isEmpty();
    assertThat(generatedApks.getStandaloneApks()).isEmpty();
    assertThat(generatedApks.getInstantApks()).isEmpty();
    assertThat(generatedApks.getSystemApks()).isEmpty();
    assertThat(generatedApks.getArchivedApks()).isEmpty();
  }

  @Test
  public void fromModuleSplits_correctSizes() {
    ModuleSplit splitApk = createModuleSplit(SplitType.SPLIT);
    ModuleSplit standaloneApk = createModuleSplit(SplitType.STANDALONE);
    ModuleSplit instantApk = createModuleSplit(SplitType.INSTANT);

    GeneratedApks generatedApks =
        GeneratedApks.fromModuleSplits(ImmutableList.of(splitApk, standaloneApk, instantApk));

    assertThat(generatedApks.size()).isEqualTo(3);
    assertThat(generatedApks.getSplitApks()).containsExactly(splitApk);
    assertThat(generatedApks.getStandaloneApks()).containsExactly(standaloneApk);
    assertThat(generatedApks.getInstantApks()).containsExactly(instantApk);
    assertThat(generatedApks.getSystemApks()).isEmpty();
    assertThat(generatedApks.getArchivedApks()).isEmpty();
  }

  @Test
  public void fromModuleSplits_withSystemSplits_correctSizes() {
    ModuleSplit systemApk = createModuleSplit(SplitType.SYSTEM);

    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(ImmutableList.of(systemApk));

    assertThat(generatedApks.size()).isEqualTo(1);
    assertThat(generatedApks.getSplitApks()).isEmpty();
    assertThat(generatedApks.getStandaloneApks()).isEmpty();
    assertThat(generatedApks.getInstantApks()).isEmpty();
    assertThat(generatedApks.getSystemApks()).containsExactly(systemApk);
    assertThat(generatedApks.getArchivedApks()).isEmpty();
  }

  @Test
  public void fromModuleSplits_withArchivedApk_correctSizes() {
    ModuleSplit archivedApk = createModuleSplit(SplitType.ARCHIVE);

    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(ImmutableList.of(archivedApk));

    assertThat(generatedApks.size()).isEqualTo(1);
    assertThat(generatedApks.getSplitApks()).isEmpty();
    assertThat(generatedApks.getStandaloneApks()).isEmpty();
    assertThat(generatedApks.getInstantApks()).isEmpty();
    assertThat(generatedApks.getSystemApks()).isEmpty();
    assertThat(generatedApks.getArchivedApks()).containsExactly(archivedApk);
  }

  private static ModuleSplit createModuleSplit(SplitType splitType) {
    return ModuleSplit.builder()
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
        .setEntries(ImmutableList.of())
        .setMasterSplit(true)
        .setSplitType(splitType)
        .setModuleName(BundleModuleName.create("base"))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .build();
  }
}
