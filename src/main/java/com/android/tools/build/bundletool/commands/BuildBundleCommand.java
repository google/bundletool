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

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.targeting.TargetingGenerator;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.validation.BundleModulesValidator;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private static final Flag<Boolean> UNCOMPRESSED_FLAG = Flag.booleanFlag("uncompressed");

  public abstract Path getOutputPath();

  public abstract ImmutableList<Path> getModulesPaths();

  /** Returns the bundle configuration. */
  public abstract Optional<BundleConfig> getBundleConfig();

  /** Returns the bundle metadata. */
  public abstract BundleMetadata getBundleMetadata();

  abstract boolean getUncompressedBundle();

  public static Builder builder() {
    // By default, everything is compressed.
    return new AutoValue_BuildBundleCommand.Builder().setUncompressedBundle(false);
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
      bundleMetadataBuilder().addFile(metadataPath, BufferedIo.inputStreamSupplier(file));
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

    /**
     * Sets whether the generated App Bundle should have its entries all uncompressed.
     *
     * <p>Defaults to {@code false}.
     */
    public abstract Builder setUncompressedBundle(boolean uncompressed);

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
    UNCOMPRESSED_FLAG.getValue(flags).ifPresent(builder::setUncompressedBundle);

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
              .withMessage("File '%s' does not seem to be a valid ZIP file.", modulePath)
              .build();
        } catch (IOException e) {
          throw CommandExecutionException.builder()
              .withCause(e)
              .withMessage("Unable to read file '%s'.", modulePath)
              .build();
        }
      }
      ImmutableList<ZipFile> moduleZipFiles = moduleZipFilesBuilder.build();

      ImmutableList<BundleModule> modules = new BundleModulesValidator().validate(moduleZipFiles);
      checkState(
          moduleZipFiles.size() == modules.size(),
          "Incorrect number of modules parsed (%s != %s).",
          moduleZipFiles.size(),
          modules.size());

      ImmutableList.Builder<BundleModule> modulesWithTargeting = ImmutableList.builder();
      for (BundleModule module : modules) {
        BundleModule.Builder moduleWithTargeting = module.toBuilder();

        Optional<Assets> assetsTargeting = generateAssetsTargeting(module);
        assetsTargeting.ifPresent(moduleWithTargeting::setAssetsConfig);

        Optional<NativeLibraries> nativeLibrariesTargeting =
            generateNativeLibrariesTargeting(module);
        nativeLibrariesTargeting.ifPresent(moduleWithTargeting::setNativeConfig);

        Optional<ApexImages> apexImagesTargeting = generateApexImagesTargeting(module);
        apexImagesTargeting.ifPresent(moduleWithTargeting::setApexConfig);

        modulesWithTargeting.add(moduleWithTargeting.build());
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

      AppBundle appBundle =
          AppBundle.buildFromModules(
              modulesWithTargeting.build(), bundleConfig, getBundleMetadata());

      new AppBundleSerializer(getUncompressedBundle()).writeToDisk(appBundle, getOutputPath());
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
    checkFileDoesNotExist(getOutputPath());
  }

  private static Optional<Assets> generateAssetsTargeting(BundleModule module) {
    ImmutableList<ZipPath> assetDirectories =
        module
            .findEntriesUnderPath(BundleModule.ASSETS_DIRECTORY)
            .map(ModuleEntry::getPath)
            .filter(path -> path.getNameCount() > 1)
            .map(ZipPath::getParent)
            .distinct()
            .collect(toImmutableList());

    if (assetDirectories.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new TargetingGenerator().generateTargetingForAssets(assetDirectories));
  }

  private static Optional<NativeLibraries> generateNativeLibrariesTargeting(BundleModule module) {
    // Validation ensures that files under "lib/" conform to pattern "lib/<abi-dir>/file.so".
    // We extract the distinct "lib/<abi-dir>" directories.
    ImmutableList<String> libAbiDirs =
        module
            .findEntriesUnderPath(BundleModule.LIB_DIRECTORY)
            .map(ModuleEntry::getPath)
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

  private static Optional<ApexImages> generateApexImagesTargeting(BundleModule module) {
    // Validation ensures that files under "apex/" conform to the pattern
    // "apex/<abi1>.<abi2>...<abiN>.img".
    ImmutableList<ZipPath> apexImageFiles =
        module
            .findEntriesUnderPath(BundleModule.APEX_DIRECTORY)
            .map(ModuleEntry::getPath)
            .collect(toImmutableList());

    if (apexImageFiles.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new TargetingGenerator().generateTargetingForApexImages(apexImageFiles));
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
