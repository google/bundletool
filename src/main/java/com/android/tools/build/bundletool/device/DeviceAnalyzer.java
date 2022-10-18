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

package com.android.tools.build.bundletool.device;

import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_SANDBOX_MIN_VERSION;
import static com.google.common.base.Preconditions.checkState;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Devices.SdkRuntime;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.activitymanager.ActivityManagerRunner;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/** Computes the device specs. */
public class DeviceAnalyzer {

  private final AdbServer adb;

  // For API M+.
  private static final String LOCALE_PROPERTY_SYS = "persist.sys.locale";
  private static final String LOCALE_PROPERTY_PRODUCT = "ro.product.locale";
  // Older APIs.
  private static final String LEGACY_LANGUAGE_PROPERTY = "ro.product.locale.language";
  private static final String LEGACY_REGION_PROPERTY = "ro.product.locale.region";

  /**
   * Creates the instance of the class.
   *
   * @param adb AdbServer facade, initialized.
   */
  public DeviceAnalyzer(AdbServer adb) {
    this.adb = adb;
  }

  public DeviceSpec getDeviceSpec(Optional<String> deviceId) {
    try {
      Device device = getAndValidateDevice(deviceId);

      // device.getVersion().getApiLevel() returns 1 in case of failure.
      checkState(
          device.getVersion().getApiLevel() > 1,
          "Error retrieving device SDK version. Please try again.");
      // We want to consider device's feature level instead of API level so that targeting matching
      // is done properly on preview builds.
      int deviceSdkVersion = device.getVersion().getFeatureLevel();
      String codename = device.getVersion().getCodename();
      int deviceDensity = device.getDensity();
      checkState(deviceDensity > 0, "Error retrieving device density. Please try again.");
      ImmutableList<String> deviceFeatures = device.getDeviceFeatures();
      ImmutableList<String> glExtensions = device.getGlExtensions();

      ActivityManagerRunner activityManagerRunner = new ActivityManagerRunner(device);
      ImmutableList<String> deviceLocales = activityManagerRunner.getDeviceLocales();
      if (deviceLocales.isEmpty()) {
        // Fallback using properties.
        deviceLocales = ImmutableList.of(getMainLocaleViaProperties(device));
      }
      ImmutableList<String> supportedAbis = activityManagerRunner.getDeviceAbis();
      if (supportedAbis.isEmpty()) {
        // Fallback using properties.
        supportedAbis = device.getAbis();
      }
      checkState(!supportedAbis.isEmpty(), "Error retrieving device ABIs. Please try again.");

      SdkRuntime sdkRuntime =
          SdkRuntime.newBuilder().setSupported(deviceSdkVersion >= SDK_SANDBOX_MIN_VERSION).build();

      DeviceSpec.Builder builder =
          DeviceSpec.newBuilder()
              .setSdkVersion(deviceSdkVersion)
              .addAllSupportedAbis(supportedAbis)
              .addAllSupportedLocales(deviceLocales)
              .setScreenDensity(deviceDensity)
              .addAllDeviceFeatures(deviceFeatures)
              .addAllGlExtensions(glExtensions)
              .setSdkRuntime(sdkRuntime);
      if (codename != null) {
        builder.setCodename(codename);
      }
      return builder.build();
    } catch (TimeoutException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("Timed out while waiting for ADB.")
          .build();
    }
  }

  private String getMainLocaleViaProperties(Device device) {
    Optional<String> locale = Optional.empty();

    int apiLevel = device.getVersion().getApiLevel();
    if (apiLevel < Versions.ANDROID_M_API_VERSION) {
      Optional<String> language = device.getProperty(LEGACY_LANGUAGE_PROPERTY);
      Optional<String> region = device.getProperty(LEGACY_REGION_PROPERTY);
      if (language.isPresent() && region.isPresent()) {
        locale = Optional.of(language.get() + "-" + region.get());
      }
    } else {
      locale = device.getProperty(LOCALE_PROPERTY_SYS);
      if (!locale.isPresent()) {
        locale = device.getProperty(LOCALE_PROPERTY_PRODUCT);
      }
    }
    return locale.orElseGet(
        () -> {
          System.err.println("Warning: Can't detect device locale, will use 'en-US'.");
          return "en-US";
        });
  }

  /** Gets and validates the connected device. */
  public Device getAndValidateDevice(Optional<String> deviceId) throws TimeoutException {
    Device device =
        getTargetDevice(deviceId)
            .orElseThrow(
                () ->
                    CommandExecutionException.builder()
                        .withInternalMessage("Unable to find the requested device.")
                        .build());

    if (device.getState().equals(DeviceState.UNAUTHORIZED)) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Device found but not authorized for connecting. "
                  + "Please allow USB debugging on the device.")
          .build();
    } else if (!device.getState().equals(DeviceState.ONLINE)) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Unable to connect to the device (device state: '%s').", device.getState().name())
          .build();
    }
    return device;
  }

  private Optional<Device> getTargetDevice(Optional<String> deviceId) throws TimeoutException {
    ImmutableList<Device> devices = adb.getDevices();
    if (devices.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage("No connected devices found.")
          .build();
    }
    if (deviceId.isPresent()) {
      return devices.stream()
          .filter(device -> device.getSerialNumber().equals(deviceId.get()))
          .findFirst();
    } else {
      if (devices.size() > 1) {
        throw CommandExecutionException.builder()
            .withInternalMessage("More than one device connected, please provide --device-id.")
            .build();
      }
      return Optional.of(devices.get(0));
    }
  }
}
