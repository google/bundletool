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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.Devices.DeviceSpec;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Fake implementation of {@link Device} for tests. */
public class FakeDevice extends Device {

  private final DeviceState state;
  private final AndroidVersion androidVersion;
  private final ImmutableList<String> abis;
  private final int density;
  private final ImmutableList<String> features;
  private final ImmutableList<String> glExtensions;
  private final String serialNumber;
  private final ImmutableMap<String, String> properties;
  private final Map<String, FakeShellCommandAction> commandInjections = new HashMap<>();
  private Optional<SideEffect<InstallOptions>> installApksSideEffect = Optional.empty();
  private Optional<SideEffect<PushOptions>> pushSideEffect = Optional.empty();
  private Optional<RemoveRemotePathSideEffect> removeRemotePathSideEffect = Optional.empty();
  private static final Joiner COMMA_JOINER = Joiner.on(',');
  private static final Joiner DASH_JOINER = Joiner.on('-');
  private static final Joiner LINE_JOINER = Joiner.on(System.getProperty("line.separator"));
  private static final Splitter DASH_SPLITTER = Splitter.on('-');

  FakeDevice(
      String serialNumber,
      DeviceState state,
      int sdkVersion,
      ImmutableList<String> abis,
      int density,
      ImmutableList<String> features,
      ImmutableList<String> glExtensions,
      ImmutableMap<String, String> properties) {
    this.state = state;
    this.androidVersion = new AndroidVersion(sdkVersion);
    this.abis = abis;
    this.density = density;
    this.serialNumber = serialNumber;
    this.properties = properties;
    this.glExtensions = glExtensions;
    this.features = features;
  }

  public static FakeDevice fromDeviceSpecWithProperties(
      String deviceId,
      DeviceState deviceState,
      DeviceSpec deviceSpec,
      ImmutableMap<String, String> properties) {
    FakeDevice device =
        new FakeDevice(
            deviceId,
            deviceState,
            deviceSpec.getSdkVersion(),
            ImmutableList.copyOf(deviceSpec.getSupportedAbisList()),
            deviceSpec.getScreenDensity(),
            ImmutableList.copyOf(deviceSpec.getDeviceFeaturesList()),
            ImmutableList.copyOf(deviceSpec.getGlExtensionsList()),
            properties);
    device.injectShellCommandOutput(
        "pm list features",
        () ->
            LINE_JOINER.join(
                deviceSpec.getDeviceFeaturesList().stream()
                    .map(feature -> "feature:" + feature)
                    .collect(toImmutableList())));
    device.injectShellCommandOutput(
        "am get-config",
        () ->
            LINE_JOINER.join(
                ImmutableList.of(
                    "abi: " + COMMA_JOINER.join(deviceSpec.getSupportedAbisList()),
                    "config: mcc234-mnc15-"
                        + DASH_JOINER.join(
                            deviceSpec.getSupportedLocalesList().stream()
                                .map(FakeDevice::convertLocaleToResourceString)
                                .collect(toImmutableList())))));
    device.injectShellCommandOutput(
        "dumpsys SurfaceFlinger",
        () ->
            LINE_JOINER.join(
                ImmutableList.of(
                    "SurfaceFlinger global state:",
                    "EGL implementation : 1.4",
                    "GLES: FakeDevice, OpenGL ES 3.0",
                    DASH_JOINER.join(deviceSpec.getGlExtensionsList()))));
    return device;
  }

  private static String convertLocaleToResourceString(String s) {
    ImmutableList<String> countryRegion = ImmutableList.copyOf(DASH_SPLITTER.split(s));
    checkArgument(countryRegion.size() > 0 && countryRegion.size() <= 2);
    if (countryRegion.size() > 1) {
      return countryRegion.get(0) + "-r" + countryRegion.get(1);
    } else {
      return countryRegion.get(0);
    }
  }

  public static FakeDevice fromDeviceSpec(
      String deviceId, DeviceState deviceState, DeviceSpec deviceSpec) {
    if (deviceSpec.getSdkVersion() < Versions.ANDROID_M_API_VERSION) {
      Locale deviceLocale = Locale.forLanguageTag(deviceSpec.getSupportedLocales(0));
      return fromDeviceSpecWithProperties(
          deviceId,
          deviceState,
          deviceSpec,
          ImmutableMap.of(
              "ro.product.locale.language",
              deviceLocale.getLanguage(),
              "ro.product.locale.region",
              deviceLocale.getCountry()));
    } else {
      return fromDeviceSpecWithProperties(
          deviceId,
          deviceState,
          deviceSpec,
          ImmutableMap.of("ro.product.locale", deviceSpec.getSupportedLocales(0)));
    }
  }

