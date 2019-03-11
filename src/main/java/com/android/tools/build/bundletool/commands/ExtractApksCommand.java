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

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.FileNames;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts from an APK Set the APKs to be installed on a given device. */
@AutoValue
public abstract class ExtractApksCommand {

  public static final String COMMAND_NAME = "extract-apks";
  static final String ALL_MODULES_SHORTCUT = "_ALL_";

  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");
  private static final Flag<Path> OUTPUT_DIRECTORY = Flag.path("output-dir");
  private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");
  private static final Flag<Boolean> INSTANT_FLAG = Flag.booleanFlag("instant");

  public abstract Path getApksArchivePath();

  public abstract DeviceSpec getDeviceSpec();

  public abstract Optional<Path> getOutputDirectory();

  public abstract Optional<ImmutableSet<String>> getModules();

  /** Gets whether instant APKs should be extracted. */
  public abstract boolean getInstant();


  public static Builder builder() {
    return new AutoValue_ExtractApksCommand.Builder()
        .setInstant(false);
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
    Optional<Path> outputDirectory = OUTPUT_DIRECTORY.getValue(flags);
    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> instant = INSTANT_FLAG.getValue(flags);
    flags.checkNoUnknownFlags();

    ExtractApksCommand.Builder command = builder();

    checkArgument(
        !Files.isDirectory(apksArchivePath), "File '%s' is a directory.", apksArchivePath);
    command.setApksArchivePath(apksArchivePath);

    checkFileExistsAndReadable(deviceSpecPath);
    command.setDeviceSpec(DeviceSpecParser.parseDeviceSpec(deviceSpecPath));

    outputDirectory.ifPresent(command::setOutputDirectory);

    modules.ifPresent(command::setModules);

    instant.ifPresent(command::setInstant);


    return command.build();
  }

  public ImmutableList<Path> execute() {
    return execute(System.out);
  }

  @VisibleForTesting
  ImmutableList<Path> execute(PrintStream output) {
    validateInput();

    BuildApksResult toc = ResultUtils.readTableOfContents(getApksArchivePath());
    Optional<ImmutableSet<String>> requestedModuleNames =
        getModules()
            .map(
                modules ->
                    modules.contains(ALL_MODULES_SHORTCUT)
                        ? toc.getVariantList().stream()
                            .flatMap(variant -> variant.getApkSetList().stream())
                            .map(apkSet -> apkSet.getModuleMetadata().getName())
                            .collect(toImmutableSet())
                        : modules);

    ApkMatcher apkMatcher = new ApkMatcher(getDeviceSpec(), requestedModuleNames, getInstant());
    ImmutableList<ZipPath> matchedApks = apkMatcher.getMatchingApks(toc);

    if (matchedApks.isEmpty()) {
      throw CommandExecutionException.builder()
          .withMessage("No compatible APKs found for the device.")
          .build();
    }


    if (Files.isDirectory(getApksArchivePath())) {
      return matchedApks.stream()
          .map(matchedApk -> getApksArchivePath().resolve(matchedApk.toString()))
          .collect(toImmutableList());
    } else {
      return extractMatchedApksFromApksArchive(matchedApks);
    }
  }

  private void validateInput() {
    if (getModules().isPresent() && getModules().get().isEmpty()) {
      throw new ValidationException("The set of modules cannot be empty.");
    }

    if (Files.isDirectory(getApksArchivePath())) {
      checkArgument(
          !getOutputDirectory().isPresent(),
          "Output directory should not be set when APKs are inside directory.");
      checkDirectoryExists(getApksArchivePath());
      checkFileExistsAndReadable(getApksArchivePath().resolve(FileNames.TABLE_OF_CONTENTS_FILE));
    } else {
      checkFileExistsAndReadable(getApksArchivePath());
    }
  }

  private ImmutableList<Path> extractMatchedApksFromApksArchive(
      ImmutableList<ZipPath> matchedApkPaths) {
    Path outputDirectoryPath =
        getOutputDirectory().orElseGet(ExtractApksCommand::createTempDirectory);
    checkDirectoryExists(outputDirectoryPath);

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    try (ZipFile apksArchive = new ZipFile(getApksArchivePath().toFile())) {
      for (ZipPath matchedApk : matchedApkPaths) {
        ZipEntry entry = apksArchive.getEntry(matchedApk.toString());
        checkNotNull(entry);
        Path extractedApkPath = outputDirectoryPath.resolve(matchedApk.getFileName().toString());
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
    System.err.printf(
        "The APKs have been extracted in the directory: %s%n", outputDirectoryPath.toString());
    return builder.build();
  }

  private static Path createTempDirectory() {
    try {
      return Files.createTempDirectory("bundletool-extracted-apks");
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to create a temporary directory for extracted APKs.", e);
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
                .setOptional(true)
                .setExampleValue("output-dir")
                .setDescription(
                    "Path to where the matched APKs will be extracted from the archive file. "
                        + "If not set, the APK Set archive is created in a temporary directory.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("base,module1,module2")
                .setOptional(true)
                .setDescription(
                    "List of modules to be extracted, or \"%s\" for all modules. Defaults to "
                        + "modules installed during the first install, i.e. not on-demand. Note "
                        + "that the dependent modules will also be extracted. The value of this "
                        + "flag is ignored if the device receives a standalone APK.",
                    ALL_MODULES_SHORTCUT)
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
