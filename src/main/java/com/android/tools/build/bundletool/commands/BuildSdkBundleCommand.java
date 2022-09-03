/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.generateAssetsTargeting;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.generateNativeLibrariesTargeting;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.Files.asByteSource;

import com.android.bundle.Config.Bundletool;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.ZipFlingerBundleSerializer;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.BundleModuleParser;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.validation.SdkBundleModulesValidator;
import com.android.tools.build.bundletool.validation.SdkBundleValidator;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Closer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command responsible for building an SDK Bundle from SDK modules. */
@AutoValue
public abstract class BuildSdkBundleCommand {

  private static final Logger logger = Logger.getLogger(BuildBundleCommand.class.getName());

  public static final String COMMAND_NAME = "build-sdk-bundle";

  private static final Flag<Path> OUTPUT_FLAG = Flag.path("output");
  private static final Flag<Boolean> OVERWRITE_OUTPUT_FLAG = Flag.booleanFlag("overwrite");
  private static final Flag<Path> SDK_BUNDLE_CONFIG_FLAG = Flag.path("sdk-bundle-config");
  private static final Flag<Path> SDK_MODULES_CONFIG_FLAG = Flag.path("sdk-modules-config");
  private static final Flag<ImmutableList<Path>> MODULES_FLAG = Flag.pathList("modules");
  private static final Flag<Path> SDK_INTERFACE_DESCRIPTORS_FLAG =
      Flag.path("sdk-interface-descriptors");
  private static final Flag<ImmutableMap<ZipPath, Path>> METADATA_FILES_FLAG =
      Flag.mapCollector("metadata-file", ZipPath.class, Path.class);

  public abstract Path getOutputPath();

  public abstract boolean getOverwriteOutput();

  public abstract ImmutableList<Path> getModulesPaths();

  public abstract Optional<SdkBundleConfig> getSdkBundleConfig();

  public abstract SdkModulesConfig getSdkModulesConfig();

  public abstract Optional<Path> getSdkInterfaceDescriptors();

  public abstract BundleMetadata getBundleMetadata();

  public static Builder builder() {
    // By default, we don't overwrite existing files.
    return new AutoValue_BuildSdkBundleCommand.Builder().setOverwriteOutput(false);
  }

  /** Builder for the {@link BuildSdkBundleCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setOutputPath(Path outputPath);

    /** Sets whether to write over the previous file if it already exists. */
    public abstract Builder setOverwriteOutput(boolean overwriteOutput);

    public abstract Builder setModulesPaths(ImmutableList<Path> modulesPaths);

    /**
     * Sets the SDK Bundle configuration.
     *
     * <p>Optional. This configuration will be merged with BundleTool defaults.
     */
    public abstract Builder setSdkBundleConfig(SdkBundleConfig sdkBundleConfig);

    /** Sets the SDK Bundle configuration from a JSON file. */
    public Builder setSdkBundleConfig(Path sdkBundleConfigFile) {
      return setSdkBundleConfig(parseSdkBundleConfigJson(sdkBundleConfigFile));
    }

    /** Sets the SDK modules configuration. */
    public abstract Builder setSdkModulesConfig(SdkModulesConfig sdkModulesConfig);

    /** Sets the SDK modules configuration from a JSON file. */
    public Builder setSdkModulesConfig(Path sdkModulesConfigFile) {
      return setSdkModulesConfig(parseSdkModulesConfigJson(sdkModulesConfigFile));
    }

    public abstract Builder setSdkInterfaceDescriptors(Path sdkInterfaceDescriptors);

    abstract BundleMetadata.Builder bundleMetadataBuilder();

    /**
     * Adds the given file as metadata to the SDK bundle into directory {@code
     * BUNDLE-METADATA/<namespaced-dir>/<file-name>}.
     *
     * <p>Optional. Throws when the given file does not exist. Adding duplicate metadata files (same
     * directory and filename) will result in exception being thrown by {@link #build()}.
     *
     * <p>See {@link BundleMetadata} for well-known metadata namespaces and file names.
     *
     * @param metadataDirectory namespaced directory inside the bundle metadata directory
     * @param fileName name of the file to be added to the metadata directory
     * @param file path to file containing the data
     */
    public Builder addMetadataFile(String metadataDirectory, String fileName, Path file) {
      addMetadataFileInternal(ZipPath.create(metadataDirectory).resolve(fileName), file);
      return this;
    }

