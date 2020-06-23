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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Wraps calling the shell commands via ddmlib library. */
public class AdbShellCommandTask {

  private final String command;
  private final Device device;

  public AdbShellCommandTask(Device device, String commandToExecute) {
    this.device = device;
    this.command = commandToExecute;
  }

  /** Executes a command and returns a list of strings containing the lines of the output. */
  public ImmutableList<String> execute() {
    // Waits forever.
    return execute(0, TimeUnit.SECONDS);
  }

  /** Executes a command and returns a list of strings containing the lines of the output. */
  public ImmutableList<String> execute(long deadline, TimeUnit deadlineUnits) {
    ImmutableList.Builder<String> outputLines = ImmutableList.builder();
    try {
      device.executeShellCommand(
          command,
          new MultiLineReceiver() {

            @Override
            public boolean isCancelled() {
              return false;
            }

            @Override
            public void processNewLines(String[] strings) {
              outputLines.add(strings);
            }
          },
          deadline,
          deadlineUnits);
      return outputLines.build();
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "I/O error while executing 'adb shell %s' on device '%s'.",
              command, device.getSerialNumber())
          .withCause(e)
          .build();
    } catch (TimeoutException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Timeout while executing 'adb shell %s' on device '%s'.",
              command, device.getSerialNumber())
          .withCause(e)
          .build();
    } catch (ShellCommandUnresponsiveException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Unresponsive shell command while executing 'adb shell %s' on device '%s'.",
              command, device.getSerialNumber())
          .withCause(e)
          .build();
    } catch (AdbCommandRejectedException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Rejected 'adb shell %s' command on device '%s'.", command, device.getSerialNumber())
          .withCause(e)
          .build();
    }
  }
}
