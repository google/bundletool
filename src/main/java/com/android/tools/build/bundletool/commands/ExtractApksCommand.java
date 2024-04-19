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

import static com.android.tools.build.bundletool.device.LocalTestingPathResolver.resolveLocalTestingPath;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.DefaultTargetingValue;
import com.android.bundle.Commands.ExtractApksResult;
import com.android.bundle.Commands.ExtractedApk;
import com.android.bundle.Commands.LocalTestingInfoForMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.ApkMatcher.GeneratedApk;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.FileNames;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts from an APK Set the APKs to be installed on a given device. */
@AutoValue
public abstract class ExtractApksCommand {

  private static final Logger logger = Logger.getLogger(ExtractApksCommand.class.getName());

  public static final String COMMAND_NAME = "extract-apks";
  static final String ALL_MODULES_SHORTCUT = "_ALL_";

  private static final String METADATA_FILE = "metadata.json";

  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");
  private static final Flag<Path> OUTPUT_DIRECTORY = Flag.path("output-dir");
  private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");
  private static final Flag<Boolean> INSTANT_FLAG = Flag.booleanFlag("instant");
  private static final Flag<Boolean> INCLUDE_METADATA_FLAG = Flag.booleanFlag("include-metadata");

  public abstract Path getApksArchivePath();

  public abstract DeviceSpec getDeviceSpec();

  public abstract Optional<Path> getOutputDirectory();

  public abstract Optional<ImmutableSet<String>> getModules();

  public abstract boolean getIncludeInstallTimeAssetModules();

  /** Gets whether instant APKs should be extracted. */
  public abstract boolean getInstant();

  public abstract boolean getIncludeMetadata();


  public static Builder builder() {
    return new AutoValue_ExtractApksCommand.Builder()
        .setInstant(false)
        .setIncludeMetadata(false)
        .setIncludeInstallTimeAssetModules(true);
  }

  /** Builder for the {@link ExtractApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setApksArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

    public Builder setDeviceSpec(Path deviceSpecPath) {
      return setDeviceSpec(DeviceSpecParser.parseDeviceSpec(deviceSpecPath));
    }

    public abstract Builder setOutputDirectory(Path outputDirectory);

    /**
     * Sets the required modules to extract.
     *
     * <p>All install-time feature modules and asset modules are extracted by default. You can
     * exclude install-time asset modules by passing {@code false} to {@link
     * #setIncludeInstallTimeAssetModules}.
     *
     * <p>"_ALL_" extracts all modules.
     */
    public abstract Builder setModules(ImmutableSet<String> modules);

    /** Whether to extract install-time asset modules (default = true). */
    public abstract Builder setIncludeInstallTimeAssetModules(boolean shouldInclude);

    /**
     * Sets whether instant APKs should be extracted.
     *
     * <p>The default is {@code false}. If this is set to {@code true}, the instant APKs will be
     * extracted instead of the installable APKs.
     */
    public abstract Builder setInstant(boolean instant);

    public abstract Builder setIncludeMetadata(boolean outputMetadata);


    abstract ExtractApksCommand autoBuild();