  public static FakeDevice inDisconnectedState(String deviceId, DeviceState deviceState) {
    checkArgument(!deviceState.equals(DeviceState.ONLINE));
    // In this state, querying device doesn't work.
    return new FakeDevice(
        deviceId,
        deviceState,
        Integer.MAX_VALUE,
        ImmutableList.of(),
        /* density= */ -1,
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableMap.of());
  }

  @Override
  public DeviceState getState() {
    return state;
  }

  @Override
  public AndroidVersion getVersion() {
    return androidVersion;
  }

  @Override
  public ImmutableList<String> getAbis() {
    return abis;
  }

  @Override
  public int getDensity() {
    return density;
  }

  @Override
  public String getSerialNumber() {
    return serialNumber;
  }

  @Override
  public Optional<String> getProperty(String propertyName) {
    return Optional.ofNullable(properties.get(propertyName));
  }

  @Override
  public ImmutableList<String> getDeviceFeatures() {
    return features;
  }

  @Override
  public ImmutableList<String> getGlExtensions() {
    return glExtensions;
  }

  @Override
  public void executeShellCommand(
      String command,
      IShellOutputReceiver receiver,
      long maxTimeToOutputResponse,
      TimeUnit maxTimeUnits)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
          IOException {

    checkState(
        commandInjections.containsKey(command),
        "Command %s not found in command injections.",
        command);
    byte[] data = commandInjections.get(command).onExecute().getBytes(UTF_8);
    receiver.addOutput(data, 0, data.length);
    receiver.flush();
  }

  @Override
  public void installApks(ImmutableList<Path> apks, InstallOptions installOptions) {
    for (Path apk : apks) {
      checkState(Files.exists(apk));
    }
    installApksSideEffect.ifPresent(val -> val.apply(apks, installOptions));
  }

  @Override
  public void push(ImmutableList<Path> files, PushOptions pushOptions) {
    for (Path file : files) {
      checkState(Files.exists(file));
    }
    pushSideEffect.ifPresent(val -> val.apply(files, pushOptions));
  }

  @Override
  public Path syncPackageToDevice(Path localFilePath) {
    checkState(Files.exists(localFilePath));
    return Paths.get("/temp", localFilePath.getFileName().toString());
  }

  @Override
  public void removeRemotePath(
      String remoteFilePath, Optional<String> runAsPackageName, Duration timeout) {
    removeRemotePathSideEffect.ifPresent(
        val -> val.apply(remoteFilePath, runAsPackageName, timeout));
  }

  @Override
  public void pull(ImmutableList<FilePullParams> files) {
    for (FilePullParams filePullParams : files) {
      Path sourcePath = Paths.get(filePullParams.getPathOnDevice());
      try (InputStream inputStream = Files.newInputStream(sourcePath);
          OutputStream outputStream = Files.newOutputStream(filePullParams.getDestinationPath())) {
        ByteStreams.copy(inputStream, outputStream);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public void setInstallApksSideEffect(SideEffect<InstallOptions> sideEffect) {
    installApksSideEffect = Optional.of(sideEffect);
  }

  public void setPushSideEffect(SideEffect<PushOptions> sideEffect) {
    pushSideEffect = Optional.of(sideEffect);
  }

  public void setRemoveRemotePathSideEffect(RemoveRemotePathSideEffect sideEffect) {
    removeRemotePathSideEffect = Optional.of(sideEffect);
  }

  public void clearInstallApksSideEffect() {
    installApksSideEffect = Optional.empty();
  }

  public void injectShellCommandOutput(String command, FakeShellCommandAction action) {
    commandInjections.put(command, action);
  }

  /** Fake shell command action. */
  @FunctionalInterface
  public interface FakeShellCommandAction {
    String onExecute()
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException;
  }

  /** Remove remote path side effect. */
  public interface RemoveRemotePathSideEffect {
    void apply(String remotePath, Optional<String> runAs, Duration timeout);
  }

  /** Side effect. */
  public interface SideEffect<T> {
    void apply(ImmutableList<Path> apks, T options);
  }
}
