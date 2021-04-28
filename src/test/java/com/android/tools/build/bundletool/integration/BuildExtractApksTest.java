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
package com.android.tools.build.bundletool.integration;

import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XHDPI;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.commands.BuildApksCommand;
import com.android.tools.build.bundletool.commands.ExtractApksCommand;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.testing.Aapt2Helper;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildExtractApksTest {

  private final AppBundleSerializer bundleSerializer = new AppBundleSerializer();

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;
  private Path bundlePath;
  private Path outputDir;
  private Path outputFilePath;

  private final Aapt2Command aapt2Command = Aapt2Helper.getAapt2Command();

  private static final DeviceSpec PRE_L_X86_ES_DEVICE =
      mergeSpecs(sdkVersion(19), abis("x86", "armeabi"), locales("es-US"), density(HDPI));
  private static final DeviceSpec L_X86_64_ES_DEVICE =
      mergeSpecs(
          sdkVersion(21), abis("x86_64", "x86", "armeabi"), locales("es-US"), density(XHDPI));

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    bundlePath = tmpDir.resolve("bundle");
    outputDir = tmp.newFolder("output").toPath();
    outputFilePath = outputDir.resolve("app.apks");
  }

  @Test
  public void forPreLDevice() throws Exception {
    AppBundle appBundle = createAppBundle();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = command.execute();

    ExtractApksCommand extractApksCommand =
        ExtractApksCommand.builder()
            .setOutputDirectory(outputDir)
            .setApksArchivePath(apkSetFilePath)
            .setDeviceSpec(PRE_L_X86_ES_DEVICE)
            .build();

    ImmutableList<Path> extractedApks = extractApksCommand.execute();
    assertThat(
            extractedApks
                .stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(toImmutableList()))
        .containsExactly("standalone-x86_hdpi.apk");
  }

  @Test
  public void forLDevice() throws Exception {
    AppBundle appBundle = createAppBundle();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .build();

    Path apkSetFilePath = command.execute();

    ExtractApksCommand extractApksCommand =
        ExtractApksCommand.builder()
            .setOutputDirectory(outputDir)
            .setApksArchivePath(apkSetFilePath)
            .setDeviceSpec(L_X86_64_ES_DEVICE)
            .build();

    ImmutableList<String> extractedApks =
        extractApksCommand.execute().stream()
            .map(Path::getFileName)
            .map(Path::toString)
            .collect(toImmutableList());
    assertThat(extractedApks).hasSize(4);

    assertThat(extractedApks).containsAtLeast("base-xhdpi.apk", "base-es.apk");
    assertThat(extractedApks).containsAnyOf("base-master.apk", "base-master_2.apk");
    assertThat(extractedApks).containsAnyOf("base-x86_64.apk", "base-x86_64_2.apk");
  }

  @Test
  public void forPreLDeviceUniversalApk() throws Exception {
    AppBundle appBundle = createAppBundle();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setApkBuildMode(UNIVERSAL)
            .build();

    Path apkSetFilePath = command.execute();

    ExtractApksCommand extractApksCommand =
        ExtractApksCommand.builder()
            .setOutputDirectory(outputDir)
            .setApksArchivePath(apkSetFilePath)
            .setDeviceSpec(PRE_L_X86_ES_DEVICE)
            .build();

    ImmutableList<Path> extractedApks = extractApksCommand.execute();
    assertThat(
            extractedApks
                .stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(toImmutableList()))
        .containsExactly("universal.apk");
  }

  @Test
  public void forLDeviceUniversalApk() throws Exception {
    AppBundle appBundle = createAppBundle();
    bundleSerializer.writeToDisk(appBundle, bundlePath);

    BuildApksCommand command =
        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(outputFilePath)
            .setAapt2Command(aapt2Command)
            .setApkBuildMode(UNIVERSAL)
            .build();

    Path apkSetFilePath = command.execute();

    ExtractApksCommand extractApksCommand =
        ExtractApksCommand.builder()
            .setOutputDirectory(outputDir)
            .setApksArchivePath(apkSetFilePath)
            .setDeviceSpec(L_X86_64_ES_DEVICE)
            .build();

    ImmutableList<Path> extractedApks = extractApksCommand.execute();
    assertThat(
            extractedApks
                .stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(toImmutableList()))
        .containsExactly("universal.apk");
  }

  /** Creates an {@link AppBundle} with all common components. */
  private static AppBundle createAppBundle() throws Exception {
    return new AppBundleBuilder()
        .addModule(
            "base",
            builder ->
                builder
                    .addFile("lib/x86/libsome.so")
                    .addFile("lib/x86_64/libsome.so")
                    .addFile("res/drawable-hdpi/image.jpg")
                    .addFile("res/drawable-xhdpi/image.jpg")
                    .setNativeConfig(
                        nativeLibraries(
                            targetedNativeDirectory(
                                "lib/x86", nativeDirectoryTargeting(AbiAlias.X86)),
                            targetedNativeDirectory(
                                "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                    .setResourceTable(createResourceTable())
                    .setManifest(androidManifest("com.test.app")))
        .build();
  }

  public static ResourceTable createResourceTable() {
    return new ResourceTableBuilder()
        .addPackage("com.test.app")
        .addDrawableResourceForMultipleDensities(
            "image",
            ImmutableMap.of(
                240, "res/drawable-hdpi/image.jpg", 320, "res/drawable-xhdpi/image.jpg"))
        .addStringResourceForMultipleLocales(
            "text", ImmutableMap.of(/* default locale */ "", "hello", "es", "hola"))
        .build();
  }
}
