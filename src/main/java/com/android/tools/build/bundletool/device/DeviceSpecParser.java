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

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.io.MoreFiles;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Parses the device spec JSON files. This supports two types of parsings, either a full device spec
 * (requires all the fields Ex: SDK, Screen Density, ABI, Language to be present) or partial device
 * spec can be parsed. The methods parseDeviceSpec require the full device spec to be present.
 */
public class DeviceSpecParser {

  private static final String JSON_EXTENSION = "json";

  public static DeviceSpec parseDeviceSpec(Path deviceSpecFile) {
    return parseDeviceSpecInternal(deviceSpecFile, /* canSkipFields= */ false);
  }

  public static DeviceSpec parseDeviceSpec(Reader deviceSpecReader) throws IOException {
    return parseDeviceSpecInternal(deviceSpecReader, /* canSkipFields= */ false);
  }

  public static DeviceSpec parsePartialDeviceSpec(Path deviceSpecFile) {
    return parseDeviceSpecInternal(deviceSpecFile, /* canSkipFields= */ true);
  }

  public static DeviceSpec parsePartialDeviceSpec(Reader deviceSpecReader) throws IOException {
    return parseDeviceSpecInternal(deviceSpecReader, /* canSkipFields= */ true);
  }

  private static DeviceSpec parseDeviceSpecInternal(Path deviceSpecFile, boolean canSkipFields) {
    if (!JSON_EXTENSION.equals(MoreFiles.getFileExtension(deviceSpecFile))) {
      throw InvalidDeviceSpecException.builder()
          .withUserMessage("Expected .json extension for the device spec file.")
          .build();
    }
    try (Reader deviceSpecReader = BufferedIo.reader(deviceSpecFile)) {
      return parseDeviceSpecInternal(deviceSpecReader, canSkipFields);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while reading the device spec file '%s'.", deviceSpecFile), e);
    }
  }

  private static DeviceSpec parseDeviceSpecInternal(Reader deviceSpecReader, boolean canSkipFields)
      throws IOException {
    DeviceSpec.Builder builder = DeviceSpec.newBuilder();
    JsonFormat.parser().merge(deviceSpecReader, builder);
    DeviceSpec deviceSpec = builder.build();
    validateDeviceSpec(deviceSpec, canSkipFields);
    return deviceSpec;
  }

  public static void validateDeviceSpec(DeviceSpec deviceSpec, boolean canSkipFields) {
    if (deviceSpec.getSdkVersion() < 0 || (!canSkipFields && deviceSpec.getSdkVersion() == 0)) {
      throw InvalidDeviceSpecException.builder()
          .withUserMessage(
              "Device spec SDK version (%d) should be set to a strictly positive number.",
              deviceSpec.getSdkVersion())
          .build();
    }
    if (deviceSpec.getScreenDensity() < 0
        || (!canSkipFields && deviceSpec.getScreenDensity() == 0)) {
      throw InvalidDeviceSpecException.builder()
          .withUserMessage(
              "Device spec screen density (%d) should be set to a strictly positive number.",
              deviceSpec.getScreenDensity())
          .build();
    }

    if (!canSkipFields) {
      if (deviceSpec.getSupportedAbisList().isEmpty()) {
        throw InvalidDeviceSpecException.builder()
            .withUserMessage("Device spec supported ABI list is empty.")
            .build();
      }
      if (deviceSpec.getSupportedLocalesList().isEmpty()) {
        throw InvalidDeviceSpecException.builder()
            .withUserMessage("Device spec supported locales list is empty.")
            .build();
      }
    }
  }

  private DeviceSpecParser() {}
}
