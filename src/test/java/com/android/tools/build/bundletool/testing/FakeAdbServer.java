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

package com.android.tools.build.bundletool.testing;

import static com.google.common.base.Preconditions.checkState;

import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.Device;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/** Fake implementation of {@link com.android.tools.build.bundletool.device.AdbServer} for tests. */
public class FakeAdbServer extends AdbServer {

  private final boolean hasInitialDeviceList;
  private final ImmutableList<Device> devices;
  private boolean initCalled = false;

  public FakeAdbServer(boolean hasInitialDeviceList, ImmutableList<Device> devices) {
    this.hasInitialDeviceList = hasInitialDeviceList;
    this.devices = devices;
  }

  @Override
  public void init(Path pathToAdb) {
    initCalled = true;
  }

  @Override
  public ImmutableList<Device> getDevicesInternal() {
    checkState(initCalled);
    return devices;
  }

  @Override
  public boolean hasInitialDeviceList() {
    checkState(initCalled);
    return hasInitialDeviceList;
  }

  @Override
  public void close() {}
}
