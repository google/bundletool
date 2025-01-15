/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.splitters;

import static com.google.common.base.Preconditions.checkState;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.DeviceGroupTargeting;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import java.util.Optional;

/**
 * Splits assets by device group.
 *
 * <p>See {@link AssetsDimensionSplitterFactory} for details of the implementation.
 */
public class DeviceGroupAssetsSplitter {

  /** Creates a {@link ModuleSplitSplitter} capable of splitting assets by device group. */
  public static ModuleSplitSplitter create(boolean stripTargetingSuffix) {
    return AssetsDimensionSplitterFactory.createSplitter(
        AssetsDirectoryTargeting::getDeviceGroup,
        DeviceGroupAssetsSplitter::fromDeviceGroup,
        ApkTargeting::hasDeviceGroupTargeting,
        stripTargetingSuffix ? Optional.of(TargetingDimension.DEVICE_GROUP) : Optional.empty());
  }

  private static ApkTargeting fromDeviceGroup(DeviceGroupTargeting targeting) {
    checkState(
        targeting.getValueCount() == 1,
        "Device Group targeting must have exactly 1 entry - found %s",
        targeting.getValueList());
    return ApkTargeting.newBuilder().setDeviceGroupTargeting(targeting).build();
  }

  // Do not instantiate.
  private DeviceGroupAssetsSplitter() {}
}
