/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Config.Compression;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApkSetBuilderTest {

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tempFolder;
  private ApkSetBuilder apkSetBuilder; // object under test.

  @Before
  public void setUp() throws Exception {
    tempFolder = tmp.getRoot().toPath();
    apkSetBuilder =
        ApkSetBuilderFactory.createApkSetBuilder(
            new SplitApkSerializer(
                new ApkPathManager(),
                aapt2Command,
                Optional.empty(),
                Compression.getDefaultInstance()),
            new StandaloneApkSerializer(
                new ApkPathManager(),
                aapt2Command,
                Optional.empty(),
                Compression.getDefaultInstance()),
            tempFolder);
  }

  @Test
  public void addsSplitApk() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit testSplit = ModuleSplit.forModule(testModule);
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addSplitApk(testSplit);
    File apkSetBuilderLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetBuilderLocation.toPath());

    assertThat(apkSetBuilderLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetBuilderLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasSplitApkMetadata()).isTrue();
    assertThat(apkDescription.getSplitApkMetadata().getSplitId()).isEqualTo("testModule");
    assertThat(apkDescription.getSplitApkMetadata().getIsMasterSplit()).isTrue();
  }

  @Test
  public void addSplitApk_masterSplitFlagPropagated() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule).toBuilder().setMasterSplit(false).build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addSplitApk(testSplit);
    File apkSetLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetLocation.toPath());

    assertThat(apkSetLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());

    assertThat(apkDescription.hasSplitApkMetadata()).isTrue();
    assertThat(apkDescription.getSplitApkMetadata().getIsMasterSplit()).isFalse();
  }

  @Test
  public void addSplitApk_targetingPropagated() throws Exception {
    ApkTargeting customTargeting = apkMinSdkTargeting(5);
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule).toBuilder().setApkTargeting(customTargeting).build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addSplitApk(testSplit);
    File apkSetLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetLocation.toPath());

    assertThat(apkSetLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());

    assertThat(apkDescription.getTargeting()).isEqualTo(customTargeting);
  }

  @Test
  public void addStandaloneApk() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule).toBuilder().setSplitType(SplitType.STANDALONE).build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addStandaloneApk(testSplit);
    File apkSetLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetLocation.toPath());

    assertThat(apkSetLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());
    assertThat(apkDescription.getPath()).isEqualTo("standalones/standalone.apk");

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasStandaloneApkMetadata()).isTrue();
  }

  @Test
  public void addStandaloneApk_targetingPropagated() throws Exception {
    ApkTargeting customTargeting = apkMinSdkTargeting(5);
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule)
            .toBuilder()
            .setApkTargeting(customTargeting)
            .setSplitType(SplitType.STANDALONE)
            .build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addStandaloneApk(testSplit);
    File apkSetLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetLocation.toPath());

    assertThat(apkSetLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());
    assertThat(apkDescription.getPath()).matches("^standalone.*\\.apk$");

    assertThat(apkDescription.hasStandaloneApkMetadata()).isTrue();
    assertThat(apkDescription.getTargeting()).isEqualTo(customTargeting);
  }

  @Test
  public void addStandaloneUniversalApk() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule).toBuilder().setSplitType(SplitType.STANDALONE).build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addStandaloneUniversalApk(testSplit);
    File apkSetLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetLocation.toPath());

    assertThat(apkSetLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());
    assertThat(apkDescription.getPath()).isEqualTo("universal.apk");

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasStandaloneApkMetadata()).isTrue();
  }

  @Test
  public void addsInstantApk() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withInstant(true)))
            .build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule).toBuilder().setSplitType(SplitType.INSTANT).build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addInstantApk(testSplit);
    File apkSetBuilderLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetBuilderLocation.toPath());

    assertThat(apkSetBuilderLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetBuilderLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());
    assertThat(apkDescription.getPath()).matches("^instant.*\\.apk$");

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasInstantApkMetadata()).isTrue();
    assertThat(apkDescription.getInstantApkMetadata().getSplitId()).isEqualTo("testModule");
    assertThat(apkDescription.getInstantApkMetadata().getIsMasterSplit()).isTrue();
  }

  @Test
  public void addInstantApk_masterSplitFlagPropagated() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withInstant(true)))
            .build();
    ModuleSplit testSplit =
        ModuleSplit.forModule(testModule)
            .toBuilder()
            .setMasterSplit(false)
            .setSplitType(SplitType.INSTANT)
            .build();
    testSplit = testSplit.writeSplitIdInManifest(testSplit.getSuffix());

    ApkDescription apkDescription = apkSetBuilder.addInstantApk(testSplit);
    File apkSetLocation = tempFolder.resolve("test.apks").toFile();
    apkSetBuilder.writeTo(apkSetLocation.toPath());

    assertThat(apkSetLocation.exists()).isTrue();
    ZipFile apkSetFile = new ZipFile(apkSetLocation);
    assertThat(apkSetFile).hasFile(apkDescription.getPath());
    assertThat(apkDescription.getPath()).matches("^instant.*\\.apk$");

    assertThat(apkDescription.hasInstantApkMetadata()).isTrue();
    assertThat(apkDescription.getInstantApkMetadata().getIsMasterSplit()).isFalse();
  }
}
