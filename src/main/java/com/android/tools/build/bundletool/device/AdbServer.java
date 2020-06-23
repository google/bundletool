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

import static java.lang.Thread.sleep;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

/** Manages the BundleTool-Ddmlib interactions. Represents ADB bridge connection. */
public abstract class AdbServer implements Closeable {

  public static final int ADB_TIMEOUT_MS = 60000;

  public abstract void init(Path pathToAdb);

  public ImmutableList<Device> getDevices() throws TimeoutException {
    waitTillInitialDeviceListPopulated(ADB_TIMEOUT_MS);
    return getDevicesInternal();
  }

  protected abstract ImmutableList<Device> getDevicesInternal();

  public abstract boolean hasInitialDeviceList();

  private final void waitTillInitialDeviceListPopulated(long timeoutMs) throws TimeoutException {
    if (hasInitialDeviceList()) {
      return;
    }
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      // We typically need to wait a little for the ADB connection.
      sleep(50);
      while (!hasInitialDeviceList()) {
        if (stopwatch.elapsed().toMillis() > timeoutMs) {
          throw new TimeoutException(
              String.format("Timed out (%d ms) while waiting for ADB.", timeoutMs));
        }
        sleep(1000);
      }
    } catch (InterruptedException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("Interrupted while waiting for ADB.")
          .build();
    }
  }

  @Override
  public abstract void close();
}
