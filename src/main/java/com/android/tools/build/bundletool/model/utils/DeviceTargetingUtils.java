/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.AndroidManifest.DEVICE_GROUP_ELEMENT_NAME;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.primitives.Ints;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utilities for device group and tier values. */
public class DeviceTargetingUtils {
  private static final Pattern DEVICE_GROUP_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

  public static void validateDeviceTierForAssetsDirectory(String directory, String tierName) {
    @Nullable Integer tier = Ints.tryParse(tierName);

    if (tier == null || tier < 0) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Device tiers should be non-negative integers. "
                  + "Found tier '%s' for directory '%s'.",
              tierName, directory)
          .build();
    }
  }

  public static void validateDeviceGroupForConditionalModule(String groupName) {
    if (!DEVICE_GROUP_PATTERN.matcher(groupName).matches()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Device group names should start with a letter and contain only letters, numbers and"
                  + " underscores. Found group named '%s' in '<dist:%s>' element.",
              groupName, DEVICE_GROUP_ELEMENT_NAME)
          .build();
    }
  }

  // Do not instantiate.
  private DeviceTargetingUtils() {}
}
