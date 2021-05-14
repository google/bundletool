/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.CheckTransparencyCommand.Mode;
import com.android.tools.build.bundletool.io.ApkSerializerHelper;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.io.ByteSource;
import com.google.protobuf.ByteString;
import dagger.Component;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ApkTransparencyCheckerTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Inject ApkSerializerHelper apkSerializerHelper;

  private Path tmpDir;
  private Path zipOfApksPath;

  @Before
  public void setUp() {
    TestComponent.useTestModule(this, TestModule.builder().build());
    tmpDir = tmp.getRoot().toPath();
    zipOfApksPath = tmpDir.resolve("apks.zip");
  }

  @Test
  public void emptyZip() throws Exception {
    new ZipBuilder().writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () -> ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "The provided .zip file must either contain a single APK, or, if multiple APK files"
                + " are present, a base APK.");
  }

  @Test
  public void noApkFoundInZip() throws Exception {
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileFromDisk(
                ZipPath.create("some-text-file.txt"),
                Files.createFile(tmpDir.resolve("some-text-file.txt")).toFile());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () -> ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "The provided .zip file must either contain a single APK, or, if multiple APK files"
                + " are present, a base APK.");
  }

  @Test
  public void singleApkInZip_noTransparencyFile() throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    ModuleSplit split = createModuleSplit();
    apkSerializerHelper.writeToZipFile(split, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () -> ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Could not verify code transparency because transparency file is not present in the"
                + " APK.");
  }

  @Test
  public void singleApkInZip_withTransparencyFile() throws Exception {
    Path apkPath = tmpDir.resolve("universal.apk");
    ModuleSplit split =
        createModuleSplit(
            /* transparencySignedFileContent= */ Optional.of(ByteSource.wrap(new byte[100])));
    apkSerializerHelper.writeToZipFile(split, apkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("universal.apk"),
                ByteString.readFrom(Files.newInputStream(apkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream));
  }

  @Test
  public void multipleApksInZip_noBaseApk() throws Exception {
    Path splitApkPath1 = tmpDir.resolve("split1.apk");
    Path splitApkPath2 = tmpDir.resolve("split2.apk");
    ModuleSplit split1 = createModuleSplit();
    ModuleSplit split2 = createModuleSplit();
    apkSerializerHelper.writeToZipFile(split1, splitApkPath1);
    apkSerializerHelper.writeToZipFile(split2, splitApkPath2);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("split1.apk"),
                ByteString.readFrom(Files.newInputStream(splitApkPath1)).toByteArray())
            .addFileWithContent(
                ZipPath.create("split2.apk"),
                ByteString.readFrom(Files.newInputStream(splitApkPath2)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () -> ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "The provided .zip file must either contain a single APK, or, if multiple APK"
                + " files are present, a base APK.");
  }

  @Test
  public void multipleApksInZip_withBaseApk_noTransparencyFile() throws Exception {
    Path baseApkPath = tmpDir.resolve("base.apk");
    Path splitApkPath = tmpDir.resolve("split.apk");
    ModuleSplit base = createModuleSplit();
    ModuleSplit split = createModuleSplit();
    apkSerializerHelper.writeToZipFile(base, baseApkPath);
    apkSerializerHelper.writeToZipFile(split, splitApkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("base.apk"),
                ByteString.readFrom(Files.newInputStream(baseApkPath)).toByteArray())
            .addFileWithContent(
                ZipPath.create("split.apk"),
                ByteString.readFrom(Files.newInputStream(splitApkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () -> ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream)));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Could not verify code transparency because transparency file is not present in the"
                + " APK.");
  }

  @Test
  public void multipleApksInZip_withBaseApk_withTransparencyFile() throws Exception {
    Path baseApkPath = tmpDir.resolve("base.apk");
    Path splitApkPath = tmpDir.resolve("split.apk");
    ModuleSplit base =
        createModuleSplit(
            /* transparencySignedFileContent= */ Optional.of(ByteSource.wrap(new byte[100])));
    ModuleSplit split = createModuleSplit();
    apkSerializerHelper.writeToZipFile(base, baseApkPath);
    apkSerializerHelper.writeToZipFile(split, splitApkPath);
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(
                ZipPath.create("base.apk"),
                ByteString.readFrom(Files.newInputStream(baseApkPath)).toByteArray())
            .addFileWithContent(
                ZipPath.create("split.apk"),
                ByteString.readFrom(Files.newInputStream(splitApkPath)).toByteArray());
    zipBuilder.writeTo(zipOfApksPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CheckTransparencyCommand command =
        CheckTransparencyCommand.builder().setMode(Mode.APK).setApkZipPath(zipOfApksPath).build();

    ApkTransparencyChecker.checkTransparency(command, new PrintStream(outputStream));
  }

  private static ModuleSplit createModuleSplit() {
    return createModuleSplit(/* transparencySignedFileContent= */ Optional.empty());
  }

  private static ModuleSplit createModuleSplit(Optional<ByteSource> transparencySignedFileContent) {
    ModuleSplit.Builder moduleSplitBuilder =
        ModuleSplit.builder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setApkTargeting(ApkTargeting.getDefaultInstance())
            .setVariantTargeting(VariantTargeting.getDefaultInstance())
            .setMasterSplit(true);

    transparencySignedFileContent.ifPresent(
        transparencyFile ->
            moduleSplitBuilder.addEntry(
                ModuleEntry.builder()
                    .setContent(transparencyFile)
                    .setPath(
                        ZipPath.create("META-INF")
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                    .build()));

    return moduleSplitBuilder.build();
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {

    void inject(ApkTransparencyCheckerTest test);

    static void useTestModule(ApkTransparencyCheckerTest testInstance, TestModule testModule) {
      DaggerApkTransparencyCheckerTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
