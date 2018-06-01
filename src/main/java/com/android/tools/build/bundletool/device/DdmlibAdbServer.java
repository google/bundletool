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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Thread.sleep;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.nio.file.Path;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * Ddmlib-backed implementation of the {@link AdbServer}.
 *
 * <p>This implementation doesn't support swapping the underlying ADB server. Instead it assumes the
 * instance always uses the ADB under the path that was used for initialization.
 */
public class DdmlibAdbServer extends AdbServer {

  private static final DdmlibAdbServer instance = new DdmlibAdbServer();
  final private static long WAIT_DEVICE_LIST_TIMEOUT = 10000; // 10 seconds

  @Nullable private AndroidDebugBridge adb;

  @GuardedBy("this")
  private State state = State.UNINITIALIZED;

  private Path pathToAdb;

  private enum State {
    UNINITIALIZED,
    INITIALIZED,
    CLOSED
  };

  private DdmlibAdbServer() {}

  public static DdmlibAdbServer getInstance() {
    return instance;
  }

  /**
   * Initializes ADB server, optionally restarting it if it points at a different location.
   *
   * <p>Can be called multiple times.
   *
   * @param pathToAdb location of the ADB server to start.
   */
  @Override
  public synchronized void init(Path pathToAdb) {
    checkState(state != State.CLOSED, "Android Debug Bridge has been closed.");
    if (state == State.INITIALIZED) {
      checkState(
          pathToAdb.equals(this.pathToAdb),
          "Re-initializing DdmlibAdbServer with a different ADB path. Expected: '%s', got '%s'.",
          this.pathToAdb,
          pathToAdb);
      return;
    }
    AndroidDebugBridge.initIfNeeded(/* clientSupport= */ false);
    this.adb = AndroidDebugBridge.createBridge(pathToAdb.toString(), /* forceNewBridge= */ false);

    if (adb == null) {
      throw new CommandExecutionException("Failed to start ADB server.");
    }

    this.pathToAdb = pathToAdb;
    this.state = State.INITIALIZED;
  }

  @Override
  public synchronized ImmutableList<Device> getDevicesInternal() {
    checkState(state == State.INITIALIZED, "Android Debug Bridge is not initialized.");

    if (adb.isConnected() && !adb.hasInitialDeviceList()) {
      waitTillDeviceListInit(WAIT_DEVICE_LIST_TIMEOUT);
    }

    return Arrays.stream(adb.getDevices()).map(DdmlibDevice::new).collect(toImmutableList());
  }

  @Override
  public synchronized boolean isConnected() {
    checkState(state == State.INITIALIZED, "Android Debug Bridge is not initialized.");
    return adb.isConnected();
  }

  @Override
  public synchronized void close() {
    if (state == State.INITIALIZED) {
      AndroidDebugBridge.terminate();
    }
    state = State.CLOSED;
  }

  private void waitTillDeviceListInit(long timeoutMs) {
    final long period = 100;
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      // com.android.ddmlib.DeviceMonitor.updateDevices runs in another thread.
      // Even if a device already connected, adb.getDevices() in our thread might still get zero
      // since DeviceMonitor has not detected the connected-device yet.
      // For that, we wait until 1) timeout  2) adb tell us the list has initialized
      while (!adb.hasInitialDeviceList()
              && stopwatch.elapsed().toMillis() < timeoutMs) {
        sleep(period);
      }
    } catch (InterruptedException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Interrupted while waiting for ADB.")
          .build();
    } finally {
      stopwatch.stop();
    }
  }
}
