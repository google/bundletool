/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.ProtoUtils.mergeFromProtos;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.DENSITY_ALIAS_TO_DPI_MAP;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Devices.SdkRuntime;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.util.JsonFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Factory to create {@link DeviceSpec} instances. */
public final class DeviceFactory {

  public static DeviceSpec deviceWithSdkAndCodename(int sdkVersion, String codename) {
    return mergeSpecs(
        sdkVersion(sdkVersion, codename),
        abis("arm64-v8a"),
        density(DensityAlias.MDPI),
        locales("en-US"));
  }

  public static DeviceSpec deviceWithSdk(int sdkVersion) {
    return deviceWithSdkAndCodename(sdkVersion, "REL");
  }

  public static DeviceSpec qDeviceWithLocales(String... locales) {
    return mergeSpecs(
        sdkVersion(Versions.ANDROID_Q_API_VERSION),
        abis("arm64-v8a"),
        density(DensityAlias.MDPI),
        locales(locales));
  }

  public static DeviceSpec lDevice() {
    return deviceWithSdk(Versions.ANDROID_L_API_VERSION);
  }

  public static DeviceSpec lDeviceWithLocales(String... locales) {
    return mergeSpecs(
        sdkVersion(Versions.ANDROID_L_API_VERSION),
        abis("arm64-v8a"),
        density(DensityAlias.MDPI),
        locales(locales));
  }

  public static DeviceSpec lDeviceWithAbis(String... abis) {
    return mergeSpecs(
        sdkVersion(Versions.ANDROID_L_API_VERSION),
        abis(abis),
        density(DensityAlias.MDPI),
        locales("en-US"));
  }

  public static DeviceSpec lDeviceWithDensity(int densityDpi) {
    return mergeSpecs(
        sdkVersion(Versions.ANDROID_L_API_VERSION),
        abis("arm64-v8a"),
        density(densityDpi),
        locales("en-US"));
  }

  public static DeviceSpec lDeviceWithDensity(DensityAlias densityAlias) {
    return mergeSpecs(
        sdkVersion(Versions.ANDROID_L_API_VERSION),
        abis("arm64-v8a"),
        density(densityAlias),
        locales("en-US"));
  }

  public static DeviceSpec lDeviceWithGlExtensions(String... glExtensions) {
    return mergeSpecs(
        sdkVersion(Versions.ANDROID_L_API_VERSION),
        abis("arm64-v8a"),
        density(DensityAlias.MDPI),
        locales("en-US"),
        glExtensions(glExtensions));
  }

  public static DeviceSpec preLDeviceWithGlExtensions(String... glExtensions) {
    return mergeSpecs(
        sdkVersion(15),
        abis("arm64-v8a"),
        density(DensityAlias.MDPI),
        locales("en-US"),
        glExtensions(glExtensions));
  }

  public static DeviceSpec locales(String... locales) {
    return DeviceSpec.newBuilder().addAllSupportedLocales(Arrays.asList(locales)).build();
  }

  public static DeviceSpec abis(String... abis) {
    return DeviceSpec.newBuilder().addAllSupportedAbis(Arrays.asList(abis)).build();
  }

  public static DeviceSpec density(int screenDpi) {
    return DeviceSpec.newBuilder().setScreenDensity(screenDpi).build();
  }

  public static DeviceSpec density(DensityAlias screenDensity) {
    return density(DENSITY_ALIAS_TO_DPI_MAP.get(screenDensity));
  }

  public static DeviceSpec sdkVersion(int sdkVersion) {
    return DeviceSpec.newBuilder().setSdkVersion(sdkVersion).build();
  }

  public static DeviceSpec sdkVersion(int sdkVersion, String codename) {
    return DeviceSpec.newBuilder().setSdkVersion(sdkVersion).setCodename(codename).build();
  }

  public static DeviceSpec deviceFeatures(String... features) {
    return DeviceSpec.newBuilder().addAllDeviceFeatures(Arrays.asList(features)).build();
  }

  public static DeviceSpec glExtensions(String... glExtensions) {
    return DeviceSpec.newBuilder().addAllGlExtensions(Arrays.asList(glExtensions)).build();
  }

  public static DeviceSpec deviceTier(int deviceTier) {
    return DeviceSpec.newBuilder().setDeviceTier(Int32Value.of(deviceTier)).build();
  }

  public static DeviceSpec countrySet(String countrySet) {
    return DeviceSpec.newBuilder().setCountrySet(StringValue.of(countrySet)).build();
  }

  public static DeviceSpec deviceGroups(String... deviceGroups) {
    return DeviceSpec.newBuilder().addAllDeviceGroups(Arrays.asList(deviceGroups)).build();
  }

  public static DeviceSpec sdkRuntimeSupported(boolean supported) {
    return DeviceSpec.newBuilder()
        .setSdkRuntime(SdkRuntime.newBuilder().setSupported(supported))
        .build();
  }

  public static DeviceSpec mergeSpecs(DeviceSpec deviceSpec, DeviceSpec... specParts) {
    return mergeFromProtos(deviceSpec, specParts);
  }

  public static Path createDeviceSpecFile(DeviceSpec deviceSpec, Path destinationFile)
      throws Exception {
    Files.write(destinationFile, JsonFormat.printer().print(deviceSpec).getBytes(UTF_8));
    return destinationFile;
  }

  private DeviceFactory() {}
}
