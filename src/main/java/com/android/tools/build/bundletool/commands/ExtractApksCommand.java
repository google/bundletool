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
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.android.tools.build.bundletool.utils.flags.Flag;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts from an APK Set the APKs to be installed on a given device. */
@AutoValue
public abstract class ExtractApksCommand {

  public static final String COMMAND_NAME = "extract-apks";

  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");
  private static final Flag<Path> OUTPUT_DIRECTORY = Flag.path("output-dir");
  private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");
  private static final Flag<Boolean> INSTANT_FLAG = Flag.booleanFlag("instant");

  public abstract Path getApksArchivePath();

  public abstract DeviceSpec getDeviceSpec();

  public abstract Path getOutputDirectory();

  public abstract Optional<ImmutableSet<String>> getModules();

  /** Gets whether instant APKs should be extracted. */
  public abstract boolean getInstant();

  public static Builder builder() {
    return new AutoValue_ExtractApksCommand.Builder().setInstant(false);
  }

  /** Builder for the {@link ExtractApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setApksArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

    public abstract Builder setOutputDirectory(Path outputDirectory);

    public abstract Builder setModules(ImmutableSet<String> modules);

    /**
     * Sets whether instant APKs should be extracted.
     *
     * <p>The default is {@code false}. If this is set to {@code true}, the instant APKs will be
     * extracted instead of the installable APKs.
     */
    public abstract Builder setInstant(boolean instant);

    public abstract ExtractApksCommand build();
  }

  public static ExtractApksCommand fromFlags(ParsedFlags flags) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Path deviceSpecPath = DEVICE_SPEC_FLAG.getRequiredValue(flags);
    Path outputDirectory = OUTPUT_DIRECTORY.getRequiredValue(flags);
    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> instant = INSTANT_FLAG.getValue(flags);
    flags.checkNoUnknownFlags();

    ExtractApksCommand.Builder command = builder();

    checkFileExistsAndReadable(apksArchivePath);
    command.setApksArchivePath(apksArchivePath);

    checkFileExistsAndReadable(deviceSpecPath);
    command.setDeviceSpec(DeviceSpecParser.parseDeviceSpec(deviceSpecPath));

    checkDirectoryExists(outputDirectory);
    command.setOutputDirectory(outputDirectory);

    modules.ifPresent(command::setModules);

    instant.ifPresent(command::setInstant);

    return command.build();
  }

  public ImmutableList<Path> execute() {
    if (getModules().isPresent() && getModules().get().isEmpty()) {
      throw new ValidationException("The set of modules cannot be empty.");
    }

    ApkMatcher apkMatcher =
        new ApkMatcher(getDeviceSpec(), /* requestedModuleNames= */ getModules(), getInstant());
    ImmutableList<ZipPath> matchedApks = apkMatcher.getMatchingApks(readTableOfContents());

    return extractMatchedApks(matchedApks);
  }

  private ImmutableList<Path> extractMatchedApks(ImmutableList<ZipPath> matchedApkPaths) {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    try (ZipFile apksArchive = new ZipFile(getApksArchivePath().toFile())) {
      for (ZipPath matchedApk : matchedApkPaths) {
        ZipEntry entry = apksArchive.getEntry(matchedApk.toString());
        checkNotNull(entry);
        Path extractedApkPath = getOutputDirectory().resolve(matchedApk.getFileName().toString());
        try (InputStream inputStream = BufferedIo.inputStream(apksArchive, entry);
            OutputStream outputApk = BufferedIo.outputStream(extractedApkPath)) {
          ByteStreams.copy(inputStream, outputApk);
          builder.add(extractedApkPath);
        } catch (IOException e) {
          throw new UncheckedIOException(
              String.format("Error while extracting APK '%s' from the APK Set.", matchedApk), e);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while processing the APK Set archive '%s'.", getApksArchivePath()),
          e);
    }
    return builder.build();
  }

  private BuildApksResult readTableOfContents() {
    try (ZipFile apksArchive = new ZipFile(getApksArchivePath().toFile());
        InputStream tocStream =
            BufferedIo.inputStream(apksArchive, new ZipEntry(TABLE_OF_CONTENTS_FILE))) {
      return BuildApksResult.parseFrom(tocStream);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "Error while reading the table of contents file from '%s'.", getApksArchivePath()),
          e);
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
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("base,module1,module2")
                .setOptional(true)
                .setDescription(
                    "List of modules to be extracted (defaults to all of them). Note that the "
                        + "dependent modules will also be extracted. Ignored if the device "
                        + "receives a standalone APK.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(INSTANT_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "When set, APKs of the instant modules will be extracted instead of the "
                        + "installable APKs.")
                .build())
        .build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  ExtractApksCommand() {}
}
