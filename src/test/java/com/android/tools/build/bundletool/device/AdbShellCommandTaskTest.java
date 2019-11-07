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

package com.android.tools.build.bundletool.device;

import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.FakeDevice;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class AdbShellCommandTaskTest {

  @Test
  public void commandExecution_output() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("id1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.injectShellCommandOutput("getprop", () -> "test output");

    AdbShellCommandTask task = new AdbShellCommandTask(fakeDevice, "getprop");
    assertThat(task.execute()).containsExactly("test output");
  }

  @Test
  public void commandExecution_outputMultiLine() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("id1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.injectShellCommandOutput("getprop", () -> "test output\nnew line");

    AdbShellCommandTask task = new AdbShellCommandTask(fakeDevice, "getprop");
    assertThat(task.execute()).containsExactly("test output", "new line").inOrder();
  }

  @Test
  public void commandExecution_timeoutException() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("id1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.injectShellCommandOutput(
        "getprop",
        () -> {
          throw new TimeoutException("Timeout");
        });

    // unknown command will cause timeout.
    AdbShellCommandTask task = new AdbShellCommandTask(fakeDevice, "getprop");
    Throwable e = assertThrows(CommandExecutionException.class, () -> task.execute());
    assertThat(e).hasMessageThat().contains("Timeout while executing 'adb shell");
    assertThat(e).hasCauseThat().isInstanceOf(TimeoutException.class);
  }

  @Test
  public void commandExecution_IOException() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("id1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.injectShellCommandOutput(
        "getprop",
        () -> {
          throw new IOException("I/O error.");
        });

    AdbShellCommandTask task = new AdbShellCommandTask(fakeDevice, "getprop");
    Throwable e = assertThrows(CommandExecutionException.class, () -> task.execute());
    assertThat(e).hasMessageThat().contains("I/O error while executing 'adb shell");
    assertThat(e).hasCauseThat().isInstanceOf(IOException.class);
  }

  @Test
  public void commandExecution_ShellCommandUnresponsive() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("id1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));
    fakeDevice.injectShellCommandOutput(
        "getprop",
        () -> {
          throw new ShellCommandUnresponsiveException();
        });

    AdbShellCommandTask task = new AdbShellCommandTask(fakeDevice, "getprop");
    Throwable e = assertThrows(CommandExecutionException.class, () -> task.execute());
    assertThat(e)
        .hasMessageThat()
        .contains("Unresponsive shell command while executing 'adb shell");
    assertThat(e).hasCauseThat().isInstanceOf(ShellCommandUnresponsiveException.class);
  }

  @Test
  public void commandExecution_AdbCommandRejectedException() {
    FakeDevice fakeDevice =
        FakeDevice.fromDeviceSpec("id1", DeviceState.ONLINE, lDeviceWithLocales("en-US"));

    // The AdbCommandRejectedException can't be instantiated outside the package.
    AdbCommandRejectedException exception = Mockito.mock(AdbCommandRejectedException.class);
    fakeDevice.injectShellCommandOutput(
        "getprop",
        () -> {
          throw exception;
        });

    AdbShellCommandTask task = new AdbShellCommandTask(fakeDevice, "getprop");
    Throwable e = assertThrows(CommandExecutionException.class, () -> task.execute());
    assertThat(e).hasMessageThat().contains("Rejected 'adb shell getprop' command");
  }
}
