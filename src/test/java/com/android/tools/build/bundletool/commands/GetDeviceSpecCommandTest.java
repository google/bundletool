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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkRuntimeSupported;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_SERIAL;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Devices.DeviceSpec;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GetDeviceSpecCommandTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;
  private static final String DEVICE_ID = "id1";
  private static final int DEVICE_TIER = 1;
  private static final ImmutableSet<String> DEVICE_GROUPS =
      ImmutableSet.of("highRam", "googlePixel");
  private static final String COUNTRY_SET = "latam";

  private SystemEnvironmentProvider systemEnvironmentProvider;
  private Path adbPath;
  private Path sdkDirPath;

  @Before
  public void setUp() throws IOException {
    tmpDir = tmp.getRoot().toPath();
    sdkDirPath = tmpDir.resolve("android-sdk");
    adbPath = sdkDirPath.resolve("platform-tools").resolve("adb");
    Files.createDirectories(adbPath.getParent());
    Files.createFile(adbPath);
    adbPath.toFile().setExecutable(true);

    this.systemEnvironmentProvider =
        new FakeSystemEnvironmentProvider(
            ImmutableMap.of(ANDROID_HOME, sdkDirPath.toString(), ANDROID_SERIAL, DEVICE_ID));
  }

  @Test
  public void fromFlagsEquivalentToBuilder_onlyOutputPath() {
    Path outputPath = tmpDir.resolve("device.json");

    GetDeviceSpecCommand commandViaFlags =
        GetDeviceSpecCommand.fromFlags(
            new FlagParser().parse("--output=" + outputPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    // The command via flags uses $ANDROID_HOME as a base for ADB location if the --adb flag is
    // missing. The builder doesn't support that so we pass what should be the resolved ADB location
    // for verification.
    GetDeviceSpecCommand commandViaBuilder =
        GetDeviceSpecCommand.builder()
            .setOutputPath(outputPath)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            // it's impractical not to copy.
            .setAdbServer(commandViaFlags.getAdbServer())
            .setDeviceId(DEVICE_ID)
            .build();

    assertThat(commandViaFlags).isEqualTo(commandViaBuilder);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_noDeviceId() {
    SystemEnvironmentProvider androidHomeProvider =
        new FakeSystemEnvironmentProvider(ImmutableMap.of(ANDROID_HOME, sdkDirPath.toString()));
    Path outputPath = tmpDir.resolve("device.json");

    GetDeviceSpecCommand commandViaFlags =
        GetDeviceSpecCommand.fromFlags(
            new FlagParser().parse("--adb=" + adbPath, "--output=" + outputPath),
            androidHomeProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    GetDeviceSpecCommand commandViaBuilder =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setOutputPath(outputPath)
            .setAdbServer(commandViaFlags.getAdbServer())
            .build();

    assertThat(commandViaFlags).isEqualTo(commandViaBuilder);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_allFlagsUsed() {
    Path outputPath = tmpDir.resolve("device.json");

    GetDeviceSpecCommand commandViaFlags =
        GetDeviceSpecCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--adb=" + adbPath,
                    "--device-id=" + DEVICE_ID,
                    "--output=" + outputPath,
                    "--device-tier=" + DEVICE_TIER,
                    "--device-groups=" + Joiner.on(",").join(DEVICE_GROUPS),
                    "--country-set=" + COUNTRY_SET),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    GetDeviceSpecCommand commandViaBuilder =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setDeviceId(DEVICE_ID)
            .setOutputPath(outputPath)
            .setAdbServer(commandViaFlags.getAdbServer())
            .setDeviceTier(DEVICE_TIER)
            .setDeviceGroups(DEVICE_GROUPS)
            .setCountrySet(COUNTRY_SET)
            .build();

    assertThat(commandViaFlags).isEqualTo(commandViaBuilder);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_allFlagsUsed_overwrite() {
    Path outputPath = tmpDir.resolve("device.json");

    GetDeviceSpecCommand commandViaFlags =
        GetDeviceSpecCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--adb=" + adbPath,
                    "--device-id=" + DEVICE_ID,
                    "--output=" + outputPath,
                    "--overwrite"),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    GetDeviceSpecCommand commandViaBuilder =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setDeviceId(DEVICE_ID)
            .setOutputPath(outputPath)
            .setAdbServer(commandViaFlags.getAdbServer())
            .setOverwriteOutput(true)
            .build();

    assertThat(commandViaFlags).isEqualTo(commandViaBuilder);
  }

  @Test
  public void fromFlagsEquivalentToBuilder_withEnvironmentalVariables() {
    Path outputPath = tmpDir.resolve("device.json");

    GetDeviceSpecCommand commandViaFlags =
        GetDeviceSpecCommand.fromFlags(
            new FlagParser().parse("--adb=" + adbPath, "--output=" + outputPath),
            systemEnvironmentProvider,
            fakeServerOneDevice(lDeviceWithLocales("en-US")));

    GetDeviceSpecCommand commandViaBuilder =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setDeviceId(DEVICE_ID)
            .setOutputPath(outputPath)
            .setAdbServer(commandViaFlags.getAdbServer())
            .build();

    assertThat(commandViaFlags).isEqualTo(commandViaBuilder);
  }

  @Test
  public void missingOuputFlag_fails() {
    expectMissingRequiredBuilderPropertyException(
        "output", () -> GetDeviceSpecCommand.builder().setAdbPath(adbPath).build());

    expectMissingRequiredFlagException(
        "output",
        () ->
            GetDeviceSpecCommand.fromFlags(
                new FlagParser().parse("--adb=" + adbPath),
                systemEnvironmentProvider,
                fakeServerOneDevice(lDeviceWithLocales("en-US"))));
  }

  @Test
  public void wrongDeviceSpecExtension_throws() throws Exception {
    Path outputPath = tmp.getRoot().toPath().resolve("device.spec");
    // We set up a fake ADB server because the real one won't work on Forge.
    Throwable exception =
        assertThrows(
            InvalidCommandException.class,
            () ->
                GetDeviceSpecCommand.builder()
                    .setAdbPath(adbPath)
                    .setAdbServer(fakeServerNoDevices())
                    .setOutputPath(outputPath)
                    .build());
    assertThat(exception)
        .hasMessageThat()
        .contains("Flag --output should be the path where to generate the device spec file.");
  }

  @Test
  public void badAdbLocation_fails() {
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(Paths.get("bad_adb_path"))
            .setOutputPath(Paths.get(tmp.getRoot().getPath(), "device.json"))
            .setAdbServer(fakeServerOneDevice(lDeviceWithLocales("en-US")))
            .build();
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("File 'bad_adb_path' was not found.");
  }

  @Test
  public void oneDevice_noDeviceId_works() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(21),
            abis("x86_64", "x86"),
            locales("en-GB"),
            density(360),
            sdkRuntimeSupported(/* supported= */ false));

    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    // We set up a fake ADB server because the real one won't work on Forge.
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .build();
    assertThat(command.execute()).isEqualTo(deviceSpec);

    try (Reader deviceSpecReader = BufferedIo.reader(outputPath)) {
      DeviceSpec.Builder writtenSpecBuilder = DeviceSpec.newBuilder();
      JsonFormat.parser().merge(deviceSpecReader, writtenSpecBuilder);
      assertThat(writtenSpecBuilder.build()).isEqualTo(deviceSpec);
    }
  }

  @Test
  public void nonExistentParentDirectory_works() {
    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(21),
            density(480),
            abis("x86"),
            locales("en-US"),
            sdkRuntimeSupported(/* supported= */ false));

    Path outputPath =
        tmp.getRoot().toPath().resolve("non-existent-parent-directory").resolve("device.json");
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .build();

    assertThat(command.execute()).isEqualTo(deviceSpec);
    assertThat(Files.exists(outputPath)).isTrue();
  }

  @Test
  public void oneDevice_badSdkVersion_throws() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(1), density(360), abis("x86_64", "x86"), locales("en-US"));
    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    // We set up a fake ADB server because the real one won't work on Forge.
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .build();
    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device SDK version");
  }

  @Test
  public void oneDevice_badDensity_throws() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(21), density(-1), abis("x86_64", "x86"), locales("en-US"));
    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    // We set up a fake ADB server because the real one won't work on Forge.
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .build();
    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device density");
  }

  @Test
  public void oneDevice_badAbis_throws() throws Exception {
    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis(), locales("en-US"));
    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    // We set up a fake ADB server because the real one won't work on Forge.
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .build();
    Throwable exception = assertThrows(IllegalStateException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("Error retrieving device ABIs");
  }

  @Test
  public void printHelp_doesNotCrash() {
    GetDeviceSpecCommand.help();
  }

  @Test
  public void overwriteSet_overwritesFile() throws Exception {
    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis("x86"), locales("en-US"));

    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    Files.createFile(outputPath);
    GetDeviceSpecCommand.builder()
        .setAdbPath(adbPath)
        .setAdbServer(fakeServerOneDevice(deviceSpec))
        .setOutputPath(outputPath)
        .setOverwriteOutput(true)
        .build()
        .execute();
    assertThat(outputPath.toFile().length()).isGreaterThan(0L);
  }

  @Test
  public void overwriteNotSet_outputFileExists_throws() throws Exception {
    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis("x86"), locales("en-US"));

    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    Files.createFile(outputPath);
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .build();

    Throwable exception = assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("File '" + outputPath + "' already exists.");
  }

  @Test
  public void countrySet_setInDeviceSpec_whenSpecified() throws Exception {
    // Arrange
    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis("x86"), locales("en-US"));
    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    Files.createFile(outputPath);
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .setCountrySet("latam")
            .setOverwriteOutput(true)
            .build();

    // Act
    command.execute();

    // Assert
    try (Reader deviceSpecReader = BufferedIo.reader(outputPath)) {
      DeviceSpec.Builder writtenSpecBuilder = DeviceSpec.newBuilder();
      JsonFormat.parser().merge(deviceSpecReader, writtenSpecBuilder);
      DeviceSpec writtenSpec = writtenSpecBuilder.build();
      assertThat(writtenSpec.hasCountrySet()).isTrue();
      assertThat(writtenSpec.getCountrySet().getValue()).isEqualTo("latam");
    }
  }

  @Test
  public void countrySet_notSetInDeviceSpec_whenNotSpecified() throws Exception {
    // Arrange
    DeviceSpec deviceSpec = mergeSpecs(sdkVersion(21), density(480), abis("x86"), locales("en-US"));
    Path outputPath = tmp.getRoot().toPath().resolve("device.json");
    Files.createFile(outputPath);
    GetDeviceSpecCommand command =
        GetDeviceSpecCommand.builder()
            .setAdbPath(adbPath)
            .setAdbServer(fakeServerOneDevice(deviceSpec))
            .setOutputPath(outputPath)
            .setOverwriteOutput(true)
            .build();

    // Act
    command.execute();

    // Assert
    try (Reader deviceSpecReader = BufferedIo.reader(outputPath)) {
      DeviceSpec.Builder writtenSpecBuilder = DeviceSpec.newBuilder();
      JsonFormat.parser().merge(deviceSpecReader, writtenSpecBuilder);
      DeviceSpec writtenSpec = writtenSpecBuilder.build();
      assertThat(writtenSpec.hasCountrySet()).isFalse();
    }
  }

  private static AdbServer fakeServerOneDevice(DeviceSpec deviceSpec) {
    return new FakeAdbServer(
        /* hasInitialDeviceList= */ true,
        ImmutableList.of(FakeDevice.fromDeviceSpec("id", DeviceState.ONLINE, deviceSpec)));
  }

  private static AdbServer fakeServerNoDevices() {
    return new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());
  }
}
