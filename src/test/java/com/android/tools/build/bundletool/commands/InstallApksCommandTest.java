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

import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksDirectory;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariantForSingleSplitApk;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.splitApkDescription;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.DefaultTargetingValue;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.LocalTestingInfo;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.LocalTestingPathResolver;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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
  private static final String PKG_NAME = "com.example";

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;

  private SystemEnvironmentProvider systemEnvironmentProvider;
  private Path adbPath;
  private Path sdkDirPath;
  private Path simpleApksPath;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    sdkDirPath = Files.createDirectory(tmpDir.resolve("android-sdk"));
    adbPath = sdkDirPath.resolve("platform-tools").resolve("adb");
    Files.createDirectories(adbPath.getParent());
    Files.createFile(adbPath);
    adbPath.toFile().setExecutable(true);
    simpleApksPath =
        createApksArchiveFile(
            createSimpleTableOfContent(ZipPath.create("base-master.apk")),
            tmpDir.resolve("simple-bundle.apks"));
    this.systemEnvironmentProvider =
        new FakeSystemEnvironmentProvider(
            ImmutableMap.of(ANDROID_HOME, sdkDirPath.toString(), ANDROID_SERIAL, DEVICE_ID));
  }

  @Test
  public void fromFlagsEquivalentToBuilder_onlyApkPaths() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + simpleApksPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_apkPathsAndAdb() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + simpleApksPath, "--adb=" + adbPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_deviceId() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + simpleApksPath, "--adb=" + adbPath, "--device-id=" + DEVICE_ID),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_allowDowngrade() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + simpleApksPath, "--allow-downgrade"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .setAllowDowngrade(true)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_androidSerialVariable() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser().parse("--apks=" + simpleApksPath, "--adb=" + adbPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_modules() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + simpleApksPath, "--adb=" + adbPath, "--modules=base,feature"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setModules(ImmutableSet.of("base", "feature"))
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_deviceTier() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + simpleApksPath, "--adb=" + adbPath, "--device-tier=1"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .setDeviceTier(1)
            .build();

    assertThat(fromBuilder).isEqualTo(fromFlags);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_timeout() throws Exception {
    InstallApksCommand fromFlags =
        InstallApksCommand.fromFlags(
            new FlagParser()
                .parse("--apks=" + simpleApksPath, "--adb=" + adbPath, "--timeout-millis=30000"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    InstallApksCommand fromBuilder =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(fromFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .setTimeout(Duration.ofSeconds(30))
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
    InstallApksCommand command =
        InstallApksCommand.builder()
            .setAdbPath(Paths.get("bad_adb_path"))
            .setApksArchivePath(simpleApksPath)
            .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
            .build();
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("File 'bad_adb_path' was not found.");
  }

  @Test
  public void noDeviceId_moreThanOneDeviceConnected() throws Exception {
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
            .setApksArchivePath(simpleApksPath)
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
    AdbServer adbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"))));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .setDeviceId("doesnt-exist")
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Unable to find the requested device.");
  }

  @Test
  public void adbInstallFails_throws() throws Exception {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect(
        (apks, installOptions) -> {
          throw CommandExecutionException.builder()
              .withInternalMessage("Sample error message")
              .build();
        });

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, command::execute);
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

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
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
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21)),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkL),
                        createApkDescription(
                            apkAbiTargeting(X86, ImmutableSet.of()),
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

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [arm64-v8a], "
                + "app ABIs: [x86]");
  }

  @Test
  public void badSdkVersionDevice_throws() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(1), density(480), abis("x86_64", "x86"), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
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
  public void incompleteApksFile_missingAbiSplitMatchedForDevice_throws() throws Exception {
    // Partial APK Set file where 'x86' split is included and 'x86_64' split is not included because
    // device spec sent to 'build-apks' command doesn't support it.
    // Next, device spec that should be matched to 'x86_64' split is provided to 'install-apks'
    // command and command must throw IncompatibleDeviceException as mathed split 'x86_64' is not
    // available.
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                        splitApkDescription(
                            apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
                            ZipPath.create("base-x86.apk")))))
            .build();

    Path apksFile = createApks(tableOfContent, /* apksInDirectory= */ false);

    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(21), density(DensityAlias.MDPI), abis("x86_64", "x86"), locales("en-US"));
    FakeDevice fakeDevice = FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, deviceSpec);
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(apksFile)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Missing APKs for [ABI] dimensions in the module 'base' for the provided device.");
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
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
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

    assertThat(getFileNames(installedApks))
        .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
  }

  @Test
  @Theory
  public void moduleDependencies_installDependency(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature1-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature2-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        DeliveryType.ON_DEMAND,
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

    assertThat(getFileNames(installedApks))
        .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
  }

  @Test
  @Theory
  public void moduleDependencies_diamondGraph(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature1-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature2-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(),
                            ZipPath.create("feature3-master.apk"))),
                    createSplitApkSet(
                        /* moduleName= */ "feature4",
                        DeliveryType.ON_DEMAND,
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

    assertThat(getFileNames(installedApks))
        .containsExactly(
            "base-master.apk",
            "feature1-master.apk",
            "feature2-master.apk",
            "feature3-master.apk",
            "feature4-master.apk");
  }

  @Test
  @Theory
  public void installAssetModules(@FromDataPoints("apksInDirectory") boolean apksInDirectory)
      throws Exception {
    String installTimeModule1 = "installtime_assetmodule1";
    String installTimeModule2 = "installtime_assetmodule2";
    String onDemandModule = "ondemand_assetmodule";
    ZipPath installTimeMasterApk1 = ZipPath.create(installTimeModule1 + "-master.apk");
    ZipPath installTimeEnApk1 = ZipPath.create(installTimeModule1 + "-en.apk");
    ZipPath installTimeMasterApk2 = ZipPath.create(installTimeModule2 + "-master.apk");
    ZipPath installTimeEnApk2 = ZipPath.create(installTimeModule2 + "-en.apk");
    ZipPath onDemandMasterApk = ZipPath.create(onDemandModule + "-master.apk");
    ZipPath baseApk = ZipPath.create("base-master.apk");
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule1)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk1))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk1)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule2)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk2))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk2)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandModule)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandMasterApk)))
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
        .setModules(ImmutableSet.of(installTimeModule1))
        .build()
        .execute();

    assertThat(getFileNames(installedApks))
        .containsExactly(
            baseApk.toString(), installTimeMasterApk1.toString(), installTimeEnApk1.toString());
  }

  @Test
  @Theory
  public void localTestingMode_defaultModules(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    String installTimeFeature = "installtime_feature";
    String onDemandFeature = "ondemand_feature";
    String installTimeAsset = "installtime_asset";
    String onDemandAsset = "ondemand_asset";
    ZipPath baseApk = ZipPath.create("base-master.apk");
    ZipPath baseEnApk = ZipPath.create("base-en.apk");
    ZipPath installTimeFeatureMasterApk = ZipPath.create(installTimeFeature + "-master.apk");
    ZipPath installTimeFeatureEnApk = ZipPath.create(installTimeFeature + "-en.apk");
    ZipPath installTimeFeaturePlApk = ZipPath.create(installTimeFeature + "-pl.apk");
    ZipPath onDemandFeatureMasterApk = ZipPath.create(onDemandFeature + "-master.apk");
    ZipPath installTimeAssetMasterApk = ZipPath.create(installTimeAsset + "-master.apk");
    ZipPath installTimeAssetEnApk = ZipPath.create(installTimeAsset + "-en.apk");
    ZipPath onDemandAssetMasterApk = ZipPath.create(onDemandAsset + "-master.apk");
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk),
                        createApkDescription(
                            apkLanguageTargeting("en"), baseEnApk, /* isMasterSplit= */ false)),
                    createSplitApkSet(
                        installTimeFeature,
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeFeatureMasterApk),
                        createApkDescription(
                            apkLanguageTargeting("en"),
                            installTimeFeatureEnApk,
                            /* isMasterSplit= */ false),
                        createApkDescription(
                            apkLanguageTargeting("pl"),
                            installTimeFeaturePlApk,
                            /* isMasterSplit= */ false)),
                    createSplitApkSet(
                        onDemandFeature,
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), onDemandFeatureMasterApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeAsset)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeAssetMasterApk))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeAssetEnApk)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandAsset)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), onDemandAssetMasterApk)))
            .setLocalTestingInfo(
                LocalTestingInfo.newBuilder()
                    .setEnabled(true)
                    .setLocalTestingPath("local_testing")
                    .build())
            .build();

    Path apksFile = createApks(tableOfContent, apksInDirectory);
    Duration timeout = Duration.ofMinutes(5);

    List<Path> installedApks = new ArrayList<>();
    List<Path> pushedFiles = new ArrayList<>();
    List<String> clearedPathes = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect(
        (apks, installOptions) -> {
          assertThat(installOptions.getTimeout()).isEqualTo(timeout);
          installedApks.addAll(apks);
        });
    fakeDevice.setPushSideEffect(
        (files, pushOptions) -> {
          assertThat(pushOptions.getTimeout()).isEqualTo(timeout);
          pushedFiles.addAll(files);
        });
    fakeDevice.setRemoveRemotePathSideEffect(
        (remotePath, runAs, removeTimeout) -> {
          assertThat(removeTimeout).isEqualTo(timeout);
          assertThat(runAs).hasValue(PKG_NAME);
          clearedPathes.add(remotePath);
        });

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setTimeout(timeout)
        .build()
        .execute();

    // Base, install-time features and install-time assets.
    assertThat(getFileNames(installedApks))
        .containsExactly(
            baseApk.toString(),
            baseEnApk.toString(),
            installTimeFeatureMasterApk.toString(),
            installTimeFeatureEnApk.toString(),
            installTimeAssetMasterApk.toString(),
            installTimeAssetEnApk.toString());
    // Base config splits, install-time and on-demand features and on-demand assets. All languages.
    assertThat(getFileNames(pushedFiles))
        .containsExactly(
            baseEnApk.toString(),
            installTimeFeatureMasterApk.toString(),
            installTimeFeatureEnApk.toString(),
            installTimeFeaturePlApk.toString(),
            onDemandFeatureMasterApk.toString(),
            onDemandAssetMasterApk.toString());
    assertThat(clearedPathes)
        .containsExactly(LocalTestingPathResolver.getLocalTestingWorkingDir(PKG_NAME));
  }

  @Test
  @Theory
  public void localTestingMode_allModules(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    String installTimeFeature = "installtime_feature";
    String onDemandFeature = "ondemand_feature";
    String installTimeAsset = "installtime_asset";
    String onDemandAsset = "ondemand_asset";
    ZipPath baseApk = ZipPath.create("base-master.apk");
    ZipPath baseEnApk = ZipPath.create("base-en.apk");
    ZipPath installTimeFeatureMasterApk = ZipPath.create(installTimeFeature + "-master.apk");
    ZipPath installTimeFeatureEnApk = ZipPath.create(installTimeFeature + "-en.apk");
    ZipPath installTimeFeaturePlApk = ZipPath.create(installTimeFeature + "-pl.apk");
    ZipPath onDemandFeatureMasterApk = ZipPath.create(onDemandFeature + "-master.apk");
    ZipPath installTimeAssetMasterApk = ZipPath.create(installTimeAsset + "-master.apk");
    ZipPath installTimeAssetEnApk = ZipPath.create(installTimeAsset + "-en.apk");
    ZipPath onDemandAssetMasterApk = ZipPath.create(onDemandAsset + "-master.apk");
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk),
                        createApkDescription(
                            apkLanguageTargeting("en"), baseEnApk, /* isMasterSplit= */ false)),
                    createSplitApkSet(
                        installTimeFeature,
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeFeatureMasterApk),
                        createApkDescription(
                            apkLanguageTargeting("en"),
                            installTimeFeatureEnApk,
                            /* isMasterSplit= */ false),
                        createApkDescription(
                            apkLanguageTargeting("pl"),
                            installTimeFeaturePlApk,
                            /* isMasterSplit= */ false)),
                    createSplitApkSet(
                        onDemandFeature,
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), onDemandFeatureMasterApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeAsset)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeAssetMasterApk))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeAssetEnApk)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandAsset)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), onDemandAssetMasterApk)))
            .setLocalTestingInfo(
                LocalTestingInfo.newBuilder()
                    .setEnabled(true)
                    .setLocalTestingPath("local_testing")
                    .build())
            .build();

    Path apksFile = createApks(tableOfContent, apksInDirectory);

    List<Path> installedApks = new ArrayList<>();
    List<Path> pushedFiles = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));
    fakeDevice.setPushSideEffect((files, installOptions) -> pushedFiles.addAll(files));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setModules(ImmutableSet.of("_ALL_"))
        .build()
        .execute();

    // Base, install-time and on-demand features and install-time assets.
    assertThat(getFileNames(installedApks))
        .containsExactly(
            baseApk.toString(),
            baseEnApk.toString(),
            installTimeFeatureMasterApk.toString(),
            installTimeFeatureEnApk.toString(),
            onDemandFeatureMasterApk.toString(),
            installTimeAssetMasterApk.toString(),
            installTimeAssetEnApk.toString());
    // Base config splits, install-time and on-demand features and on-demand assets. All languages.
    assertThat(getFileNames(pushedFiles))
        .containsExactly(
            baseEnApk.toString(),
            installTimeFeatureMasterApk.toString(),
            installTimeFeatureEnApk.toString(),
            installTimeFeaturePlApk.toString(),
            onDemandFeatureMasterApk.toString(),
            onDemandAssetMasterApk.toString());
  }

  @Test
  public void localTestingMode_additionalLocalTestingFiles() throws Exception {
    ZipPath baseApk = ZipPath.create("base-master.apk");
    ZipPath baseEnApk = ZipPath.create("base-en.apk");
    ZipPath onDemandFeatureMasterApk = ZipPath.create("ondemand_feature-master.apk");

    Path additionalXml = tmpDir.resolve("additional.xml");
    Files.write(additionalXml, new byte[0]);

    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk),
                        createApkDescription(
                            apkLanguageTargeting("en"), baseEnApk, /* isMasterSplit= */ false)),
                    createSplitApkSet(
                        "ondemand_feature",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), onDemandFeatureMasterApk))))
            .setLocalTestingInfo(
                LocalTestingInfo.newBuilder()
                    .setEnabled(true)
                    .setLocalTestingPath("local_testing")
                    .build())
            .build();

    Path apksFile = createApks(tableOfContent, /* apksInDirectory= */ false);

    List<Path> installedApks = new ArrayList<>();
    List<Path> pushedFiles = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));
    fakeDevice.setPushSideEffect((files, installOptions) -> pushedFiles.addAll(files));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setAdditionalLocalTestingFiles(ImmutableList.of(additionalXml))
        .build()
        .execute();

    // Base, install-time and on-demand features and install-time assets.
    assertThat(getFileNames(installedApks))
        .containsExactly(baseApk.toString(), baseEnApk.toString());
    // Base config splits, install-time and on-demand features and on-demand assets. All languages.
    assertThat(getFileNames(pushedFiles))
        .containsExactly(
            baseEnApk.toString(),
            onDemandFeatureMasterApk.toString(),
            additionalXml.getFileName().toString());
  }

  @Test
  public void noLocalTesting_additionalLocalTestingFiles_throws() throws Exception {
    Path additionalXml = tmpDir.resolve("additional.xml");
    Files.write(additionalXml, new byte[0]);

    AdbServer adbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"))));

    InstallApksCommand command =
        InstallApksCommand.builder()
            .setApksArchivePath(simpleApksPath)
            .setAdbPath(adbPath)
            .setAdbServer(adbServer)
            .setAdditionalLocalTestingFiles(ImmutableList.of(additionalXml))
            .build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "'additional-local-testing-files' flag is only supported for APKs built in local"
                + " testing mode.");
  }

  @Test
  public void bundleWithDeviceTierTargeting_noDeviceTierSpecified_usesDefaultValue()
      throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseLowApk = ZipPath.create("base-tier_0.apk");
    ZipPath baseHighApk = ZipPath.create("base-tier_1.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            baseLowApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            baseHighApk))))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder()
                    .setDimension(Value.DEVICE_TIER)
                    .setDefaultValue("1"))
            .build();

    Path apksFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .build()
        .execute();

    // Base only, the on demand asset is not installed. Low tier splits are filtered out.
    assertThat(getFileNames(installedApks))
        .containsExactly(baseMasterApk.toString(), baseHighApk.toString());
  }

  @Test
  public void bundleWithDeviceTierTargeting_noDeviceTierSpecifiedNorDefault_usesZeroAsDefault()
      throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseLowApk = ZipPath.create("base-tier_0.apk");
    ZipPath baseHighApk = ZipPath.create("base-tier_1.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            baseLowApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            baseHighApk))))
            .build();

    Path apksFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    List<Path> installedApks = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .build()
        .execute();

    // Base only, the on demand asset is not installed. Tier 0 splits are returned, since it is the
    // default when unspecified.
    assertThat(getFileNames(installedApks))
        .containsExactly(baseMasterApk.toString(), baseLowApk.toString());
  }

  @Test
  @Theory
  public void bundleWithDeviceTierTargeting_deviceTierSet_filtersByTier(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseLowApk = ZipPath.create("base-tier_0.apk");
    ZipPath baseHighApk = ZipPath.create("base-tier_1.apk");
    ZipPath asset1MasterApk = ZipPath.create("asset1-master.apk");
    ZipPath asset1LowApk = ZipPath.create("asset1-tier_0.apk");
    ZipPath asset1HighApk = ZipPath.create("asset1-tier_1.apk");
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setPackageName(PKG_NAME)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            baseLowApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            baseHighApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName("asset1")
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), asset1MasterApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            asset1LowApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            asset1HighApk)))
            // Add local testing info to check that pushed APKs are also filtered by tier.
            .setLocalTestingInfo(
                LocalTestingInfo.newBuilder().setEnabled(true).setLocalTestingPath("local_testing"))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder()
                    .setDimension(Value.DEVICE_TIER)
                    .setDefaultValue("0"))
            .build();

    Path apksFile = createApks(tableOfContent, apksInDirectory);

    List<Path> installedApks = new ArrayList<>();
    List<Path> pushedFiles = new ArrayList<>();
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(DEVICE_ID, DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer adbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    fakeDevice.setInstallApksSideEffect((apks, installOptions) -> installedApks.addAll(apks));
    fakeDevice.setPushSideEffect((files, installOptions) -> pushedFiles.addAll(files));

    InstallApksCommand.builder()
        .setApksArchivePath(apksFile)
        .setAdbPath(adbPath)
        .setAdbServer(adbServer)
        .setDeviceTier(1)
        .build()
        .execute();

    // Base only, the on demand asset is not installed. Low tier splits are filtered out.
    assertThat(getFileNames(installedApks))
        .containsExactly(baseMasterApk.toString(), baseHighApk.toString());
    // Base config splits and on-demand assets. Low tier splits are filtered out.
    assertThat(getFileNames(pushedFiles))
        .containsExactly(
            baseHighApk.toString(), asset1MasterApk.toString(), asset1HighApk.toString());
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
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
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
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
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

  private static ImmutableList<String> getFileNames(List<Path> paths) {
    return paths.stream().map(Path::getFileName).map(Path::toString).collect(toImmutableList());
  }
}
