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
import static com.android.tools.build.bundletool.model.utils.TextureCompressionUtils.TEXTURE_COMPRESSION_FORMAT_TO_MANIFEST_VALUE;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Devices.SdkRuntime;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import java.util.Optional;

/** Utils for {@link DeviceSpec}. */
public final class DeviceSpecUtils {
  private static final String GL_ES_VERSION_FEATURE_PREFIX = "reqGlEsVersion=";

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

  public static boolean isTextureCompressionFormatMissing(DeviceSpec deviceSpec) {
    return deviceSpec.getGlExtensionsList().isEmpty() && !getGlEsVersion(deviceSpec).isPresent();
  }

  public static boolean isDeviceTierMissing(DeviceSpec deviceSpec) {
    return !deviceSpec.hasDeviceTier();
  }

  public static boolean isCountrySetMissing(DeviceSpec deviceSpec) {
    return !deviceSpec.hasCountrySet();
  }

  public static boolean isSdkRuntimeUnspecified(DeviceSpec deviceSpec) {
    return !deviceSpec.hasSdkRuntime();
  }

  /** Extracts the GL ES version, if any, form the device features. */
  public static Optional<Integer> getGlEsVersion(DeviceSpec deviceSpec) {
    try {
      return deviceSpec.getDeviceFeaturesList().stream()
          .filter(deviceFeature -> deviceFeature.startsWith(GL_ES_VERSION_FEATURE_PREFIX))
          .map(
              deviceFeature ->
                  Integer.decode(deviceFeature.substring(GL_ES_VERSION_FEATURE_PREFIX.length())))
          .max(Integer::compareTo);
    } catch (NumberFormatException e) {
      System.err.println(
          "WARNING: the OpenGL ES version in the device spec is not a valid number. It will be"
              + " considered as missing for texture compression format matching with the device.");
    }
    return Optional.empty();
  }

  /** Extracts all the texture compression formats supported by the device. */
  public static ImmutableSet<TextureCompressionFormatAlias>
      getDeviceSupportedTextureCompressionFormats(DeviceSpec deviceSpec) {
    ImmutableSet<TextureCompressionFormatAlias> glExtensionSupportedFormats =
        deviceSpec.getGlExtensionsList().stream()
            .map(TextureCompressionUtils::textureCompressionFormat)
            .flatMap(Streams::stream)
            .collect(toImmutableSet());
    ImmutableList<TextureCompressionFormatAlias> glVersionSupportedFormats =
        DeviceSpecUtils.getGlEsVersion(deviceSpec)
            .map(TextureCompressionUtils::textureCompressionFormatsForGl)
            .orElse(ImmutableList.of());

    return ImmutableSet.<TextureCompressionFormatAlias>builder()
        .addAll(glExtensionSupportedFormats)
        .addAll(glVersionSupportedFormats)
        .build();
  }

  /** Utils for building {@link DeviceSpec} from targetings. */
  public static class DeviceSpecFromTargetingBuilder {
    private final DeviceSpec.Builder deviceSpec;

    DeviceSpecFromTargetingBuilder(DeviceSpec deviceSpec) {
      this.deviceSpec = deviceSpec.toBuilder();
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setSdkVersion(SdkVersionTargeting sdkVersionTargeting) {
      if (!sdkVersionTargeting.equals(SdkVersionTargeting.getDefaultInstance())) {
        deviceSpec.setSdkVersion(
            Iterables.getOnlyElement(sdkVersionTargeting.getValueList()).getMin().getValue());
      }
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setSupportedAbis(AbiTargeting abiTargeting) {
      if (!abiTargeting.equals(AbiTargeting.getDefaultInstance())) {
        deviceSpec.addSupportedAbis(
            AbiName.fromProto(Iterables.getOnlyElement(abiTargeting.getValueList()).getAlias())
                .getPlatformName());
      }
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setScreenDensity(ScreenDensityTargeting screenDensityTargeting) {
      getScreenDensityDpi(screenDensityTargeting).ifPresent(deviceSpec::setScreenDensity);
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setSupportedLocales(LanguageTargeting languageTargeting) {
      if (!languageTargeting.equals(LanguageTargeting.getDefaultInstance())
          && languageTargeting.getValueCount() > 0) {
        deviceSpec.addSupportedLocales(Iterables.getOnlyElement(languageTargeting.getValueList()));
      }
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setSupportedTextureCompressionFormats(
        TextureCompressionFormatTargeting textureTargeting) {
      if (!textureTargeting.equals(TextureCompressionFormatTargeting.getDefaultInstance())) {
        deviceSpec.addAllGlExtensions(
            textureTargeting.getValueList().stream()
                .map(TextureCompressionFormat::getAlias)
                .filter(TEXTURE_COMPRESSION_FORMAT_TO_MANIFEST_VALUE::containsKey)
                .map(TEXTURE_COMPRESSION_FORMAT_TO_MANIFEST_VALUE::get)
                .collect(toImmutableSet()));
        textureTargeting.getValueList().stream()
            .map(TextureCompressionUtils::getMinGlEsVersionRequired)
            .flatMap(Streams::stream)
            .max(Integer::compareTo)
            .map(
                glEsVersion ->
                    GL_ES_VERSION_FEATURE_PREFIX + "0x" + Integer.toHexString(glEsVersion))
            .ifPresent(deviceSpec::addDeviceFeatures);
      }
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setDeviceTier(DeviceTierTargeting deviceTierTargeting) {
      if (!deviceTierTargeting.equals(DeviceTierTargeting.getDefaultInstance())) {
        deviceSpec.setDeviceTier(
            Int32Value.of(Iterables.getOnlyElement(deviceTierTargeting.getValueList()).getValue()));
      }
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setCountrySet(CountrySetTargeting countrySetTargeting) {
      if (!countrySetTargeting.equals(CountrySetTargeting.getDefaultInstance())) {
        deviceSpec.setCountrySet(
            StringValue.of(Iterables.getOnlyElement(countrySetTargeting.getValueList(), "")));
      }
      return this;
    }

    @CanIgnoreReturnValue
    DeviceSpecFromTargetingBuilder setSdkRuntime(SdkRuntimeTargeting sdkRuntimeTargeting) {
      deviceSpec.setSdkRuntime(
          SdkRuntime.newBuilder()
              .setSupported(sdkRuntimeTargeting.getRequiresSdkRuntime())
              .build());
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
