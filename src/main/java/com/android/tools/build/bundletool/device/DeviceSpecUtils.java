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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.getScreenDensityDpi;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.AbiName;
import com.google.common.collect.Iterables;

/** Utils for {@link DeviceSpec}. */
public final class DeviceSpecUtils {

  public static boolean isAbiMissing(DeviceSpec deviceSpec) {
    return deviceSpec.getSupportedAbisList().isEmpty();
  }

  public static boolean isScreenDensityMissing(DeviceSpec deviceSpec) {
    return deviceSpec.getScreenDensity() == 0;
  }

  public static boolean isSdkVersionMissing(DeviceSpec deviceSpec) {
    return deviceSpec.getSdkVersion() == 0;
  }

  public static boolean isLocalesMissing(DeviceSpec deviceSpec) {
    return deviceSpec.getSupportedLocalesList().isEmpty();
  }

  /** Utils for building {@link DeviceSpec} from targetings. */
  public static class DeviceSpecFromTargetingBuilder {
    private final DeviceSpec.Builder deviceSpec;

    DeviceSpecFromTargetingBuilder(DeviceSpec deviceSpec) {
      this.deviceSpec = deviceSpec.toBuilder();
    }

    DeviceSpecFromTargetingBuilder setSdkVersion(SdkVersionTargeting sdkVersionTargeting) {
      if (!sdkVersionTargeting.equals(SdkVersionTargeting.getDefaultInstance())) {
        deviceSpec.setSdkVersion(
            Iterables.getOnlyElement(sdkVersionTargeting.getValueList()).getMin().getValue());
      }
      return this;
    }

    DeviceSpecFromTargetingBuilder setSupportedAbis(AbiTargeting abiTargeting) {
      if (!abiTargeting.equals(AbiTargeting.getDefaultInstance())) {
        deviceSpec.addSupportedAbis(
            AbiName.fromProto(Iterables.getOnlyElement(abiTargeting.getValueList()).getAlias())
                .getPlatformName());
      }
      return this;
    }

    DeviceSpecFromTargetingBuilder setScreenDensity(ScreenDensityTargeting screenDensityTargeting) {
      getScreenDensityDpi(screenDensityTargeting).ifPresent(deviceSpec::setScreenDensity);
      return this;
    }

    DeviceSpecFromTargetingBuilder setSupportedLocales(LanguageTargeting languageTargeting) {
      if (!languageTargeting.equals(LanguageTargeting.getDefaultInstance())) {
        deviceSpec.addSupportedLocales(Iterables.getOnlyElement(languageTargeting.getValueList()));
      }
      return this;
    }

    DeviceSpecFromTargetingBuilder setDeviceFeatures(
        DeviceFeatureTargeting deviceFeatureTargeting) {
      throw new UnsupportedOperationException();
    }

    DeviceSpec build() {
      return deviceSpec.build();
    }
  }
}
