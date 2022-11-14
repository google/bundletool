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

package com.android.tools.build.bundletool.model;

import com.android.bundle.Devices.DeviceSpec;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/** Request to compute the size of APKs for a given device spec. */
public interface GetSizeRequest {

  /** Dimensions to expand the sizes against. */
  enum Dimension {
    SDK,
    ABI,
    SCREEN_DENSITY,
    LANGUAGE,
    TEXTURE_COMPRESSION_FORMAT,
    DEVICE_TIER,
    COUNTRY_SET,
    SDK_RUNTIME,
    ALL
  }

  DeviceSpec getDeviceSpec();

  Optional<ImmutableSet<String>> getModules();

  ImmutableSet<Dimension> getDimensions();

  /** Gets whether instant APKs should be used for size calculation. */
  boolean getInstant();
}
