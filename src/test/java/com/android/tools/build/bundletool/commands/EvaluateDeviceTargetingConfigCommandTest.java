/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.google.common.io.CharStreams;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EvaluateDeviceTargetingConfigCommandTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private static final String DEVICE_ID = "id";
  private static final String COUNTRY_CODE = "AR";
  private Path deviceTargetingConfigPath;
  private Path devicePropertiesPath;
  private static final Path ADB_PATH =
      Paths.get("third_party/java/android/android_sdk_linux/platform-tools/adb.static");
  private final AdbServer fakeAdbServer = mock(AdbServer.class);

  @Before
  public void setUp() {
    Path tmpDir = tmp.getRoot().toPath();
    deviceTargetingConfigPath = tmpDir.resolve("config.json");
    devicePropertiesPath = tmpDir.resolve("device_properties.json");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withDeviceProperties() {
    EvaluateDeviceTargetingConfigCommand commandViaFlags =
        EvaluateDeviceTargetingConfigCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--config=" + deviceTargetingConfigPath,
                    "--device-properties=" + devicePropertiesPath,
                    "--country-code=" + COUNTRY_CODE),
            fakeAdbServer);

    EvaluateDeviceTargetingConfigCommand commandViaBuilder =
        EvaluateDeviceTargetingConfigCommand.builder()
            .setDeviceTargetingConfigurationPath(deviceTargetingConfigPath)
            .setDevicePropertiesPath(devicePropertiesPath)
            .setCountryCode(COUNTRY_CODE)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult_withConnectedDevice() {
    EvaluateDeviceTargetingConfigCommand commandViaFlags =
        EvaluateDeviceTargetingConfigCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--config=" + deviceTargetingConfigPath,
                    "--connected-device",
                    "--device-id=" + DEVICE_ID,
                    "--country-code=" + COUNTRY_CODE,
                    "--adb=" + ADB_PATH),
            fakeAdbServer);

    EvaluateDeviceTargetingConfigCommand commandViaBuilder =
        EvaluateDeviceTargetingConfigCommand.builder()
            .setDeviceTargetingConfigurationPath(deviceTargetingConfigPath)
            .setAdbPath(ADB_PATH)
            .setConnectedDeviceMode(true)
            .setDeviceId(Optional.of(DEVICE_ID))
            .setCountryCode(COUNTRY_CODE)
            .setAdbServer(fakeAdbServer)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void buildingCommandViaFlags_deviceTargetingConfigPathNotSet_throws() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () ->
                EvaluateDeviceTargetingConfigCommand.fromFlags(
                    new FlagParser().parse("--device-properties=" + devicePropertiesPath),
                    fakeAdbServer));

    assertThat(e).hasMessageThat().contains("Missing the required --config flag");
  }

  @Test
  public void buildingCommandViaFlags_devicePropertiesPathNotSet_noConnectedDevice_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                EvaluateDeviceTargetingConfigCommand.fromFlags(
                    new FlagParser().parse("--config=" + deviceTargetingConfigPath),
                    fakeAdbServer));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Missing required flag: Either '--connected-device' or '--device-properties' must be"
                + " specified.");
  }

  @Test
  public void buildingCommandViaFlags_devicePropertiesPathSet_withConnectedDevice_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                EvaluateDeviceTargetingConfigCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--config=" + deviceTargetingConfigPath,
                            "--device-properties=" + devicePropertiesPath,
                            "--connected-device"),
                    fakeAdbServer));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Conflicting options: '--connected-device' and '--device-properties' cannot be present"
                + " together.");
  }

  @Test
  public void buildingCommandViaFlags_withDeviceId_withoutConnectedDevice_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                EvaluateDeviceTargetingConfigCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--config=" + deviceTargetingConfigPath,
                            "--device-properties=" + devicePropertiesPath,
                            "--device-id=test"),
                    fakeAdbServer));

    assertThat(e).hasMessageThat().contains("Device id can only be used with '--connected-device'");
  }

  @Test
  public void
      buildingCommandViaFlags_withoutConnectedDevice_withDevicePropertiesConfig_withAdbPath_throws() {
    Throwable e =
        assertThrows(
            InvalidCommandException.class,
            () ->
                EvaluateDeviceTargetingConfigCommand.fromFlags(
                    new FlagParser()
                        .parse(
                            "--config=" + deviceTargetingConfigPath,
                            "--device-properties=" + devicePropertiesPath,
                            "--adb=" + ADB_PATH),
                    fakeAdbServer));

    assertThat(e).hasMessageThat().contains("Adb path can only be used with '--connected-device'");
  }

  @Test
  public void printHelp_ok() {
    EvaluateDeviceTargetingConfigCommand.help();
  }

  @Test
  public void nonDefaultTierAndMultipleGroups() throws Exception {
    assertOutputIsExpected(
        "multiple_groups_and_selectors.json",
        "very_high_ram_device_properties.json",
        "very_high_ram_multiple_groups_and_selectors_evaluation.txt");
  }

  @Test
  public void nonDefaultTierAndSingleGroup() throws Exception {
    assertOutputIsExpected(
        "multiple_groups_and_selectors.json",
        "mid_ram_device_properties.json",
        "mid_ram_multiple_groups_and_selectors_evaluation.txt");
  }

  @Test
  public void defaultTierSelectedAndNoGroups() throws Exception {
    assertOutputIsExpected(
        "multiple_groups_and_selectors.json",
        "very_low_ram_device_properties.json",
        "no_groups_default_tier_evaluation.txt");
  }

  @Test
  public void countrySetWithMultipleGroupsAndTiers() throws Exception {
    assertOutputIsExpected(
        "country_sets_with_multiple_groups_and_tiers.json",
        "very_high_ram_device_properties.json",
        Optional.of(COUNTRY_CODE),
        "country_sets_with_multiple_groups_and_tiers_evaluation.txt");
  }

  @Test
  public void countrySetWithoutGroupsAndTiers() throws Exception {
    assertOutputIsExpected(
        "only_country_sets.json",
        "very_high_ram_device_properties.json",
        Optional.of(COUNTRY_CODE),
        "only_country_sets_evaluation.txt");
  }

  @Test
  public void countrySetWithoutGroupsAndTiers_countryCodeNotPresentInDefinedCountrySets()
      throws Exception {
    assertOutputIsExpected(
        "only_country_sets.json",
        "very_high_ram_device_properties.json",
        Optional.of("UK"),
        "only_country_sets_restofworld_country_code_evaluation.txt");
  }

  @Test
  public void deviceTierConfigValidatorIsCalled() throws Exception {
    EvaluateDeviceTargetingConfigCommand command =
        EvaluateDeviceTargetingConfigCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--config="
                        + TestData.copyToTempDir(
                            tmp, "testdata/device_targeting_config/tier_with_undefined_group.json"),
                    "--device-properties="
                        + TestData.copyToTempDir(
                            tmp,
                            "testdata/device_targeting_config/mid_ram_device_properties.json")),
            fakeAdbServer);

    CommandExecutionException exception =
        assertThrows(CommandExecutionException.class, () -> command.execute(System.out));

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Tier 1 must specify existing groups, but found undefined group 'undefined_group'.");
  }

  private void assertOutputIsExpected(
      String configFileName, String devicePropertiesFileName, String expectedOutputFileName)
      throws Exception {
    assertOutputIsExpected(
        configFileName,
        devicePropertiesFileName,
        /* countryCode =*/ Optional.empty(),
        expectedOutputFileName);
  }

  private void assertOutputIsExpected(
      String configFileName,
      String devicePropertiesFileName,
      Optional<String> countryCode,
      String expectedOutputFileName)
      throws Exception {
    String testFilePath = "testdata/device_targeting_config/";
    EvaluateDeviceTargetingConfigCommand command =
        EvaluateDeviceTargetingConfigCommand.fromFlags(
            getParsedFlags(
                TestData.copyToTempDir(tmp, testFilePath + configFileName).toString(),
                TestData.copyToTempDir(tmp, testFilePath + devicePropertiesFileName).toString(),
                countryCode),
            fakeAdbServer);

    try (ByteArrayOutputStream outputByteArrayStream = new ByteArrayOutputStream();
        PrintStream outputPrintStream = new PrintStream(outputByteArrayStream);
        Reader textReader = TestData.openReader(testFilePath + expectedOutputFileName)) {
      command.execute(outputPrintStream);
      String expectedOutput = CharStreams.toString(textReader);
      String actualOutput = new String(outputByteArrayStream.toByteArray(), UTF_8);
      actualOutput = actualOutput.replace("\r", "");

      assertThat(actualOutput).isEqualTo(expectedOutput);
    }
  }

  private ParsedFlags getParsedFlags(
      String deviceTierConfigPath, String devicePropertiesPath, Optional<String> countryCode) {
    if (countryCode.isPresent()) {
      return new FlagParser()
          .parse(
              "--config=" + deviceTierConfigPath,
              "--device-properties=" + devicePropertiesPath,
              "--country-code=" + countryCode.get());
    }
    return new FlagParser()
        .parse("--config=" + deviceTierConfigPath, "--device-properties=" + devicePropertiesPath);
  }
}
