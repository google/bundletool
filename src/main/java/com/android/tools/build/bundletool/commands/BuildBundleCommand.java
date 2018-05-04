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

import static com.android.tools.build.bundletool.model.AppBundle.BUNDLE_CONFIG_FILE_NAME;
import static com.android.tools.build.bundletool.model.AppBundle.METADATA_DIRECTORY;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.MoreFiles.getNameWithoutExtension;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.InputStreamSupplier;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.targeting.TargetingGenerator;
import com.android.tools.build.bundletool.utils.ZipUtils;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.android.tools.build.bundletool.utils.flags.Flag;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.android.tools.build.bundletool.validation.BundleModulesValidator;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command responsible for building an App Bundle from App Bundle modules. */
@AutoValue
public abstract class BuildBundleCommand {

  public static final String COMMAND_NAME = "build-bundle";

  private static final Flag<Path> OUTPUT_FLAG = Flag.path("output");
  private static final Flag<Path> BUNDLE_CONFIG_FLAG = Flag.path("config");
  private static final Flag<ImmutableList<Path>> MODULES_FLAG = Flag.pathList("modules");
  private static final Flag<ImmutableMap<ZipPath, Path>> METADATA_FILES_FLAG =
      Flag.mapCollector("metadata-file", ZipPath.class, Path.class);

  public abstract Path getOutputPath();

  public abstract ImmutableList<Path> getModulesPaths();

  /** Returns the bundle configuration. */
  public abstract Optional<BundleConfig> getBundleConfig();

  /** Returns the bundle metadata. */
  public abstract BundleMetadata getBundleMetadata();

  public static Builder builder() {
    return new AutoValue_BuildBundleCommand.Builder();
  }

  /** Builder for the {@link BuildBundleCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setOutputPath(Path outputPath);

    public abstract Builder setModulesPaths(ImmutableList<Path> modulesPaths);

    /** Sets the Bundle configuration. Optional. */
    public abstract Builder setBundleConfig(BundleConfig bundleConfig);

    abstract BundleMetadata.Builder bundleMetadataBuilder();

    /**
     * Adds the given file as metadata to the bundle into directory {@code
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
        throw ValidationException.builder()
            .withMessage("Metadata file '%s' does not exist.", file)
            .build();
      }
      bundleMetadataBuilder().addFile(metadataPath, () -> BufferedIo.inputStream(file));
    }

    /**
     * Sets the main dex list file as bundle metadata.
     *
     * <p>Optional.
     *
     * @param file plain text file that contains one Java class name per line using format
     *     "com/example/MyClass.class"
     */
    public Builder setMainDexListFile(Path file) {
      return addMetadataFile(
          BundleMetadata.BUNDLETOOL_NAMESPACE, BundleMetadata.MAIN_DEX_LIST_FILE_NAME, file);
    }

