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

import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.Device.InstallOptions;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AdbRunnerTest {

  private static final InstallOptions DEFAULT_INSTALL_OPTIONS =
      InstallOptions.builder()
          .setAllowDowngrade(false)
          .setAllowReinstall(true)
          .setGrantRuntimePermissions(false)
          .setTimeout(Duration.ofMillis(100))
          .build();

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path apkPath;

  @Before
  public void setUp() throws Exception {
    apkPath = Paths.get(tmp.getRoot().toString(), "apkOne.apk");
    Files.createFile(apkPath);
  }

  @Test
  public void installApks_noDeviceId_noConnectedDevices_throws() {
    AdbServer testAdbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of());
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                adbRunner.run(
                    device ->
                        device.installApks(ImmutableList.of(apkPath), DEFAULT_INSTALL_OPTIONS)));
    assertThat(exception)
        .hasMessageThat()
        .contains("Expected to find one connected device, but found none.");
  }

  @Test
  public void installApks_noDeviceId_oneConnectedDevice_ok() {
    AdbServer testAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec("a", DeviceState.ONLINE, lDeviceWithLocales("en-US"))));
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    adbRunner.run(device -> device.installApks(ImmutableList.of(apkPath), DEFAULT_INSTALL_OPTIONS));
  }

  @Test
  public void installApks_noDeviceId_twoConnectedDevices_throws() {
    AdbServer testAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec("a", DeviceState.ONLINE, lDeviceWithLocales("en-US")),
                FakeDevice.inDisconnectedState("b", DeviceState.UNAUTHORIZED)));
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                adbRunner.run(
                    device ->
                        device.installApks(ImmutableList.of(apkPath), DEFAULT_INSTALL_OPTIONS)));
    assertThat(exception)
        .hasMessageThat()
        .contains("Expected to find one connected device, but found 2.");
  }

  @Test
  public void installApks_withDeviceId_noConnectedDevices_throws() {
    AdbServer testAdbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of());
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                adbRunner.run(
                    device ->
                        device.installApks(ImmutableList.of(apkPath), DEFAULT_INSTALL_OPTIONS),
                    "device1"));
    assertThat(exception)
        .hasMessageThat()
        .contains("Expected to find one connected device with serial number 'device1'.");
  }

  @Test
  public void installApks_withDeviceId_connectedDevices_ok() {
    AdbServer testAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "device1", DeviceState.ONLINE, lDeviceWithLocales("en-US")),
                FakeDevice.inDisconnectedState("device2", DeviceState.UNAUTHORIZED)));
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    adbRunner.run(
        device -> device.installApks(ImmutableList.of(apkPath), DEFAULT_INSTALL_OPTIONS),
        "device1");
  }

  @Test
  public void installApks_withDeviceId_disconnectedDevice_throws() {
    FakeDevice disconnectedDevice =
        FakeDevice.inDisconnectedState("device2", DeviceState.UNAUTHORIZED);
    AdbServer testAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "device1", DeviceState.ONLINE, lDeviceWithLocales("en-US")),
                disconnectedDevice));
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    disconnectedDevice.setInstallApksSideEffect(
        (apks, installOptions) -> {
          throw CommandExecutionException.builder()
              .withInternalMessage("Enable USB debugging on the connected device.")
              .build();
        });
    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                adbRunner.run(
                    device ->
                        device.installApks(ImmutableList.of(apkPath), DEFAULT_INSTALL_OPTIONS),
                    "device2"));
    assertThat(exception)
        .hasMessageThat()
        .contains("Enable USB debugging on the connected device.");
  }

  @Test
  public void installApks_allowingDowngrade() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("device1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    AdbServer testAdbServer =
        new FakeAdbServer(/* hasInitialDeviceList= */ true, ImmutableList.of(fakeDevice));
    testAdbServer.init(Paths.get("/test/adb"));
    AdbRunner adbRunner = new AdbRunner(testAdbServer);

    fakeDevice.setInstallApksSideEffect(
        (apks, installOptions) -> {
          if (!installOptions.getAllowDowngrade()) {
            throw new RuntimeException("Downgrade disallowed.");
          }
        });

    adbRunner.run(
        device ->
            device.installApks(
                ImmutableList.of(apkPath),
                InstallOptions.builder().setAllowDowngrade(true).build()));
  }
}
