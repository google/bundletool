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

package com.android.tools.build.bundletool.device;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceProperties;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.util.JsonFormat;
import java.io.Reader;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceTargetingConfigEvaluatorTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void highRamDevice_highestTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "multiple_groups_and_selectors.json", "very_high_ram_device_properties.json");

    assertThat(selectedTier)
        .hasValue(DeviceTier.newBuilder().setLevel(3).addDeviceGroupNames("very_high_ram").build());
  }

  @Test
  public void midRamDevice_midTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "multiple_groups_and_selectors.json", "mid_ram_device_properties.json");

    assertThat(selectedTier)
        .hasValue(DeviceTier.newBuilder().setLevel(1).addDeviceGroupNames("mid_ram").build());
  }

  @Test
  public void lowRamDevice_defaultTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "multiple_groups_and_selectors.json", "very_low_ram_device_properties.json");

    assertThat(selectedTier).isEmpty();
  }

  @Test
  public void tiersWithCommonGroup_highestTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "tiers_with_common_group.json", "very_high_ram_device_properties.json");

    assertThat(selectedTier)
        .hasValue(DeviceTier.newBuilder().setLevel(3).addDeviceGroupNames("exabyte_ram").build());
  }

  @Test
  public void tierWithAllSelectorTypes_nonDefaultTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "group_with_all_selector_types.json", "very_high_ram_device_properties.json");

    assertThat(selectedTier)
        .hasValue(
            DeviceTier.newBuilder().setLevel(1).addDeviceGroupNames("specific_group").build());
  }

  @Test
  public void deviceWithForbiddenFeature_defaultTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "group_with_all_selector_types.json", "greentooth_device_properties.json");

    assertThat(selectedTier).isEmpty();
  }

  @Test
  public void emptyDevice_defaultTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier("multiple_groups_and_selectors.json", "empty_device_properties.json");

    assertThat(selectedTier).isEmpty();
  }

  @Test
  public void noTiersDefined_defaultTierSelected() throws Exception {
    Optional<DeviceTier> selectedTier =
        getSelectedDeviceTier(
            "selector_with_min_and_max_bytes.json", "very_low_ram_device_properties.json");

    assertThat(selectedTier).isEmpty();
  }

  @Test
  public void singleMatchingGroup() throws Exception {
    ImmutableSet<DeviceGroup> matchingGroups =
        getMatchingDeviceGroups(
            "selector_with_min_and_max_bytes.json", "very_low_ram_device_properties.json");

    assertThat(matchingGroups).hasSize(1);
  }

  @Test
  public void multipleMatchingGroups() throws Exception {
    ImmutableSet<DeviceGroup> matchingGroups =
        getMatchingDeviceGroups(
            "multiple_groups_and_selectors.json", "very_high_ram_device_properties.json");

    assertThat(matchingGroups).hasSize(3);
  }

  @Test
  public void noMatchingGroups() throws Exception {
    ImmutableSet<DeviceGroup> matchingGroups =
        getMatchingDeviceGroups(
            "selector_with_min_and_max_bytes.json", "very_high_ram_device_properties.json");

    assertThat(matchingGroups).isEmpty();
  }

  private Optional<DeviceTier> getSelectedDeviceTier(
      String deviceTierConfigFileName, String devicePropertiesFileName) throws Exception {
    DeviceTierConfig deviceTierConfig = loadDeviceTierConfig(deviceTierConfigFileName);
    DeviceProperties deviceProperties = loadDeviceProperties(devicePropertiesFileName);
    return DeviceTargetingConfigEvaluator.getSelectedDeviceTier(deviceTierConfig, deviceProperties);
  }

  private ImmutableSet<DeviceGroup> getMatchingDeviceGroups(
      String deviceTierConfigFileName, String devicePropertiesFileName) throws Exception {
    DeviceTierConfig deviceTierConfig = loadDeviceTierConfig(deviceTierConfigFileName);
    DeviceProperties deviceProperties = loadDeviceProperties(devicePropertiesFileName);
    return DeviceTargetingConfigEvaluator.getMatchingDeviceGroups(
        deviceTierConfig, deviceProperties);
  }

  private DeviceProperties loadDeviceProperties(String devicePropertiesFileName) throws Exception {
    try (Reader devicePropertiesReader =
        BufferedIo.reader(
            TestData.copyToTempDir(
                tmp, "testdata/device_targeting_config/" + devicePropertiesFileName))) {
      DeviceProperties.Builder devicePropertiesBuilder = DeviceProperties.newBuilder();
      JsonFormat.parser().merge(devicePropertiesReader, devicePropertiesBuilder);
      return devicePropertiesBuilder.build();
    }
  }

  private DeviceTierConfig loadDeviceTierConfig(String deviceTierConfigFileName) throws Exception {
    try (Reader configReader =
        BufferedIo.reader(
            TestData.copyToTempDir(
                tmp, "testdata/device_targeting_config/" + deviceTierConfigFileName))) {
      DeviceTierConfig.Builder configBuilder = DeviceTierConfig.newBuilder();
      JsonFormat.parser().merge(configReader, configBuilder);
      return configBuilder.build();
    }
  }
}
