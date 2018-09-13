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
package com.android.tools.build.bundletool.io;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ApkPathManagerTest {

  @Test
  public void splitApkDirectory() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit = createModuleSplit().setSplitType(SplitType.SPLIT).build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getNameCount()).isEqualTo(2);
    assertThat(zipPath.toString()).startsWith("splits/");
  }

  @Test
  public void standaloneApkDirectory() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit = createModuleSplit().setSplitType(SplitType.STANDALONE).build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getNameCount()).isEqualTo(2);
    assertThat(zipPath.toString()).startsWith("standalones/");
  }

  @Test
  public void instantApkDirectory() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit = createModuleSplit().setSplitType(SplitType.INSTANT).build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getNameCount()).isEqualTo(2);
    assertThat(zipPath.toString()).startsWith("instant/");
  }

  @Test
  public void splitApkFileName_masterSplit() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit =
        createModuleSplit().setSplitType(SplitType.SPLIT).setMasterSplit(true).build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getFileName().toString()).isEqualTo("base-master.apk");
  }

  @Test
  public void splitApkFileName_configSplit() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit =
        createModuleSplit()
            .setSplitType(SplitType.SPLIT)
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ARM64_V8A))
            .build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getFileName().toString()).isEqualTo("base-arm64_v8a.apk");
  }

  @Test
  public void splitApkFileName_configSplitWithConflictingNames() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit1 =
        createModuleSplit()
            .setSplitType(SplitType.SPLIT)
            .setMasterSplit(false)
            .setApkTargeting(mergeApkTargeting(apkAbiTargeting(ARM64_V8A), apkMinSdkTargeting(21)))
            .build();
    ModuleSplit moduleSplit2 =
        createModuleSplit()
            .setSplitType(SplitType.SPLIT)
            .setMasterSplit(false)
            .setApkTargeting(mergeApkTargeting(apkAbiTargeting(ARM64_V8A), apkMinSdkTargeting(23)))
            .build();
    ModuleSplit moduleSplit3 =
        createModuleSplit()
            .setSplitType(SplitType.SPLIT)
            .setMasterSplit(false)
            .setApkTargeting(mergeApkTargeting(apkAbiTargeting(ARM64_V8A), apkMinSdkTargeting(25)))
            .build();

    ZipPath zipPath1 = apkPathManager.getApkPath(moduleSplit1);
    assertThat(zipPath1.getFileName().toString()).isEqualTo("base-arm64_v8a.apk");
    ZipPath zipPath2 = apkPathManager.getApkPath(moduleSplit2);
    assertThat(zipPath2.getFileName().toString()).isEqualTo("base-arm64_v8a_2.apk");
    ZipPath zipPath3 = apkPathManager.getApkPath(moduleSplit3);
    assertThat(zipPath3.getFileName().toString()).isEqualTo("base-arm64_v8a_3.apk");
  }

  @Test
  public void instantApkFileName_masterSplit() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit =
        createModuleSplit().setSplitType(SplitType.INSTANT).setMasterSplit(true).build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getFileName().toString()).isEqualTo("instant-base-master.apk");
  }

  @Test
  public void instantApkFileName_configSplit() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit =
        createModuleSplit()
            .setSplitType(SplitType.INSTANT)
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(ARM64_V8A))
            .build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getFileName().toString()).isEqualTo("instant-base-arm64_v8a.apk");
  }

  @Test
  public void standaloneApkFileName_noShard() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit = createModuleSplit().setSplitType(SplitType.STANDALONE).build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getFileName().toString()).isEqualTo("standalone.apk");
  }

  @Test
  public void standaloneApkFileName_shardByDensityAndAbi() {
    ApkPathManager apkPathManager = new ApkPathManager();
    ModuleSplit moduleSplit =
        createModuleSplit()
            .setSplitType(SplitType.STANDALONE)
            .setApkTargeting(
                mergeApkTargeting(apkAbiTargeting(ARM64_V8A), apkDensityTargeting(HDPI)))
            .build();

    ZipPath zipPath = apkPathManager.getApkPath(moduleSplit);
    assertThat(zipPath.getFileName().toString()).isEqualTo("standalone-arm64_v8a_hdpi.apk");
  }

  private static ModuleSplit.Builder createModuleSplit() {
    return ModuleSplit.builder()
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
        .setModuleName(BundleModuleName.create("base"))
        .setMasterSplit(false)
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(VariantTargeting.getDefaultInstance());
  }
}