    public abstract BuildBundleCommand build();
  }

  public static BuildBundleCommand fromFlags(ParsedFlags flags) {
    BuildBundleCommand.Builder builder =
        builder()
            .setOutputPath(OUTPUT_FLAG.getRequiredValue(flags))
            .setModulesPaths(MODULES_FLAG.getRequiredValue(flags));

    // Optional flags.
    BUNDLE_CONFIG_FLAG
        .getValue(flags)
        .ifPresent(path -> builder.setBundleConfig(parseBundleConfigJson(path)));
    METADATA_FILES_FLAG
        .getValue(flags)
        .ifPresent(metadataFiles -> metadataFiles.forEach(builder::addMetadataFileInternal));

    flags.checkNoUnknownFlags();

    return builder.build();
  }

  public void execute() {
    validateInput();

    ZipBuilder bundleBuilder = new ZipBuilder();

    List<ZipFile> openedZipFiles = new ArrayList<>();
    try {
      // Merge in all the modules, each module into its own sub-directory.
      for (Path module : getModulesPaths()) {
        try {
          ZipFile moduleZipFile = new ZipFile(module.toFile());
          openedZipFiles.add(moduleZipFile);

          ZipPath moduleDir = ZipPath.create(getNameWithoutExtension(module));

          bundleBuilder.copyAllContentsFromZip(moduleDir, moduleZipFile);

          Optional<Assets> assetsTargeting = generateAssetsTargeting(moduleZipFile);
          if (assetsTargeting.isPresent()) {
            bundleBuilder.addFileWithProtoContent(
                moduleDir.resolve("assets.pb"), assetsTargeting.get());
          }

          Optional<NativeLibraries> nativeLibrariesTargeting =
              generateNativeLibrariesTargeting(moduleZipFile);
          if (nativeLibrariesTargeting.isPresent()) {
            bundleBuilder.addFileWithProtoContent(
                moduleDir.resolve("native.pb"), nativeLibrariesTargeting.get());
          }
        } catch (ZipException e) {
          throw CommandExecutionException.builder()
              .withCause(e)
              .withMessage("File '%s' does not seem to be a valid ZIP file.", module)
              .build();
        } catch (IOException e) {
          throw CommandExecutionException.builder()
              .withCause(e)
              .withMessage("Unable to read file '%s'.", module)
              .build();
        } catch (CommandExecutionException e) {
          // Re-throw with additional context.
          throw CommandExecutionException.builder()
              .withCause(e)
              .withMessage("Error processing module file '%s'.", module)
              .build();
        }
      }

      // Read the Bundle Config file if provided by the developer.
      BundleConfig bundleConfig =
          getBundleConfig()
              .orElse(BundleConfig.getDefaultInstance())
              .toBuilder()
              .setBundletool(
                  Bundletool.newBuilder()
                      .setVersion(BundleToolVersion.getCurrentVersion().toString()))
              .build();
      bundleBuilder.addFileWithContent(
          ZipPath.create(BUNDLE_CONFIG_FILE_NAME), bundleConfig.toByteArray());

      // Add metadata files.
      for (Map.Entry<ZipPath, InputStreamSupplier> metadataEntry :
          getBundleMetadata().getFileDataMap().entrySet()) {
        bundleBuilder.addFile(
            METADATA_DIRECTORY.resolve(metadataEntry.getKey()), metadataEntry.getValue());
      }

      try {
        bundleBuilder.writeTo(getOutputPath());
      } catch (IOException e) {
        throw CommandExecutionException.builder()
            .withCause(e)
            .withMessage("Unable to write file to location '%s'.")
            .build();
      }
    } finally {
      ZipUtils.closeZipFiles(openedZipFiles);
    }
  }

  private void validateInput() {
    checkFileDoesNotExist(getOutputPath());

    new BundleModulesValidator().validate(getModulesPaths());
  }

  private Optional<Assets> generateAssetsTargeting(ZipFile module) {
    ImmutableList<ZipPath> assetDirectories =
        ZipUtils.getFilesWithPathPrefix(module, BundleModule.ASSETS_DIRECTORY)
            .filter(path -> path.getNameCount() > 1)
            .map(ZipPath::getParent)
            .distinct()
            .collect(toImmutableList());

    if (assetDirectories.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new TargetingGenerator().generateTargetingForAssets(assetDirectories));
  }

  private Optional<NativeLibraries> generateNativeLibrariesTargeting(ZipFile module) {
    // Validation ensures that files under "lib/" conform to pattern "lib/<abi-dir>/file.so".
    // We extract the distinct "lib/<abi-dir>" directories.
    ImmutableList<String> libAbiDirs =
        ZipUtils.getFilesWithPathPrefix(module, BundleModule.LIB_DIRECTORY)
            .filter(path -> path.getNameCount() > 2)
            .map(path -> path.subpath(0, 2))
            .map(ZipPath::toString)
            .distinct()
            .collect(toImmutableList());

    if (libAbiDirs.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new TargetingGenerator().generateTargetingForNativeLibraries(libAbiDirs));
  }

  private static BundleConfig parseBundleConfigJson(Path bundleConfigJsonPath) {
    BundleConfig.Builder bundleConfig = BundleConfig.newBuilder();
    try (Reader bundleConfigReader = BufferedIo.reader(bundleConfigJsonPath)) {
      JsonFormat.parser().merge(bundleConfigReader, bundleConfig);
    } catch (InvalidProtocolBufferException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("The file '%s' is not a valid BundleConfig JSON file.", bundleConfigJsonPath)
          .build();
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage(
              "An error occurred while trying to read the file '%s'.", bundleConfigJsonPath)
          .build();
    }
    return bundleConfig.build();
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Builds an Android App Bundle from a set of Bundle modules provided as zip "
                        + "files.")
                .addAdditionalParagraph(
                    "Note that the resource table, the AndroidManifest.xml and the resources must "
                        + "already have been compiled with aapt2 in the proto format.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FLAG.getName())
                .setExampleValue("bundle.aab")
                .setDescription("Path to the where the Android App Bundle should be built.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("path/to/module1.zip,path/to/module2.zip,...")
                .setDescription(
                    "The list of module files to build the final Android App Bundle from.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(BUNDLE_CONFIG_FLAG.getName())
                .setExampleValue("BundleConfig.pb.json")
                .setDescription(
                    "Path to a JSON file that describes the configuration of the App Bundle. "
                        + "This configuration will be merged with BundleTool defaults.")
                .setOptional(true)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(METADATA_FILES_FLAG.getName())
                .setExampleValue("com.some.namespace/file-name:path/to/file")
                .setDescription(
                    "Specifies a file that will be included as metadata in the Android App Bundle. "
                        + "The format of the flag value is '<bundle-path>:<physical-file>' where "
                        + "'bundle-path' denotes the file location inside the App Bundle's "
                        + "metadata directory, and 'physical-file' is an existing file containing "
                        + "the raw data to be stored. The flag can be repeated.")
                .setOptional(true)
                .build())
        .build();
  }
}
