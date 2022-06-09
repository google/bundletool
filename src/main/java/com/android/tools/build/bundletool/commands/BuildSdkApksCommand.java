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

import static com.android.tools.build.bundletool.commands.BuildSdkApksCommand.OutputFormat.APK_SET;
import static com.android.tools.build.bundletool.commands.BuildSdkApksCommand.OutputFormat.DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.getModulesZip;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.validation.SdkBundleValidator;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command to generate APKs from an Android SDK Bundle. */
@AutoValue
public abstract class BuildSdkApksCommand {

  private static final int DEFAULT_THREAD_POOL_SIZE = 4;

  public static final String COMMAND_NAME = "build-sdk-apks";

  private static final Integer DEFAULT_SDK_VERSION_CODE = 1;

  /** Output format for generated APKs. */
  public enum OutputFormat {
    /** Generated APKs are stored inside created APK Set archive. */
    APK_SET,
    /** Generated APKs are stored inside specified directory. */
    DIRECTORY
  }

  private static final Flag<Path> SDK_BUNDLE_LOCATION_FLAG = Flag.path("sdk-bundle");
  private static final Flag<Integer> VERSION_CODE_FLAG = Flag.positiveInteger("version-code");
  private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");
  private static final Flag<OutputFormat> OUTPUT_FORMAT_FLAG =
      Flag.enumFlag("output-format", OutputFormat.class);
  private static final Flag<Boolean> OVERWRITE_OUTPUT_FLAG = Flag.booleanFlag("overwrite");
  private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");
  private static final Flag<Integer> MAX_THREADS_FLAG = Flag.positiveInteger("max-threads");
  private static final Flag<Boolean> VERBOSE_FLAG = Flag.booleanFlag("verbose");

  // Signing-related flags: should match flags from apksig library.
  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");
  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");
  private static final Flag<Password> KEYSTORE_PASSWORD_FLAG = Flag.password("ks-pass");
  private static final Flag<Password> KEY_PASSWORD_FLAG = Flag.password("key-pass");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  abstract Path getSdkBundlePath();

  abstract Integer getVersionCode();

  abstract Path getOutputFile();

  abstract boolean getOverwriteOutput();

  ListeningExecutorService getExecutorService() {
    return getExecutorServiceInternal();
  }

  abstract ListeningExecutorService getExecutorServiceInternal();

  public abstract boolean getVerbose();

  abstract boolean isExecutorServiceCreatedByBundleTool();

  abstract OutputFormat getOutputFormat();

  abstract Optional<Aapt2Command> getAapt2Command();

  abstract Optional<SigningConfiguration> getSigningConfiguration();

  public abstract Optional<ApkListener> getApkListener();

  public abstract Optional<ApkModifier> getApkModifier();

  public abstract Optional<Integer> getFirstVariantNumber();

  /** Creates a builder for the {@link BuildSdkApksCommand} with some default settings. */
  public static BuildSdkApksCommand.Builder builder() {
    return new AutoValue_BuildSdkApksCommand.Builder()
        .setOverwriteOutput(false)
        .setOutputFormat(APK_SET)
        .setVersionCode(DEFAULT_SDK_VERSION_CODE)
        .setVerbose(false);
  }

  /** Builder for the {@link BuildSdkApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the input SDK bundle. Must have the extension ".asb". */
    public abstract Builder setSdkBundlePath(Path sdkBundlePath);

    /** Sets the SDK version code */
    public abstract Builder setVersionCode(Integer versionCode);

    /**
     * Sets path to the output produced by the command. Depends on the output format:
     *
     * <ul>
     *   <li>'APK_SET', path to where the APK Set must be generated. Must have the extension
     *       ".apks".
     *   <li>'DIRECTORY', path to the directory where generated APKs will be stored.
     * </ul>
     */
    public abstract Builder setOutputFile(Path outputFile);

    /**
     * Sets whether to overwrite the contents of the output file.
     *
     * <p>The default is {@code false}. If set to {@code false} and the output file is present,
     * exception is thrown.
     */
    public abstract Builder setOverwriteOutput(boolean overwriteOutput);

    /** Sets the output format. */
    public abstract Builder setOutputFormat(OutputFormat outputFormat);

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

    /**
     * Sets whether to display verbose information about what is happening during the command
     * execution.
     */
    public abstract Builder setVerbose(boolean enableVerbose);

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
     * Provides the lowest variant number to use.
     *
     * <p>By default, variants are numbered from 0 to {@code variantNum - 1}. By setting a value
     * here, the variants will be numbered from {@code firstVariantNumber} and up.
     */
    public abstract Builder setFirstVariantNumber(int firstVariantNumber);

    abstract BuildSdkApksCommand autoBuild();

