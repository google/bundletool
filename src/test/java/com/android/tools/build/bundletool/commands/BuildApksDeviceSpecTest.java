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

import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XHDPI;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.instantApkVariants;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.splitApkVariants;
import static com.android.tools.build.bundletool.testing.ApkSetUtils.extractTocFromApkSetFile;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createInstantBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createLdpiHdpiAppBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createMaxSdkBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createMinMaxSdkAppBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createMinSdkBundle;
import static com.android.tools.build.bundletool.testing.AppBundleFactory.createX86AppBundle;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.createDeviceSpecFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithDensity;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.FakeAdbServer;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class BuildApksDeviceSpecTest {

  private final AppBundleSerializer bundleSerializer = new AppBundleSerializer();

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  private Path bundlePath;
  private Path outputDir;
  private Path outputFilePath;
  private final AdbServer fakeAdbServer =
      new FakeAdbServer(/* hasInitialDeviceList= */ true, /* devices= */ ImmutableList.of());
  private final SystemEnvironmentProvider systemEnvironmentProvider =
      new FakeSystemEnvironmentProvider(/* variables= */ ImmutableMap.of());

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle");
    outputDir = tmp.newFolder("output").toPath();
    outputFilePath = outputDir.resolve("app.apks");
  }

  @Test
  public void deviceSpecFlags_inJavaViaProtos_equivalent() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    DeviceSpec deviceSpec = deviceWithSdk(28);
    Path deviceSpecPath = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--device-spec=" + deviceSpecPath),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setDeviceSpec(deviceSpec)
            // Must copy instance of the internal executor service.
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void deviceSpecFlags_inJavaViaFiles_equivalent() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    DeviceSpec deviceSpec = deviceWithSdk(28);
    Path deviceSpecPath = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksCommand commandViaFlags =
        BuildApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--bundle=" + bundlePath,
                    "--output=" + outputFilePath,
                    "--device-spec=" + deviceSpecPath),
            new PrintStream(output),
            systemEnvironmentProvider,
            fakeAdbServer);

    BuildApksCommand.Builder commandViaBuilder =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            // Optional values.
            .setDeviceSpec(deviceSpecPath)
            // Must copy instance of the internal executor service.
            .setExecutorServiceInternal(commandViaFlags.getExecutorService())
            .setExecutorServiceCreatedByBundleTool(true)
            .setOutputPrintStream(commandViaFlags.getOutputPrintStream().get());

    DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider)
        .ifPresent(commandViaBuilder::setSigningConfiguration);

    assertThat(commandViaBuilder.build()).isEqualTo(commandViaFlags);
  }

  @Test
  public void deviceSpec_universalApk_throws() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand.Builder command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .setApkBuildMode(UNIVERSAL);

    Throwable exception = assertThrows(InvalidCommandException.class, command::build);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Optimizing for device spec is not possible when running with 'universal' mode flag.");
  }

  @Test
  @Theory
  public void deviceSpec_systemApkMode_withoutDeviceSpec_throws() throws Exception {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand.Builder command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setApkBuildMode(SYSTEM);

    Throwable exception = assertThrows(InvalidCommandException.class, command::build);
    assertThat(exception)
        .hasMessageThat()
        .contains("Device spec must always be set when running with 'system' mode flag.");
  }

  @Test
  @Theory
  public void deviceSpec_systemApkMode_partialDeviceSpecWithAbiAndScreenDensity_succeeds()
      throws Exception {
    DeviceSpec deviceSpec = mergeSpecs(abis("arm64-v8a"), density(DensityAlias.MDPI));

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand.Builder command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .setApkBuildMode(SYSTEM);

    command.build();
  }

  @Test
  @Theory
  public void deviceSpec_systemApkMode_partialDeviceSpecMissingAbi_throws() throws Exception {
    DeviceSpec deviceSpec = mergeSpecs(density(DensityAlias.MDPI));

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand.Builder command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .setApkBuildMode(SYSTEM);

    Throwable exception = assertThrows(InvalidCommandException.class, command::build);
    assertThat(exception)
        .hasMessageThat()
        .contains("Device spec must have ABIs set when running with 'system' mode flag.");
  }

  @Test
  public void deviceSpec_andConnectedDevice_throws() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule("base", module -> module.setManifest(androidManifest("com.app")))
            .build();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand.Builder command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .setGenerateOnlyForConnectedDevice(true);

    Throwable exception = assertThrows(InvalidCommandException.class, command::build);
    assertThat(exception)
        .hasMessageThat()
        .contains("Cannot optimize for the device spec and connected device at the same time.");
  }

  @Test
  public void deviceSpec_correctSplitsGenerated() throws Exception {
    DeviceSpec deviceSpec = lDeviceWithDensity(XHDPI);

    bundleSerializer.writeToDisk(createLdpiHdpiAppBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
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
  public void deviceSpec_correctStandaloneGenerated() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(19), abis("x86"), locales("en-US"), density(HDPI));

    bundleSerializer.writeToDisk(createLdpiHdpiAppBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
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
  public void deviceSpecL_bundleTargetsPreL_throws() throws Exception {
    bundleSerializer.writeToDisk(createMaxSdkBundle(/* KitKat */ 19), bundlePath);

    DeviceSpec deviceSpec = lDeviceWithDensity(XHDPI);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "App Bundle targets pre-L devices, but the device has SDK version "
                + "higher or equal to L.");
  }

  @Test
  public void deviceSpecPreL_bundleTargetsLPlus_throws() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(/* KitKat */ 19), abis("x86"),
            locales("en-US"), density(XHDPI));

    bundleSerializer.writeToDisk(createMinSdkBundle(21), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .build();

    IncompatibleDeviceException exception =
        assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("App Bundle targets L+ devices, but the device has SDK version lower than L.");
  }

  @Test
  public void deviceSpecL_bundleTargetsMPlus_throws() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(/* Lollipop */ 21), abis("x86"),
            locales("en-US"), density(XHDPI));

    bundleSerializer.writeToDisk(createMinSdkBundle(/* Marshmallow */ 23), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception).hasMessageThat().contains("");
  }

  @Test
  public void deviceSpecMips_bundleTargetsX86_throws() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(/* Lollipop */ 21), abis("mips"),
            locales("en-US"), density(XHDPI));

    bundleSerializer.writeToDisk(createX86AppBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [mips], "
                + "app ABIs: [x86]");
  }

  @Test
  public void deviceSpecN_bundleTargetsLtoM() throws Exception {
    DeviceSpec deviceSpec =
        mergeSpecs(
            sdkVersion(/* Nougat */ 25), abis("x86"),
            locales("en-US"), density(XHDPI));

    bundleSerializer.writeToDisk(
        createMinMaxSdkAppBundle(/* Lollipop */ 21, /* Marshmallow */ 23), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("Max SDK version of the App Bundle is lower than SDK version of the device");
  }

  @Test
  public void deviceSpec_instantSplitsGenerated() throws Exception {
    DeviceSpec deviceSpec = lDeviceWithDensity(XHDPI);

    bundleSerializer.writeToDisk(createInstantBundle(), bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setDeviceSpec(deviceSpec)
            .build();

    Path apksArchive = command.execute();
    BuildApksResult result;
    try (ZipFile apksZipFile = new ZipFile(apksArchive.toFile())) {
      assertThat(apksZipFile)
          .containsExactlyEntries(
              "toc.pb", "splits/base-master.apk", "instant/instant-base-master.apk");
      result = extractTocFromApkSetFile(apksZipFile, outputDir);
    }
    assertThat(instantApkVariants(result)).hasSize(1);
    assertThat(splitApkVariants(result)).hasSize(1);

    Variant variant = splitApkVariants(result).get(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    ApkSet apkSet = variant.getApkSet(0);
    assertThat(apkSet.getApkDescriptionList()).hasSize(1);
    assertThat(apkNamesInSet(apkSet)).containsExactly("splits/base-master.apk");

    variant = instantApkVariants(result).get(0);
    assertThat(variant.getApkSetList()).hasSize(1);
    apkSet = variant.getApkSet(0);
    assertThat(apkSet.getApkDescriptionList()).hasSize(1);
    assertThat(apkNamesInSet(apkSet)).containsExactly("instant/instant-base-master.apk");
  }

  private static ImmutableList<String> apkNamesInSet(ApkSet apkSet) {
    return apkSet.getApkDescriptionList().stream()
        .map(ApkDescription::getPath)
        .collect(toImmutableList());
  }
}
