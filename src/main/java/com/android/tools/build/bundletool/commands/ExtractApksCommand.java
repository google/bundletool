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

import static com.android.tools.build.bundletool.utils.FileNames.TABLE_OF_CONTENTS_FILE;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Devices.ApkMatchingMetadata;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.ApkMatchingMetadataUtils;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.android.tools.build.bundletool.utils.flags.Flag;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts from an APK Set the APKs to be installed on a given device. */
@AutoValue
public abstract class ExtractApksCommand {

  public static final String COMMAND_NAME = "extract-apks";

  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");
  private static final Flag<Path> OUTPUT_DIRECTORY = Flag.path("output-dir");
  private static final String JSON_EXTENSION = "json";

  public abstract Path getApksArchivePath();

  public abstract DeviceSpec getDeviceSpec();

  public abstract Path getOutputDirectory();

  public static Builder builder() {
    return new AutoValue_ExtractApksCommand.Builder();
  }

  /** Builder for the {@link ExtractApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setApksArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

    public abstract Builder setOutputDirectory(Path outputDirectory);

    public abstract ExtractApksCommand build();
  }

  public static ExtractApksCommand fromFlags(ParsedFlags flags) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Path deviceSpecPath = DEVICE_SPEC_FLAG.getRequiredValue(flags);
    Path outputDirectory = OUTPUT_DIRECTORY.getRequiredValue(flags);
    flags.checkNoUnknownFlags();

    ExtractApksCommand.Builder commandBuilder = builder();

    checkFileExistsAndReadable(apksArchivePath);
    commandBuilder.setApksArchivePath(apksArchivePath);

    checkFileExistsAndReadable(deviceSpecPath);
    commandBuilder.setDeviceSpec(parseDeviceSpec(deviceSpecPath));

    checkDirectoryExists(outputDirectory);
    commandBuilder.setOutputDirectory(outputDirectory);

    return commandBuilder.build();
  }

  private static DeviceSpec parseDeviceSpec(Path deviceSpecFile) {
    DeviceSpec.Builder builder = DeviceSpec.newBuilder();
    try {
      if (!JSON_EXTENSION.equals(MoreFiles.getFileExtension(deviceSpecFile))) {
        throw CommandExecutionException.builder()
            .withMessage(
                "Expected .json extension of the device spec file but found '%s'.", deviceSpecFile)
            .build();
      }
      try (Reader deviceSpecReader = BufferedIo.reader(deviceSpecFile)) {
        JsonFormat.parser().merge(deviceSpecReader, builder);
      }
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("I/O error while reading the device spec file '%s'.", deviceSpecFile)
          .build();
    }
    return builder.build();
  }

  public ImmutableList<Path> execute() {
    ImmutableMap<Path, ApkMatchingMetadata> apksToProcess =
        ApkMatchingMetadataUtils.toApkMatchingMap(readTableOfContents());

    ImmutableList.Builder<Path> matchedApksBuilder = ImmutableList.builder();
    ApkMatcher apkMatcher = new ApkMatcher(getDeviceSpec());
    for (Entry<Path, ApkMatchingMetadata> apk : apksToProcess.entrySet()) {
      Path apkPath = apk.getKey();
      ApkMatchingMetadata apkMatchingMetadata = apk.getValue();
      if (apkMatcher.matchesApk(apkMatchingMetadata)) {
        matchedApksBuilder.add(apkPath);
      }
    }
    return extractMatchedApks(matchedApksBuilder.build());
  }

  private ImmutableList<Path> extractMatchedApks(ImmutableList<Path> matchedApks) {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    try (ZipFile apksArchive = new ZipFile(getApksArchivePath().toFile())) {
      for (Path matchedApk : matchedApks) {
        ZipEntry entry = apksArchive.getEntry(matchedApk.toString());
        checkNotNull(entry);
        Path extractedApkPath = getOutputDirectory().resolve(matchedApk);
        try (InputStream inputStream = BufferedIo.inputStream(apksArchive, entry);
            OutputStream outputApk = BufferedIo.outputStream(extractedApkPath)) {
          ByteStreams.copy(inputStream, outputApk);
          builder.add(extractedApkPath);
        } catch (IOException e) {
          throw CommandExecutionException.builder()
              .withCause(e)
              .withMessage("I/O error while extracting APK '%s' from the APK set.", matchedApk)
              .build();
        }
      }
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("I/O error while processing the APK set archive '%s'.", getApksArchivePath())
          .build();
    }
    return builder.build();
  }

  private BuildApksResult readTableOfContents() {
    try (ZipFile apksArchive = new ZipFile(getApksArchivePath().toFile());
        InputStream tocStream =
            BufferedIo.inputStream(apksArchive, new ZipEntry(TABLE_OF_CONTENTS_FILE))) {
      return BuildApksResult.parseFrom(tocStream);
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage(
              "I/O error while reading the table of contents file from '%s'.", getApksArchivePath())
          .build();
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Extracts from an APK Set the APKs that should be installed on a given device.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APKS_ARCHIVE_FILE_FLAG.getName())
                .setExampleValue("archive.apks")
                .setDescription(
                    "Path to the archive file generated by the '%s' command.",
                    BuildApksCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_SPEC_FLAG.getName())
                .setExampleValue("device-spec.json")
                .setDescription(
                    "Path to the device spec file generated by the '%s' command.",
                    GetDeviceSpecCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_DIRECTORY.getName())
                .setExampleValue("output-dir")
                .setDescription(
                    "Path to where the matched APKs will be extracted from the archive file.")
                .build())
        .build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  ExtractApksCommand() {}
}
