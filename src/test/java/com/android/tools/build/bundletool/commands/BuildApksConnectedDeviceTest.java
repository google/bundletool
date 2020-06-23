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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createLdpiHdpiAppBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createMaxSdkBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createMinMaxSdkAppBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createMinSdkBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createX86AppBundle;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithDensity;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider.ANDROID_HOME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeDevice;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildApksConnectedDeviceTest {

  private final AppBundleSerializer bundleSerializer = new AppBundleSerializer();

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  private Path bundlePath;
  private Path outputDir;
  private Path outputFilePath;
  private Path sdkDirPath;

  private AdbServer fakeAdbServer =
      new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());
  private SystemEnvironmentProvider androidHomeProvider;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle");
    outputDir = tmp.newFolder("output").toPath();
    outputFilePath = outputDir.resolve("app.apks");

    sdkDirPath = Files.createDirectory(tmpDir.resolve("android-sdk"));
    setUpAdb(sdkDirPath);
    this.androidHomeProvider =
        new FakeSystemEnvironmentProvider(ImmutableMap.of(ANDROID_HOME, sdkDirPath.toString()));
  }

  private Path setUpAdb(Path sdkDirPath) throws Exception {
    Path adbPath = sdkDirPath.resolve("platform-tools").resolve("adb");
    Files.createDirectories(adbPath.getParent());
    Files.createFile(adbPath);
    adbPath.toFile().setExecutable(true);
    return adbPath;
  }

  @Test
  public void connectedDevice_flagsEquivalent_androidHome() {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    // Optional values.
                    "--connected-device"),
            System.out,
            androidHomeProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            // Must copy instance of the internal executor service.
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(androidHomeProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void connectedDevice_flagsEquivalent_explicitAdbPath() throws Exception {
    Path adbPath = setUpAdb(tmp.newFolder("different-location").toPath());

    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    // Optional values.
                    "--connected-device",
                    "--adb=" + adbPath),
            System.out,
            androidHomeProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(adbPath)
            .setAdbServer(fakeAdbServer)
            // Must copy instance of the internal executor service.
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(androidHomeProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void connectedDevice_flagsEquivalent_deviceId() {
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    // Optional values.
                    "--connected-device",
                    "--device-id=id1"),
            System.out,
            androidHomeProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .setDeviceId("id1")
            // Must copy instance of the internal executor service.
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());
    DebugKeystoreUtils.getDebugSigningConfiguration(androidHomeProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void connectedDevice_universalApk_throws() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand.Builder command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setApkBuildMode(UNIVERSAL)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer);

    Throwable exception = assertThrows(InvalidCommandException.class, command::build);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Optimizing for connected device only possible when running with 'default' mode flag.");
  }

  @Test
  public void connectedDeviceL_bundleTargetsPreL_throws() throws Exception {
    bundleSerializer.writeToDisk(createMaxSdkBundle(/* KitKat */ 19), bundlePath);

    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.XHDPI))));

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "App Bundle targets pre-L devices, but the device has SDK version "
                + "higher or equal to L.");
  }

  @Test
  public void connectedDevicePreL_bundleTargetsLPlus_throws() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(
                        sdkVersion(/* KitKat */ 19), abis("x86"),
                        locales("en-US"), density(DensityAlias.XHDPI)))));

    bundleSerializer.writeToDisk(createMinSdkBundle(21), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("App Bundle targets L+ devices, but the device has SDK version lower than L.");
  }

  @Ignore("Re-enable when minSdk version propagation is fixed.")
  @Test
  public void connectedDeviceL_bundleTargetsMPlus_throws() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(
                        sdkVersion(/* Lollipop */ 21), abis("x86"),
                        locales("en-US"), density(DensityAlias.XHDPI)))));

    bundleSerializer.writeToDisk(createMinSdkBundle(/* Marshmallow */ 23), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("");
  }

  @Test
  public void connectedDeviceMips_bundleTargetsX86_throws() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(
                        sdkVersion(/* Lollipop */ 21), abis("mips"),
                        locales("en-US"), density(DensityAlias.XHDPI)))));

    bundleSerializer.writeToDisk(createX86AppBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [mips], "
                + "app ABIs: [x86]");
  }

  @Test
  public void connectedDeviceN_bundleTargetsLtoM() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(
                        sdkVersion(/* Nougat */ 25), abis("x86"),
                        locales("en-US"), density(DensityAlias.XHDPI)))));

    bundleSerializer.writeToDisk(
        createMinMaxSdkAppBundle(/* Lollipop */ 21, /* Marshmallow */ 23), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("Max SDK version of the App Bundle is lower than SDK version of the device");
  }

  @Test
  public void connectedDevice_noDevicesFound_throws() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception).hasMessageThat().contains("No connected devices found.");
  }

  @Test
  public void connectedDevice_moreThanOneDevice_throws() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.XHDPI)),
                FakeDevice.fromDeviceSpec(
                    "id2", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.MDPI))));

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Throwable exception = assertThrows(CommandExecutionException.class, () -> command.execute());
    assertThat(exception)
        .hasMessageThat()
        .contains("More than one device connected, please provide --device-id.");
  }

  @Test
  public void connectedDevice_correctSplitsGenerated() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.XHDPI))));

    bundleSerializer.writeToDisk(createLdpiHdpiAppBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Path apksArchive = command.execute();
    BuildApksResult result;
    try (ZipFile apksZipFile = new ZipFile(apksArchive.toFile())) {
      assertThat(apksZipFile)
          .containsExactlyEntries("toc.pb", "splits/base-master.apk", "splits/base-xhdpi.apk");
      result = extractTocFromApkSetFile(apksZipFile, outputDir);
    }
    assertThat(result.getVariantList()).hasSize(1);
    Variant variant = result.getVariant(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    // One master and one density split.
    assertThat(apkSet.getApkDescriptionList()).hasSize(2);
    assertThat(apkNamesInSet(apkSet))
        .containsExactly("splits/base-master.apk", "splits/base-xhdpi.apk");
  }

  @Test
  public void connectedDevice_withDeviceId_correctSplitsGenerated() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.XHDPI)),
                FakeDevice.fromDeviceSpec(
                    "id2", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.MDPI))));

    bundleSerializer.writeToDisk(createLdpiHdpiAppBundle(), bundlePath);

    // We are picking the "id2" - MDPI device.
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .setDeviceId("id2")
            .build();

    Path apksArchive = command.execute();
    BuildApksResult result;
    try (ZipFile apksZipFile = new ZipFile(apksArchive.toFile())) {
      assertThat(apksZipFile)
          .containsExactlyEntries("toc.pb", "splits/base-master.apk", "splits/base-mdpi.apk");
      result = extractTocFromApkSetFile(apksZipFile, outputDir);
    }
    assertThat(result.getVariantList()).hasSize(1);
    Variant variant = result.getVariant(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    // One master and one density split.
    assertThat(apkSet.getApkDescriptionList()).hasSize(2);
    assertThat(apkNamesInSet(apkSet))
        .containsExactly("splits/base-master.apk", "splits/base-mdpi.apk");
  }

  @Test
  public void connectedDevice_correctStandaloneGenerated() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(
                        sdkVersion(19), abis("x86"),
                        locales("en-US"), density(DensityAlias.HDPI)))));

    bundleSerializer.writeToDisk(createLdpiHdpiAppBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .build();

    Path apksArchive = command.execute();
    BuildApksResult result;
    try (ZipFile apksZipFile = new ZipFile(apksArchive.toFile())) {
      assertThat(apksZipFile).containsExactlyEntries("toc.pb", "standalones/standalone-hdpi.apk");
      result = extractTocFromApkSetFile(apksZipFile, outputDir);
    }
    assertThat(result.getVariantList()).hasSize(1);
    Variant variant = result.getVariant(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    // One standalone APK.
    assertThat(apkSet.getApkDescriptionList()).hasSize(1);
    assertThat(apkNamesInSet(apkSet)).containsExactly("standalones/standalone-hdpi.apk");
  }

  @Test
  public void connectedDevice_withDeviceId_correctStandaloneGenerated() throws Exception {
    fakeAdbServer =
        new FakeAdbServer(
            /* hasInitialDeviceList= */ true,
            ImmutableList.of(
                FakeDevice.fromDeviceSpec(
                    "id1",
                    DeviceState.ONLINE,
                    mergeSpecs(
                        sdkVersion(19), abis("x86"),
                        locales("en-US"), density(DensityAlias.XHDPI))),
                FakeDevice.fromDeviceSpec(
                    "id2", DeviceState.ONLINE, lDeviceWithDensity(DensityAlias.XXHDPI))));

    bundleSerializer.writeToDisk(createLdpiHdpiAppBundle(), bundlePath);

    // Selecting "id1" device - KitKat, XHDPI.
    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setGenerateOnlyForConnectedDevice(true)
            .setAdbPath(sdkDirPath.resolve("platform-tools").resolve("adb"))
            .setAdbServer(fakeAdbServer)
            .setDeviceId("id1")
            .build();

    Path apksArchive = command.execute();
    BuildApksResult result;
    try (ZipFile apksZipFile = new ZipFile(apksArchive.toFile())) {
      assertThat(apksZipFile).containsExactlyEntries("toc.pb", "standalones/standalone-xhdpi.apk");
      result = extractTocFromApkSetFile(apksZipFile, outputDir);
    }
    assertThat(result.getVariantList()).hasSize(1);
    Variant variant = result.getVariant(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    // One standalone APK.
    assertThat(apkSet.getApkDescriptionList()).hasSize(1);
    assertThat(apkNamesInSet(apkSet)).containsExactly("standalones/standalone-xhdpi.apk");
  }

  public static ImmutableList<String> apkNamesInSet(ApkSet apkSet) {
    return apkSet.getApkDescriptionList().stream()
        .map(ApkDescription::getPath)
        .collect(toImmutableList());
  }
}
