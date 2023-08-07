/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.APK_SET;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.getModulesZip;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.RuntimeEnabledSdkConfigProto.SdkSplitPropertiesInheritedFromApp;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SdkAsar;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.sdkmodule.SdkModuleToAppBundleModuleConverter;
import com.android.tools.build.bundletool.validation.SdkAsarValidator;
import com.android.tools.build.bundletool.validation.SdkBundleValidator;
import com.google.auto.value.AutoValue;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command to generate SDK split for an app from a given ASAR. */
@AutoValue
public abstract class BuildSdkApksForAppCommand {

  private static final int DEFAULT_THREAD_POOL_SIZE = 4;

  private static final String APK_SET_ARCHIVE_EXTENSION = "apks";

  public static final String COMMAND_NAME = "build-sdk-apks-for-app";

  private static final Logger logger = Logger.getLogger(BuildSdkApksForAppCommand.class.getName());

  private static final Flag<Path> SDK_BUNDLE_LOCATION_FLAG = Flag.path("sdk-bundle");

  private static final Flag<Path> SDK_ARCHIVE_LOCATION_FLAG = Flag.path("sdk-archive");

  private static final Flag<Path> INHERITED_APP_PROPERTIES_LOCATION_FLAG =
      Flag.path("app-properties");

  private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");

  private static final Flag<OutputFormat> OUTPUT_FORMAT_FLAG =
      Flag.enumFlag("output-format", OutputFormat.class);

  private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");

  // Signing-related flags: should match flags from apksig library.
  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");
  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");
  private static final Flag<Password> KEYSTORE_PASSWORD_FLAG = Flag.password("ks-pass");
  private static final Flag<Password> KEY_PASSWORD_FLAG = Flag.password("key-pass");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Optional<Path> getSdkBundlePath();

  public abstract Optional<Path> getSdkArchivePath();

  public abstract SdkSplitPropertiesInheritedFromApp getInheritedAppProperties();

  public abstract Path getOutputFile();

  public abstract OutputFormat getOutputFormat();

  public abstract Optional<Aapt2Command> getAapt2Command();

  public abstract Optional<SigningConfiguration> getSigningConfiguration();

  public abstract Optional<ApkListener> getApkListener();

  public abstract Optional<ApkModifier> getApkModifier();

  public abstract boolean getSerializeTableOfContents();

  ListeningExecutorService getExecutorService() {
    return getExecutorServiceInternal();
  }

  abstract ListeningExecutorService getExecutorServiceInternal();

  abstract boolean isExecutorServiceCreatedByBundleTool();

  public static BuildSdkApksForAppCommand.Builder builder() {
    return new AutoValue_BuildSdkApksForAppCommand.Builder()
        .setOutputFormat(APK_SET)
        .setSerializeTableOfContents(false);
  }

  /** Builder for {@link BuildSdkApksForAppCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Sets the path to the SDK bundle file. Must have the extension ".asb". */
    public abstract Builder setSdkBundlePath(Path sdkBundlePath);

    /** Sets the path to the SDK archive file. Must have the extension ".asar". */
    public abstract Builder setSdkArchivePath(Path sdkArchivePath);

    /** Sets the config containing app properties that the SDK split should inherit. */
    public abstract Builder setInheritedAppProperties(
        SdkSplitPropertiesInheritedFromApp sdkSplitPropertiesInheritedFromApp);

    /** Sets path to a config file containing app properties that the SDK split should inherit. */
    public Builder setInheritedAppProperties(Path inheritedAppProperties) {
      return setInheritedAppProperties(parseInheritedAppProperties(inheritedAppProperties));
    }

    /** Path to the output produced by this command. Must have extension ".apks". */
    public abstract Builder setOutputFile(Path outputFile);

    /** Sets the output format. */
    public abstract Builder setOutputFormat(OutputFormat outputFormat);

    /** Provides a wrapper around the execution of the aapt2 command. */
    public abstract Builder setAapt2Command(Aapt2Command aapt2Command);

    /** Sets the signing configuration to be used for all generated APKs. */
    public abstract Builder setSigningConfiguration(SigningConfiguration signingConfiguration);

    /**
     * Provides an {@link ApkListener} that will be notified at defined stages of APK creation.
     *
     * <p>The {@link ApkListener} must be thread-safe.
     */
    public abstract Builder setApkListener(ApkListener apkListener);

    /**
     * Provides an {@link ApkModifier} that will be invoked just before the APKs are finalized,
     * serialized on disk and signed.
     *
     * <p>The {@link ApkModifier} must be thread-safe as it may in the future be invoked
     * concurrently for the different APKs.
     */
    public abstract Builder setApkModifier(ApkModifier apkModifier);

