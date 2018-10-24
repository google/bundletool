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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withExtractNativeLibs;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstant;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMinSdkTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Config.Compression;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
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
public class SplitApkSerializerTest {

  @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();
  private Path tempFolder;

  @Before
  public void setUp() throws Exception {
    tempFolder = tempFolderRule.getRoot().toPath();
  }

  @Test
  public void writeBasicSplitApk() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = ModuleSplit.forModule(module);
    split = split.writeSplitIdInManifest(split.getSuffix());

    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasSplitApkMetadata()).isTrue();
    assertThat(apkDescription.getSplitApkMetadata().getSplitId()).isEqualTo("testModule");
    assertThat(apkDescription.getSplitApkMetadata().getIsMasterSplit()).isTrue();
  }

  @Test
  public void writeSplitApk_toSubDirectory() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = ModuleSplit.forModule(module);

    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();

    ZipPath apkRelPath = ZipPath.create(apkDescription.getPath());
    assertThat(apkRelPath.getNameCount()).isEqualTo(2);
    assertThat(apkRelPath.getParent().toString()).isEqualTo("splits");
  }

  @Test
  public void writeBasicInstantSplitApk() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withInstant(true)))
            .build();
    ModuleSplit split =
        ModuleSplit.forModule(module).toBuilder().setSplitType(SplitType.INSTANT).build();
    split = split.writeSplitIdInManifest(split.getSuffix());

    ApkDescription apkDescription = splitApkSerializer.writeInstantSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString())
        .isEqualTo("instant-testModule-master.apk");

    assertThat(apkDescription.getTargeting()).isEqualToDefaultInstance();
    assertThat(apkDescription.hasInstantApkMetadata()).isTrue();
    assertThat(apkDescription.getInstantApkMetadata().getSplitId()).isEqualTo("testModule");
    assertThat(apkDescription.getInstantApkMetadata().getIsMasterSplit()).isTrue();
  }

  @Test
  public void writeInstantSplitApk_toSubDirectory() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withInstant(true)))
            .build();
    ModuleSplit split =
        ModuleSplit.forModule(module).toBuilder().setSplitType(SplitType.INSTANT).build();

    ApkDescription apkDescription = splitApkSerializer.writeInstantSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();

    ZipPath apkRelPath = ZipPath.create(apkDescription.getPath());
    assertThat(apkRelPath.getNameCount()).isEqualTo(2);
    assertThat(apkRelPath.getParent().toString()).isEqualTo("instant");
  }

  @Test
  public void featureSplitFileName_master() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = ModuleSplit.forModule(module);
    split = split.writeSplitIdInManifest(split.getSuffix());
    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString())
        .isEqualTo("testModule-master.apk");
  }

  @Test
  public void featureSplitFileName_base_master() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = ModuleSplit.forModule(module);
    split = split.writeSplitIdInManifest(split.getSuffix());
    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString()).isEqualTo("base-master.apk");
  }

  @Test
  public void featureSplitFileName_base_multipleBaseSplits() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = ModuleSplit.forModule(module);
    split = split.writeSplitIdInManifest(split.getSuffix());
    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);
    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString()).isEqualTo("base-master.apk");

    BundleModule module1 =
        new BundleModuleBuilder("base")
            .setManifest(androidManifest("com.test.app", withExtractNativeLibs(true)))
            .build();
    ModuleSplit split1 = ModuleSplit.forModule(module1);
    split1 = split1.writeSplitIdInManifest(split1.getSuffix());
    ApkDescription apkDescription1 = splitApkSerializer.writeSplitToDisk(split1, tempFolder);
    File expectedApkFile1 = tempFolder.resolve(apkDescription1.getPath()).toFile();
    assertThat(expectedApkFile1.exists()).isTrue();
    assertThat(expectedApkFile1.toPath().getFileName().toString()).isEqualTo("base-master_2.apk");
  }

  @Test
  public void featureSplitFileName_base_configSplit() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split =
        createModuleSplitBuilder(module).setApkTargeting(apkAbiTargeting(AbiAlias.X86)).build();
    split = split.writeSplitIdInManifest(split.getSuffix());
    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString()).isEqualTo("base-x86.apk");
  }

  @Test
  public void featureSplitFileName_module_configSplit() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("feature1").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split =
        createModuleSplitBuilder(module).setApkTargeting(apkAbiTargeting(AbiAlias.MIPS)).build();
    split = split.writeSplitIdInManifest(split.getSuffix());
    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString()).isEqualTo("feature1-mips.apk");
  }

  @Test
  public void featureSplitFileName_module_multipleConfigSplitsSameSplitId() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("feature1").setManifest(androidManifest("com.test.app")).build();

    ModuleSplit split =
        createModuleSplitBuilder(module).setApkTargeting(apkAbiTargeting(AbiAlias.MIPS)).build();
    split = split.writeSplitIdInManifest(split.getSuffix());
    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);
    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(expectedApkFile.toPath().getFileName().toString()).isEqualTo("feature1-mips.apk");

    ModuleSplit split1 =
        createModuleSplitBuilder(module).setApkTargeting(apkAbiTargeting(AbiAlias.MIPS)).build();

    split1 = split1.writeSplitIdInManifest(split1.getSuffix());
    ApkDescription apkDescription1 = splitApkSerializer.writeSplitToDisk(split1, tempFolder);
    File expectedApkFile1 = tempFolder.resolve(apkDescription1.getPath()).toFile();
    assertThat(expectedApkFile1.exists()).isTrue();
    assertThat(expectedApkFile1.toPath().getFileName().toString()).isEqualTo("feature1-mips_2.apk");
  }

  @Test
  public void masterSplitFlagIsPropagated() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split = createModuleSplitBuilder(module).build();
    split = split.writeSplitIdInManifest(split.getSuffix());

    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();

    assertThat(apkDescription.hasSplitApkMetadata()).isTrue();
    assertThat(apkDescription.getSplitApkMetadata().getIsMasterSplit()).isFalse();
  }

  @Test
  public void targetingIsPropagated() throws Exception {
    SplitApkSerializer splitApkSerializer = createSplitApkSerializer();

    ApkTargeting customTargeting = apkMinSdkTargeting(5);
    BundleModule module =
        new BundleModuleBuilder("testModule").setManifest(androidManifest("com.test.app")).build();
    ModuleSplit split =
        ModuleSplit.forModule(module).toBuilder().setApkTargeting(customTargeting).build();
    split = split.writeSplitIdInManifest(split.getSuffix());

    ApkDescription apkDescription = splitApkSerializer.writeSplitToDisk(split, tempFolder);

    File expectedApkFile = tempFolder.resolve(apkDescription.getPath()).toFile();
    assertThat(expectedApkFile.exists()).isTrue();
    assertThat(apkDescription.getTargeting()).isEqualTo(customTargeting);
  }

  private SplitApkSerializer createSplitApkSerializer() {
    return new SplitApkSerializer(
        new ApkPathManager(), aapt2Command, Optional.empty(), Compression.getDefaultInstance());
  }

  private static ModuleSplit.Builder createModuleSplitBuilder(BundleModule module) {
    return ModuleSplit.forModule(module).toBuilder().setMasterSplit(false);
  }
}
