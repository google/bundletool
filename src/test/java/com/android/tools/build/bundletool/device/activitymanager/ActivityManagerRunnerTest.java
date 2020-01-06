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

package com.android.tools.build.bundletool.device.activitymanager;

import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ActivityManagerRunnerTest {

  private static final String SERIAL_NUMBER = "TESTDEVICE";

  @Test
  public void preLDevice_noResults() {
    Device device =
        FakeDevice.fromDeviceSpec(
            SERIAL_NUMBER,
            DeviceState.ONLINE,
            mergeSpecs(sdkVersion(20), locales("en-US"), density(240), abis("armeabi")));

    ActivityManagerRunner runner = new ActivityManagerRunner(device);
    assertThat(runner.getDeviceLocales()).isEmpty();
    assertThat(runner.getDeviceAbis()).isEmpty();
  }

  @Test
  public void pixelDevice_detectsFeatures() {
    FakeDevice device =
        FakeDevice.fromDeviceSpec(
            SERIAL_NUMBER,
            DeviceState.ONLINE,
            mergeSpecs(sdkVersion(27), locales("en-US"), density(560), abis("armv64-v8a")));

    device.injectShellCommandOutput(
        "am get-config",
        () ->
            Joiner.on('\n')
                .join(
                    ImmutableList.of(
                        "abi: arm64-v8a,armeabi-v7a,armeabi",
                        "config: mcc234-mnc15-en-rGB,in-rID,pl-rPL-ldltr-sw411dp-w411dp-h746dp-"
                            + "normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-"
                            + "keysexposed-nokeys-navhidden-nonav-v27")));
    ActivityManagerRunner runner = new ActivityManagerRunner(device);
    assertThat(runner.getDeviceLocales()).containsExactly("en-GB", "in-ID", "pl-PL").inOrder();
    assertThat(runner.getDeviceAbis())
        .containsExactly("arm64-v8a", "armeabi-v7a", "armeabi")
        .inOrder();
  }
}
