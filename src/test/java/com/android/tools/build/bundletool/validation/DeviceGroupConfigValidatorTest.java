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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceGroupConfig;
import com.android.bundle.DeviceSelector;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceGroupConfigValidatorTest {

  private static final DeviceSelector S1 = DeviceSelector.getDefaultInstance();

  private AppBundle noConfigAppBundle;

  @Before
  public void setUp() {
    BundleModule moduleA =
        new BundleModuleBuilder("a").setManifest(androidManifest("com.test.app")).build();
    noConfigAppBundle =
        AppBundle.buildFromModules(
            ImmutableList.of(moduleA),
            BundleConfig.getDefaultInstance(),
            BundleMetadata.builder().build());
  }

  @Test
  public void noConfig_ok() {
    new DeviceGroupConfigValidator().validateBundle(noConfigAppBundle);
  }

  @Test
  public void noGroups_throws() {
    AppBundle appBundle = bundleWith(DeviceGroupConfig.getDefaultInstance());

    assertInvalidBundleExceptionIsThrownAndHasMessage(
        appBundle, "The device group config must contain at least one device group.");
  }

  @Test
  public void emptyGroupName_throws() {
    AppBundle appBundle =
        bundleWith(
            DeviceGroupConfig.newBuilder()
                .addDeviceGroups(DeviceGroup.getDefaultInstance())
                .build());

    assertInvalidBundleExceptionIsThrownAndHasMessage(
        appBundle, "Device groups must specify a name.");
  }

  @Test
  public void invalidGroupName_throws() {
    AppBundle appBundle =
        bundleWith(
            DeviceGroupConfig.newBuilder()
                .addDeviceGroups(DeviceGroup.newBuilder().setName("123!").addDeviceSelectors(S1))
                .build());

    assertInvalidBundleExceptionIsThrownAndHasMessage(
        appBundle,
        "Device group name should match the regex '^[a-zA-Z][a-zA-Z0-9_]*$', but found '123!'.");
  }

  @Test
  public void groupWithoutSelector_throws() {
    String groupName = "groupWithoutSelector";
    AppBundle appBundle =
        bundleWith(
            DeviceGroupConfig.newBuilder()
                .addDeviceGroups(DeviceGroup.newBuilder().setName(groupName))
                .build());

    assertInvalidBundleExceptionIsThrownAndHasMessage(
        appBundle,
        String.format("Device group '%s' must specify at least one selector.", groupName));
  }

  @Test
  public void duplicateDeviceGroupNames_throws() {
    AppBundle appBundle =
        bundleWith(
            DeviceGroupConfig.newBuilder()
                .addDeviceGroups(DeviceGroup.newBuilder().setName("groupA").addDeviceSelectors(S1))
                .addDeviceGroups(DeviceGroup.newBuilder().setName("groupB").addDeviceSelectors(S1))
                .addDeviceGroups(DeviceGroup.newBuilder().setName("groupA").addDeviceSelectors(S1))
                .build());

    assertInvalidBundleExceptionIsThrownAndHasMessage(
        appBundle, "Found duplicated device group names [groupA].");
  }

  @Test
  public void validConfig_ok() {
    AppBundle appBundle =
        bundleWith(
            DeviceGroupConfig.newBuilder()
                .addDeviceGroups(
                    DeviceGroup.newBuilder().setName("groupName").addDeviceSelectors(S1))
                .build());

    new DeviceGroupConfigValidator().validateBundle(appBundle);
  }

  private AppBundle bundleWith(DeviceGroupConfig deviceGroupConfig) {
    return noConfigAppBundle.toBuilder()
        .setDeviceGroupConfig(Optional.of(deviceGroupConfig))
        .build();
  }

  private void assertInvalidBundleExceptionIsThrownAndHasMessage(
      AppBundle appBundle, String message) {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new DeviceGroupConfigValidator().validateBundle(appBundle));

    assertThat(exception).hasMessageThat().isEqualTo(message);
  }
}
