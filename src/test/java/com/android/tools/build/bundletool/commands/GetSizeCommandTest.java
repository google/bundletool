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

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS;
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS64;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.LDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.tools.build.bundletool.commands.GetSizeCommand.SUPPORTED_DIMENSIONS;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.ALL;
import static com.android.tools.build.bundletool.model.utils.CsvFormatter.CRLF;
import static com.android.tools.build.bundletool.model.utils.ZipUtils.calculateGzipCompressedSize;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createInstantApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createMasterApkDescription;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createSplitApkSet;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createVariant;
import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.standaloneVariant;
import static com.android.tools.build.bundletool.testing.DeviceFactory.createDeviceSpecFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.commands.GetSizeCommand.GetSizeSubcommand;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest.Dimension;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.protobuf.util.JsonFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
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
public final class GetSizeCommandTest {

  private static final byte[] DUMMY_BYTES = new byte[100];

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;
  private long compressedApkSize;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    try (InputStream myInputStream = new ByteArrayInputStream(DUMMY_BYTES)) {
      compressedApkSize = calculateGzipCompressedSize(myInputStream);
    }
  }

  @Test
  public void missingDeviceSpecFlag_defaultDeviceSpec() throws Exception {
    Path apksArchiveFile =
        createApksArchiveFile(BuildApksResult.getDefaultInstance(), tmpDir.resolve("bundle.apks"));

    GetSizeCommand getSizeCommand =
        GetSizeCommand.fromFlags(
            new FlagParser().parse("get-size", "total", "--apks=" + apksArchiveFile));

    assertThat(getSizeCommand.getDeviceSpec()).isEqualToDefaultInstance();
  }

  @Test
  public void missingApksArchiveFlag_throws() {
    expectMissingRequiredFlagException(
        "apks", () -> GetSizeCommand.fromFlags(new FlagParser().parse()));
  }

  @Test
  public void nonExistentApksArchiveFile_throws() throws Exception {
    Path deviceSpecFile =
        createDeviceSpecFile(DeviceSpec.getDefaultInstance(), tmpDir.resolve("device.json"));

    ParsedFlags flags =
        new FlagParser()
            .parse("get-size", "total", "--device-spec=" + deviceSpecFile, "--apks=nonexistent");
    Throwable exception =
        assertThrows(IllegalArgumentException.class, () -> GetSizeCommand.fromFlags(flags));

    assertThat(exception).hasMessageThat().contains("File 'nonexistent' was not found");
  }

  @DataPoints("deviceSpecs")
  public static final ImmutableSet<String> DEVICE_SPECS =
      ImmutableSet.of(
          "testdata/device/pixel2_spec.json", "testdata/device/invalid_spec_abi_empty.json");

  @Test
  @Theory
  public void checkFlagsConstructionWithDeviceSpec(
      @FromDataPoints("deviceSpecs") String deviceSpecPath) throws Exception {
    DeviceSpec.Builder expectedDeviceSpecBuilder = DeviceSpec.newBuilder();
    try (Reader reader = TestData.openReader(deviceSpecPath)) {
      JsonFormat.parser().merge(reader, expectedDeviceSpecBuilder);
    }
    DeviceSpec expectedDeviceSpec = expectedDeviceSpecBuilder.build();

    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));
    Path deviceSpecFile = copyToTempDir(deviceSpecPath);

    GetSizeCommand command =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "get-size",
                    "total",
                    "--device-spec=" + deviceSpecFile,
                    "--apks=" + apksArchiveFile));

    assertThat(command.getDeviceSpec()).isEqualTo(expectedDeviceSpec);
  }

  @Test
  public void deviceSpecUnknownExtension_throws() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("bad_filename.dat"));
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ParsedFlags flags =
        new FlagParser()
            .parse(
                "get-size",
                "total",
                "--device-spec=" + deviceSpecFile,
                "--apks=" + apksArchiveFile);
    Throwable exception =
        assertThrows(CommandExecutionException.class, () -> GetSizeCommand.fromFlags(flags));

    assertThat(exception).hasMessageThat().contains("Expected .json extension for the device spec");
    assertThat(exception).hasMessageThat().contains("bad_filename.dat");
  }

  @Test
  public void testSupportedDimensions_onlySkipsAllDimension() {
    assertThat(Sets.difference(SUPPORTED_DIMENSIONS, ImmutableSet.copyOf(Dimension.values())))
        .isEmpty();
    assertThat(Sets.difference(ImmutableSet.copyOf(Dimension.values()), SUPPORTED_DIMENSIONS))
        .containsExactly(ALL);
  }

  @Test
  public void wrongSubCommand_throws() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ParsedFlags flags = new FlagParser().parse("get-size", "full", "--apks=" + apksArchiveFile);
    Throwable exception =
        assertThrows(ValidationException.class, () -> GetSizeCommand.fromFlags(flags));

    assertThat(exception).hasMessageThat().contains("Unrecognized get-size command target:");
    assertThat(exception).hasMessageThat().contains("full");
  }

  @Test
  public void deviceSpecWrongDimensions_throws() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ParsedFlags flags =
        new FlagParser()
            .parse(
                "get-size", "total", "--apks=" + apksArchiveFile, "--dimensions=" + "ABI,SCREEN");
    Throwable exception =
        assertThrows(FlagParseException.class, () -> GetSizeCommand.fromFlags(flags));

    assertThat(exception).hasMessageThat().contains("Not a valid enum value");
    assertThat(exception).hasMessageThat().contains("SCREEN");
  }

  @Test
  public void deviceSpecAll_hasAllDimensions() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand command =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse("get-size", "total", "--apks=" + apksArchiveFile, "--dimensions=" + "ALL"));

    assertThat(command.getDimensions()).isSameInstanceAs(SUPPORTED_DIMENSIONS);
  }

  @Test
  public void builderAndFlagsConstruction_optionalDimensions_equivalent() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "get-size",
                    "total",
                    "--apks=" + apksArchiveFile,
                    "--dimensions=" + "ABI,SCREEN_DENSITY"));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDimensions(ImmutableSet.of(Dimension.ABI, Dimension.SCREEN_DENSITY))
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_equivalent() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser().parse("get-size", "total", "--apks=" + apksArchiveFile));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(DeviceSpec.getDefaultInstance())
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalDeviceSpec_inJavaViaApi_equivalent()
      throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "get-size",
                    "total",
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--device-spec=" + deviceSpecFile));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalDeviceSpec_inJavaViaFiles_equivalent()
      throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "get-size",
                    "total",
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--device-spec=" + deviceSpecFile));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpecFile)
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalModules_equivalent() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "get-size",
                    "total",
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--modules=base"));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setModules(ImmutableSet.of("base"))
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalInstant_equivalent() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "get-size",
                    "total",
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--instant"));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setInstant(true)
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void getSizeTotalInternal_singleSplitVariant() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
                    ZipPath.create("base-x86.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86_64, ImmutableSet.of(X86)),
                    ZipPath.create("base-x86_64.apk"),
                    /* isMasterSplit= */ false)));

    ZipBuilder archiveBuilder = new ZipBuilder();
    archiveBuilder.addFileWithContent(ZipPath.create("base-master.apk"), DUMMY_BYTES);
    archiveBuilder.addFileWithContent(
        ZipPath.create("base-x86.apk"),
        DUMMY_BYTES,
        EntryOption.UNCOMPRESSED); // APK stored uncompressed in the APKs zip.
    archiveBuilder.addFileWithContent(ZipPath.create("base-x86_64.apk"), new byte[10000]);
    archiveBuilder.addFileWithProtoContent(
        ZipPath.create("toc.pb"),
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .build());
    Path apksArchiveFile = archiveBuilder.writeTo(tmpDir.resolve("bundle.apks"));

    ConfigurationSizes configurationSizes =
        GetSizeCommand.builder()
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .setApksArchivePath(apksArchiveFile)
            .build()
            .getSizeTotalInternal();

    assertThat(configurationSizes.getMinSizeConfigurationMap().keySet())
        .containsExactly(SizeConfiguration.getDefaultInstance());
    assertThat(
            configurationSizes
                .getMinSizeConfigurationMap()
                .get(SizeConfiguration.getDefaultInstance()))
        .isEqualTo(2 * compressedApkSize); // base+x86
    assertThat(configurationSizes.getMaxSizeConfigurationMap().keySet())
        .containsExactly(SizeConfiguration.getDefaultInstance());
    assertThat(
            configurationSizes
                .getMaxSizeConfigurationMap()
                .get(SizeConfiguration.getDefaultInstance()))
        .isGreaterThan(2 * compressedApkSize); // base+x86_64
  }

  @Test
  public void getSizeTotalInternal_multipleStandaloneVariant() throws Exception {
    Variant preLLdpiVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantDensityTargeting(LDPI, ImmutableSet.of(MDPI))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL-ldpi.apk"));

    Variant preLMdpiVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantDensityTargeting(MDPI, ImmutableSet.of(LDPI))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL-mdpi.apk"));

    ZipBuilder archiveBuilder = new ZipBuilder();
    archiveBuilder.addFileWithContent(ZipPath.create("preL-ldpi.apk"), new byte[1000]);
    archiveBuilder.addFileWithContent(ZipPath.create("preL-mdpi.apk"), new byte[10]);
    archiveBuilder.addFileWithProtoContent(
        ZipPath.create("toc.pb"),
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(preLLdpiVariant)
            .addVariant(preLMdpiVariant)
            .build());
    Path apksArchiveFile = archiveBuilder.writeTo(tmpDir.resolve("bundle.apks"));

    ConfigurationSizes configurationSizes =
        GetSizeCommand.builder()
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .setApksArchivePath(apksArchiveFile)
            .build()
            .getSizeTotalInternal();

    assertThat(configurationSizes.getMinSizeConfigurationMap().keySet())
        .containsExactly(SizeConfiguration.getDefaultInstance());
    assertThat(
            configurationSizes
                .getMinSizeConfigurationMap()
                .get(SizeConfiguration.getDefaultInstance()))
        .isLessThan(compressedApkSize);
    assertThat(configurationSizes.getMaxSizeConfigurationMap().keySet())
        .containsExactly(SizeConfiguration.getDefaultInstance());
    assertThat(
            configurationSizes
                .getMaxSizeConfigurationMap()
                .get(SizeConfiguration.getDefaultInstance()))
        .isGreaterThan(compressedApkSize);
  }

  @Test
  public void getSizeTotalInternal_withNoDimensionsAndDeviceSpec() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))));
    Variant preLVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(1), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(X86, ImmutableSet.of(ARMEABI_V7A, ARM64_V8A))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL.apk"));

    ZipBuilder archiveBuilder = new ZipBuilder();
    archiveBuilder.addFileWithContent(ZipPath.create("base-master.apk"), DUMMY_BYTES);
    archiveBuilder.addFileWithContent(ZipPath.create("preL.apk"), new byte[10000]);
    archiveBuilder.addFileWithProtoContent(
        ZipPath.create("toc.pb"),
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .addVariant(preLVariant)
            .build());
    Path apksArchiveFile = archiveBuilder.writeTo(tmpDir.resolve("bundle.apks"));

    ConfigurationSizes configurationSizes =
        GetSizeCommand.builder()
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(DeviceSpec.newBuilder().setSdkVersion(21).build())
            .build()
            .getSizeTotalInternal();

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.getDefaultInstance(), compressedApkSize); // only split apk
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.getDefaultInstance(), compressedApkSize); // only split apk
  }

  @Test
  public void getSizeTotalInternal_withDimensionsAndDeviceSpec() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkLanguageTargeting("jp"),
                    ZipPath.create("base-jp.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkLanguageTargeting("fr"),
                    ZipPath.create("base-fr.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(HDPI, ImmutableSet.of(LDPI)),
                    ZipPath.create("base-hdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(LDPI, ImmutableSet.of(HDPI)),
                    ZipPath.create("base-ldpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(ARM64_V8A, ImmutableSet.of(ARMEABI_V7A)),
                    ZipPath.create("base-arm64_v8a.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(ARMEABI_V7A, ImmutableSet.of(ARM64_V8A)),
                    ZipPath.create("base-armeabi_v7a.apk"),
                    /* isMasterSplit= */ false)));

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ConfigurationSizes configurationSizes =
        GetSizeCommand.builder()
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(
                DeviceSpec.newBuilder()
                    .setSdkVersion(21)
                    .setScreenDensity(125)
                    .addSupportedLocales("jp")
                    .build())
            .setDimensions(
                ImmutableSet.of(
                    Dimension.SDK, Dimension.ABI, Dimension.LANGUAGE, Dimension.SCREEN_DENSITY))
            .build()
            .getSizeTotalInternal();

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                    .setAbi("armeabi-v7a")
                    .setLocale("jp")
                    .setScreenDensity("125")
                    .setSdkVersion("21")
                    .build(),
                4 * compressedApkSize,
            SizeConfiguration.builder()
                    .setAbi("arm64-v8a")
                    .setLocale("jp")
                    .setScreenDensity("125")
                    .setSdkVersion("21")
                    .build(),
                4 * compressedApkSize);

    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                    .setAbi("armeabi-v7a")
                    .setLocale("jp")
                    .setScreenDensity("125")
                    .setSdkVersion("21")
                    .build(),
                4 * compressedApkSize,
            SizeConfiguration.builder()
                    .setAbi("arm64-v8a")
                    .setLocale("jp")
                    .setScreenDensity("125")
                    .setSdkVersion("21")
                    .build(),
                4 * compressedApkSize);
  }

  @Test
  public void getSizeTotalInternal_multipleDimensions() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkDensityTargeting(LDPI, ImmutableSet.of(MDPI)),
                    ZipPath.create("base-mdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(MDPI, ImmutableSet.of(LDPI)),
                    ZipPath.create("base-ldpi.apk"),
                    /* isMasterSplit= */ false)));
    Variant preLVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(ARMEABI, ImmutableSet.of(X86))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL.apk"));

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .addVariant(preLVariant)
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ConfigurationSizes configurationSizes =
        GetSizeCommand.builder()
            .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
            .setApksArchivePath(apksArchiveFile)
            .setDimensions(
                ImmutableSet.of(
                    Dimension.SDK, Dimension.ABI, Dimension.LANGUAGE, Dimension.SCREEN_DENSITY))
            .build()
            .getSizeTotalInternal();

    assertThat(configurationSizes.getMinSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setSdkVersion("21-")
                .setScreenDensity("LDPI")
                .setAbi("")
                .setLocale("")
                .build(),
            2 * compressedApkSize,
            SizeConfiguration.builder()
                .setSdkVersion("21-")
                .setScreenDensity("MDPI")
                .setAbi("")
                .setLocale("")
                .build(),
            2 * compressedApkSize,
            SizeConfiguration.builder()
                .setSdkVersion("15-20")
                .setAbi("armeabi")
                .setScreenDensity("")
                .setLocale("")
                .build(),
            compressedApkSize);
    assertThat(configurationSizes.getMaxSizeConfigurationMap())
        .containsExactly(
            SizeConfiguration.builder()
                .setSdkVersion("21-")
                .setScreenDensity("LDPI")
                .setAbi("")
                .setLocale("")
                .build(),
            2 * compressedApkSize,
            SizeConfiguration.builder()
                .setSdkVersion("21-")
                .setScreenDensity("MDPI")
                .setAbi("")
                .setLocale("")
                .build(),
            2 * compressedApkSize,
            SizeConfiguration.builder()
                .setSdkVersion("15-20")
                .setAbi("armeabi")
                .setScreenDensity("")
                .setLocale("")
                .build(),
            compressedApkSize);
  }

  @Test
  public void getSizeTotal_noDimensions() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkDensityTargeting(LDPI, ImmutableSet.of(MDPI)),
                    ZipPath.create("base-mdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(MDPI, ImmutableSet.of(LDPI)),
                    ZipPath.create("base-ldpi.apk"),
                    /* isMasterSplit= */ false)),
            createSplitApkSet(
                /* moduleName= */ "feature1",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-feature1.apk"))));

    Variant preLVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(sdkVersionFrom(21)))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL.apk"));
    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .addVariant(preLVariant)
            .build();

    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    GetSizeCommand.builder()
        .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
        .setApksArchivePath(apksArchiveFile)
        .build()
        .getSizeTotal(new PrintStream(outputStream));

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo(
            "MIN,MAX"
                + CRLF
                + String.format("%d,%d", compressedApkSize, 3 * compressedApkSize)
                + CRLF);
  }

  @Test
  public void getSizeTotal_withSelectModules() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk"))),
            createSplitApkSet(
                /* moduleName= */ "feature1",
                DeliveryType.ON_DEMAND,
                /* moduleDependencies= */ ImmutableList.of(),
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-feature1.apk"))),
            createSplitApkSet(
                /* moduleName= */ "feature2",
                DeliveryType.ON_DEMAND,
                /* moduleDependencies= */ ImmutableList.of("feature3"),
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-feature2.apk"))),
            createSplitApkSet(
                /* moduleName= */ "feature3",
                DeliveryType.ON_DEMAND,
                /* moduleDependencies= */ ImmutableList.of(),
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-feature3.apk"))));

    Variant preLVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(sdkVersionFrom(21)))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL.apk"));

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .addVariant(preLVariant)
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    GetSizeCommand.builder()
        .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
        .setApksArchivePath(apksArchiveFile)
        .setModules(ImmutableSet.of("base", "feature2"))
        .build()
        .getSizeTotal(new PrintStream(outputStream));

    // base, feature2, feature 3 modules are selected and standalone variants are skipped.
    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo(
            "MIN,MAX"
                + CRLF
                + String.format("%d,%d", 3 * compressedApkSize, 3 * compressedApkSize)
                + CRLF);
  }

  @Test
  public void getSizeTotal_withInstant() throws Exception {
    Variant lInstantVariant =
        createVariant(
            variantSdkTargeting(
                sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
            createInstantApkSet(
                "base", ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
            createInstantApkSet(
                "instant",
                ApkTargeting.getDefaultInstance(),
                ZipPath.create("instant-master.apk")));
    Variant lSplitVariant =
        createVariant(
            variantSdkTargeting(
                sdkVersionFrom(21), ImmutableSet.of(SdkVersion.getDefaultInstance())),
            createSplitApkSet(
                "other",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("other-master.apk"))));

    Variant preLVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(sdkVersionFrom(21)))),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL.apk"));

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lInstantVariant)
            .addVariant(lSplitVariant)
            .addVariant(preLVariant)
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    GetSizeCommand.builder()
        .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
        .setApksArchivePath(apksArchiveFile)
        .setInstant(true)
        .build()
        .getSizeTotal(new PrintStream(outputStream));

    // only instant split variant is selected
    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo(
            "MIN,MAX"
                + CRLF
                + String.format("%d,%d", 2 * compressedApkSize, 2 * compressedApkSize)
                + CRLF);
  }

  @Test
  public void getSizeTotal_multipleDimensions() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkAbiTargeting(MIPS64, ImmutableSet.of(MIPS)),
                    ZipPath.create("base-mips64.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(MIPS, ImmutableSet.of(MIPS64)),
                    ZipPath.create("base-mips.apk"),
                    /* isMasterSplit= */ false)));

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    GetSizeCommand.builder()
        .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
        .setApksArchivePath(apksArchiveFile)
        .setDimensions(
            ImmutableSet.of(
                Dimension.SDK, Dimension.ABI, Dimension.LANGUAGE, Dimension.SCREEN_DENSITY))
        .build()
        .getSizeTotal(new PrintStream(outputStream));

    ImmutableList<String> csvRows =
        ImmutableList.copyOf(new String(outputStream.toByteArray(), UTF_8).split(CRLF));

    assertThat(csvRows)
        .containsExactly(
            "SDK,ABI,SCREEN_DENSITY,LANGUAGE,MIN,MAX",
            String.format("21-,mips64,,,%d,%d", 2 * compressedApkSize, 2 * compressedApkSize),
            String.format("21-,mips,,,%d,%d", 2 * compressedApkSize, 2 * compressedApkSize));
  }

  @Test
  public void getSizeTotal_withDimensionsAndDeviceSpec() throws Exception {
    Variant lVariant =
        createVariant(
            lPlusVariantTargeting(),
            createSplitApkSet(
                /* moduleName= */ "base",
                createMasterApkDescription(
                    ApkTargeting.getDefaultInstance(), ZipPath.create("base-master.apk")),
                createApkDescription(
                    apkDensityTargeting(LDPI, ImmutableSet.of(MDPI)),
                    ZipPath.create("base-mdpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkDensityTargeting(MDPI, ImmutableSet.of(LDPI)),
                    ZipPath.create("base-ldpi.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86, ImmutableSet.of(X86_64)),
                    ZipPath.create("base-x86.apk"),
                    /* isMasterSplit= */ false),
                createApkDescription(
                    apkAbiTargeting(X86_64, ImmutableSet.of(X86)),
                    ZipPath.create("base-x86_64.apk"),
                    /* isMasterSplit= */ false)));

    Variant preLVariant =
        standaloneVariant(
            mergeVariantTargeting(
                variantSdkTargeting(sdkVersionFrom(15), ImmutableSet.of(sdkVersionFrom(21))),
                variantAbiTargeting(X86),
                variantDensityTargeting(LDPI)),
            ApkTargeting.getDefaultInstance(),
            ZipPath.create("preL.apk"));

    BuildApksResult tableOfContentsProto =
        BuildApksResult.newBuilder()
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addVariant(lVariant)
            .addVariant(preLVariant)
            .build();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    GetSizeCommand.builder()
        .setGetSizeSubCommand(GetSizeSubcommand.TOTAL)
        .setApksArchivePath(apksArchiveFile)
        .setDeviceSpec(
            DeviceSpec.newBuilder().setScreenDensity(124).addSupportedAbis("x86").build())
        .setDimensions(ImmutableSet.of(Dimension.ABI, Dimension.SCREEN_DENSITY))
        .build()
        .getSizeTotal(new PrintStream(outputStream));

    assertThat(new String(outputStream.toByteArray(), UTF_8))
        .isEqualTo(
            "ABI,SCREEN_DENSITY,MIN,MAX"
                + CRLF
                + String.format("x86,124,%d,%d", compressedApkSize, 3 * compressedApkSize)
                + CRLF);
  }

  /** Copies the testdata resource into the temporary directory. */
  private Path copyToTempDir(String testDataPath) throws Exception {
    Path testDataFilename = Paths.get(testDataPath).getFileName();
    Path outputFile = tmp.newFolder().toPath().resolve(testDataFilename);
    try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile.toFile())) {
      ByteStreams.copy(TestData.openStream(testDataPath), fileOutputStream);
    }
    return outputFile;
  }
}
