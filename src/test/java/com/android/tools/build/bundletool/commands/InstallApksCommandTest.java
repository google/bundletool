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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksDirectory;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariantForSingleSplitApk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InstallationException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class InstallApksCommandTest {

  private static final String DEVICE_ID = "id1";

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;

  private SystemEnvironmentProvider systemEnvironmentProvider;
  private Path adbPath;
  private Path sdkDirPath;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    sdkDirPath = Files.createDirectory(tmpDir.resolve("android-sdk"));
    adbPath = sdkDirPath.resolve("platform-tools").resolve("adb");
    Files.createDirectories(adbPath.getParent());
    Files.createFile(adbPath);
    adbPath.toFile().setExecutable(true);
    this.systemEnvironmentProvider =
        new FakeSystemEnvironmentProvider(
            ImmutableMap.of(ANDROID_HOME, sdkDirPath.toString(), ANDROID_SERIAL, DEVICE_ID));
  }

  @Test
  public void fromFlagsEquivalentToBuilder_onlyApkPaths() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_apkPathsAndAdb() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile, "--adb=" + adbPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_deviceId() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + apksFile, "--adb=" + adbPath, "--device-id=" + DEVICE_ID),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_allowDowngrade() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile, "--allow-downgrade"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .setAllowDowngrade(true)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_androidSerialVariable() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + apksFile, "--adb=" + adbPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_modules() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + apksFile, "--adb=" + adbPath, "--modules=base,feature"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setModules(ImmutableSet.of("base", "feature"))
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void missingApksFlag_fails() {
    expectMissingRequiredBuilderPropertyException(
        "apksArchivePath",
        () ->
            InstallApksCommand.builder()
                .setAdbPath(adbPath)
                .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
                .build());

    expectMissingRequiredFlagException(
        "apks",
        () ->
            InstallApksCommand.fromFlags(
                new FlagParser().parse("--adb=" + adbPath),
                systemEnvironmentProvider,
                fakeServerOneDevice(lDeviceWithLocales("en-US"))));
  }

  @Test
  public void badApkLocation_fails() throws Exception {
    Path apksFile = tmpDir.resolve("/the/apks/is/not/there.apks");

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
            .build();

    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("File '%s' was not found.", apksFile));
  }

  @Test
  public void badAdbLocation_fails() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setAdbPath(Paths.get("bad_adb_path"))
            .setApksArchivePath(apksFile)
            .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
            .build();
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("File 'bad_adb_path' was not found.");
  }

  @Test
  public void noDeviceId_moreThanOneDeviceConnected() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    AdbServer adbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US")),
                FakeDevice.fromDeviceSpec(
                    "id2", DeviceState.ONLINE, lDeviceWithLocales("en-US", "en-GB"))));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("More than one device connected, please provide --device-id.");
  }

  @Test
  public void missingDeviceWithId() throws Exception {
    Path apksFile = tmpDir.resolve("appbundle.apks");
    Files.createFile(apksFile);

    AdbServer adbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"))));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .setDeviceId("doesnt-exist")
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Unable to find the requested device.");
  }

  @Test
  public void adbInstallFails_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createSimpleTableOfContent(ZipPath.create("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect(
        (apks, installOptions) -> {
          throw InstallationException.builder().withMessage("Sample error message").build();
        });

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(InstallationException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Sample error message");
  }

  @Test
  public void deviceSdkIncompatible_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createLPlusTableOfContent(ZipPath.create("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            DEVICE_ID,
            DeviceState.ONLINE,
            mergeSpecs(
                sdkVersion(19), abis("arm64_v8a"), locales("en-US"), density(DensityAlias.HDPI)));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("The app doesn't support SDK version of the device: (19).");
  }

  @Test
  public void deviceAbiIncompatible_throws() throws Exception {
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkLx86 = ZipPath.create("splits/apkL-x86.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21)),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkL),
                        createApkDescription(
                            apkAbiTargeting(AbiAlias.X86, ImmutableSet.of()),
                            apkLx86,
                            /* isMasterSplit= */ false))))
            .build();

    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(21), abis("arm64-v8a"), locales("en-US"), density(DensityAlias.HDPI));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [arm64-v8a], "
                + "app ABIs: [x86]");
  }

  @Test
  public void badSdkVersionDevice_throws() throws Exception {
    Path apksFile =
        createApksArchiveFile(
            createSimpleTableOfContent(ZipPath.create("base-master.apk")),
            tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(1), density(480), abis("x86_64", "x86"), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device SDK version");
  }

  @DataPoints("apksInDirectory")
  public static final ImmutableSet<Boolean> APKS_IN_DIRECTORY = ImmutableSet.of(true, false);

  @Test
  @Theory
  public void badDensityDevice_throws(@FromDataPoints("apksInDirectory") boolean apksInDirectory)
      throws Exception {
    Path apksFile =
        createApks(createSimpleTableOfContent(ZipPath.create("base-master.apk")), apksInDirectory);

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(21), density(-1), abis("x86_64", "x86"), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device density");
  }

  @Test
  @Theory
  public void badAbisDevice_throws(@FromDataPoints("apksInDirectory") boolean apksInDirectory)
      throws Exception {
    Path apksFile =
        createApks(createSimpleTableOfContent(ZipPath.create("base-master.apk")), apksInDirectory);

    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis(), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device ABIs");
  }

  @Test
  @Theory
  public void installsOnlySpecifiedModules(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
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
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature1-master.apk"))),
                    createSplitApkSet(
                        "feature2",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature2-master.apk")))))
            .build();
    Path apksFile = createApks(tableOfContent, apksInDirectory);

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("base", "feature2"))
        .build()
        .execute();

    assertThat(Lists.transform(installedApks, apkPath -> apkPath.getFileName().toString()))
        .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
  }

  @Test
  @Theory
  public void moduleDependencies_installDependency(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature1-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature2-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature2"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature3-master.apk")))))
            .build();
    Path apksFile = createApks(tableOfContent, apksInDirectory);

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("feature2"))
        .build()
        .execute();

    assertThat(Lists.transform(installedApks, apkPath -> apkPath.getFileName().toString()))
        .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
  }

  @Test
  @Theory
  public void moduleDependencies_diamondGraph(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature1-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature2-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature3-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature4",
                        /* onDemand= */ true,
                        /* moduleDependencies= */ ImmutableList.of("feature2", "feature3"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature4-master.apk")))))
            .build();
    Path apksFile = createApks(tableOfContent, apksInDirectory);

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("feature4"))
        .build()
        .execute();

    assertThat(Lists.transform(installedApks, apkPath -> apkPath.getFileName().toString()))
        .containsExactly(
            "base-master.apk",
            "feature1-master.apk",
            "feature2-master.apk",
            "feature3-master.apk",
            "feature4-master.apk");
  }

  @Test
  public void printHelp_doesNotCrash() {
    GetDeviceSpecCommand.help();
  }

  private static AdbServer fakeServerOneDevice(DeviceSpec deviceSpec) {
    return new FakeAdbServer(
        /* hasInitialDeviceList= */ true,
        ImmutableList.of(FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec)));
  }

  /** Creates a table of content matching L+ devices. */
  private static BuildApksResult createLPlusTableOfContent(ZipPath apkPath) {
    return BuildApksResult.newBuilder()
        .addVariant(
            createVariantForSingleSplitApk(
                variantSdkTargeting(sdkVersionFrom(21)),
                ApkTargeting.getDefaultInstance(),
                apkPath))
        .build();
  }

  /** Creates a table of content matching all devices to a given apkPath. */
  private static BuildApksResult createSimpleTableOfContent(ZipPath apkPath) {
    return BuildApksResult.newBuilder()
        .addVariant(
            createVariantForSingleSplitApk(
                VariantTargeting.getDefaultInstance(), ApkTargeting.getDefaultInstance(), apkPath))
        .build();
  }

  private Path createApks(BuildApksResult buildApksResult, boolean apksInDirectory)
      throws Exception {
    if (apksInDirectory) {
      return createApksDirectory(buildApksResult, tmpDir);
    } else {
      return createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));
    }
  }
}
