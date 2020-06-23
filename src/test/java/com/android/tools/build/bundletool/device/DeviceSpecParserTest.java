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

package com.android.tools.build.bundletool.device;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceSpecParserTest {

  @Test
  public void parsesCorrectDeviceSpecFile() throws Exception {
    DeviceSpec deviceSpec =
        DeviceSpecParser.parseDeviceSpec(TestData.openReader("testdata/device/pixel2_spec.json"));
    assertThat(deviceSpec.getSupportedAbisList())
        .containsExactly("arm64-v8a", "armeabi-v7a", "armeabi");
    assertThat(deviceSpec.getScreenDensity()).isEqualTo(560);
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(26);
    assertThat(deviceSpec.getSupportedLocalesList()).containsExactly("en-GB");
  }

  @Test
  public void parsesCorrectDeviceSpecFile_parsePartialDeviceSpec_Succeeds() throws Exception {
    DeviceSpec deviceSpec =
        DeviceSpecParser.parsePartialDeviceSpec(
            TestData.openReader("testdata/device/pixel2_spec.json"));
    assertThat(deviceSpec.getSupportedAbisList())
        .containsExactly("arm64-v8a", "armeabi-v7a", "armeabi");
    assertThat(deviceSpec.getScreenDensity()).isEqualTo(560);
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(26);
    assertThat(deviceSpec.getSupportedLocalesList()).containsExactly("en-GB");
  }

  @Test
  public void wrongFileExtension_throws() throws Exception {
    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () -> DeviceSpecParser.parseDeviceSpec(Paths.get("device_spec.wrong_extension")));
    assertThat(exception)
        .hasMessageThat()
        .contains("Expected .json extension for the device spec file");
  }

  @Test
  public void rejectsNegativeDensity() throws Exception {
    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () ->
                DeviceSpecParser.parseDeviceSpec(
                    TestData.openReader("testdata/device/invalid_spec_density_negative.json")));
    assertThat(exception).hasMessageThat().contains("Device spec screen density (-1)");
  }

  @Test
  public void rejectsSdkLevelZero() throws Exception {
    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () ->
                DeviceSpecParser.parseDeviceSpec(
                    TestData.openReader("testdata/device/invalid_spec_sdk_zero.json")));
    assertThat(exception).hasMessageThat().contains("Device spec SDK version (0)");
  }

  @Test
  public void rejectsEmptyLocaleList() throws Exception {
    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () ->
                DeviceSpecParser.parseDeviceSpec(
                    TestData.openReader("testdata/device/invalid_spec_locales_empty.json")));
    assertThat(exception).hasMessageThat().contains("Device spec supported locales list is empty");
  }

  @Test
  public void parsePartialDeviceSpec_acceptEmptyAbi() throws Exception {
    DeviceSpec deviceSpec =
        DeviceSpecParser.parsePartialDeviceSpec(
            TestData.openReader("testdata/device/invalid_spec_abi_empty.json"));
    assertThat(deviceSpec.getSupportedAbisList()).isEmpty();
    assertThat(deviceSpec.getScreenDensity()).isEqualTo(411);
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(1);
    assertThat(deviceSpec.getSupportedLocalesList()).containsExactly("en-US", "es-US");
  }

  @Test
  public void parsePartialDeviceSpec_acceptEmptyLocaleList() throws Exception {
    DeviceSpec deviceSpec =
        DeviceSpecParser.parsePartialDeviceSpec(
            TestData.openReader("testdata/device/invalid_spec_locales_empty.json"));
    assertThat(deviceSpec.getSupportedAbisList()).containsExactly("armeabi-v7a");
    assertThat(deviceSpec.getScreenDensity()).isEqualTo(411);
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(24);
    assertThat(deviceSpec.getSupportedLocalesList()).isEmpty();
  }

  @Test
  public void parsePartialDeviceSpec_acceptZeroSdk() throws Exception {
    DeviceSpec deviceSpec =
        DeviceSpecParser.parsePartialDeviceSpec(
            TestData.openReader("testdata/device/invalid_spec_sdk_zero.json"));
    assertThat(deviceSpec.getSupportedAbisList()).containsExactly("armeabi-v7a");
    assertThat(deviceSpec.getScreenDensity()).isEqualTo(411);
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(0);
    assertThat(deviceSpec.getSupportedLocalesList()).containsExactly("en-US", "es-US");
  }

  @Test
  public void parsePartialDeviceSpec_acceptZeroDensity() throws Exception {
    DeviceSpec deviceSpec =
        DeviceSpecParser.parsePartialDeviceSpec(
            TestData.openReader("testdata/device/invalid_spec_density_zero.json"));
    assertThat(deviceSpec.getSupportedAbisList()).containsExactly("armeabi-v7a");
    assertThat(deviceSpec.getScreenDensity()).isEqualTo(0);
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(23);
    assertThat(deviceSpec.getSupportedLocalesList()).containsExactly("en-US", "es-US");
  }

  @Test
  public void parsePartialDeviceSpec_rejectsNegativeDensity() throws Exception {
    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () ->
                DeviceSpecParser.parsePartialDeviceSpec(
                    TestData.openReader("testdata/device/invalid_spec_density_negative.json")));
    assertThat(exception).hasMessageThat().contains("Device spec screen density (-1)");
  }
}
