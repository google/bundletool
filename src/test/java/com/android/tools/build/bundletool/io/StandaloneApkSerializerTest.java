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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusedModuleNames;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Config.Compression;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StandaloneApkSerializerTest {

  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
  private Path tempFolder;

  @Before
  public void setUp() throws Exception {
    tempFolder = tempFolderRule.getRoot().toPath();
  }

  @Test
  public void writeToDisk_basicApk() throws Exception {
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            new ApkPathManager(), aapt2Command, Optional.empty(), Compression.getDefaultInstance());

    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = createStandaloneApkBuilder(module).build();

    ApkDescription apkDescription = standaloneApkSerializer.writeToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(apkDescription.getPath()).isEqualTo("standalones/standalone.apk");

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasStandaloneApkMetadata()).isTrue();
  }

  @Test
  public void writeToDiskAsUniversal_basicApk() throws Exception {
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            new ApkPathManager(), aapt2Command, Optional.empty(), Compression.getDefaultInstance());

    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = createStandaloneApkBuilder(module).build();

    ApkDescription apkDescription =
        standaloneApkSerializer.writeToDiskAsUniversal(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(apkDescription.getPath()).isEqualTo("universal.apk");

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasStandaloneApkMetadata()).isTrue();
  }

  @Test
  public void writeToDiskInternal_targetingPropagated() throws Exception {
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            new ApkPathManager(), aapt2Command, Optional.empty(), Compression.getDefaultInstance());

    ApkTargeting customTargeting = apkMinSdkTargeting(5);
    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = createStandaloneApkBuilder(module).setApkTargeting(customTargeting).build();

    ApkDescription apkDescription =
        standaloneApkSerializer.writeToDiskInternal(split, tempFolder, ZipPath.create("file.apk"));

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();

    assertThat(apkDescription.getTargeting()).isEqualTo(customTargeting);
  }

  @Test
  public void writeToDiskInternal_fusedModuleNamePropagated() throws Exception {
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            new ApkPathManager(), aapt2Command, Optional.empty(), Compression.getDefaultInstance());

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withFusedModuleNames("a,b,c")))
            .build();
    ModuleSplit split = createStandaloneApkBuilder(module).build();

    ApkDescription apkDescription =
        standaloneApkSerializer.writeToDiskInternal(split, tempFolder, ZipPath.create("file.apk"));

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();

    assertThat(apkDescription.hasStandaloneApkMetadata()).isTrue();
    assertThat(apkDescription.getStandaloneApkMetadata().getFusedModuleNameList())
        .containsExactly("a", "b", "c");
  }

  private ModuleSplit.Builder createStandaloneApkBuilder(BundleModule module) {
    return ModuleSplit.forModule(module)
        .toBuilder()
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .setSplitType(SplitType.STANDALONE);
  }
}