    /**
     * Allows to set an executor service for parallelization.
     *
     * <p>Optional. The caller is responsible for providing a service that accepts new tasks, and
     * for shutting it down afterwards.
     */
    @CanIgnoreReturnValue
    public Builder setExecutorService(ListeningExecutorService executorService) {
      setExecutorServiceInternal(executorService);
      setExecutorServiceCreatedByBundleTool(false);
      return this;
    }

    /**
     * Whether the {@link BuildApksResult} should be serialized in the output directory or the .apks
     * archive.
     */
    public abstract Builder setSerializeTableOfContents(boolean serializeTableOfContents);

    abstract Builder setExecutorServiceInternal(ListeningExecutorService executorService);

    abstract Optional<ListeningExecutorService> getExecutorServiceInternal();

    /**
     * Sets whether the ExecutorService has been created by bundletool, otherwise provided by the
     * client.
     *
     * <p>If true, the ExecutorService is shut down at the end of execution of this command.
     */
    abstract Builder setExecutorServiceCreatedByBundleTool(boolean value);

    public abstract BuildSdkApksForAppCommand autoBuild();

    public BuildSdkApksForAppCommand build() {
      if (!getExecutorServiceInternal().isPresent()) {
        setExecutorServiceInternal(createInternalExecutorService(DEFAULT_THREAD_POOL_SIZE));
        setExecutorServiceCreatedByBundleTool(true);
      }
      BuildSdkApksForAppCommand command = autoBuild();
      checkState(
          command.getSdkBundlePath().isPresent() ^ command.getSdkArchivePath().isPresent(),
          "One and only one of SdkBundlePath and SdkArchivePath should be set.");
      return command;
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(CommandDescription.builder().setShortDescription("").build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("sdk.asb")
                .setDescription(
                    "Path to SDK bundle to generate app-specific split APKs from. Can"
                        + " not be used together with the `sdk-archive` flag.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_ARCHIVE_LOCATION_FLAG.getName())
                .setExampleValue("sdk.asar")
                .setDescription(
                    "Path to SDK archive to generate app-specific split APKs from. Can"
                        + " not be used together with the `sdk-bundle` flag.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(INHERITED_APP_PROPERTIES_LOCATION_FLAG.getName())
                .setExampleValue("config.json")
                .setDescription(
                    "Path to the JSON config containing app properties that the SDK split should"
                        + " inherit.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FILE_FLAG.getName())
                .setExampleValue("output.apks")
                .setDescription(
                    "Path to where the APK Set archive should be created (default) or path to the"
                        + " directory where generated APKs should be stored when flag --%s is set"
                        + " to '%s'.",
                    OUTPUT_FORMAT_FLAG.getName(), DIRECTORY)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FORMAT_FLAG.getName())
                .setExampleValue(joinFlagOptions(OutputFormat.values()))
                .setOptional(true)
                .setDescription(
                    "Specifies output format for generated APKs. If set to '%s' outputs APKs into"
                        + " the created APK Set archive (default). If set to '%s' outputs APKs"
                        + " into the specified directory.",
                    APK_SET, DIRECTORY)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(AAPT2_PATH_FLAG.getName())
                .setExampleValue("path/to/aapt2")
                .setOptional(true)
                .setDescription("Path to the aapt2 binary to use.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_FLAG.getName())
                .setExampleValue("path/to/keystore")
                .setOptional(true)
                .setDescription(
                    "Path to the keystore that should be used to sign the generated APKs. If not "
                        + "set, the default debug keystore will be used if it exists. If not found "
                        + "the APKs will not be signed. If set, the flag '%s' must also be set.",
                    KEY_ALIAS_FLAG)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEY_ALIAS_FLAG.getName())
                .setExampleValue("key-alias")
                .setOptional(true)
                .setDescription(
                    "Alias of the key to use in the keystore to sign the generated APKs.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_PASSWORD_FLAG.getName())
                .setExampleValue("[pass|file]:value")
                .setOptional(true)
                .setDescription(
                    "Password of the keystore to use to sign the generated APKs. If provided, must "
                        + "be prefixed with either 'pass:' (if the password is passed in clear "
                        + "text, e.g. 'pass:qwerty') or 'file:' (if the password is the first line "
                        + "of a file, e.g. 'file:/tmp/myPassword.txt'). If this flag is not set, "
                        + "the password will be requested on the prompt.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEY_PASSWORD_FLAG.getName())
                .setExampleValue("key-password")
                .setOptional(true)
                .setDescription(
                    "Password of the key in the keystore to use to sign the generated APKs. If "
                        + "provided, must be prefixed with either 'pass:' (if the password is "
                        + "passed in clear text, e.g. 'pass:qwerty') or 'file:' (if the password "
                        + "is the first line of a file, e.g. 'file:/tmp/myPassword.txt'). If this "
                        + "flag is not set, the keystore password will be tried. If that fails, "
                        + "the password will be requested on the prompt.")
                .build())
        .build();
  }

  private static String joinFlagOptions(Enum<?>... flagOptions) {
    return stream(flagOptions).map(Enum::name).map(String::toLowerCase).collect(joining("|"));
  }

  public static BuildSdkApksForAppCommand fromFlags(ParsedFlags flags) {
    return fromFlags(flags, System.out, DEFAULT_PROVIDER);
  }

  static BuildSdkApksForAppCommand fromFlags(
      ParsedFlags flags, PrintStream out, SystemEnvironmentProvider systemEnvironmentProvider) {
    BuildSdkApksForAppCommand.Builder command =
        BuildSdkApksForAppCommand.builder()
            .setInheritedAppProperties(
                INHERITED_APP_PROPERTIES_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags));
    OUTPUT_FORMAT_FLAG.getValue(flags).ifPresent(command::setOutputFormat);
    SDK_BUNDLE_LOCATION_FLAG.getValue(flags).ifPresent(command::setSdkBundlePath);
    SDK_ARCHIVE_LOCATION_FLAG.getValue(flags).ifPresent(command::setSdkArchivePath);
    AAPT2_PATH_FLAG
        .getValue(flags)
        .ifPresent(
            aapt2Path -> command.setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path)));

    populateSigningConfigurationFromFlags(command, flags, out, systemEnvironmentProvider);

    return command.build();
  }

  @CanIgnoreReturnValue
  public Path execute() {
    validateInput();

    Path outputDirectory =
        getOutputFormat().equals(APK_SET) ? getOutputFile().getParent() : getOutputFile();
    if (outputDirectory != null && Files.notExists(outputDirectory)) {
      logger.info("Output directory '" + outputDirectory + "' does not exist, creating it.");
      FileUtils.createDirectories(outputDirectory);
    }

    if (getSdkBundlePath().isPresent()) {
      executeForSdkBundle();
    } else if (getSdkArchivePath().isPresent()) {
      executeForSdkArchive();
    } else {
      throw new IllegalStateException(
          "One and only one of SdkBundlePath and SdkArchivePath should be set.");
    }
    return getOutputFile();
  }

  private void validateInput() {
    if (getSdkBundlePath().isPresent()) {
      checkFileExistsAndReadable(getSdkBundlePath().get());
      checkFileHasExtension("ASB file", getSdkBundlePath().get(), ".asb");
    }
    if (getSdkArchivePath().isPresent()) {
      checkFileExistsAndReadable(getSdkArchivePath().get());
      checkFileHasExtension("ASAR file", getSdkArchivePath().get(), ".asar");
    }
    if (getOutputFormat().equals(APK_SET)) {
      if (!Objects.equals(MoreFiles.getFileExtension(getOutputFile()), APK_SET_ARCHIVE_EXTENSION)) {
        throw InvalidCommandException.builder()
            .withInternalMessage(
                "Flag --output should be the path where to generate the APK Set. "
                    + "Its extension must be '.apks'.")
            .build();
      }
      checkFileDoesNotExist(getOutputFile());
    }
  }

  private void executeForSdkBundle() {
    try (TempDirectory tempDir = new TempDirectory(getClass().getSimpleName());
        ZipFile sdkBundleZip = new ZipFile(getSdkBundlePath().get().toFile())) {
      Path modulesPath = tempDir.getPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
      try (ZipFile modulesZip = getModulesZip(sdkBundleZip, modulesPath)) {
        SdkBundleValidator sdkBundleValidator = SdkBundleValidator.create();
        sdkBundleValidator.validateModulesFile(modulesZip);
        // SdkBundle#getVersionCode is not used in `build-apks`. It does not matter what
        // value we set here, so we are just setting 0.
        SdkBundle sdkBundle =
            SdkBundle.buildFromZip(sdkBundleZip, modulesZip, /* versionCode= */ 0);
        sdkBundleValidator.validate(sdkBundle);
        generateAppApks(sdkBundle.getModule(), tempDir);
      }
    } catch (ZipException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("ASB is not a valid zip file.")
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the SDK bundle.", e);
    } finally {
      if (isExecutorServiceCreatedByBundleTool()) {
        getExecutorService().shutdown();
      }
    }
  }

  private void executeForSdkArchive() {
    try (TempDirectory tempDir = new TempDirectory(getClass().getSimpleName());
        ZipFile asarZip = new ZipFile(getSdkArchivePath().get().toFile())) {
      Path modulesPath = tempDir.getPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
      try (ZipFile modulesZip = getModulesZip(asarZip, modulesPath)) {
        SdkAsarValidator.validateModulesFile(modulesZip);
        SdkAsar sdkAsar = SdkAsar.buildFromZip(asarZip, modulesZip, modulesPath);
        generateAppApks(sdkAsar.getModule(), tempDir);
      }
    } catch (ZipException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("ASAR is not a valid zip file.")
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the SDK archive.", e);
    } finally {
      if (isExecutorServiceCreatedByBundleTool()) {
        getExecutorService().shutdown();
      }
    }
  }

  private void generateAppApks(BundleModule sdkModule, TempDirectory tempDirectory) {
    BundleModule convertedAppModule =
        new SdkModuleToAppBundleModuleConverter(sdkModule, getInheritedAppProperties()).convert();
    DaggerBuildSdkApksForAppManagerComponent.builder()
        .setBuildSdkApksForAppCommand(this)
        .setModule(convertedAppModule)
        .setTempDirectory(tempDirectory)
        .build()
        .create()
        .execute();
  }

  private static SdkSplitPropertiesInheritedFromApp parseInheritedAppProperties(
      Path propertiesInheritedFromAppFile) {
    checkFileExistsAndReadable(propertiesInheritedFromAppFile);
    checkFileHasExtension(
        "JSON file containing properties inherited from the app",
        propertiesInheritedFromAppFile,
        ".json");
    try (Reader reader = BufferedIo.reader(propertiesInheritedFromAppFile)) {
      SdkSplitPropertiesInheritedFromApp.Builder builder =
          SdkSplitPropertiesInheritedFromApp.newBuilder();
      JsonFormat.parser().merge(reader, builder);
      return builder.build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while reading the file '%s'.", propertiesInheritedFromAppFile), e);
    }
  }

  private static void populateSigningConfigurationFromFlags(
      Builder buildSdkApksForAppCommand,
      ParsedFlags flags,
      PrintStream out,
      SystemEnvironmentProvider provider) {
    // Signing-related arguments.
    Optional<Path> keystorePath = KEYSTORE_FLAG.getValue(flags);
    Optional<String> keyAlias = KEY_ALIAS_FLAG.getValue(flags);
    Optional<Password> keystorePassword = KEYSTORE_PASSWORD_FLAG.getValue(flags);
    Optional<Password> keyPassword = KEY_PASSWORD_FLAG.getValue(flags);

    if (keystorePath.isPresent() && keyAlias.isPresent()) {
      SignerConfig signerConfig =
          SignerConfig.extractFromKeystore(
              keystorePath.get(), keyAlias.get(), keystorePassword, keyPassword);
      SigningConfiguration.Builder builder =
          SigningConfiguration.builder().setSignerConfig(signerConfig);
      buildSdkApksForAppCommand.setSigningConfiguration(builder.build());
    } else if (keystorePath.isPresent() && !keyAlias.isPresent()) {
      throw InvalidCommandException.builder()
          .withInternalMessage("Flag --ks-key-alias is required when --ks is set.")
          .build();
    } else if (!keystorePath.isPresent() && keyAlias.isPresent()) {
      throw InvalidCommandException.builder()
          .withInternalMessage("Flag --ks is required when --ks-key-alias is set.")
          .build();
    } else {
      // Try to use debug keystore if present.
      Optional<SigningConfiguration> debugConfig =
          DebugKeystoreUtils.getDebugSigningConfiguration(provider);
      if (debugConfig.isPresent()) {
        out.printf(
            "INFO: The APKs will be signed with the debug keystore found at '%s'.%n",
            DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.getUnchecked(provider).get());
        buildSdkApksForAppCommand.setSigningConfiguration(debugConfig.get());
      } else {
        out.println(
            "WARNING: The APKs won't be signed and thus not installable unless you also pass a "
                + "keystore via the flag --ks. See the command help for more information.");
      }
    }
  }

  /**
   * Creates an internal executor service that uses at most the given number of threads.
   *
   * <p>The caller is responsible for shutting down the executor service.
   */
  private static ListeningExecutorService createInternalExecutorService(int maxThreads) {
    checkArgument(maxThreads >= 0, "The maxThreads must be positive, got %s.", maxThreads);
    return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(maxThreads));
  }
}
