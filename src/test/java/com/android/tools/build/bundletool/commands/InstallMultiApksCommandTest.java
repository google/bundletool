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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
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
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.AdbCommand;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Int32Value;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InstallMultiApksCommandTest {

  private static final String DEVICE_ID = "id1";
  private static final String PKG_NAME_1 = "com.example.a";
  private static final String PKG_NAME_2 = "com.example.b";

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;
  private SystemEnvironmentProvider systemEnvironmentProvider;
  private Path adbPath;
  private Path sdkDirPath;
  private FakeDevice device;
  private boolean adbCommandExecuted;

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
    adbCommandExecuted = false;
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

    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
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
    InvalidCommandException e =
        assertThrows(
            InvalidCommandException.class,
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
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchiveZipPath(zipBundle)
            .setAapt2Command(
                createFakeAapt2Command(
                    ImmutableMap.of(
                        PKG_NAME_1, 1L,
                        PKG_NAME_2, 2L)))
            .setAdbCommand(
                createFakeAdbCommand(
                    ImmutableListMultimap.<String, String>builder()
                        .putAll(
                            PKG_NAME_1,
                            ImmutableList.of(
                                Paths.get(PKG_NAME_1, PKG_NAME_1 + "base-master.apk").toString(),
                                Paths.get(PKG_NAME_1, PKG_NAME_1 + "feature1-master.apk")
                                    .toString(),
                                Paths.get(PKG_NAME_1, PKG_NAME_1 + "feature2-master.apk")
                                    .toString()))
                        .putAll(
                            PKG_NAME_2,
                            Paths.get(PKG_NAME_2, PKG_NAME_2 + "base-master.apk").toString(),
                            Paths.get(PKG_NAME_2, PKG_NAME_2 + "feature1-master.apk").toString(),
                            Paths.get(PKG_NAME_2, PKG_NAME_2 + "feature2-master.apk").toString())
                        .build(),
                    false,
                    Optional.of(DEVICE_ID)))
            .build();

    // EXPECT
    // 1) Get existing packages.
    device.injectShellCommandOutput("pm list packages --show-versioncode", () -> "");
    device.injectShellCommandOutput("pm list packages --apex-only --show-versioncode", () -> "");
    // 2) Install command (above)
    command.execute();
    assertAdbCommandExecuted();
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
        "pm list packages --show-versioncode",
        () -> String.format("package:%s versionCode:1\njunk_to_ignore", PKG_NAME_1));
    device.injectShellCommandOutput("pm list packages --apex-only --show-versioncode", () -> "");

    // GIVEN the --update-only flag is set on the command...
    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setUpdateOnly(true)
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchiveZipPath(zipBundle)
            .setAapt2Command(
                createFakeAapt2Command(
                    ImmutableMap.of(
                        PKG_NAME_1, 2L,
                        PKG_NAME_2, 2L)))
            .setAdbCommand(
                createFakeAdbCommand(
                    expectedInstallApks(PKG_NAME_1, tableOfContent1),
                    false,
                    Optional.of(DEVICE_ID)))
            .build();

    // EXPECT only one of the packages
    command.execute();
    assertAdbCommandExecuted();
  }

  @Test
  public void execute_updateOnly_apex() throws Exception {
    // GIVEN a zip file containing fake .apks files for multiple packages, one of which is an
    // apex.
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
                    ZipPath.create(PKG_NAME_2 + "base.apex")))
            .build();
    Path package2Apks = createApksArchiveFile(apexTableOfContents, tmpDir.resolve("package2.apks"));
    ZipBuilder bundleBuilder = new ZipBuilder();
    bundleBuilder
        .addFileFromDisk(ZipPath.create("package1.apks"), package1Apks.toFile())
        .addFileFromDisk(ZipPath.create("package2.apks"), package2Apks.toFile());
    Path zipBundle = bundleBuilder.writeTo(tmpDir.resolve("bundle.zip"));

    // GIVEN only one of the packages is installed on the device...
    device.injectShellCommandOutput("pm list packages --show-versioncode", () -> "");
    device.injectShellCommandOutput(
        "pm list packages --apex-only --show-versioncode",
        () -> String.format("package:%s versionCode:1\njunk_to_ignore", PKG_NAME_2));

    // GIVEN the --update-only flag is set on the command...
    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setUpdateOnly(true)
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchiveZipPath(zipBundle)
            .setAapt2Command(
                createFakeAapt2Command(
                    ImmutableMap.of(
                        PKG_NAME_1, 2L,
                        PKG_NAME_2, 2L)))
            .setAdbCommand(
                createFakeAdbCommand(
                    expectedInstallApks(PKG_NAME_2, apexTableOfContents),
                    false,
                    Optional.of(DEVICE_ID)))
            .build();

    // EXPECT only one of the packages
    command.execute();
    assertAdbCommandExecuted();
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

    // GIVEN no packages are installed on the device...
    givenEmptyListPackages(device);

    // GIVEN the --update-only flag is used to restrict to previously installed packages.
    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchiveZipPath(zipBundle)
            .setUpdateOnly(true)
            .setAapt2Command(
                createFakeAapt2Command(
                    ImmutableMap.of(
                        PKG_NAME_1, 1L,
                        PKG_NAME_2, 2L)))
            .setAdbCommand(
                // EXPECT that execute will not be called
                createFakeAdbCommand(ImmutableListMultimap.of(), false, Optional.of(DEVICE_ID)))
            .build();

    // EXPECT no further commands to be executed.
    command.execute();
    assertThat(adbCommandExecuted).isFalse();
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
                    ZipPath.create(PKG_NAME_1 + "base.apex")))
            .build();
    Path package1Apks = createApksArchiveFile(apexTableOfContents, tmpDir.resolve("package1.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchivePaths(ImmutableList.of(package1Apks))
            .setAapt2Command(createFakeAapt2Command(ImmutableMap.of(PKG_NAME_1, 1L)))
            .setAdbCommand(
                // EXPECT that execute will not be called
                createFakeAdbCommand(ImmutableListMultimap.of(), false, Optional.of(DEVICE_ID)))
            .build();

    // EXPECT to check the existing list of packages...
    givenEmptyListPackages(device);

    // THEN the command executes without triggering any other shell commands.
    command.execute();
    assertThat(adbCommandExecuted).isFalse();
  }

  @Test
  public void execute_packageNameMissing_aapt2Failure() throws Exception {
    // GIVEN fake .apks files, one of which will fail on the aapt2 command...
    BuildApksResult tableOfContents1 = fakeTableOfContents(PKG_NAME_1);
    Path apksPath1 = createApksArchiveFile(tableOfContents1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContents2 = fakeTableOfContents(PKG_NAME_2);
    Path apksPath2 = createApksArchiveFile(tableOfContents2, tmpDir.resolve("package2.apks"));

    // GIVEN a fake aapt2 command that will fail with an IncompatibleDeviceException.
    Aapt2Command aapt2Command =
        createFakeAapt2CommandFromSupplier(
            ImmutableMap.of(
                PKG_NAME_1,
                    () -> {
                      throw IncompatibleDeviceException.builder()
                          .withUserMessage("incompatible device")
                          .build();
                    },
                PKG_NAME_2,
                    () ->
                        ImmutableList.of(
                            String.format("package: name='%s' versionCode='%d' ", PKG_NAME_2, 2))));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setAdbPath(adbPath)
            .setApksArchivePaths(ImmutableList.of(apksPath1, apksPath2))
            .setAapt2Command(aapt2Command)
            .setAdbCommand(
                createFakeAdbCommand(
                    expectedInstallApks(PKG_NAME_2, tableOfContents2), false, Optional.empty()))
            .build();

    // EXPECT the command to skip the incompatible package.
    givenEmptyListPackages(device);
    command.execute();
    assertAdbCommandExecuted();
  }

  @Test
  public void execute_includeEqualVersionInstalled() throws Exception {
    // GIVEN fake .apks files, one of which will have an equal version already installed...
    BuildApksResult tableOfContents1 = fakeTableOfContents(PKG_NAME_1);
    Path apksPath1 = createApksArchiveFile(tableOfContents1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContents2 = fakeTableOfContents(PKG_NAME_2);
    Path apksPath2 = createApksArchiveFile(tableOfContents2, tmpDir.resolve("package2.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchivePaths(ImmutableList.of(apksPath1, apksPath2))
            .setAapt2Command(
                createFakeAapt2Command(ImmutableMap.of(PKG_NAME_1, 1L, PKG_NAME_2, 2L)))
            .setAdbCommand(
                // EXPECT both packages to be installed.
                createFakeAdbCommand(
                    ImmutableListMultimap.<String, String>builder()
                        .putAll(expectedInstallApks(PKG_NAME_1, tableOfContents1))
                        .putAll(expectedInstallApks(PKG_NAME_2, tableOfContents2))
                        .build(),
                    false,
                    Optional.of(DEVICE_ID)))
            .build();

    device.injectShellCommandOutput(
        "pm list packages --show-versioncode",
        () ->
            String.format(
                "package:%s versionCode:1\npackage:%s versionCode:1", PKG_NAME_1, PKG_NAME_2));
    device.injectShellCommandOutput("pm list packages --apex-only --show-versioncode", () -> "");
    command.execute();
    assertAdbCommandExecuted();
  }

  @Test
  public void execute_skipHigherVersionInstalled() throws Exception {
    // GIVEN fake .apks files, one of which will have an higher version already installed...
    BuildApksResult tableOfContents1 = fakeTableOfContents(PKG_NAME_1);
    Path apksPath1 = createApksArchiveFile(tableOfContents1, tmpDir.resolve("package1.apks"));
    BuildApksResult tableOfContents2 = fakeTableOfContents(PKG_NAME_2);
    Path apksPath2 = createApksArchiveFile(tableOfContents2, tmpDir.resolve("package2.apks"));

    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setApksArchivePaths(ImmutableList.of(apksPath1, apksPath2))
            .setAapt2Command(
                createFakeAapt2Command(ImmutableMap.of(PKG_NAME_1, 1L, PKG_NAME_2, 2L)))
            .setAdbCommand(
                // EXPECT only the higher version package to be installed.
                createFakeAdbCommand(
                    expectedInstallApks(PKG_NAME_2, tableOfContents2),
                    false,
                    Optional.of(DEVICE_ID)))
            .build();

    device.injectShellCommandOutput(
        "pm list packages --show-versioncode",
        () ->
            String.format(
                "package:%s versionCode:3\npackage:%s versionCode:1", PKG_NAME_1, PKG_NAME_2));
    device.injectShellCommandOutput("pm list packages --apex-only --show-versioncode", () -> "");
    command.execute();
    assertAdbCommandExecuted();
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
                    ZipPath.create(PKG_NAME_1 + "base.apex")))
            .build();
    Path apksPath = createApksArchiveFile(apexTableOfContents, tmpDir.resolve("package1.apks"));

    // GIVEN an install command with the --enable-rollback flag...
    InstallMultiApksCommand command =
        InstallMultiApksCommand.builder()
            .setAdbServer(fakeServerOneDevice(device))
            .setDeviceId(DEVICE_ID)
            .setAdbPath(adbPath)
            .setEnableRollback(true)
            .setApksArchivePaths(ImmutableList.of(apksPath))
            .setAapt2Command(createFakeAapt2Command(ImmutableMap.of(PKG_NAME_1, 1L)))
            .setAdbCommand(
                // EXPECT the --enable-rollback flag to be included.
                createFakeAdbCommand(
                    expectedInstallApks(PKG_NAME_1, apexTableOfContents),
                    true,
                    Optional.of(DEVICE_ID)))
            .build();

    givenEmptyListPackages(device);
    command.execute();
    assertAdbCommandExecuted();
  }

  private void assertAdbCommandExecuted() {
    assertThat(adbCommandExecuted).isTrue();
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
                        ApkTargeting.getDefaultInstance(),
                        ZipPath.create(packageName + "base-master.apk"))),
                createSplitApkSet(
                    "feature1",
                    createMasterApkDescription(
                        ApkTargeting.getDefaultInstance(),
                        ZipPath.create(packageName + "feature1-master.apk"))),
                createSplitApkSet(
                    "feature2",
                    createMasterApkDescription(
                        ApkTargeting.getDefaultInstance(),
                        ZipPath.create(packageName + "feature2-master.apk")))))
        .build();
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

  private static ImmutableListMultimap<String, String> expectedInstallApks(
      String packageName, BuildApksResult toc) {
    return ImmutableListMultimap.<String, String>builder()
        .putAll(
            packageName,
            apkDescriptionStream(toc)
                .map(ApkDescription::getPath)
                .map(Paths::get)
                .map(Path::getFileName)
                .map(fileName -> Paths.get(packageName, fileName.toString()).toString())
                .collect(toImmutableList()))
        .build();
  }

  private static void givenEmptyListPackages(FakeDevice device) {
    device.injectShellCommandOutput("pm list packages --show-versioncode", () -> "");
    device.injectShellCommandOutput("pm list packages --apex-only --show-versioncode", () -> "");
  }

  private static Aapt2Command createFakeAapt2Command(ImmutableMap<String, Long> packageVersionMap) {
    return createFakeAapt2CommandFromSupplier(
        packageVersionMap.entrySet().stream()
            .collect(
                toImmutableMap(
                    Map.Entry::getKey,
                    entry ->
                        () ->
                            ImmutableList.of(
                                String.format(
                                    "package: name='%s' versionCode='%d' ",
                                    entry.getKey(), entry.getValue())))));
  }

  private AdbCommand createFakeAdbCommand(
      ImmutableListMultimap<String, String> expectedApkToInstallByPackage,
      boolean expectedEnableRollback,
      Optional<String> expectedDeviceId) {
    return new FakeAdbCommand(
        expectedApkToInstallByPackage, expectedEnableRollback, expectedDeviceId);
  }

  private class FakeAdbCommand implements AdbCommand {
    private final ImmutableListMultimap<String, String> expectedApkToInstallByPackage;
    private final boolean expectedEnableRollback;
    private final Optional<String> expectedDeviceId;

    public FakeAdbCommand(
        ImmutableListMultimap<String, String> expectedApkToInstallByPackage,
        boolean expectedEnableRollback,
        Optional<String> expectedDeviceId) {
      this.expectedApkToInstallByPackage =
          ImmutableListMultimap.copyOf(expectedApkToInstallByPackage);
      this.expectedEnableRollback = expectedEnableRollback;
      this.expectedDeviceId = expectedDeviceId;
    }

    @Override
    public ImmutableList<String> installMultiPackage(
        ImmutableListMultimap<String, String> apkToInstallByPackage,
        boolean enableRollback,
        Optional<String> deviceId) {
      adbCommandExecuted = true;
      // The apkToInstallByPackage include files in a temporary directory, and there is no good way
      // to know the temporary directory path, so try to strip it out.

      ImmutableListMultimap.Builder<String, String> cleanApkToInstallByPackageBuilder =
          ImmutableListMultimap.builder();

      String pattern = Pattern.quote(tmpDir.getParent().toString()) + "[/\\\\]?\\d+[/\\\\]";

      apkToInstallByPackage
          .keySet()
          .forEach(
              packageName ->
                  cleanApkToInstallByPackageBuilder.putAll(
                      packageName,
                      apkToInstallByPackage.get(packageName).stream()
                          .map(apkFileName -> apkFileName.replaceAll(pattern, ""))
                          .collect(toImmutableList())));
      assertThat(cleanApkToInstallByPackageBuilder.build())
          .isEqualTo(expectedApkToInstallByPackage);
      assertThat(enableRollback).isEqualTo(expectedEnableRollback);
      assertThat(deviceId).isEqualTo(expectedDeviceId);
      return ImmutableList.of();
    }
  }

  private static Aapt2Command createFakeAapt2CommandFromSupplier(
      ImmutableMap<String, Supplier<ImmutableList<String>>> packageSupplierMap) {
    return new Aapt2Command() {
      @Override
      public void convertApkProtoToBinary(Path protoApk, Path binaryApk) {
        throw new UnsupportedOperationException();
      }

      @Override
      public ImmutableList<String> dumpBadging(Path apkPath) {
        return packageSupplierMap.keySet().stream()
            .filter(packageName -> apkPath.toString().contains(packageName))
            .findFirst()
            .map(packageName -> packageSupplierMap.get(packageName).get())
            .orElse(ImmutableList.of());
      }
    };
  }
}
