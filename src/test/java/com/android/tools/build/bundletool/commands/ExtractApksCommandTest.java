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

package com.android.tools.build.bundletool.commands;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXHDPI;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksDirectory;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createConditionalApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createInstantApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSdkApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createStandaloneApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariantForSingleSplitApk;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.multiAbiTargetingApexVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.splitApkDescription;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.createDeviceSpecFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDevice;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkRuntimeSupported;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDeviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeModuleTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleFeatureTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.moduleMinSdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.multiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.DefaultTargetingValue;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.ExtractApksResult;
import com.android.bundle.Commands.ExtractedApk;
import com.android.bundle.Commands.FeatureModuleType;
import com.android.bundle.Commands.LocalTestingInfo;
import com.android.bundle.Commands.LocalTestingInfoForMetadata;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.PermanentlyFusedModule;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.util.JsonFormat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ExtractApksCommandTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void missingDeviceSpecFlag_throws() throws Exception {
    Path apksArchiveFile = createApksArchiveFile(minimalApkSet(), tmpDir.resolve("bundle.apks"));

    expectMissingRequiredFlagException(
        "device-spec",
        () -> ExtractApksCommand.fromFlags(new FlagParser().parse("--apks=" + apksArchiveFile)));
  }

  @Test
  public void missingApksArchiveFlag_throws() throws Exception {
    Path deviceSpecFile =
        createDeviceSpecFile(DeviceSpec.getDefaultInstance(), tmpDir.resolve("device.json"));

    expectMissingRequiredFlagException(
        "apks",
        () ->
            ExtractApksCommand.fromFlags(
                new FlagParser().parse("--device-spec=" + deviceSpecFile)));
  }

  @Test
  public void nonExistentApksArchiveFile_throws() throws Exception {
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(21), tmpDir.resolve("device.json"));

    Throwable exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ExtractApksCommand.fromFlags(
                        new FlagParser()
                            .parse("--device-spec=" + deviceSpecFile, "--apks=nonexistent"))
                    .execute());

    assertThat(exception).hasMessageThat().contains("File 'nonexistent' was not found");
  }

  @Test
  public void nonExistentDeviceSpecFile_throws() throws Exception {
    Path apksArchiveFile = createApksArchiveFile(minimalApkSet(), tmpDir.resolve("bundle.apks"));

    Throwable exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ExtractApksCommand.fromFlags(
                    new FlagParser()
                        .parse("--device-spec=not-found.pb", "--apks=" + apksArchiveFile)));

    assertThat(exception).hasMessageThat().contains("File 'not-found.pb' was not found");
  }

  @Test
  public void outputDirectoryCreatedIfDoesNotExist() throws Exception {
    Path apksArchivePath = createApksArchiveFile(minimalApkSet(), tmpDir.resolve("bundle.apks"));
    DeviceSpec deviceSpec = deviceWithSdk(21);

    Path outputDirectory = tmpDir.resolve("directory-that-does-not-exist");
    ExtractApksCommand.builder()
        .setApksArchivePath(apksArchivePath)
        .setDeviceSpec(deviceSpec)
        .setOutputDirectory(outputDirectory)
        .build()
        .execute();

    assertThat(Files.exists(outputDirectory)).isTrue();
  }

  @Test
  public void outputDirectorySetWhenUsingDirectory_throws() throws Exception {
    Path apksArchivePath = createApksDirectory(minimalApkSet(), tmpDir);
    DeviceSpec deviceSpec = deviceWithSdk(21);

    Throwable exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ExtractApksCommand.builder()
                    .setApksArchivePath(apksArchivePath)
                    .setDeviceSpec(deviceSpec)
                    .setOutputDirectory(tmpDir)
                    .build()
                    .execute());

    assertThat(exception)
        .hasMessageThat()
        .contains("Output directory should not be set when APKs are inside directory");
  }

  @Test
  public void permanentlyMergedModule() throws Exception {
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLBase))))
            .addPermanentlyFusedModules(
                PermanentlyFusedModule.newBuilder().setName("permanent").build())
            .build();

    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(21), tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--device-spec=" + deviceSpecFile,
                    "--apks=" + apksArchiveFile,
                    "--modules=permanent"));

    ImmutableList<Path> matchedApks = command.execute();
    assertThat(matchedApks.stream().map(apk -> apk.getFileName().toString()))
        .containsExactly("apkL-base.apk");
  }

  @Test
  public void nonExistentModule_throws() throws Exception {
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLBase))))
            .build();

    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(21), tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--device-spec=" + deviceSpecFile,
                    "--apks=" + apksArchiveFile,
                    "--modules=unknown_module"));

    Throwable exception = assertThrows(InvalidCommandException.class, command::execute);

    assertThat(exception)
        .hasMessageThat()
        .contains("The APK Set archive does not contain the following modules: [unknown_module]");
  }

  @Test
  public void emptyModules_throws() throws Exception {
    Path apksArchiveFile = createApksArchiveFile(minimalApkSet(), tmpDir.resolve("bundle.apks"));
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(21), tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile, "--modules="));

    Throwable exception = assertThrows(InvalidCommandException.class, command::execute);

    assertThat(exception).hasMessageThat().contains("The set of modules cannot be empty.");
  }

  @Test
  public void deviceSpecFromPbJson() throws Exception {
    DeviceSpec.Builder expectedDeviceSpecBuilder = DeviceSpec.newBuilder();
    try (Reader reader = TestData.openReader("testdata/device/pixel2_spec.json")) {
      JsonFormat.parser().merge(reader, expectedDeviceSpecBuilder);
    }
    DeviceSpec expectedDeviceSpec = expectedDeviceSpecBuilder.build();

    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));
    Path deviceSpecFile = TestData.copyToTempDir(tmp, "testdata/device/pixel2_spec.json");

    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    assertThat(command.getDeviceSpec()).isEqualTo(expectedDeviceSpec);
  }

  @Test
  public void deviceSpecUnknownExtension_throws() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("bad_filename.dat"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () ->
                ExtractApksCommand.fromFlags(
                    new FlagParser()
                        .parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile)));

    assertThat(exception).hasMessageThat().contains("Expected .json extension for the device spec");
  }

  @Test
  public void deviceSpecViaJavaApi_invalid_throws() throws Exception {
    DeviceSpec invalidDeviceSpec = deviceWithSdk(-1);
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    Throwable exception =
        assertThrows(
            InvalidDeviceSpecException.class,
            () ->
                ExtractApksCommand.builder()
                    .setApksArchivePath(apksArchiveFile)
                    .setDeviceSpec(invalidDeviceSpec)
                    .build());

    assertThat(exception).hasMessageThat().contains("Device spec SDK version");
  }

  @Test
  public void builderAndFlagsConstruction_inJavaViaProtos_equivalent() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ExtractApksCommand fromFlags =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    ExtractApksCommand fromBuilderApi =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_inJavaViaFiles_equivalent() throws Exception {
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(21), tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ExtractApksCommand fromFlags =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    ExtractApksCommand fromBuilderApi =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpecFile)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalModules_equivalent() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ExtractApksCommand fromFlags =
        ExtractApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--device-spec=" + deviceSpecFile,
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--modules=base"));

    ExtractApksCommand fromBuilderApi =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setModules(ImmutableSet.of("base"))
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalInstant_equivalent() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ExtractApksCommand fromFlags =
        ExtractApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--device-spec=" + deviceSpecFile,
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--instant"));

    ExtractApksCommand fromBuilderApi =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setInstant(true)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalInstantFalse_equivalent() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ExtractApksCommand fromFlags =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    ExtractApksCommand fromBuilderApi =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setInstant(false)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalOutputDirectory_equivalent() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = minimalApkSet();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ExtractApksCommand fromFlags =
        ExtractApksCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--device-spec=" + deviceSpecFile,
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--output-dir=" + tmp.getRoot()));

    ExtractApksCommand fromBuilderApi =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @DataPoints("apksInDirectory")
  public static final ImmutableSet<Boolean> APKS_IN_DIRECTORY = ImmutableSet.of(true, false);

  @Test
  @Theory
  public void oneModule_Ldevice_matchesLmasterSplit(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    ZipPath apkOne = ZipPath.create("apk_one.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(sdkVersionFrom(21)),
                    ApkTargeting.getDefaultInstance(),
                    apkOne))
            .build();
    Path apksPath = createApks(tableOfContentsProto, apksInDirectory);

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ExtractApksCommand.Builder extractedApksCommand =
        ExtractApksCommand.builder().setApksArchivePath(apksPath).setDeviceSpec(deviceSpec);
    if (!apksInDirectory) {
      extractedApksCommand.setOutputDirectory(tmpDir);
    }
    ImmutableList<Path> matchedApks = extractedApksCommand.build().execute();

    if (apksInDirectory) {
      assertThat(matchedApks).containsExactly(inTempDirectory(apkOne.toString()));
    } else {
      assertThat(matchedApks).containsExactly(inOutputDirectory(apkOne.getFileName()));
    }
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  @Theory
  public void oneModule_Mdevice_matchesMSplit(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    ZipPath apkPreL = ZipPath.create("standalones/apkPreL.apk");
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkM = ZipPath.create("splits/apkM.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        SdkVersion.getDefaultInstance(),
                        ImmutableSet.of(sdkVersionFrom(21), sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkPreL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        sdkVersionFrom(21),
                        ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        sdkVersionFrom(23),
                        ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(21))),
                    ApkTargeting.getDefaultInstance(),
                    apkM))
            .build();
    Path apksPath = createApks(tableOfContentsProto, apksInDirectory);

    DeviceSpec deviceSpec = deviceWithSdk(23);

    ExtractApksCommand.Builder extractedApksCommand =
        ExtractApksCommand.builder().setApksArchivePath(apksPath).setDeviceSpec(deviceSpec);
    if (!apksInDirectory) {
      extractedApksCommand.setOutputDirectory(tmpDir);
    }
    ImmutableList<Path> matchedApks = extractedApksCommand.build().execute();

    if (apksInDirectory) {
      assertThat(matchedApks).containsExactly(inTempDirectory(apkM.toString()));
    } else {
      assertThat(matchedApks).containsExactly(inOutputDirectory(apkM.getFileName()));
    }
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  @Theory
  public void oneModule_Kdevice_matchesPreLSplit(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    ZipPath apkPreL = ZipPath.create("standalones/apkPreL.apk");
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkM = ZipPath.create("splits/apkM.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        SdkVersion.getDefaultInstance(),
                        ImmutableSet.of(sdkVersionFrom(21), sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkPreL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        sdkVersionFrom(21),
                        ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        sdkVersionFrom(23),
                        ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(21))),
                    ApkTargeting.getDefaultInstance(),
                    apkM))
            .build();

    Path apksPath = createApks(tableOfContentsProto, apksInDirectory);

    DeviceSpec deviceSpec = deviceWithSdk(19);

    ExtractApksCommand.Builder extractedApksCommand =
        ExtractApksCommand.builder().setApksArchivePath(apksPath).setDeviceSpec(deviceSpec);
    if (!apksInDirectory) {
      extractedApksCommand.setOutputDirectory(tmpDir);
    }
    ImmutableList<Path> matchedApks = extractedApksCommand.build().execute();

    if (apksInDirectory) {
      assertThat(matchedApks).containsExactly(inTempDirectory(apkPreL.toString()));
    } else {
      assertThat(matchedApks).containsExactly(inOutputDirectory(apkPreL.getFileName()));
    }
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  @Theory
  public void oneModule_Ldevice_matchesLSplit(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    ZipPath apkPreL = ZipPath.create("standalones/apkPreL.apk");
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkM = ZipPath.create("splits/apkM.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        SdkVersion.getDefaultInstance(),
                        ImmutableSet.of(sdkVersionFrom(21), sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkPreL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        sdkVersionFrom(21),
                        ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(
                        sdkVersionFrom(23),
                        ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(21))),
                    ApkTargeting.getDefaultInstance(),
                    apkM))
            .build();

    Path apksPath = createApks(tableOfContentsProto, apksInDirectory);
    DeviceSpec deviceSpec = deviceWithSdk(21);

    ExtractApksCommand.Builder extractedApksCommand =
        ExtractApksCommand.builder().setApksArchivePath(apksPath).setDeviceSpec(deviceSpec);
    if (!apksInDirectory) {
      extractedApksCommand.setOutputDirectory(tmpDir);
    }
    ImmutableList<Path> matchedApks = extractedApksCommand.build().execute();

    if (apksInDirectory) {
      assertThat(matchedApks).containsExactly(inTempDirectory(apkL.toString()));
    } else {
      assertThat(matchedApks).containsExactly(inOutputDirectory(apkL.getFileName()));
    }
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void apexModule_noMatch() throws Exception {
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                multiAbiTargetingApexVariant(
                    multiAbiTargeting(X86_64), ZipPath.create("standalones/standalone-x86_64.apk")))
            .build();

    Path apksPath = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));
    ExtractApksCommand.Builder extractedApksCommand =
        ExtractApksCommand.builder().setApksArchivePath(apksPath).setDeviceSpec(abis("x86"));

    Throwable exception =
        assertThrows(
            IncompatibleDeviceException.class, () -> extractedApksCommand.build().execute());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "No set of ABI architectures that the app supports is contained in the ABI "
                + "architecture set of the device.");
  }

  @Test
  @Theory
  public void apexModule_getsBestPossibleApk(
      @FromDataPoints("apksInDirectory") boolean apksInDirectory) throws Exception {
    ZipPath x64Apk = ZipPath.create("standalones/standalone-x86_64.apk");
    ZipPath x64X86Apk = ZipPath.create("standalones/standalone-x86_64.x86.apk");
    ZipPath x64ArmApk = ZipPath.create("standalones/standalone-x86_64.arm64_v8a.apk");

    MultiAbiTargeting x64Targeting =
        multiAbiTargeting(
            ImmutableSet.of(ImmutableSet.of(X86_64)),
            ImmutableSet.of(ImmutableSet.of(X86_64, X86), ImmutableSet.of(X86_64, ARM64_V8A)));
    MultiAbiTargeting x64X86Targeting =
        multiAbiTargeting(
            ImmutableSet.of(ImmutableSet.of(X86_64, X86)),
            ImmutableSet.of(ImmutableSet.of(X86_64), ImmutableSet.of(X86_64, ARM64_V8A)));
    MultiAbiTargeting x64ArmTargeting =
        multiAbiTargeting(
            ImmutableSet.of(ImmutableSet.of(X86_64, ARM64_V8A)),
            ImmutableSet.of(ImmutableSet.of(X86_64), ImmutableSet.of(X86_64, X86)));

    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(multiAbiTargetingApexVariant(x64Targeting, x64Apk))
            .addVariant(multiAbiTargetingApexVariant(x64X86Targeting, x64X86Apk))
            .addVariant(multiAbiTargetingApexVariant(x64ArmTargeting, x64ArmApk))
            .build();

    Path apksPath = createApks(buildApksResult, apksInDirectory);
    ExtractApksCommand.Builder extractedApksCommand =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksPath)
            .setDeviceSpec(abis("x86_64", "x86"));
    if (!apksInDirectory) {
      extractedApksCommand.setOutputDirectory(tmpDir);
    }

    ImmutableList<Path> matchedApks = extractedApksCommand.build().execute();

    if (apksInDirectory) {
      assertThat(matchedApks).containsExactly(inTempDirectory(x64X86Apk.toString()));
    } else {
      assertThat(matchedApks).containsExactly(inOutputDirectory(x64X86Apk.getFileName()));
    }
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void oneModule_Kdevice_noMatchingSdkVariant_throws() throws Exception {
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkM = ZipPath.create("splits/apkM.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))),
                    ApkTargeting.getDefaultInstance(),
                    apkL))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(sdkVersionFrom(23), ImmutableSet.of(sdkVersionFrom(21))),
                    ApkTargeting.getDefaultInstance(),
                    apkM))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(19);

    ExtractApksCommand command =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (19) of the device is not supported.");
  }

  @Test
  public void oneModule_MipsDevice_noMatchingAbiSplit_throws() throws Exception {
    ZipPath apkL = ZipPath.create("splits/apkL.apk");
    ZipPath apkLx86 = ZipPath.create("splits/apkL-x86.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21)),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkL),
                        createApkDescription(
                            apkAbiTargeting(AbiAlias.X86, ImmutableSet.of()),
                            apkLx86,
                            /* isMasterSplit= */ false))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(sdkVersion(21), abis("arm64-v8a"), locales("en-US"), density(DensityAlias.HDPI));

    ExtractApksCommand command =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .build();

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The app doesn't support ABI architectures of the device. Device ABIs: [arm64-v8a], "
                + "app ABIs: [x86]");
  }

  @Test
  public void oneModule_extractedToTemporaryDirectory() throws Exception {
    ZipPath apkOne = ZipPath.create("apk_one.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariantForSingleSplitApk(
                    variantSdkTargeting(sdkVersionFrom(21)),
                    ApkTargeting.getDefaultInstance(),
                    apkOne))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ExtractApksCommand command =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .build();

    ImmutableList<Path> matchedApks = command.execute();

    assertThat(matchedApks).hasSize(1);

    String apkOnePathPrefix =
        Paths.get(System.getProperty("java.io.tmpdir"))
            .resolve("bundletool-extracted-apks")
            .toString();

    assertThat(Iterables.getOnlyElement(matchedApks).toString()).startsWith(apkOnePathPrefix);
  }

  @Test
  public void twoModules_Ldevice_matchesLSplitsForSpecifiedModules() throws Exception {
    ZipPath apkPreL = ZipPath.create("apkPreL.apk");
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    ZipPath apkLFeature = ZipPath.create("apkL-feature.apk");
    ZipPath apkLOther = ZipPath.create("apkL-other.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        SdkVersion.getDefaultInstance(), ImmutableSet.of(sdkVersionFrom(21))),
                    createStandaloneApkSet(ApkTargeting.getDefaultInstance(), apkPreL)))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLBase)),
                    createSplitApkSet(
                        "feature",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLFeature)),
                    createSplitApkSet(
                        "other",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLOther))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("feature"))
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(apkLBase), inOutputDirectory(apkLFeature));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void moduleWithDependency_extractDependency() throws Exception {
    ZipPath apkBase = ZipPath.create("base-master.apk");
    ZipPath apkFeature1 = ZipPath.create("feature1-master.apk");
    ZipPath apkFeature2 = ZipPath.create("feature2-master.apk");
    ZipPath apkFeature3 = ZipPath.create("feature3-master.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature1)),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature2)),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature2"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkFeature3))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> apks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("feature2"))
            .build()
            .execute();

    assertThat(apks)
        .containsExactly(
            inOutputDirectory(apkBase),
            inOutputDirectory(apkFeature1),
            inOutputDirectory(apkFeature2));
    for (Path apk : apks) {
      checkFileExistsAndReadable(tmpDir.resolve(apk));
    }
  }

  @Test
  public void diamondModuleDependenciesGraph() throws Exception {
    ZipPath apkBase = ZipPath.create("base-master.apk");
    ZipPath apkFeature1 = ZipPath.create("feature1-master.apk");
    ZipPath apkFeature2 = ZipPath.create("feature2-master.apk");
    ZipPath apkFeature3 = ZipPath.create("feature3-master.apk");
    ZipPath apkFeature4 = ZipPath.create("feature4-master.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature1)),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature2)),
                    createSplitApkSet(
                        /* moduleName= */ "feature3",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature3)),
                    createSplitApkSet(
                        /* moduleName= */ "feature4",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature2", "feature3"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkFeature4))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> apks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("feature4"))
            .build()
            .execute();

    assertThat(apks)
        .containsExactly(
            inOutputDirectory(apkBase),
            inOutputDirectory(apkFeature1),
            inOutputDirectory(apkFeature2),
            inOutputDirectory(apkFeature3),
            inOutputDirectory(apkFeature4));
    for (Path apk : apks) {
      checkFileExistsAndReadable(tmpDir.resolve(apk));
    }
  }

  @Test
  public void installTimeModule_alwaysExtracted() throws Exception {
    ZipPath apkBase = ZipPath.create("base-master.apk");
    ZipPath apkFeature1 = ZipPath.create("feature1-master.apk");
    ZipPath apkFeature2 = ZipPath.create("feature2-master.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature1)),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        DeliveryType.INSTALL_TIME,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkFeature2))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> apks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("feature1"))
            .build()
            .execute();

    assertThat(apks)
        .containsExactly(
            inOutputDirectory(apkBase),
            inOutputDirectory(apkFeature1),
            inOutputDirectory(apkFeature2));
    for (Path apk : apks) {
      checkFileExistsAndReadable(tmpDir.resolve(apk));
    }
  }

  @Test
  public void printHelp_doesNotCrash() {
    ExtractApksCommand.help();
  }

  @Test
  public void extractInstant_withBaseOnly() throws Exception {
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createInstantApkSet("base", ApkTargeting.getDefaultInstance(), apkLBase)))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setInstant(true)
            .build()
            .execute();

    assertThat(matchedApks).containsExactly(inOutputDirectory(apkLBase));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void extractInstant_withNoInstantModules() throws Exception {
    ZipPath apkPreL = ZipPath.create("apkPreL.apk");
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    ZipPath apkLFeature = ZipPath.create("apkL-feature.apk");
    ZipPath apkLOther = ZipPath.create("apkL-other.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createStandaloneApkSet(ApkTargeting.getDefaultInstance(), apkPreL)))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLBase)),
                    createSplitApkSet(
                        "feature",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLFeature)),
                    createSplitApkSet(
                        "other",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkLOther))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    IncompatibleDeviceException exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                ExtractApksCommand.builder()
                    .setApksArchivePath(apksArchiveFile)
                    .setDeviceSpec(deviceSpec)
                    .setInstant(true)
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("No compatible APKs found for the device");
  }

  @Test
  public void extractApks_aboveMaxSdk_throws() throws Exception {
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        // The variant contains a fake alternative to limit the matching up to 23.
                        sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(24))),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(26);

    IncompatibleDeviceException exception =
        assertThrows(
            IncompatibleDeviceException.class,
            () ->
                ExtractApksCommand.builder()
                    .setApksArchivePath(apksArchiveFile)
                    .setDeviceSpec(deviceSpec)
                    .setInstant(true)
                    .build()
                    .execute());

    assertThat(exception).hasMessageThat().contains("No compatible APKs found for the device");
  }

  @Test
  public void extractInstant_withModulesFlag() throws Exception {
    ZipPath apkPreL = ZipPath.create("apkPreL.apk");
    ZipPath apkLBase = ZipPath.create("apkL-base.apk");
    ZipPath apkLFeature = ZipPath.create("apkL-feature.apk");
    ZipPath apkLOther = ZipPath.create("apkL-other.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createStandaloneApkSet(ApkTargeting.getDefaultInstance(), apkPreL)))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createInstantApkSet("base", ApkTargeting.getDefaultInstance(), apkLBase),
                    createInstantApkSet("feature", ApkTargeting.getDefaultInstance(), apkLFeature),
                    createInstantApkSet("other", ApkTargeting.getDefaultInstance(), apkLOther)))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setInstant(true)
            .setModules(ImmutableSet.of("feature"))
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(apkLBase), inOutputDirectory(apkLFeature));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void extractInstant_withBaseAndSingleInstantModule() throws Exception {
    ZipPath apkBase = ZipPath.create("apkL-base.apk");
    ZipPath apkInstant = ZipPath.create("apkL-instant.apk");
    ZipPath apkOther = ZipPath.create("apkL-other.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createInstantApkSet("base", ApkTargeting.getDefaultInstance(), apkBase),
                    createInstantApkSet("instant", ApkTargeting.getDefaultInstance(), apkInstant)))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "other",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkOther))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setInstant(true)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(apkInstant), inOutputDirectory(apkBase));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void extractInstant_withMultipleInstantModule() throws Exception {
    ZipPath apkBase = ZipPath.create("apkL-base.apk");
    ZipPath apkInstant = ZipPath.create("apkL-instant.apk");
    ZipPath apkInstant2 = ZipPath.create("apkL-instant2.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createInstantApkSet("base", ApkTargeting.getDefaultInstance(), apkBase),
                    createInstantApkSet("instant", ApkTargeting.getDefaultInstance(), apkInstant),
                    createInstantApkSet("other", ApkTargeting.getDefaultInstance(), apkInstant2)))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setInstant(true)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(
            inOutputDirectory(apkInstant),
            inOutputDirectory(apkInstant2),
            inOutputDirectory(apkBase));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void testExtractFromDirectoryNoTableOfContents_throws() throws Exception {
    Files.createFile(tmpDir.resolve("base-master.apk"));
    Exception e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ExtractApksCommand.builder()
                    .setApksArchivePath(tmpDir)
                    .setDeviceSpec(deviceWithSdk(21))
                    .build()
                    .execute());

    assertThat(e).hasMessageThat().matches("File '.*toc.pb' was not found.");
  }


  @Test
  public void conditionalModule_deviceMatching() throws Exception {
    ZipPath apkBase = ZipPath.create("apkL-base.apk");
    ZipPath apkConditional = ZipPath.create("apkN-conditional-module.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createConditionalApkSet(
                        "conditional",
                        mergeModuleTargeting(
                            moduleMinSdkVersionTargeting(24),
                            moduleFeatureTargeting("android.hardware.camera.ar")),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkConditional))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec =
        mergeSpecs(deviceWithSdk(24), deviceFeatures("android.hardware.camera.ar"));

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(apkConditional), inOutputDirectory(apkBase));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void conditionalModule_deviceNotMatching() throws Exception {
    ZipPath apkBase = ZipPath.create("apkL-base.apk");
    ZipPath apkConditional = ZipPath.create("apkN-conditional-module.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createConditionalApkSet(
                        "conditional",
                        mergeModuleTargeting(
                            moduleMinSdkVersionTargeting(24),
                            moduleFeatureTargeting("android.hardware.camera.ar")),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkConditional))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = mergeSpecs(deviceWithSdk(21));

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    assertThat(matchedApks).containsExactly(inOutputDirectory(apkBase));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void conditionalModule_deviceNotMatching_moduleInFlags() throws Exception {
    ZipPath apkBase = ZipPath.create("apkL-base.apk");
    ZipPath apkConditional = ZipPath.create("apkN-conditional-module.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(1))),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createConditionalApkSet(
                        "conditional",
                        mergeModuleTargeting(
                            moduleMinSdkVersionTargeting(24),
                            moduleFeatureTargeting("android.hardware.camera.ar")),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkConditional))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = mergeSpecs(deviceWithSdk(21));

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("conditional"))
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(apkConditional), inOutputDirectory(apkBase));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  /** Ensures that --modules=_ALL_ extracts all modules. */
  @Test
  public void shortcutToExtractAllModules() throws Exception {
    ZipPath apkBase = ZipPath.create("base-master.apk");
    ZipPath apkBaseXxhdpi = ZipPath.create("base-xxhdpi.apk");
    ZipPath apkFeature = ZipPath.create("feature-master.apk");
    ZipPath apkFeatureXxhdpi = ZipPath.create("feature-xxhdpi.apk");
    ZipPath apkFeature2 = ZipPath.create("feature2.apk");
    ZipPath apkFeature2Arm64 = ZipPath.create("feature2-arm64_v8a.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase),
                        createApkDescription(apkDensityTargeting(XXHDPI), apkBaseXxhdpi, false)),
                    createSplitApkSet(
                        "feature",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature2"),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature),
                        createApkDescription(apkDensityTargeting(XXHDPI), apkFeatureXxhdpi, false)),
                    createSplitApkSet(
                        "feature2",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature2),
                        createApkDescription(apkAbiTargeting(ARM64_V8A), apkFeature2Arm64, false))))
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("_ALL_"))
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(
            inOutputDirectory(apkBase),
            inOutputDirectory(apkBaseXxhdpi),
            inOutputDirectory(apkFeature),
            inOutputDirectory(apkFeatureXxhdpi),
            inOutputDirectory(apkFeature2),
            inOutputDirectory(apkFeature2Arm64));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void extractAssetModules() throws Exception {
    String installTimeModule1 = "installtime_assetmodule1";
    String installTimeModule2 = "installtime_assetmodule2";
    String onDemandModule = "ondemand_assetmodule";
    ZipPath installTimeMasterApk1 = ZipPath.create(installTimeModule1 + "-master.apk");
    ZipPath installTimeEnApk1 = ZipPath.create(installTimeModule1 + "-en.apk");
    ZipPath installTimeMasterApk2 = ZipPath.create(installTimeModule2 + "-master.apk");
    ZipPath installTimeEnApk2 = ZipPath.create(installTimeModule2 + "-en.apk");
    ZipPath onDemandMasterApk = ZipPath.create(onDemandModule + "-master.apk");
    ZipPath baseApk = ZipPath.create("base-master.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule1)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk1))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk1)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule2)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk2))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk2)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandModule)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandMasterApk)))
            .build();

    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = lDeviceWithLocales("en-US");

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(
            inOutputDirectory(installTimeMasterApk1),
            inOutputDirectory(installTimeEnApk1),
            inOutputDirectory(installTimeMasterApk2),
            inOutputDirectory(installTimeEnApk2),
            inOutputDirectory(baseApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void extractAssetModules_allModules() throws Exception {
    String installTimeModule1 = "installtime_assetmodule1";
    String installTimeModule2 = "installtime_assetmodule2";
    String onDemandModule = "ondemand_assetmodule";
    ZipPath installTimeMasterApk1 = ZipPath.create(installTimeModule1 + "-master.apk");
    ZipPath installTimeEnApk1 = ZipPath.create(installTimeModule1 + "-en.apk");
    ZipPath installTimeMasterApk2 = ZipPath.create(installTimeModule2 + "-master.apk");
    ZipPath installTimeEnApk2 = ZipPath.create(installTimeModule2 + "-en.apk");
    ZipPath onDemandMasterApk = ZipPath.create(onDemandModule + "-master.apk");
    ZipPath baseApk = ZipPath.create("base-master.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), baseApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule1)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk1))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk1)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(installTimeModule2)
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        splitApkDescription(
                            ApkTargeting.getDefaultInstance(), installTimeMasterApk2))
                    .addApkDescription(
                        splitApkDescription(apkLanguageTargeting("en"), installTimeEnApk2)))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandModule)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandMasterApk)))
            .build();

    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = lDeviceWithLocales("en-US");

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("_ALL_"))
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(
            inOutputDirectory(installTimeMasterApk1),
            inOutputDirectory(installTimeEnApk1),
            inOutputDirectory(installTimeMasterApk2),
            inOutputDirectory(installTimeEnApk2),
            inOutputDirectory(onDemandMasterApk),
            inOutputDirectory(baseApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void bundleWithDeviceTierTargeting_noDeviceTierSpecified_usesDefaults() throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseLowApk = ZipPath.create("base-tier_0.apk");
    ZipPath baseHighApk = ZipPath.create("base-tier_1.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            baseLowApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            baseHighApk))))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder()
                    .setDimension(Value.DEVICE_TIER)
                    .setDefaultValue("1"))
            .build();

    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = lDevice();

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    // Master and high tier splits for base and asset module.
    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(baseMasterApk), inOutputDirectory(baseHighApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void bundleWithDeviceTierTargeting_noDeviceTierSpecifiedNorDefault_usesZeroAsDefault()
      throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseLowApk = ZipPath.create("base-tier_0.apk");
    ZipPath baseHighApk = ZipPath.create("base-tier_1.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            baseLowApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            baseHighApk))))
            .build();

    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = lDevice();

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    // Master and high tier splits for base and asset module.
    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(baseMasterApk), inOutputDirectory(baseLowApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void bundleWithDeviceTierTargeting_deviceTierSet_filtersByTier() throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseLowApk = ZipPath.create("base-tier_0.apk");
    ZipPath baseHighApk = ZipPath.create("base-tier_1.apk");
    ZipPath asset1MasterApk = ZipPath.create("asset1-master.apk");
    ZipPath asset1LowApk = ZipPath.create("asset1-tier_0.apk");
    ZipPath asset1HighApk = ZipPath.create("asset1-tier_1.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            baseLowApk),
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            baseHighApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName("asset1")
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), asset1MasterApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1))),
                            asset1LowApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkDeviceTierTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0))),
                            asset1HighApk)))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder()
                    .setDimension(Value.DEVICE_TIER)
                    .setDefaultValue("0"))
            .build();

    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = lDevice().toBuilder().setDeviceTier(Int32Value.of(1)).build();

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    // Master and high tier splits for base and asset module.
    assertThat(matchedApks)
        .containsExactly(
            inOutputDirectory(baseMasterApk),
            inOutputDirectory(baseHighApk),
            inOutputDirectory(asset1MasterApk),
            inOutputDirectory(asset1HighApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void bundleWithCountrySetTargeting_noCountrySetSpecifiedNorDefault_usesFallback()
      throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseRestOfWorldApk = ZipPath.create("base-other_countries.apk");
    ZipPath baseSeaApk = ZipPath.create("base-countries_sea.apk");
    ZipPath baseLatamApk = ZipPath.create("base-countries_latam.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "sea",
                                    /* alternatives= */ ImmutableList.of("latam"))),
                            baseSeaApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "latam",
                                    /* alternatives= */ ImmutableList.of("sea"))),
                            baseLatamApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                alternativeCountrySetTargeting(ImmutableList.of("sea", "latam"))),
                            baseRestOfWorldApk))))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder().setDimension(Value.COUNTRY_SET))
            .build();
    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));
    DeviceSpec deviceSpec = lDevice();

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(baseMasterApk), inOutputDirectory(baseRestOfWorldApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void bundleWithCountrySetTargeting_noCountrySetSpecified_usesDefaults() throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseRestOfWorldApk = ZipPath.create("base-other_countries.apk");
    ZipPath baseSeaApk = ZipPath.create("base-countries_sea.apk");
    ZipPath baseLatamApk = ZipPath.create("base-countries_latam.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "sea",
                                    /* alternatives= */ ImmutableList.of("latam"))),
                            baseSeaApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "latam",
                                    /* alternatives= */ ImmutableList.of("sea"))),
                            baseLatamApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                alternativeCountrySetTargeting(ImmutableList.of("sea", "latam"))),
                            baseRestOfWorldApk))))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder()
                    .setDimension(Value.COUNTRY_SET)
                    .setDefaultValue("latam"))
            .build();
    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));
    DeviceSpec deviceSpec = lDevice();

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(inOutputDirectory(baseMasterApk), inOutputDirectory(baseLatamApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void bundleWithCountrySetTargeting_countrySetSpecified_filterByCountrySet()
      throws Exception {
    ZipPath baseMasterApk = ZipPath.create("base-master.apk");
    ZipPath baseRestOfWorldApk = ZipPath.create("base-other_countries.apk");
    ZipPath baseSeaApk = ZipPath.create("base-countries_sea.apk");
    ZipPath baseLatamApk = ZipPath.create("base-countries_latam.apk");
    ZipPath asset1MasterApk = ZipPath.create("asset1-master.apk");
    ZipPath asset1RestOfWorldApk = ZipPath.create("asset1-other_countries.apk");
    ZipPath asset1SeaApk = ZipPath.create("asset1-countries_sea.apk");
    ZipPath asset1LatamApk = ZipPath.create("asset1-countries_latam.apk");
    BuildApksResult buildApksResult =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), baseMasterApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "sea",
                                    /* alternatives= */ ImmutableList.of("latam"))),
                            baseSeaApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "latam",
                                    /* alternatives= */ ImmutableList.of("sea"))),
                            baseLatamApk),
                        splitApkDescription(
                            apkCountrySetTargeting(
                                alternativeCountrySetTargeting(ImmutableList.of("sea", "latam"))),
                            baseRestOfWorldApk))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName("asset1")
                            .setDeliveryType(DeliveryType.INSTALL_TIME))
                    .addApkDescription(
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), asset1MasterApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "latam",
                                    /* alternatives= */ ImmutableList.of("sea"))),
                            asset1LatamApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkCountrySetTargeting(
                                countrySetTargeting(
                                    /* value= */ "sea",
                                    /* alternatives= */ ImmutableList.of("latam"))),
                            asset1SeaApk))
                    .addApkDescription(
                        splitApkDescription(
                            apkCountrySetTargeting(
                                alternativeCountrySetTargeting(ImmutableList.of("latam", "sea"))),
                            asset1RestOfWorldApk)))
            .addDefaultTargetingValue(
                DefaultTargetingValue.newBuilder().setDimension(Value.COUNTRY_SET))
            .build();
    Path apksArchiveFile = createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));
    DeviceSpec deviceSpec = lDevice().toBuilder().setCountrySet(StringValue.of("latam")).build();

    ImmutableList<Path> matchedApks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .build()
            .execute();

    assertThat(matchedApks)
        .containsExactly(
            inOutputDirectory(baseMasterApk),
            inOutputDirectory(baseLatamApk),
            inOutputDirectory(asset1MasterApk),
            inOutputDirectory(asset1LatamApk));
    for (Path matchedApk : matchedApks) {
      checkFileExistsAndReadable(tmpDir.resolve(matchedApk));
    }
  }

  @Test
  public void incompleteApksFile_missingMatchedAbiSplit_throws() throws Exception {
    // Partial APK Set file where 'x86' split is included and 'x86_64' split is not included because
    // device spec sent to 'build-apks' command doesn't support it.
    // Next, device spec that should be matched to 'x86_64' split is provided to 'extract-apks'
    // command and command throws IncompatibleDeviceException.
    BuildApksResult tableOfContent =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    VariantTargeting.getDefaultInstance(),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                        splitApkDescription(
                            apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
                            ZipPath.create("base-x86.apk")))))
            .build();

    Path apksArchiveFile = createApksArchiveFile(tableOfContent, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = mergeSpecs(deviceWithSdk(21), abis("x86_64", "x86"));

    ExtractApksCommand command =
        ExtractApksCommand.builder()
            .setDeviceSpec(deviceSpec)
            .setApksArchivePath(apksArchiveFile)
            .setOutputDirectory(tmpDir)
            .build();
    IncompatibleDeviceException exception =
        assertThrows(IncompatibleDeviceException.class, command::execute);
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Missing APKs for [ABI] dimensions in the module 'base' for the provided device.");
  }

  @DataPoints("localTestingEnabled")
  public static final ImmutableSet<Boolean> LOCAL_TESTING_ENABLED = ImmutableSet.of(true, false);

  @Test
  @Theory
  public void extractApks_producesOutputMetadata(
      @FromDataPoints("localTestingEnabled") boolean localTestingEnabled) throws Exception {
    String onDemandModule = "ondemand_assetmodule";
    ZipPath apkBase = ZipPath.create("base-master.apk");
    ZipPath apkFeature1 = ZipPath.create("feature1-master.apk");
    ZipPath apkFeature2 = ZipPath.create("feature2-master.apk");
    ZipPath onDemandMasterApk = ZipPath.create(onDemandModule + "-master.apk");
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(
                createVariant(
                    variantSdkTargeting(
                        sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
                    createSplitApkSet(
                        /* moduleName= */ "base",
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkBase)),
                    createSplitApkSet(
                        /* moduleName= */ "feature1",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of(),
                        createMasterApkDescription(ApkTargeting.getDefaultInstance(), apkFeature1)),
                    createSplitApkSet(
                        /* moduleName= */ "feature2",
                        DeliveryType.ON_DEMAND,
                        /* moduleDependencies= */ ImmutableList.of("feature1"),
                        createMasterApkDescription(
                            ApkTargeting.getDefaultInstance(), apkFeature2))))
            .addAssetSliceSet(
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        AssetModuleMetadata.newBuilder()
                            .setName(onDemandModule)
                            .setDeliveryType(DeliveryType.ON_DEMAND))
                    .addApkDescription(
                        splitApkDescription(ApkTargeting.getDefaultInstance(), onDemandMasterApk)))
            .build();

    if (localTestingEnabled) {
      tableOfContentsProto =
          tableOfContentsProto.toBuilder()
              .setPackageName("com.acme.anvil")
              .setLocalTestingInfo(
                  LocalTestingInfo.newBuilder()
                      .setEnabled(true)
                      .setLocalTestingPath("local_testing_dir"))
              .build();
    }
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    DeviceSpec deviceSpec = deviceWithSdk(21);

    ImmutableList<Path> apks =
        ExtractApksCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setOutputDirectory(tmpDir)
            .setModules(ImmutableSet.of("feature2", "ondemand_assetmodule"))
            .setIncludeMetadata(true)
            .build()
            .execute();

    assertThat(apks)
        .containsExactly(
            inOutputDirectory(apkBase),
            inOutputDirectory(apkFeature1),
            inOutputDirectory(apkFeature2),
            inOutputDirectory(onDemandMasterApk));

    Path metadataFile = inTempDirectory("metadata.json");
    assertThat(Files.isRegularFile(metadataFile)).isTrue();
    ExtractApksResult expectedResult =
        ExtractApksResult.newBuilder()
            .addApks(
                ExtractedApk.newBuilder()
                    .setPath(apkBase.toString())
                    .setDeliveryType(DeliveryType.INSTALL_TIME)
                    .setModuleName("base")
                    .build())
            .addApks(
                ExtractedApk.newBuilder()
                    .setPath(apkFeature1.toString())
                    .setDeliveryType(DeliveryType.ON_DEMAND)
                    .setModuleName("feature1")
                    .build())
            .addApks(
                ExtractedApk.newBuilder()
                    .setPath(apkFeature2.toString())
                    .setDeliveryType(DeliveryType.ON_DEMAND)
                    .setModuleName("feature2")
                    .build())
            .addApks(
                ExtractedApk.newBuilder()
                    .setPath(onDemandMasterApk.toString())
                    .setDeliveryType(DeliveryType.ON_DEMAND)
                    .setModuleName("ondemand_assetmodule")
                    .build())
            .build();
    if (localTestingEnabled) {
      expectedResult =
          expectedResult.toBuilder()
              .setLocalTestingInfo(
                  LocalTestingInfoForMetadata.newBuilder()
                      .setLocalTestingDir(
                          "/sdcard/Android/data/com.acme.anvil/files/local_testing_dir"))
              .build();
    }
    assertThat(parseExtractApksResult(metadataFile))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedResult);
  }

  @Test
  public void extractApks_appWithRuntimeSdkVariant_noSdkRuntimeInSpec() throws Exception {
    String withSdkRuntimeApk = "with_sdk_runtime.apk";
    String withoutSdkRuntimeApk = "without_sdk_runtime.apk";
    Variant variantWithSdkRuntime =
        Variant.newBuilder()
            .addApkSet(
                ApkSet.newBuilder()
                    .setModuleMetadata(
                        ModuleMetadata.newBuilder()
                            .setName("base")
                            .setDeliveryType(DeliveryType.INSTALL_TIME)
                            .setModuleType(FeatureModuleType.FEATURE_MODULE))
                    .addApkDescription(
                        ApkDescription.newBuilder().setPath("standalones/" + withSdkRuntimeApk)))
            .setTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(SdkVersion.newBuilder().setMin(Int32Value.of(33))))
                    .setSdkRuntimeTargeting(
                        SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true)))
            .build();
    Variant variantWithoutSdkRuntime =
        Variant.newBuilder()
            .addApkSet(
                ApkSet.newBuilder()
                    .setModuleMetadata(
                        ModuleMetadata.newBuilder()
                            .setName("base")
                            .setDeliveryType(DeliveryType.INSTALL_TIME)
                            .setModuleType(FeatureModuleType.FEATURE_MODULE))
                    .addApkDescription(
                        ApkDescription.newBuilder().setPath("standalones/" + withoutSdkRuntimeApk)))
            .setTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(SdkVersion.newBuilder().setMin(Int32Value.of(33))))
                    .setSdkRuntimeTargeting(
                        SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(false)))
            .build();

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .addVariant(variantWithSdkRuntime)
            .addVariant(variantWithoutSdkRuntime)
            .setBundletool(Bundletool.newBuilder().setVersion("1.10.1"))
            .build();
    Path apksArchiveFile = createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("app.apks"));
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(34), tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    ImmutableList<Path> matchedApks = command.execute();

    assertThat(matchedApks.stream().map(apk -> apk.getFileName().toString()))
        .containsExactly(withSdkRuntimeApk);
  }

  @Test
  public void extractApks_appWithRuntimeSdkVariant_withSdkRuntimeInSpec() throws Exception {
    String withSdkRuntimeApk = "with_sdk_runtime.apk";
    String withoutSdkRuntimeApk = "without_sdk_runtime.apk";
    Variant variantWithSdkRuntime =
        Variant.newBuilder()
            .addApkSet(
                ApkSet.newBuilder()
                    .setModuleMetadata(
                        ModuleMetadata.newBuilder()
                            .setName("base")
                            .setDeliveryType(DeliveryType.INSTALL_TIME)
                            .setModuleType(FeatureModuleType.FEATURE_MODULE))
                    .addApkDescription(
                        ApkDescription.newBuilder().setPath("standalones/" + withSdkRuntimeApk)))
            .setTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(SdkVersion.newBuilder().setMin(Int32Value.of(33))))
                    .setSdkRuntimeTargeting(
                        SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true)))
            .build();
    Variant variantWithoutSdkRuntime =
        Variant.newBuilder()
            .addApkSet(
                ApkSet.newBuilder()
                    .setModuleMetadata(
                        ModuleMetadata.newBuilder()
                            .setName("base")
                            .setDeliveryType(DeliveryType.INSTALL_TIME)
                            .setModuleType(FeatureModuleType.FEATURE_MODULE))
                    .addApkDescription(
                        ApkDescription.newBuilder().setPath("standalones/" + withoutSdkRuntimeApk)))
            .setTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(SdkVersion.newBuilder().setMin(Int32Value.of(33))))
                    .setSdkRuntimeTargeting(
                        SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(false)))
            .build();

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .addVariant(variantWithSdkRuntime)
            .addVariant(variantWithoutSdkRuntime)
            .setBundletool(Bundletool.newBuilder().setVersion("1.10.1"))
            .build();
    Path apksArchiveFile = createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("app.apks"));
    Path deviceSpecFile =
        createDeviceSpecFile(
            mergeSpecs(deviceWithSdk(34), sdkRuntimeSupported(false)),
            tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    ImmutableList<Path> matchedApks = command.execute();

    assertThat(matchedApks.stream().map(apk -> apk.getFileName().toString()))
        .containsExactly(withoutSdkRuntimeApk);
  }

  @Test
  public void extractApks_sdkApkSetMatching() throws Exception {
    String standaloneApk = "standalone.apk";
    Variant variant =
        Variant.newBuilder()
            .addApkSet(
                ApkSet.newBuilder()
                    .setModuleMetadata(
                        ModuleMetadata.newBuilder()
                            .setName("base")
                            .setDeliveryType(DeliveryType.INSTALL_TIME)
                            .setModuleType(FeatureModuleType.FEATURE_MODULE))
                    .addApkDescription(
                        ApkDescription.newBuilder().setPath("standalones/" + standaloneApk)))
            .setTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(SdkVersion.newBuilder().setMin(Int32Value.of(33)))))
            .build();
    BuildSdkApksResult tableOfContentsProto =
        BuildSdkApksResult.newBuilder()
            .addVariant(variant)
            .setBundletool(Bundletool.newBuilder().setVersion("1.10.1"))
            .build();
    Path apksArchiveFile =
        createSdkApksArchiveFile(tableOfContentsProto, tmpDir.resolve("sdk.apks"));
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(34), tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    ImmutableList<Path> matchedApks = command.execute();

    assertThat(matchedApks.stream().map(apk -> apk.getFileName().toString()))
        .containsExactly(standaloneApk);
  }

  @Test
  public void extractApks_sdkApkSetNotMatching_throws() throws Exception {
    Variant variant =
        Variant.newBuilder()
            .addApkSet(
                ApkSet.newBuilder()
                    .setModuleMetadata(
                        ModuleMetadata.newBuilder()
                            .setName("base")
                            .setDeliveryType(DeliveryType.INSTALL_TIME)
                            .setModuleType(FeatureModuleType.FEATURE_MODULE))
                    .addApkDescription(
                        ApkDescription.newBuilder().setPath("standalones/standalone.apk")))
            .setTargeting(
                VariantTargeting.newBuilder()
                    .setSdkVersionTargeting(
                        SdkVersionTargeting.newBuilder()
                            .addValue(SdkVersion.newBuilder().setMin(Int32Value.of(33)))))
            .build();
    BuildSdkApksResult tableOfContentsProto =
        BuildSdkApksResult.newBuilder()
            .addVariant(variant)
            .setBundletool(Bundletool.newBuilder().setVersion("1.10.1"))
            .build();
    Path apksArchiveFile =
        createSdkApksArchiveFile(tableOfContentsProto, tmpDir.resolve("sdk.apks"));
    Path deviceSpecFile = createDeviceSpecFile(deviceWithSdk(21), tmpDir.resolve("device.json"));
    ExtractApksCommand command =
        ExtractApksCommand.fromFlags(
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

    Throwable exception = assertThrows(IncompatibleDeviceException.class, command::execute);

    assertThat(exception)
        .hasMessageThat()
        .contains("SDK version (21) of the device is not supported.");
  }

  private static ExtractApksResult parseExtractApksResult(Path file) throws Exception {
    ExtractApksResult.Builder builder = ExtractApksResult.newBuilder();
    JsonFormat.parser().merge(MoreFiles.asCharSource(file, UTF_8).read(), builder);
    return builder.build();
  }

  private Path createApks(BuildApksResult buildApksResult, boolean apksInDirectory)
      throws Exception {
    if (apksInDirectory) {
      return createApksDirectory(buildApksResult, tmpDir);
    } else {
      return createApksArchiveFile(buildApksResult, tmpDir.resolve("bundle.apks"));
    }
  }

  private static BuildApksResult minimalApkSet() {
    return BuildApksResult.newBuilder()
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
        .addVariant(
            createVariant(
                VariantTargeting.getDefaultInstance(),
                createSplitApkSet(
                    "base",
                    createMasterApkDescription(
                        ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")))))
        .build();
  }

  private Path inOutputDirectory(ZipPath file) {
    return tmpDir.resolve(Paths.get(file.toString()));
  }

  private Path inTempDirectory(String file) {
    return tmpDir.resolve(Paths.get(file));
  }
}