    /**
     * Builds a {@link BuildSdkApksCommand}.
     *
     * <p>Sets a default {@link ListeningExecutorService} if not already set.
     */
    public BuildSdkApksCommand build() {
      if (!getExecutorServiceInternal().isPresent()) {
        setExecutorServiceInternal(createInternalExecutorService(DEFAULT_THREAD_POOL_SIZE));
        setExecutorServiceCreatedByBundleTool(true);
      }
      return autoBuild();
    }
  }

  public static BuildSdkApksCommand fromFlags(ParsedFlags flags) {
    return fromFlags(flags, System.out, DEFAULT_PROVIDER);
  }

  static BuildSdkApksCommand fromFlags(
      ParsedFlags flags, PrintStream out, SystemEnvironmentProvider provider) {
    Builder sdkApksCommandBuilder =
        BuildSdkApksCommand.builder()
            .setSdkBundlePath(SDK_BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags));

    // Optional arguments.
    OUTPUT_FORMAT_FLAG.getValue(flags).ifPresent(sdkApksCommandBuilder::setOutputFormat);
    OVERWRITE_OUTPUT_FLAG.getValue(flags).ifPresent(sdkApksCommandBuilder::setOverwriteOutput);
    VERSION_CODE_FLAG.getValue(flags).ifPresent(sdkApksCommandBuilder::setVersionCode);
    AAPT2_PATH_FLAG
        .getValue(flags)
        .ifPresent(
            aapt2Path ->
                sdkApksCommandBuilder.setAapt2Command(
                    Aapt2Command.createFromExecutablePath(aapt2Path)));
    MAX_THREADS_FLAG
        .getValue(flags)
        .ifPresent(
            maxThreads ->
                sdkApksCommandBuilder
                    .setExecutorService(createInternalExecutorService(maxThreads))
                    .setExecutorServiceCreatedByBundleTool(true));
    VERBOSE_FLAG.getValue(flags).ifPresent(sdkApksCommandBuilder::setVerbose);

    populateSigningConfigurationFromFlags(sdkApksCommandBuilder, flags, out, provider);

    flags.checkNoUnknownFlags();

    return sdkApksCommandBuilder.build();
  }

  public Path execute() {
    validateInput();

    try (ZipFile bundleZip = new ZipFile(getSdkBundlePath().toFile());
        TempDirectory tempDir = new TempDirectory(getClass().getSimpleName())) {

      SdkBundleValidator bundleValidator = SdkBundleValidator.create();
      bundleValidator.validateFile(bundleZip);

      Path modulesPath = tempDir.getPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
      try (ZipFile modulesZip = getModulesZip(bundleZip, modulesPath)) {
        bundleValidator.validateModulesFile(modulesZip);
        SdkBundle sdkBundle = SdkBundle.buildFromZip(bundleZip, modulesZip, getVersionCode());
        bundleValidator.validate(sdkBundle);

        DaggerBuildSdkApksManagerComponent.builder()
            .setBuildSdkApksCommand(this)
            .setTempDirectory(tempDir)
            .setSdkBundle(sdkBundle)
            .build()
            .create()
            .execute();
      }
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The SDK Bundle is not a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when validating the Sdk Bundle.", e);
    } finally {
      if (isExecutorServiceCreatedByBundleTool()) {
        getExecutorService().shutdown();
      }
    }

    return getOutputFile();
  }

  private void validateInput() {
    FilePreconditions.checkFileExistsAndReadable(getSdkBundlePath());
    FilePreconditions.checkFileHasExtension("ASB file", getSdkBundlePath(), ".asb");

    switch (getOutputFormat()) {
      case APK_SET:
        if (!getOverwriteOutput()) {
          checkFileDoesNotExist(getOutputFile());
        }
        break;
      case DIRECTORY:
        if (getOverwriteOutput()) {
          throw InvalidCommandException.builder()
              .withInternalMessage(
                  "'%s' flag is not supported for '%s' output format.",
                  OVERWRITE_OUTPUT_FLAG.getName(), BuildSdkApksCommand.OutputFormat.DIRECTORY)
              .build();
        }
        break;
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

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription("Generates APKs from an Android SDK Bundle.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/SDKbundle.asb")
                .setDescription("Path to SDK bundle. Must have the extension '.asb'.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(VERSION_CODE_FLAG.getName())
                .setExampleValue("1")
                .setOptional(true)
                .setDescription("SDK version code")
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
                .setExampleValue("apk_set|directory")
                .setOptional(true)
                .setDescription(
                    "Specifies output format for generated APKs. If set to '%s'"
                        + " outputs APKs into the created APK Set archive (default). If set to '%s'"
                        + " outputs APKs into the specified directory.",
                    APK_SET, DIRECTORY)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OVERWRITE_OUTPUT_FLAG.getName())
                .setOptional(true)
                .setDescription("If set, any previous existing output will be overwritten.")
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
                .setFlagName(VERBOSE_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "If set, prints extra information about the command execution in the standard"
                        + " output.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_FLAG.getName())
                .setExampleValue("path/to/keystore")
                .setDescription(
                    "Path to the keystore that should be used to sign the generated APKs.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEY_ALIAS_FLAG.getName())
                .setExampleValue("key-alias")
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

  private static void populateSigningConfigurationFromFlags(
      Builder buildSdkApksCommand,
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
      buildSdkApksCommand.setSigningConfiguration(builder.build());
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
        buildSdkApksCommand.setSigningConfiguration(debugConfig.get());
      } else {
        out.println(
            "WARNING: The APKs won't be signed and thus not installable unless you also pass a "
                + "keystore via the flag --ks. See the command help for more information.");
      }
    }
  }
}
