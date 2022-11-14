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

import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.io.CharStreams;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrintDeviceTargetingConfigCommandTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path deviceTargetingConfigPath;

  @Before
  public void setUp() {
    Path tmpDir = tmp.getRoot().toPath();
    deviceTargetingConfigPath = tmpDir.resolve("config.json");
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult() {
    PrintDeviceTargetingConfigCommand commandViaFlags =
        PrintDeviceTargetingConfigCommand.fromFlags(
            new FlagParser().parse("--config=" + deviceTargetingConfigPath));

    PrintDeviceTargetingConfigCommand commandViaBuilder =
        PrintDeviceTargetingConfigCommand.builder()
            .setDeviceTargetingConfigurationPath(deviceTargetingConfigPath)
            .setOutputStream(System.out)
            .build();

    assertThat(commandViaBuilder).isEqualTo(commandViaFlags);
  }

  @Test
  public void multipleGroupsAndSelectors_ok() throws Exception {
    assertOutputIsExpected(
        "multiple_groups_and_selectors.json", "multiple_groups_and_selectors_expected_output.txt");
  }

  @Test
  public void groupWithAllDeviceSelectorTypes_ok() throws Exception {
    assertOutputIsExpected(
        "group_with_all_selector_types.json", "group_with_all_selector_types_expected_output.txt");
  }

  @Test
  public void deviceSelectorWithoutRamRule_ok() throws Exception {
    assertOutputIsExpected(
        "selector_without_ram_rule.json", "selector_without_ram_rule_expected_output.txt");
  }

  @Test
  public void deviceSelectorWithMinAndMaxBytes_ok() throws Exception {
    assertOutputIsExpected(
        "selector_with_min_and_max_bytes.json",
        "selector_with_min_and_max_bytes_expected_output.txt");
  }

  @Test
  public void deviceTierConfigValidatorIsCalled() throws Exception {
    PrintDeviceTargetingConfigCommand command =
        PrintDeviceTargetingConfigCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--config="
                        + TestData.copyToTempDir(
                            tmp, "testdata/device_targeting_config/empty_group.json")));

    CommandExecutionException exception =
        assertThrows(CommandExecutionException.class, command::execute);

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Device group 'empty_group' must specify at least one selector.");
  }

  @Test
  public void operandListWithMoreThanThreeOperandsUsesEllipsis() throws Exception {
    assertOutputIsExpected(
        "operand_list_with_more_than_three_operands.json",
        "operand_list_with_more_than_three_operands_expected_output.txt");
  }

  @Test
  public void tierWithMultipleGroups_ok() throws Exception {
    assertOutputIsExpected(
        "tier_with_multiple_groups.json", "tier_with_multiple_groups_expected_output.txt");
  }

  @Test
  public void singleTier_ok() throws Exception {
    assertOutputIsExpected("single_tier.json", "single_tier_expected_output.txt");
  }

  @Test
  public void commonDeviceSelectorOnlyPrintedOnceInDeviceTier() throws Exception {
    assertOutputIsExpected(
        "groups_with_common_device_selector.json",
        "groups_with_common_device_selector_expected_output.txt");
  }

  @Test
  public void tiersWithCommonGroup_ok() throws Exception {
    assertOutputIsExpected(
        "tiers_with_common_group.json", "tiers_with_common_group_expected_output.txt");
  }

  @Test
  public void bytesAreRoundedToTwoDecimalPlaces() throws Exception {
    assertOutputIsExpected(
        "selector_with_bytes_not_multiple_of_1024.json",
        "selector_with_bytes_not_multiple_of_1024_expected_output.txt");
  }

  @Test
  public void onlyCountrySets_ok() throws Exception {
    assertOutputIsExpected("only_country_sets.json", "only_country_sets_expected_output.txt");
  }

  @Test
  public void countrySetsWithDeviceTiers_ok() throws Exception {
    assertOutputIsExpected(
        "country_sets_with_device_tiers.json",
        "country_sets_with_device_tiers_expected_output.txt");
  }

  @Test
  public void buildingCommandViaFlags_deviceTargetingConfigPathNotSet_throws() {
    Throwable e =
        assertThrows(
            RequiredFlagNotSetException.class,
            () -> PrintDeviceTargetingConfigCommand.fromFlags(new FlagParser().parse("")));

    assertThat(e).hasMessageThat().contains("Missing the required --config flag");
  }

  @Test
  public void printHelp_ok() {
    PrintDeviceTargetingConfigCommand.help();
  }

  private void assertOutputIsExpected(String configFileName, String expectedOutputFileName)
      throws Exception {
    String testFilePath = "testdata/device_targeting_config/";

    try (ByteArrayOutputStream outputByteArrayStream = new ByteArrayOutputStream();
        PrintStream outputPrintStream = new PrintStream(outputByteArrayStream);
        Reader textReader = TestData.openReader(testFilePath + expectedOutputFileName)) {
      PrintDeviceTargetingConfigCommand command =
          PrintDeviceTargetingConfigCommand.fromFlags(
              new FlagParser()
                  .parse("--config=" + TestData.copyToTempDir(tmp, testFilePath + configFileName)),
              outputPrintStream);
      command.execute();
      String expectedOutput = CharStreams.toString(textReader);
      String actualOutput = new String(outputByteArrayStream.toByteArray(), UTF_8);
      actualOutput = actualOutput.replace("\r", "");

      assertThat(actualOutput).isEqualTo(expectedOutput);
    }
  }
}
