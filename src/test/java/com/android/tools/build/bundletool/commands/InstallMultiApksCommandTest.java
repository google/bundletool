/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.apexVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.apkDescriptionStream;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.DeviceFactory.qDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.TimeoutException;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.IncompatibleDeviceException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ParseException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Int32Value;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InstallMultiApksCommandTest {
  private static final Integer PARENT_SESSION_ID = 1111111;
  private static final String DEVICE_ID = "id1";
  private static final String PKG_NAME_1 = "com.example.a";
  private static final String PKG_NAME_2 = "com.example.b";

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private SystemEnvironmentProvider systemEnvironmentProvider;
  private Path adbPath;
  private Path sdkDirPath;
  private FakeDevice device;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    sdkDirPath = Files.createDirectory(tmpDir.resolve("android-sdk"));
    adbPath = sdkDirPath.resolve("platform-tools").resolve("adb");
    Files.createDirectories(adbPath.getParent());
    Files.createFile(adbPath);
    adbPath.toFile().setExecutable(true);
    systemEnvironmentProvider =
        new FakeSystemEnvironmentProvider(
            ImmutableMap.of(ANDROID_HOME, sdkDirPath.toString(), ANDROID_SERIAL, DEVICE_ID));
    device = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, qDeviceWithLocales("en-US"));
  }

  @Test
  public void fromFlags_matchBuilder_apksZip() {
    Path zipFile = tmpDir.resolve("container.zip");

    InstallMultiApksCommand fromFlags =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipFile),
            systemEnvironmentProvider,
            fakeServerOneDevice(qDeviceWithLocales("en-US")));

    InstallMultiApksCommand fromBuilder =
        InstallMultiApksCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setApksArchiveZipPath(zipFile)
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void execute_badAdbPath() {
    Path zipFile = tmpDir.resolve("container.zip");

    InstallMultiApksCommand fromFlags =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--adb=foo", "--apks-zip=" + zipFile),
            systemEnvironmentProvider,
            fakeServerOneDevice(qDeviceWithLocales("en-US")));

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, fromFlags::execute);
    assertThat(e).hasMessageThat().contains("was not found");
  }

  @Test
  public void fromFlags_enableRollback() {
    Path zipFile = tmpDir.resolve("container.zip");

    InstallMultiApksCommand fromFlags =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--enable-rollback", "--apks-zip=" + zipFile),
            systemEnvironmentProvider,
            fakeServerOneDevice(qDeviceWithLocales("en-US")));

    assertThat(fromFlags.getEnableRollback()).isTrue();
  }

  @Test
  public void fromFlags_deviceId() {
    Path zipFile = tmpDir.resolve("container.zip");

    InstallMultiApksCommand fromFlags =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--device-id=" + DEVICE_ID, "--apks-zip=" + zipFile),
            new FakeSystemEnvironmentProvider(
                ImmutableMap.of(
                    ANDROID_HOME, sdkDirPath.toString(), ANDROID_SERIAL, "other-device-id")),
            fakeServerOneDevice(qDeviceWithLocales("en-US")));

    assertThat(fromFlags.getDeviceId()).hasValue(DEVICE_ID);
  }

  @Test
  public void fromFlags_apks() throws Exception {
    Path apkFile1 = tmpDir.resolve("file1.apks");
    Path apkFile2 = tmpDir.resolve("file2.apks");
    Files.createFile(apkFile1);
    Files.createFile(apkFile2);

    InstallMultiApksCommand fromFlags =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apkFile1 + "," + apkFile2),
            systemEnvironmentProvider,
            fakeServerOneDevice(qDeviceWithLocales("en-US")));

    assertThat(fromFlags.getApksArchivePaths()).containsExactly(apkFile1, apkFile2);
  }

  @Test
  public void fromFlags_exclusiveApksOptions() {
    Path apkDir = tmpDir.resolve("apk_dir");
    Path apkFile1 = apkDir.resolve("file1.apks");
    Path apkFile2 = apkDir.resolve("file2.apks");
    Path zipFile = tmpDir.resolve("container.zip");

    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                InstallMultiApksCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            String.format("--apks=%s,%s", apkFile1, apkFile2),
                            "--apks-zip=" + zipFile),
                    systemEnvironmentProvider,
                    fakeServerOneDevice(qDeviceWithLocales("en-US"))));
    assertThat(e).hasMessageThat().contains("Exactly one of");
  }

  @Test
  public void fromFlags_missingApksOption() {
    CommandExecutionException e =
        assertThrows(
            CommandExecutionException.class,
            () ->
                InstallMultiApksCommand.fromFlags(
                    new FlagParser().parse(),
                    systemEnvironmentProvider,
                    fakeServerOneDevice(qDeviceWithLocales("en-US"))));
    assertThat(e).hasMessageThat().contains("Exactly one of");
  }

  @Test
  public void execute_extractZip() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    // EXPECT
    // 1) parent session creation
    device.injectShellCommandOutput(
        "pm install-create --multi-package --staged", () -> "Success: blah blah [1111111]");
    // 2) child session creation
    AtomicInteger childSessionCounter = new AtomicInteger();
    device.injectShellCommandOutput(
        "pm install-create --staged",
        () -> "Success: blah blah [" + childSessionCounter.getAndIncrement() + "]");
    // 3) apk writes
    device.injectShellCommandOutput(
        String.format("pm install-write 0 com.example.a_0 %s", pushedFileName("base-master.apk")),
        () -> "Success");
    device.injectShellCommandOutput(
        String.format(
            "pm install-write 0 com.example.a_1 %s", pushedFileName("feature1-master.apk")),
        () -> "Success");
    device.injectShellCommandOutput(
        String.format(
            "pm install-write 0 com.example.a_2 %s", pushedFileName("feature2-master.apk")),
        () -> "Success");
    device.injectShellCommandOutput(
        String.format("pm install-write 1 com.example.b_0 %s", pushedFileName("base-master.apk")),
        () -> "Success");
    device.injectShellCommandOutput(
        String.format(
            "pm install-write 1 com.example.b_1 %s", pushedFileName("feature1-master.apk")),
        () -> "Success");
    device.injectShellCommandOutput(
        String.format(
            "pm install-write 1 com.example.b_2 %s", pushedFileName("feature2-master.apk")),
        () -> "Success");
    // 4) Adding child to parent
    device.injectShellCommandOutput("pm install-add-session 1111111 0 1", () -> "Success");
    // 5) Commit.
    device.injectShellCommandOutput("pm install-commit 1111111", () -> "Success");
    command.execute();
  }

  @Test
  public void execute_ignoreEmptyCommandResponseLines() throws Exception {
    // GIVEN a zip file containing fake .apks files for multiple packages.
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    // GIVEN the success message on the parent session includes empty lines.
    device.injectShellCommandOutput(
        "pm install-create --multi-package --staged", () -> "Success: blah blah [1111111]\n\n");

    // EXPECT processing to continue normally.
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContent1);
    givenInstallWrites(device, 1, PKG_NAME_2, tableOfContent2);
    givenInstallAddAndCommit(device, ImmutableList.of(0, 1));
    command.execute();
  }

  @Test
  public void execute_updateOnly() throws Exception {
    // GIVEN a zip file containing fake .apks files for multiple packages.
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    // GIVEN only one of the packages is installed on the device...
    device.injectShellCommandOutput(
        "pm list packages", () -> String.format("package:%s\njunk_to_ignore", PKG_NAME_1));

    // GIVEN the --update-only flag is set on the command...
    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle, "--update-only"),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallAddAndCommit(device, ImmutableList.of(0));

    // EXPECT only one of the packages
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContent1);
    command.execute();
  }

  @Test
  public void execute_installNewPackages() throws Exception {
    // GIVEN a zip file containing fake .apks files for multiple packages.
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    // GIVEN the --update-only flag is not set.
    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallAddAndCommit(device, ImmutableList.of(0, 1));

    // EXPECT
    // both packages to be installed.
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContent1);
    givenInstallWrites(device, 1, PKG_NAME_2, tableOfContent2);

    // ACT
    // Make sure the package list was not retrieved since the --update-only flag was not set.
    device.injectShellCommandOutput(
        "pm list packages",
        () -> {
          throw new IllegalStateException("Package list should not be called");
        });

    command.execute();
  }

  @Test
  public void execute_noCommit() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    // GIVEN a command with --no-commit
    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle, "--no-commit"),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContent1);
    givenInstallWrites(device, 1, PKG_NAME_2, tableOfContent2);
    device.injectShellCommandOutput("pm install-add-session 1111111 0 1", () -> "Success");

    // EXPECT
    // Abandon session instead of committing, even though all prior commands succeeded.
    device.injectShellCommandOutput("pm install-abandon 1111111", () -> "Success");
    command.execute();
  }

  @Test
  public void execute_gracefulExitIfNoPackagesFound() throws Exception {
    // GIVEN a zip file containing fake .apks files for multiple packages.
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    // GIVEN only no packages are installed on the device...
    device.injectShellCommandOutput("pm list packages", () -> "");

    // GIVEN the --update-only flag is used to restrict to previously installed packages.
    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle, "--update-only"),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    // EXPECT no further commands to be executed.
    command.execute();
  }

  @Test
  public void execute_skipUnsupportedSdks() throws Exception {
    // GIVEN a .apks containing containing only targets that are greater than the device SDK...
    BuildApksResult apexTableOfContents =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME_1)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                apexVariant(
                    VariantTargeting.newBuilder()
                        .setSdkVersionTargeting(
                            SdkVersionTargeting.newBuilder()
                                .addValue(
                                    SdkVersion.newBuilder()
                                        .setMin(Int32Value.of(ANDROID_Q_API_VERSION + 3)))
                                .build())
                        .build(),
                    ApkTargeting.getDefaultInstance(),
                    ZipPath.create("base.apex")))
            .build();
    Path package1Apks = createApksArchiveFile(apexTableOfContents, tmpDir.resolve("package1.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + package1Apks),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    // THEN the command executes without triggering any shell commands.
    command.execute();
  }

  @Test
  public void execute_processApex() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult apexTableOfContents =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME_2)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                apexVariant(
                    VariantTargeting.getDefaultInstance(),
                    ApkTargeting.getDefaultInstance(),
                    ZipPath.create("base.apex")))
            .build();
    Path package2Apks = createApksArchiveFile(apexTableOfContents, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks-zip=" + zipBundle),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContent1);
    givenInstallAddAndCommit(device, ImmutableList.of(0, 1));

    // EXPECT
    // Apex session and write
    device.injectShellCommandOutput(
        "pm install-create --staged --apex", () -> "Success: blah blah [1]");
    device.injectShellCommandOutput(
        String.format("pm install-write 1 com.example.b_0 %s", pushedFileName("base.apex")),
        () -> "Success");
    command.execute();
  }

  @Test
  public void execute_packageNameMissing_fallBackOnAapt2() throws Exception {
    // GIVEN a fake .apks file without a package name.
    BuildApksResult tableOfContents1 =
        fakeTableOfContents(PKG_NAME_1).toBuilder().clearPackageName().build();
    Path apksPath = createApksArchiveFile(tableOfContents1, tmpDir.resolve("package1.apks"));

    // GIVEN a command with a fake aapt2 command...
    Aapt2Command aapt2Command =
        createFakeAapt2Command(
            () ->
                ImmutableList.of(
                    String.format(
                        "package: name='%s' versionCode='292200000' versionName=''"
                            + " platformBuildVersionName='' platformBuildVersionCode=''"
                            + " compileSdkVersion='29' compileSdkVersionCodename='10'",
                        PKG_NAME_1),
                    "application: label='' icon=''",
                    "sdkVersion:'29'",
                    "maxSdkVersion:'29'",
                    ""));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .addApksArchivePath(apksPath)
            .setAapt2Command(aapt2Command)
            .build();

    // EXPECT the command to execute successfully.
    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContents1);
    givenInstallAddAndCommit(device, ImmutableList.of(0));
    command.execute();
  }

  @Test
  public void execute_packageNameMissing_aapt2Failure() throws Exception {
    // GIVEN a fake .apks file without a package name, and another with a package name...
    BuildApksResult tableOfContents1 =
        fakeTableOfContents(PKG_NAME_1).toBuilder().clearPackageName().build();
    Path apksPath1 = createApksArchiveFile(tableOfContents1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContents2 = fakeTableOfContents(PKG_NAME_2);
    Path apksPath2 = createApksArchiveFile(tableOfContents2, tmpDir.resolve("package2.apks"));

    // GIVEN a command with a fake aapt2 command that will fail with an IncompatibleDeviceException.
    Aapt2Command aapt2Command =
        createFakeAapt2Command(
            () -> {
              throw new IncompatibleDeviceException("Invalid device");
            });

    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchivePaths(ImmutableList.of(apksPath1, apksPath2))
            .setAapt2Command(aapt2Command)
            .build();

    // EXPECT the command to skip the incompatible package.
    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_2, tableOfContents2);
    givenInstallAddAndCommit(device, ImmutableList.of(0));
    command.execute();
  }

  @Test
  public void execute_enableRollback() throws Exception {
    // GIVEN a fake .apks file with an .apex file.
    BuildApksResult apexTableOfContents =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME_1)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                apexVariant(
                    VariantTargeting.getDefaultInstance(),
                    ApkTargeting.getDefaultInstance(),
                    ZipPath.create("base.apex")))
            .build();
    Path apksPath = createApksArchiveFile(apexTableOfContents, tmpDir.resolve("package1.apks"));

    // GIVEN an install command with the --enable-rollback flag...
    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksPath, "--enable-rollback"),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenInstallWrites(device, 0, PKG_NAME_1, ImmutableList.of("base.apex"));
    givenInstallAddAndCommit(device, ImmutableList.of(0));

    // EXPECT
    // 1) parent session creation with rollback
    device.injectShellCommandOutput(
        "pm install-create --multi-package --staged --enable-rollback",
        () -> "Success: blah blah [1111111]");
    // 2) child session creation with rollback
    device.injectShellCommandOutput(
        "pm install-create --staged --enable-rollback --apex", () -> "Success: blah blah [0]");

    command.execute();
  }

  @Test
  public void execute_apkList_handleFailureException() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContent2 = fakeTableOfContents(PKG_NAME_2);
    Path package2Apks = createApksArchiveFile(tableOfContent2, tmpDir.resolve("package2.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse(String.format("--apks=%s,%s", package1Apks, package2Apks)),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_1, ImmutableList.of("base-master.apk"));

    // ACT
    // Simulate a timeout exception.
    device.injectShellCommandOutput(
        String.format(
            "pm install-write 0 com.example.a_1 %s", pushedFileName("feature1-master.apk")),
        () -> {
          throw new TimeoutException("Timeout");
        });

    // EXPECT
    // Abandon the parent session.
    device.injectShellCommandOutput("pm install-abandon 1111111", () -> "Success");
    CommandExecutionException e = assertThrows(CommandExecutionException.class, command::execute);
    assertThat(e).hasMessageThat().contains("Timeout");
    assertThat(e).hasCauseThat().isInstanceOf(TimeoutException.class);
  }

  @Test
  public void execute_apkList_handleMalformedSuccess() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + package1Apks),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);

    // ACT
    // Simulate a *malformed* child session creation
    device.injectShellCommandOutput("pm install-create --staged", () -> "Success: blah blah");

    // EXPECT
    // Abandon the parent session.
    device.injectShellCommandOutput("pm install-abandon 1111111", () -> "Success");
    ParseException e = assertThrows(ParseException.class, command::execute);
    assertThat(e).hasMessageThat().contains("failed to parse session id from output");
  }

  @Test
  public void execute_apkList_handleSessionFailure() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + package1Apks),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);

    // ACT
    // Simulate a *failed* child session creation
    device.injectShellCommandOutput("pm install-create --staged", () -> "Failed");

    // EXPECT
    // Abandon the parent session
    device.injectShellCommandOutput("pm install-abandon 1111111", () -> "Success");
    CommandExecutionException e = assertThrows(CommandExecutionException.class, command::execute);
    assertThat(e).hasMessageThat().contains("adb failed: pm install-create --staged");
  }

  @Test
  public void execute_apkList_handleAddSessionFailure() throws Exception {
    // GIVEN a zip file containing fake .apks files
    BuildApksResult tableOfContent1 = fakeTableOfContents(PKG_NAME_1);
    Path package1Apks = createApksArchiveFile(tableOfContent1, tmpDir.resolve("package1.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + package1Apks),
            systemEnvironmentProvider,
            fakeServerOneDevice(device));

    givenParentSessionCreation(device);
    givenChildSessionCreate(device);
    givenInstallWrites(device, 0, PKG_NAME_1, tableOfContent1);

    // ACT
    // Simulate *fail* on add session
    device.injectShellCommandOutput("pm install-add-session 1111111 0", () -> "Failure");

    // EXPECT
    // Abandon the parent session
    device.injectShellCommandOutput("pm install-abandon 1111111", () -> "Success");
    CommandExecutionException e = assertThrows(CommandExecutionException.class, command::execute);
    assertThat(e).hasMessageThat().contains("install-add-session");
  }

  private static BuildApksResult fakeTableOfContents(String packageName) {
    return BuildApksResult.newBuilder()
        .setPackageName(packageName)
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
        .addVariant(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                createSplitApkSet(
                    "base",
                    createMasterApkDescription(
                        ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
                createSplitApkSet(
                    "feature1",
                    createMasterApkDescription(
                        ApkTargeting.getDefaultInstance(), ZipPath.create("feature1-master.apk"))),
                createSplitApkSet(
                    "feature2",
                    createMasterApkDescription(
                        ApkTargeting.getDefaultInstance(), ZipPath.create("feature2-master.apk")))))
        .build();
  }

  private static String pushedFileName(String fileName) {
    return Paths.get("/temp", fileName).toAbsolutePath().toString();
  }

  private static AdbServer fakeServerOneDevice(DeviceSpec deviceSpec) {
    return new FakeAdbServer(
        /* hasInitialDeviceList= */ true,
        ImmutableList.of(FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec)));
  }

  private static AdbServer fakeServerOneDevice(Device device) {
    return new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(device));
  }

  // Utility methods for populating shell commands
  //
  // NOTE: these methods replicate the logic under test, and therefore should be used with
  // caution. Expected changes per unit test should be injected manually.

  private static void givenParentSessionCreation(FakeDevice device) {
    device.injectShellCommandOutput(
        "pm install-create --multi-package --staged",
        () -> String.format("Success: blah blah [%d]", PARENT_SESSION_ID));
  }

  private static void givenInstallWrites(
      FakeDevice device, int sessionId, String packageName, BuildApksResult toc) {
    ImmutableList<String> fileNames =
        apkDescriptionStream(toc)
            .map(ApkDescription::getPath)
            .map(Paths::get)
            .map(Path::getFileName)
            .map(Path::toString)
            .collect(toImmutableList());
    givenInstallWrites(device, sessionId, packageName, fileNames);
  }

  private static void givenInstallWrites(
      FakeDevice device, int sessionId, String packageName, ImmutableList<String> fileNames) {
    for (int i = 0; i < fileNames.size(); i++) {
      device.injectShellCommandOutput(
          String.format(
              "pm install-write %d %s_%d %s",
              sessionId, packageName, i, pushedFileName(fileNames.get(i))),
          () -> "Success");
    }
  }

  private static void givenInstallAddAndCommit(
      FakeDevice device, ImmutableList<Integer> childSessionIds) {
    device.injectShellCommandOutput(
        String.format(
            "pm install-add-session %d %s",
            PARENT_SESSION_ID,
            childSessionIds.stream().map(i -> Integer.toString(i)).collect(joining(" "))),
        () -> "Success");
    device.injectShellCommandOutput(
        String.format("pm install-commit %d", PARENT_SESSION_ID), () -> "Success");
  }

  private static void givenChildSessionCreate(FakeDevice device) {
    AtomicInteger childSessionCounter = new AtomicInteger();
    device.injectShellCommandOutput(
        "pm install-create --staged",
        () -> "Success: blah blah [" + childSessionCounter.getAndIncrement() + "]");
  }

  private static Aapt2Command createFakeAapt2Command(
      Supplier<ImmutableList<String>> dumpBadgingSupplier) {
    return new Aapt2Command() {
      @Override
      public void convertApkProtoToBinary(Path protoApk, Path binaryApk) {
        throw new UnsupportedOperationException();
      }

      @Override
      public ImmutableList<String> dumpBadging(Path apkPath) {
        return dumpBadgingSupplier.get();
      }
    };
  }
}
