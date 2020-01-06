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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
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
    if (state.equals(State.INITIALIZED)) {
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
    checkState(state.equals(State.INITIALIZED), "Android Debug Bridge is not initialized.");
    return Arrays.stream(adb.getDevices()).map(DdmlibDevice::new).collect(toImmutableList());
  }

  @Override
  public synchronized boolean hasInitialDeviceList() {
    checkState(state.equals(State.INITIALIZED), "Android Debug Bridge is not initialized.");
    return adb.hasInitialDeviceList();
  }

  @Override
  public synchronized void close() {
    if (state.equals(State.INITIALIZED)) {
      AndroidDebugBridge.terminate();
    }
    state = State.CLOSED;
  }
}
