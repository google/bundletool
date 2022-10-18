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

import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.DeviceFactory.glExtensions;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Devices.DeviceSpec;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceAnalyzerTest {

  @Test
  public void noDeviceId_noConnectedDevices_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class, () -> analyzer.getDeviceSpec(Optional.empty()));
    assertThat(exception).hasMessageThat().contains("No connected devices found");
  }

  @Test
  public void deviceId_noConnectedDevices_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class, () -> analyzer.getDeviceSpec(Optional.of("a")));
    assertThat(exception).hasMessageThat().contains("No connected devices found");
  }

  @Test
  public void noDeviceId_oneConnectedDevice_sdk21_fine() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                createUsbEnabledDevice("a", /* sdkVersion= */ 21, "fr-CA")));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceSpec spec = new DeviceAnalyzer(fakeAdbServer).getDeviceSpec(Optional.empty());

    assertThat(spec.getScreenDensity()).isEqualTo(480);
    assertThat(spec.getSupportedAbisList()).containsExactly("armeabi");
    assertThat(spec.getSdkVersion()).isEqualTo(21);
    assertThat(spec.getSupportedLocalesList()).containsExactly("fr-CA");
  }

  @Test
  public void noDeviceId_oneConnectedDevice_sdk19_fallBack() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                createDeviceWithNoProperties("a", /* sdkVersion= */ 19, /* locale= */ "de-DE")));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceSpec spec = new DeviceAnalyzer(fakeAdbServer).getDeviceSpec(Optional.empty());

    assertThat(spec.getScreenDensity()).isEqualTo(480);
    assertThat(spec.getSupportedAbisList()).containsExactly("armeabi");
    assertThat(spec.getSdkVersion()).isEqualTo(19);

    // We couldn't detect locale so we expect to fallback to en-US.
    assertThat(spec.getSupportedLocalesList()).containsExactly("en-US");
  }

  @Test
  public void noDeviceId_oneConnectedDevice_sdk26_fine() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                createUsbEnabledDevice("a", /* sdkVersion= */ 26, /* locale= */ "pt-PT")));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceSpec spec = new DeviceAnalyzer(fakeAdbServer).getDeviceSpec(Optional.empty());

    assertThat(spec.getScreenDensity()).isEqualTo(480);
    assertThat(spec.getSupportedAbisList()).containsExactly("armeabi");
    assertThat(spec.getSdkVersion()).isEqualTo(26);
    assertThat(spec.getSupportedLocalesList()).containsExactly("pt-PT");
  }

  @Test
  public void noDeviceId_multipleConnectedDevices_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                createUsbEnabledDevice("a"), createUsbEnabledDevice("b")));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class, () -> analyzer.getDeviceSpec(Optional.empty()));
    assertThat(exception)
        .hasMessageThat()
        .contains("More than one device connected, please provide --device-id.");
  }

  @Test
  public void deviceId_multipleConnectedDevices_match() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                createUsbEnabledDevice("a"),
                createUsbEnabledDevice("b", /* sdkVersion= */ 23, /* locale= */ "pt-BR")));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceSpec spec = new DeviceAnalyzer(fakeAdbServer).getDeviceSpec(Optional.of("b"));

    assertThat(spec.getScreenDensity()).isEqualTo(480);
    assertThat(spec.getSupportedAbisList()).containsExactly("armeabi");
    assertThat(spec.getSdkVersion()).isEqualTo(23);
    assertThat(spec.getSupportedLocalesList()).containsExactly("pt-BR");
  }

  @Test
  public void deviceId_multipleConnectedDevices_noMatch() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                createUsbEnabledDevice("a"),
                createUsbEnabledDevice("b", /* sdkVersion= */ 23, /* locale= */ "ja-JP")));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () -> analyzer.getDeviceSpec(Optional.of("non_existent_id")));
    assertThat(exception).hasMessageThat().contains("Unable to find the requested device.");
  }

  @Test
  public void deviceIdMatch_debuggingNotEnabled_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                FakeDevice.inDisconnectedState("a", DeviceState.UNAUTHORIZED)));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class, () -> analyzer.getDeviceSpec(Optional.of("a")));
    assertThat(exception)
        .hasMessageThat()
        .contains("Device found but not authorized for connecting.");
  }

  @Test
  public void deviceIdMatch_otherUnsupportedState_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                FakeDevice.inDisconnectedState("a", DeviceState.BOOTLOADER)));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class, () -> analyzer.getDeviceSpec(Optional.of("a")));
    assertThat(exception)
        .hasMessageThat()
        .contains("Unable to connect to the device (device state: 'BOOTLOADER')");
  }

  @Test
  public void deviceWithBadSdkVersion_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(density(240), locales("en-US"), abis("armeabi"), sdkVersion(1)))));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(IllegalStateException.class, () -> analyzer.getDeviceSpec(Optional.empty()));
    assertThat(exception)
        .hasMessageThat()
        .contains("Error retrieving device SDK version. Please try again.");
  }

  @Test
  public void deviceWithBadDensity_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(density(-1), locales("en-US"), abis("armeabi"), sdkVersion(21)))));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(IllegalStateException.class, () -> analyzer.getDeviceSpec(Optional.empty()));
    assertThat(exception)
        .hasMessageThat()
        .contains("Error retrieving device density. Please try again.");
  }

  @Test
  public void deviceWithBadAbis_throws() {
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            /* devices= */ ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(density(240), locales("en-US"), abis(), sdkVersion(21)))));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    Throwable exception =
        assertThrows(IllegalStateException.class, () -> analyzer.getDeviceSpec(Optional.empty()));
    assertThat(exception)
        .hasMessageThat()
        .contains("Error retrieving device ABIs. Please try again.");
  }

  @Test
  public void extractsDeviceFeatures() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            "id1",
            DeviceState.ONLINE,
            mergeSpecs(
                density(240),
                locales("en-US"),
                abis("x86"),
                sdkVersion(21),
                deviceFeatures("com.feature1", "com.feature2")));

    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    DeviceSpec deviceSpec = analyzer.getDeviceSpec(Optional.empty());
    assertThat(deviceSpec.getDeviceFeaturesList()).containsExactly("com.feature1", "com.feature2");
  }

  @Test
  public void extractsGlExtensions() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            "id1",
            DeviceState.ONLINE,
            mergeSpecs(
                density(240),
                locales("en-US"),
                abis("x86"),
                sdkVersion(21),
                glExtensions("GL_EXT_extension1", "GL_EXT_extension2")));

    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    DeviceSpec deviceSpec = analyzer.getDeviceSpec(Optional.empty());
    assertThat(deviceSpec.getGlExtensionsList())
        .containsExactly("GL_EXT_extension1", "GL_EXT_extension2");
  }

  @Test
  public void prefersAbisLocalesViaActivityManager() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            "id1",
            DeviceState.ONLINE,
            mergeSpecs(sdkVersion(27), locales("en-US"), density(560), abis("armv64-v8a")));

    fakeDevice.injectShellCommandOutput(
        "am get-config",
        () ->
            Joiner.on('\n')
                .join(
                    ImmutableList.of(
                        "abi: arm64-v8a,armeabi-v7a,armeabi",
                        "config: mcc234-mnc15-en-rGB,in-rID,pl-rPL-ldltr-sw411dp-w411dp-h746dp-"
                            + "normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-"
                            + "keysexposed-nokeys-navhidden-nonav-v27")));

    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    DeviceSpec deviceSpec = analyzer.getDeviceSpec(Optional.empty());

    assertThat(deviceSpec.getSupportedAbisList())
        .containsExactly("arm64-v8a", "armeabi-v7a", "armeabi")
        .inOrder();
    assertThat(deviceSpec.getSupportedLocalesList())
        .containsExactly("en-GB", "in-ID", "pl-PL")
        .inOrder();
  }

  @Test
  public void activityManagerFails_propertiesFallback() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            "id1",
            DeviceState.ONLINE,
            mergeSpecs(sdkVersion(26), locales("de-DE"), density(480), abis("armeabi")));
    fakeDevice.injectShellCommandOutput("am get-config", () -> "error");

    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceSpec spec = new DeviceAnalyzer(fakeAdbServer).getDeviceSpec(Optional.empty());

    assertThat(spec.getScreenDensity()).isEqualTo(480);
    assertThat(spec.getSupportedAbisList()).containsExactly("armeabi");
    assertThat(spec.getSdkVersion()).isEqualTo(26);
    assertThat(spec.getSupportedLocalesList()).containsExactly("de-DE");
  }

  @Test
  public void activityManagerFails_noProperties_defaultLocaleFallback() {
    // It creates no properties, hence locale fetch via properties should also fall back.
    FakeDevice fakeDevice =
        createDeviceWithNoProperties("a", /* sdkVersion= */ 26, /* locale= */ "de-DE");
    fakeDevice.injectShellCommandOutput("am get-config", () -> "error");

    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceSpec spec = new DeviceAnalyzer(fakeAdbServer).getDeviceSpec(Optional.empty());

    assertThat(spec.getScreenDensity()).isEqualTo(480);
    assertThat(spec.getSupportedAbisList()).containsExactly("armeabi");
    assertThat(spec.getSdkVersion()).isEqualTo(26);

    // We couldn't detect locale so we expect to fallback to en-US.
    assertThat(spec.getSupportedLocalesList()).containsExactly("en-US");
  }

  @Test
  public void getDeviceSpec_deviceVersionIsPreview_sdkLevelReturnsFeatureLevel() {
    int apiLevel = 21;
    int featureLevel = 22;
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            "id1",
            DeviceState.ONLINE,
            mergeSpecs(
                density(240), locales("en-US"), abis("x86"), sdkVersion(apiLevel, "codeName")));

    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));

    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    DeviceSpec deviceSpec = analyzer.getDeviceSpec(Optional.empty());
    assertThat(deviceSpec.getSdkVersion()).isEqualTo(featureLevel);
  }

  @Test
  public void getDeviceSpec_deviceVersionIsSandboxMin_sdkRuntimeSupported() {
    String deviceId = "id1";
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            deviceId,
            DeviceState.ONLINE,
            mergeSpecs(
                density(240), locales("en-US"), abis("x86"), sdkVersion(SDK_SANDBOX_MIN_VERSION)));
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));
    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    DeviceSpec deviceSpec = analyzer.getDeviceSpec(Optional.of(deviceId));

    assertThat(deviceSpec.getSdkRuntime().getSupported()).isTrue();
  }

  @Test
  public void getDeviceSpec_deviceVersionNotSandboxMin_sdkRuntimeNotSupported() {
    String deviceId = "id1";
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec(
            deviceId,
            DeviceState.ONLINE,
            mergeSpecs(
                density(240),
                locales("en-US"),
                abis("x86"),
                sdkVersion(SDK_SANDBOX_MIN_VERSION - 1)));
    FakeAdbServer fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of(fakeDevice));
    fakeAdbServer.init(Paths.get("path/to/adb"));
    DeviceAnalyzer analyzer = new DeviceAnalyzer(fakeAdbServer);

    DeviceSpec deviceSpec = analyzer.getDeviceSpec(Optional.of(deviceId));

    assertThat(deviceSpec.getSdkRuntime().getSupported()).isFalse();
  }

  private static Device createUsbEnabledDevice(String serialNumber) {
    return createUsbEnabledDevice(serialNumber, /* sdkVersion= */ 21, /* locale= */ "en-US");
  }

  private static Device createUsbEnabledDevice(String serialNumber, int sdkVersion, String locale) {
    return FakeDevice.fromDeviceSpec(
        serialNumber,
        DeviceState.ONLINE,
        mergeSpecs(sdkVersion(sdkVersion), abis("armeabi"), locales(locale), density(480)));
  }

  private static FakeDevice createDeviceWithNoProperties(
      String serialNumber, int sdkVersion, String locale) {
    return FakeDevice.fromDeviceSpecWithProperties(
        serialNumber,
        DeviceState.ONLINE,
        mergeSpecs(sdkVersion(sdkVersion), abis("armeabi"), locales(locale), density(480)),
        ImmutableMap.of());
  }
}