    void addMetadataFileInternal(ZipPath metadataPath, Path file) {
      if (!Files.exists(file)) {
        throw InvalidCommandException.builder()
            .withInternalMessage("Metadata file '%s' does not exist.", file)
            .build();
      }
      bundleMetadataBuilder().addFile(metadataPath, asByteSource(file.toFile()));
    }

    public abstract BuildSdkBundleCommand build();
  }

  public static BuildSdkBundleCommand fromFlags(ParsedFlags flags) {
    BuildSdkBundleCommand.Builder builder =
        builder()
            .setOutputPath(OUTPUT_FLAG.getRequiredValue(flags))
            .setModulesPaths(MODULES_FLAG.getRequiredValue(flags))
            .setSdkModulesConfig(SDK_MODULES_CONFIG_FLAG.getRequiredValue(flags));

    // Optional flags.
    OVERWRITE_OUTPUT_FLAG.getValue(flags).ifPresent(builder::setOverwriteOutput);
    SDK_BUNDLE_CONFIG_FLAG.getValue(flags).ifPresent(builder::setSdkBundleConfig);
    SDK_INTERFACE_DESCRIPTORS_FLAG.getValue(flags).ifPresent(builder::setSdkInterfaceDescriptors);
    METADATA_FILES_FLAG
        .getValue(flags)
        .ifPresent(metadataFiles -> metadataFiles.forEach(builder::addMetadataFileInternal));

    flags.checkNoUnknownFlags();

    return builder.build();
  }

  public void execute() {
    validateInput();

    try (Closer closer = Closer.create()) {
      ImmutableList.Builder<ZipFile> moduleZipFilesBuilder = ImmutableList.builder();
      for (Path modulePath : getModulesPaths()) {
        try {
          moduleZipFilesBuilder.add(closer.register(new ZipFile(modulePath.toFile())));
        } catch (ZipException e) {
          throw CommandExecutionException.builder()
              .withCause(e)
              .withInternalMessage("File '%s' does not seem to be a valid ZIP file.", modulePath)
              .build();
        } catch (IOException e) {
          throw CommandExecutionException.builder()
              .withCause(e)
              .withInternalMessage("Unable to read file '%s'.", modulePath)
              .build();
        }
      }
      ImmutableList<ZipFile> moduleZipFiles = moduleZipFilesBuilder.build();

      new SdkBundleModulesValidator().validateModuleZipFiles(moduleZipFiles);

      SdkModulesConfig sdkModulesConfig =
          getSdkModulesConfig().toBuilder()
              .setBundletool(
                  Bundletool.newBuilder()
                      .setVersion(BundleToolVersion.getCurrentVersion().toString()))
              .build();

      SdkBundleConfig sdkBundleConfig =
          getSdkBundleConfig().orElse(SdkBundleConfig.getDefaultInstance());

      ImmutableList<BundleModule> modules =
          moduleZipFiles.stream()
              .map(
                  moduleZipPath ->
                      BundleModuleParser.parseSdkBundleModule(moduleZipPath, sdkModulesConfig))
              .collect(toImmutableList());

      new SdkBundleModulesValidator().validateSdkBundleModules(modules);

      BundleModule baseModule = parseModuleTargeting(Iterables.getOnlyElement(modules));

      SdkBundle.Builder sdkBundleBuilder =
          SdkBundle.builder()
              .setModule(baseModule)
              .setSdkModulesConfig(sdkModulesConfig)
              .setSdkBundleConfig(sdkBundleConfig)
              .setBundleMetadata(getBundleMetadata());

      getSdkInterfaceDescriptors()
          .map(descriptorPath -> asByteSource(descriptorPath.toFile()))
          .ifPresent(sdkBundleBuilder::setSdkInterfaceDescriptors);

      SdkBundle sdkBundle = sdkBundleBuilder.build();

      SdkBundleValidator.create().validate(sdkBundle);

      Path outputDirectory = getOutputPath().toAbsolutePath().getParent();
      if (Files.notExists(outputDirectory)) {
        logger.info("Output directory '" + outputDirectory + "' does not exist, creating it.");
        FileUtils.createDirectories(outputDirectory);
      }

      if (getOverwriteOutput()) {
        Files.deleteIfExists(getOutputPath());
      }

      new ZipFlingerBundleSerializer().serializeSdkBundle(sdkBundle, getOutputPath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void validateInput() {
    getModulesPaths()
        .forEach(
            path -> {
              checkFileHasExtension("File", path, ".zip");
              checkFileExistsAndReadable(path);
            });
    getSdkInterfaceDescriptors()
        .ifPresent(
            path -> {
              checkFileHasExtension("SDK interface descriptors", path, ".jar");
              checkFileExistsAndReadable(path);
            });
    if (!getOverwriteOutput()) {
      checkFileDoesNotExist(getOutputPath());
    }
  }

  private BundleModule parseModuleTargeting(BundleModule rawModule) {
    BundleModule.Builder moduleWithTargeting = rawModule.toBuilder();

    Optional<Assets> assetsTargeting = generateAssetsTargeting(rawModule);
    assetsTargeting.ifPresent(moduleWithTargeting::setAssetsConfig);

    Optional<NativeLibraries> nativeLibrariesTargeting =
        generateNativeLibrariesTargeting(rawModule);
    nativeLibrariesTargeting.ifPresent(moduleWithTargeting::setNativeConfig);

    return moduleWithTargeting.build();
  }

  private static SdkBundleConfig parseSdkBundleConfigJson(Path sdkBundleConfigJsonPath) {
    SdkBundleConfig.Builder sdkBundleConfig = SdkBundleConfig.newBuilder();
    populateConfigFromJson(sdkBundleConfigJsonPath, sdkBundleConfig);
    return sdkBundleConfig.build();
  }

  private static SdkModulesConfig parseSdkModulesConfigJson(Path sdkModulesConfigJsonPath) {
    SdkModulesConfig.Builder sdkModulesConfig = SdkModulesConfig.newBuilder();
    populateConfigFromJson(sdkModulesConfigJsonPath, sdkModulesConfig);
    return sdkModulesConfig.build();
  }

  private static <T extends Message.Builder> void populateConfigFromJson(
      Path configJsonPath, T builder) {
    try (Reader configReader = BufferedIo.reader(configJsonPath)) {
      JsonFormat.parser().merge(configReader, builder);
    } catch (InvalidProtocolBufferException e) {
      throw InvalidCommandException.builder()
          .withCause(e)
          .withInternalMessage(
              "The file '%s' is not a valid %s JSON file.",
              configJsonPath, builder.build().getClass().getSimpleName())
          .build();
    } catch (IOException e) {
      throw InvalidCommandException.builder()
          .withCause(e)
          .withInternalMessage(
              "An error occurred while trying to read the file '%s'.", configJsonPath)
          .build();
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Builds an Android SDK Bundle from a set of Bundle modules provided as zip "
                        + "files.")
                .addAdditionalParagraph(
                    "Note that the resource table, the AndroidManifest.xml and the resources must "
                        + "already have been compiled with aapt2 in the proto format.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FLAG.getName())
                .setExampleValue("bundle.asb")
                .setDescription("Path to the where the Android SDK Bundle should be built.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OVERWRITE_OUTPUT_FLAG.getName())
                .setOptional(true)
                .setDescription("If set, any previous existing output will be overwritten.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("path/to/module1.zip,path/to/module2.zip,...")
                .setDescription(
                    "The list of module files to build the final Android SDK Bundle from.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_BUNDLE_CONFIG_FLAG.getName())
                .setExampleValue("SdkBundleConfig.pb.json")
                .setDescription(
                    "Path to a JSON file that describes the configuration of the SDK Bundle. "
                        + "This configuration will be merged with BundleTool defaults.")
                .setOptional(true)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_MODULES_CONFIG_FLAG.getName())
                .setExampleValue("SdkModulesConfig.pb.json")
                .setDescription(
                    "Path to a JSON file that describes the configuration of the SDK modules.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_INTERFACE_DESCRIPTORS_FLAG.getName())
                .setExampleValue("path/to/sdk_api_descriptors.jar")
                .setDescription("The packaged public API stubs of this SDK.")
                .setOptional(true)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(METADATA_FILES_FLAG.getName())
                .setExampleValue("com.some.namespace/file-name:path/to/file")
                .setDescription(
                    "Specifies a file that will be included as metadata in the Android SDK Bundle. "
                        + "The format of the flag value is '<bundle-path>:<physical-file>' where "
                        + "'bundle-path' denotes the file location inside the SDK Bundle's "
                        + "metadata directory, and 'physical-file' is an existing file containing "
                        + "the raw data to be stored. The flag can be repeated.")
                .setOptional(true)
                .build())
        .build();
  }
}
