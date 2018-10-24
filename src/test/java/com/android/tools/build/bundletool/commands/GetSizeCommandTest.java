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

import static com.android.tools.build.bundletool.testing.ApksArchiveHelpers.createApksArchiveFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.createDeviceSpecFile;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceWithSdk;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.utils.flags.FlagParser;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.protobuf.util.JsonFormat;
import java.io.FileOutputStream;
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

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  @Before
  public void setUp() {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void missingDeviceSpecFlag_defaultDeviceSpec() throws Exception {
    Path apksArchiveFile =
        createApksArchiveFile(BuildApksResult.getDefaultInstance(), tmpDir.resolve("bundle.apks"));

    GetSizeCommand getSizeCommand =
        GetSizeCommand.fromFlags(new FlagParser().parse("--apks=" + apksArchiveFile));

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
        new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=nonexistent");
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
            new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile));

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
        new FlagParser().parse("--device-spec=" + deviceSpecFile, "--apks=" + apksArchiveFile);
    Throwable exception =
        assertThrows(CommandExecutionException.class, () -> GetSizeCommand.fromFlags(flags));

    assertThat(exception).hasMessageThat().contains("Expected .json extension for the device spec");
    assertThat(exception).hasMessageThat().contains("bad_filename.dat");
  }

  @Test
  public void builderAndFlagsConstruction_equivalent() throws Exception {
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(new FlagParser().parse("--apks=" + apksArchiveFile));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(DeviceSpec.getDefaultInstance())
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
  }

  @Test
  public void builderAndFlagsConstruction_optionalDeviceSpec_equivalent() throws Exception {
    DeviceSpec deviceSpec = deviceWithSdk(21);
    Path deviceSpecFile = createDeviceSpecFile(deviceSpec, tmpDir.resolve("device.json"));
    BuildApksResult tableOfContentsProto = BuildApksResult.getDefaultInstance();
    Path apksArchiveFile =
        createApksArchiveFile(tableOfContentsProto, tmpDir.resolve("bundle.apks"));

    GetSizeCommand fromFlags =
        GetSizeCommand.fromFlags(
            new FlagParser()
                .parse(
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--device-spec=" + deviceSpecFile));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(deviceSpec)
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
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--modules=base"));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(DeviceSpec.getDefaultInstance())
            .setModules(ImmutableSet.of("base"))
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
                    "--apks=" + apksArchiveFile,
                    // Optional values.
                    "--instant"));

    GetSizeCommand fromBuilderApi =
        GetSizeCommand.builder()
            .setApksArchivePath(apksArchiveFile)
            .setDeviceSpec(DeviceSpec.getDefaultInstance())
            .setInstant(true)
            .build();

    assertThat(fromFlags).isEqualTo(fromBuilderApi);
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
