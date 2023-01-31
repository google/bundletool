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

import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.getModulesZip;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.bundle.RuntimeEnabledSdkConfigProto.SdkSplitPropertiesInheritedFromApp;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SdkAsar;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.sdkmodule.SdkModuleToAppBundleModuleConverter;
import com.android.tools.build.bundletool.validation.SdkAsarValidator;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command to generate SDK split for an app from a given ASAR. */
@AutoValue
public abstract class BuildSdkApksForAppCommand {

  private static final int DEFAULT_THREAD_POOL_SIZE = 4;

  public static final String COMMAND_NAME = "build-sdk-apks-for-app";

  private static final Flag<Path> SDK_ARCHIVE_LOCATION_FLAG = Flag.path("sdk-archive");
  private static final Flag<Path> INHERITED_APP_PROPERTIES_LOCATION_FLAG =
      Flag.path("app-properties");

  private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");

  private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");

  // Signing-related flags: should match flags from apksig library.
  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");
  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");
  private static final Flag<Password> KEYSTORE_PASSWORD_FLAG = Flag.password("ks-pass");
  private static final Flag<Password> KEY_PASSWORD_FLAG = Flag.password("key-pass");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Path getSdkArchivePath();

  public abstract SdkSplitPropertiesInheritedFromApp getInheritedAppProperties();

  public abstract Path getOutputFile();

  public abstract Optional<Aapt2Command> getAapt2Command();

  public abstract Optional<SigningConfiguration> getSigningConfiguration();

  ListeningExecutorService getExecutorService() {
    return getExecutorServiceInternal();
  }

  abstract ListeningExecutorService getExecutorServiceInternal();

  abstract boolean isExecutorServiceCreatedByBundleTool();

  public static BuildSdkApksForAppCommand.Builder builder() {
    return new AutoValue_BuildSdkApksForAppCommand.Builder();
  }

  /** Builder for {@link BuildSdkApksForAppCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Sets the path to the SDK archive file. Must have the extension ".asar". */
    public abstract Builder setSdkArchivePath(Path sdkArchivePath);

    /** Sets the config containing app properties that the SDK split should inherit. */
    abstract Builder setInheritedAppProperties(
        SdkSplitPropertiesInheritedFromApp sdkSplitPropertiesInheritedFromApp);

    /** Sets path to a config file containing app properties that the SDK split should inherit. */
    public Builder setInheritedAppProperties(Path inheritedAppProperties) {
      return setInheritedAppProperties(parseInheritedAppProperties(inheritedAppProperties));
    }

    /** Path to the output produced by this command. Must have extension ".apks". */
    public abstract Builder setOutputFile(Path outputFile);

    /** Provides a wrapper around the execution of the aapt2 command. */
    public abstract Builder setAapt2Command(Aapt2Command aapt2Command);

    /** Sets the signing configuration to be used for all generated APKs. */
    public abstract Builder setSigningConfiguration(SigningConfiguration signingConfiguration);

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
      return autoBuild();
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(CommandDescription.builder().setShortDescription("").build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_ARCHIVE_LOCATION_FLAG.getName())
                .setExampleValue("sdk.asar")
                .setDescription("Path to SDK archive to generate app-specific split APKs from.")
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
                .setDescription("Path to where the APK Set archive should be created.")
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

  public static BuildSdkApksForAppCommand fromFlags(ParsedFlags flags) {
    return fromFlags(flags, System.out, DEFAULT_PROVIDER);
  }

  static BuildSdkApksForAppCommand fromFlags(
      ParsedFlags flags, PrintStream out, SystemEnvironmentProvider systemEnvironmentProvider) {
    BuildSdkApksForAppCommand.Builder command =
        BuildSdkApksForAppCommand.builder()
            .setSdkArchivePath(SDK_ARCHIVE_LOCATION_FLAG.getRequiredValue(flags))
            .setInheritedAppProperties(
                INHERITED_APP_PROPERTIES_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags));

    AAPT2_PATH_FLAG
        .getValue(flags)
        .ifPresent(
            aapt2Path -> command.setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path)));

    populateSigningConfigurationFromFlags(command, flags, out, systemEnvironmentProvider);

    return command.build();
  }

  public void execute() {
    validateInput();

    try (TempDirectory tempDir = new TempDirectory(getClass().getSimpleName());
        ZipFile asarZip = new ZipFile(getSdkArchivePath().toFile())) {
      Path modulesPath = tempDir.getPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
      try (ZipFile modulesZip = getModulesZip(asarZip, modulesPath)) {
        SdkAsarValidator.validateModulesFile(modulesZip);
        SdkAsar sdkAsar = SdkAsar.buildFromZip(asarZip, modulesZip, modulesPath);
        generateAppApks(sdkAsar, tempDir);
      }
    } catch (ZipException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("ASAR is not a valid zip file.")
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the SDK archive.", e);
    }
  }

  private void validateInput() {
    checkFileExistsAndReadable(getSdkArchivePath());
    checkFileHasExtension("ASAR file", getSdkArchivePath(), ".asar");
  }

  private void generateAppApks(SdkAsar sdkAsar, TempDirectory tempDirectory) {
    BundleModule convertedAppModule =
        new SdkModuleToAppBundleModuleConverter(sdkAsar.getModule(), getInheritedAppProperties())
            .convert();
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