    /**
     * Builds the command
     *
     * @throws com.android.tools.build.bundletool.model.exceptions.InvalidDeviceSpecException if the
     *     device spec is invalid. See {@link DeviceSpecParser#validateDeviceSpec}
     */
    public ExtractApksCommand build() {
      ExtractApksCommand command = autoBuild();
      DeviceSpecParser.validateDeviceSpec(
          command.getDeviceSpec(),
          /* canSkipFields= */ true); // Allow partial device spec for APEX bundles
      return command;
    }
  }

  public static ExtractApksCommand fromFlags(ParsedFlags flags) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Path deviceSpecPath = DEVICE_SPEC_FLAG.getRequiredValue(flags);
    Optional<Path> outputDirectory = OUTPUT_DIRECTORY.getValue(flags);
    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> instant = INSTANT_FLAG.getValue(flags);
    Optional<Boolean> includeMetadata = INCLUDE_METADATA_FLAG.getValue(flags);
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
    includeMetadata.ifPresent(command::setIncludeMetadata);


    return command.build();
  }

  public ImmutableList<Path> execute() {
    return execute(System.out);
  }

  @VisibleForTesting
  ImmutableList<Path> execute(PrintStream output) {
    validateInput();

    BuildApksResult toc = ResultUtils.readTableOfContents(getApksArchivePath());
    DeviceSpec deviceSpec = applyDefaultsToDeviceSpec(getDeviceSpec(), toc);
    Optional<ImmutableSet<String>> requestedModuleNames =
        getModules().map(modules -> resolveRequestedModules(modules, toc, deviceSpec));

    ApkMatcher apkMatcher =
        new ApkMatcher(
            deviceSpec,
            requestedModuleNames,
            getIncludeInstallTimeAssetModules(),
            getInstant(),
            /* ensureDensityAndAbiApksMatched= */ true);
    ImmutableList<GeneratedApk> generatedApks = apkMatcher.getMatchingApks(toc);

    if (generatedApks.isEmpty()) {
      throw IncompatibleDeviceException.builder()
          .withUserMessage("No compatible APKs found for the device.")
          .build();
    }


    if (Files.isDirectory(getApksArchivePath())) {
      return generatedApks.stream()
          .map(matchedApk -> getApksArchivePath().resolve(matchedApk.getPath().toString()))
          .collect(toImmutableList());
    } else {
      return extractMatchedApksFromApksArchive(generatedApks, toc);
    }
  }

  static ImmutableSet<String> resolveRequestedModules(
      ImmutableSet<String> requestedModules, BuildApksResult toc, DeviceSpec deviceSpec) {
    return requestedModules.contains(ALL_MODULES_SHORTCUT)
        ? Stream.concat(
                getVariantsMatchingSdkRuntimeTargeting(toc, deviceSpec).stream()
                    .flatMap(variant -> variant.getApkSetList().stream())
                    .map(apkSet -> apkSet.getModuleMetadata().getName()),
                toc.getAssetSliceSetList().stream()
                    .map(AssetSliceSet::getAssetModuleMetadata)
                    .map(AssetModuleMetadata::getName))
            .collect(toImmutableSet())
        : requestedModules;
  }

  private static ImmutableSet<Variant> getVariantsMatchingSdkRuntimeTargeting(
      BuildApksResult toc, DeviceSpec deviceSpec) {
    ImmutableSet<Variant> sdkRuntimeVariants =
        toc.getVariantList().stream()
            .filter(
                variant -> variant.getTargeting().getSdkRuntimeTargeting().getRequiresSdkRuntime())
            .collect(toImmutableSet());
    if (deviceSpec.getSdkRuntime().getSupported() && !sdkRuntimeVariants.isEmpty()) {
      return sdkRuntimeVariants;
    }
    return Sets.difference(ImmutableSet.copyOf(toc.getVariantList()), sdkRuntimeVariants)
        .immutableCopy();
  }

  private void validateInput() {
    if (getModules().isPresent() && getModules().get().isEmpty()) {
      throw InvalidCommandException.builder()
          .withInternalMessage("The set of modules cannot be empty.")
          .build();
    }

    if (Files.isDirectory(getApksArchivePath())) {
      checkArgument(
          !getOutputDirectory().isPresent(),
          "Output directory should not be set when APKs are inside directory.");
      checkDirectoryExists(getApksArchivePath());
      Path tocFile =
          Files.exists(getApksArchivePath().resolve(FileNames.TABLE_OF_CONTENTS_JSON_FILE))
              ? getApksArchivePath().resolve(FileNames.TABLE_OF_CONTENTS_JSON_FILE)
              : getApksArchivePath().resolve(FileNames.TABLE_OF_CONTENTS_FILE);
      checkFileExistsAndReadable(tocFile);
    } else {
      checkFileExistsAndReadable(getApksArchivePath());
    }
  }

  private ImmutableList<Path> extractMatchedApksFromApksArchive(
      ImmutableList<GeneratedApk> generatedApks, BuildApksResult toc) {
    Path outputDirectoryPath =
        getOutputDirectory().orElseGet(ExtractApksCommand::createTempDirectory);

    getOutputDirectory()
        .ifPresent(
            dir -> {
              if (!Files.exists(dir)) {
                logger.info("Output directory '" + dir + "' does not exist, creating it.");
                FileUtils.createDirectories(dir);
              }
            });

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    try (ZipFile apksArchive = new ZipFile(getApksArchivePath().toFile())) {
      for (GeneratedApk matchedApk : generatedApks) {
        ZipEntry entry = apksArchive.getEntry(matchedApk.getPath().toString());
        checkNotNull(entry);
        Path extractedApkPath =
            outputDirectoryPath.resolve(matchedApk.getPath().getFileName().toString());
        try (InputStream inputStream = apksArchive.getInputStream(entry);
            OutputStream outputApk = Files.newOutputStream(extractedApkPath)) {
          ByteStreams.copy(inputStream, outputApk);
          builder.add(extractedApkPath);
        } catch (IOException e) {
          throw new UncheckedIOException(
              String.format("Error while extracting APK '%s' from the APK Set.", matchedApk), e);
        }
      }
      if (getIncludeMetadata()) {
        produceCommandMetadata(generatedApks, toc, outputDirectoryPath);
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

  private static void produceCommandMetadata(
      ImmutableList<GeneratedApk> generatedApks, BuildApksResult toc, Path outputDir) {

    ImmutableList<ExtractedApk> apks =
        generatedApks.stream()
            .map(
                apk ->
                    ExtractedApk.newBuilder()
                        .setPath(apk.getPath().getFileName().toString())
                        .setModuleName(apk.getModuleName())
                        .setDeliveryType(apk.getDeliveryType())
                        .build())
            .collect(toImmutableList());

    try {
      JsonFormat.Printer printer = JsonFormat.printer();
      ExtractApksResult.Builder builder = ExtractApksResult.newBuilder();
      if (toc.getLocalTestingInfo().getEnabled()) {
        builder.setLocalTestingInfo(createLocalTestingInfo(toc));
      }
      String metadata = printer.print(builder.addAllApks(apks).build());
      Files.write(outputDir.resolve(METADATA_FILE), metadata.getBytes(UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Error while writing metadata.json.", e);
    }
  }

  private static LocalTestingInfoForMetadata createLocalTestingInfo(BuildApksResult toc) {
    String localTestingPath = toc.getLocalTestingInfo().getLocalTestingPath();
    String packageName = toc.getPackageName();
    return LocalTestingInfoForMetadata.newBuilder()
        .setLocalTestingDir(resolveLocalTestingPath(localTestingPath, Optional.of(packageName)))
        .build();
  }

  private static Path createTempDirectory() {
    try {
      return Files.createTempDirectory("bundletool-extracted-apks");
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to create a temporary directory for extracted APKs.", e);
    }
  }

  private static DeviceSpec applyDefaultsToDeviceSpec(DeviceSpec deviceSpec, BuildApksResult toc) {
    DeviceSpec.Builder builder = deviceSpec.toBuilder();
    if (!deviceSpec.hasDeviceTier()) {
      int defaultDeviceTier =
          toc.getDefaultTargetingValueList().stream()
              .filter(
                  defaultTargetingValue ->
                      defaultTargetingValue.getDimension().equals(Value.DEVICE_TIER))
              .map(DefaultTargetingValue::getDefaultValue)
              // Don't fail if the default value is empty.
              .filter(defaultValue -> !defaultValue.isEmpty())
              .map(Integer::parseInt)
              .collect(toOptional())
              .orElse(0);
      builder.setDeviceTier(Int32Value.of(defaultDeviceTier));
    }
    if (!deviceSpec.hasCountrySet()) {
      String defaultCountrySet =
          toc.getDefaultTargetingValueList().stream()
              .filter(
                  defaultTargetingValue ->
                      defaultTargetingValue.getDimension().equals(Value.COUNTRY_SET))
              .map(DefaultTargetingValue::getDefaultValue)
              .filter(defaultValue -> !defaultValue.isEmpty())
              .collect(toOptional())
              .orElse("");
      builder.setCountrySet(StringValue.of(defaultCountrySet));
    }
    if (!deviceSpec.hasSdkRuntime()) {
      builder
          .getSdkRuntimeBuilder()
          .setSupported(deviceSpec.getSdkVersion() >= AndroidManifest.SDK_SANDBOX_MIN_VERSION);
    }

    return builder.build();
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
                    "Path to the archive file generated by either the '%s' command or the '%s'"
                        + " command.",
                    BuildApksCommand.COMMAND_NAME, BuildSdkApksCommand.COMMAND_NAME)
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
                    "List of modules to be extracted, or \"%s\" for all modules. "
                        + "Defaults to modules installed during the first install, i.e. not "
                        + "on-demand. Note that the dependent modules will also be extracted. The "
                        + "value of this flag is ignored if the device receives a standalone APK.",
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
        .addFlag(
            FlagDescription.builder()
                .setFlagName(INCLUDE_METADATA_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "When set, metadata.json will be produced to the output directory with"
                        + " description about extracted APKs.")
                .build())
        .build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  ExtractApksCommand() {}
}
