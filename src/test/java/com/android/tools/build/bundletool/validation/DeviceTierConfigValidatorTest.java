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

package com.android.tools.build.bundletool.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceSelector;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.bundle.DeviceTierSet;
import com.android.bundle.UserCountrySet;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceTierConfigValidatorTest {

  @Test
  public void noGroups_noCountrySet_throws() {
    DeviceTierConfig deviceTierConfig = DeviceTierConfig.getDefaultInstance();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        "The device tier config must contain at least one group or user country set.");
  }

  @Test
  public void emptyGroupName_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder().addDeviceGroups(DeviceGroup.getDefaultInstance()).build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig, "Device groups must specify a name.");
  }

  @Test
  public void emptyCountrySetName_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addUserCountrySets(UserCountrySet.getDefaultInstance())
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig, "Country Sets must specify a name.");
  }

  @Test
  public void emptyCountryCodesList_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addUserCountrySets(UserCountrySet.newBuilder().setName("latam"))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig, "Country set 'latam' must specify at least one country code.");
  }

  @Test
  public void duplicateCountrySetNames_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addUserCountrySets(UserCountrySet.newBuilder().setName("latam").addCountryCodes("AR"))
            .addUserCountrySets(UserCountrySet.newBuilder().setName("latam").addCountryCodes("BR"))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        "Country set names should be unique. Found multiple country sets with these names:"
            + " [latam].");
  }

  @Test
  public void duplicateCountryCodes_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addUserCountrySets(UserCountrySet.newBuilder().setName("sea").addCountryCodes("AR"))
            .addUserCountrySets(UserCountrySet.newBuilder().setName("latam").addCountryCodes("AR"))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        "A country code can belong to only one country set. Found multiple occurrences of these"
            + " country codes: [AR].");
  }

  @Test
  public void invalidCountrySetName_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addUserCountrySets(
                UserCountrySet.newBuilder().setName("latam#$%").addCountryCodes("AR"))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        "Country set name should match the regex '^[a-zA-Z][a-zA-Z0-9_]*$', but found 'latam#$%'.");
  }

  @Test
  public void invalidCountryCode_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addUserCountrySets(
                UserCountrySet.newBuilder().setName("latam").addCountryCodes("brazil"))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig, "Country code should match the regex '^[A-Z]{2}$', but found 'brazil'.");
  }

  @Test
  public void groupWithoutSelector_throws() {
    String groupName = "groupWithoutSelector";
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(DeviceGroup.newBuilder().setName(groupName))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        String.format("Device group '%s' must specify at least one selector.", groupName));
  }

  @Test
  public void tierWithoutGroup_throws() {
    int tierLevel = 1;
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(
                DeviceGroup.newBuilder()
                    .setName("groupName")
                    .addDeviceSelectors(DeviceSelector.getDefaultInstance()))
            .setDeviceTierSet(
                DeviceTierSet.newBuilder()
                    .addDeviceTiers(DeviceTier.newBuilder().setLevel(tierLevel)))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig, String.format("Tier %d must specify at least one group", tierLevel));
  }

  @Test
  public void undefinedGroupReferencedInTier_throws() {
    int tierLevel = 1;
    String undefinedGroupName = "undefinedGroup";
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(
                DeviceGroup.newBuilder()
                    .setName("groupName")
                    .addDeviceSelectors(DeviceSelector.getDefaultInstance()))
            .setDeviceTierSet(
                DeviceTierSet.newBuilder()
                    .addDeviceTiers(
                        DeviceTier.newBuilder()
                            .setLevel(tierLevel)
                            .addDeviceGroupNames(undefinedGroupName)))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        String.format(
            "Tier %d must specify existing groups, but found undefined group '%s'.",
            tierLevel, undefinedGroupName));
  }

  @Test
  public void nonPositiveTierLevel_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(
                DeviceGroup.newBuilder()
                    .setName("groupName")
                    .addDeviceSelectors(DeviceSelector.getDefaultInstance()))
            .setDeviceTierSet(
                DeviceTierSet.newBuilder().addDeviceTiers(DeviceTier.getDefaultInstance()))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig, "Each tier must specify a positive level, but found 0.");
  }

  @Test
  public void nonSequentialTierLevels_throws() {
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(
                DeviceGroup.newBuilder()
                    .setName("groupName")
                    .addDeviceSelectors(DeviceSelector.getDefaultInstance()))
            .setDeviceTierSet(
                DeviceTierSet.newBuilder().addDeviceTiers(DeviceTier.newBuilder().setLevel(2)))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        "Tier 1 is undefined. You should define all tiers between 1 and your total number of"
            + " tiers.");
  }

  @Test
  public void duplicateTierLevels_throws() {
    int tierLevel = 1;
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(
                DeviceGroup.newBuilder()
                    .setName("groupName")
                    .addDeviceSelectors(DeviceSelector.getDefaultInstance()))
            .setDeviceTierSet(
                DeviceTierSet.newBuilder()
                    .addDeviceTiers(DeviceTier.newBuilder().setLevel(tierLevel))
                    .addDeviceTiers(DeviceTier.newBuilder().setLevel(tierLevel)))
            .build();

    assertCommandExecutionExceptionIsThrownAndHasMessage(
        deviceTierConfig,
        String.format(
            "Tier %d should be uniquely defined, but it was defined 2 times.", tierLevel));
  }

  @Test
  public void validConfig_ok() {
    String groupName = "groupName";
    DeviceTierConfig deviceTierConfig =
        DeviceTierConfig.newBuilder()
            .addDeviceGroups(
                DeviceGroup.newBuilder()
                    .setName(groupName)
                    .addDeviceSelectors(DeviceSelector.getDefaultInstance()))
            .setDeviceTierSet(
                DeviceTierSet.newBuilder()
                    .addDeviceTiers(
                        DeviceTier.newBuilder().setLevel(1).addDeviceGroupNames(groupName)))
            .build();

    DeviceTierConfigValidator.validateDeviceTierConfig(deviceTierConfig);
  }

  private void assertCommandExecutionExceptionIsThrownAndHasMessage(
      DeviceTierConfig deviceTierConfig, String message) {
    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> DeviceTierConfigValidator.validateDeviceTierConfig(deviceTierConfig));

    assertThat(exception).hasMessageThat().isEqualTo(message);
  }
}
