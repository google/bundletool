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

import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.DEFAULT;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM_COMPRESSED;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.splitters.DexCompressionSplitter;
import com.android.tools.build.bundletool.splitters.NativeLibrariesCompressionSplitter;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Command to generate APKs from an Android App Bundle. */
@AutoValue
public abstract class BuildApksCommand {

  private static final int DEFAULT_THREAD_POOL_SIZE = 4;

  public static final String COMMAND_NAME = "build-apks";

  /** Modes to run {@link BuildApksCommand} against to generate APKs. */
  public enum ApkBuildMode {
    /** DEFAULT mode generates split, standalone and instant APKs. */
    DEFAULT,
    /** UNIVERSAL mode generates universal APK. */
    UNIVERSAL,
    /** SYSTEM mode generates APKs for the system image. */
    SYSTEM,
    /**
     * SYSTEM_COMPRESSED mode generates compressed APK and an additional uncompressed stub APK
     * (containing only android manifest) for the system image.
     */
    SYSTEM_COMPRESSED;

    public String getLowerCaseName() {
      return Ascii.toLowerCase(name());
    }

    public boolean isAnySystemMode() {
      return equals(SYSTEM) || equals(SYSTEM_COMPRESSED);
    }
  }

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");
  private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");
  private static final Flag<Boolean> OVERWRITE_OUTPUT_FLAG = Flag.booleanFlag("overwrite");
  private static final Flag<ImmutableSet<OptimizationDimension>> OPTIMIZE_FOR_FLAG =
      Flag.enumSet("optimize-for", OptimizationDimension.class);
  private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");
  private static final Flag<Integer> MAX_THREADS_FLAG = Flag.positiveInteger("max-threads");
  private static final Flag<ApkBuildMode> MODE_FLAG = Flag.enumFlag("mode", ApkBuildMode.class);

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");
  private static final Flag<Boolean> CONNECTED_DEVICE_FLAG = Flag.booleanFlag("connected-device");
  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");
  private static final String ANDROID_HOME_VARIABLE = "ANDROID_HOME";
  private static final String ANDROID_SERIAL_VARIABLE = "ANDROID_SERIAL";

  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");

  // Signing-related flags: should match flags from apksig library.
  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");
  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");
  private static final Flag<Password> KEYSTORE_PASSWORD = Flag.password("ks-pass");
  private static final Flag<Password> KEY_PASSWORD = Flag.password("key-pass");

  private static final String APK_SET_ARCHIVE_EXTENSION = "apks";

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Path getBundlePath();

  public abstract Path getOutputFile();

  public abstract boolean getOverwriteOutput();

  public abstract ImmutableSet<OptimizationDimension> getOptimizationDimensions();

  public abstract Optional<DeviceSpec> getDeviceSpec();

  public abstract boolean getGenerateOnlyForConnectedDevice();

  public abstract Optional<String> getDeviceId();

  /** Required when getGenerateOnlyForConnectedDevice is true. */
  abstract Optional<AdbServer> getAdbServer();

  /** Required when getGenerateOnlyForConnectedDevice is true. */
  public abstract Optional<Path> getAdbPath();

  public abstract ApkBuildMode getApkBuildMode();

  public abstract Optional<Aapt2Command> getAapt2Command();

  public abstract Optional<SigningConfiguration> getSigningConfiguration();

  ListeningExecutorService getExecutorService() {
    return getExecutorServiceInternal();
  }

  abstract ListeningExecutorService getExecutorServiceInternal();

  abstract boolean isExecutorServiceCreatedByBundleTool();

  public abstract boolean getCreateApkSetArchive();


  public abstract Optional<ApkListener> getApkListener();

  public abstract Optional<ApkModifier> getApkModifier();

  public abstract Optional<Integer> getFirstVariantNumber();

  public abstract Optional<PrintStream> getOutputPrintStream();

  public static Builder builder() {
    return new AutoValue_BuildApksCommand.Builder()
        .setOverwriteOutput(false)
        .setApkBuildMode(DEFAULT)
        .setGenerateOnlyForConnectedDevice(false)
        .setCreateApkSetArchive(true)
        .setOptimizationDimensions(ImmutableSet.of());
  }

  /** Builder for the {@link BuildApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the bundle. Must have the extension ".aab". */
    public abstract Builder setBundlePath(Path bundlePath);

    /** Sets the path to where the APK Set must be generated. Must have the extension ".apks". */
    public abstract Builder setOutputFile(Path outputFile);

    /**
     * Sets whether to overwrite the contents of the output file.
     *
     * <p>The default is {@code false}. If set to {@code false} and the output file is present,
     * exception is thrown.
     */
    public abstract Builder setOverwriteOutput(boolean overwriteOutput);

    /** List of config dimensions to split the APKs by. */
    @Deprecated // Use setBundleConfig() instead.
    public abstract Builder setOptimizationDimensions(
        ImmutableSet<OptimizationDimension> optimizationDimensions);

    /**
     * Sets against which mode APK should be generated.
     *
     * <p>By default we generate split, standalone ans instant APKs.
     */
    public abstract Builder setApkBuildMode(ApkBuildMode mode);

    /**
     * Sets if the generated APK Set will contain APKs compatible only with the connected device.
     */
    public abstract Builder setGenerateOnlyForConnectedDevice(boolean onlyForConnectedDevice);

    /** Sets the {@link DeviceSpec} for which the only the matching APKs will be generated. */
    public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

    /**
     * Sets the device serial number. Required if more than one device including emulators is
     * connected.
     */
    public abstract Builder setDeviceId(String deviceId);

    /** Path to the ADB binary. Required if ANDROID_HOME environment variable is not set. */
    public abstract Builder setAdbPath(Path adbPath);

    /** The caller is responsible for the lifecycle of the {@link AdbServer}. */
    public abstract Builder setAdbServer(AdbServer adbServer);

    /** Provides a wrapper around the execution of the aapt2 command. */
    public abstract Builder setAapt2Command(Aapt2Command aapt2Command);

    /**
     * Sets the signing configuration for the generated APKs.
     *
     * <p>Optional. If not set, the generated APKs will not be signed.
     */
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
     * If false will extract the APK set to the output directory without creating the final archive.
     * Important: if this mode is used, the caller should still provide a "apks file" as the output
     * file parameter. This is because there is a lots of validation here that assumes that. The
     * command will return the directory where the APK files were copied to.
     */
    public abstract Builder setCreateApkSetArchive(boolean value);


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
     * here, the variants will be numbered from {@code lowestVariantNumber} and up.
     */
    public abstract Builder setFirstVariantNumber(int firstVariantNumber);

    /** For command line, sets the {@link PrintStream} to use for outputting the warnings. */
    public abstract Builder setOutputPrintStream(PrintStream outputPrintStream);

    abstract BuildApksCommand autoBuild();

    public BuildApksCommand build() {
      if (!getExecutorServiceInternal().isPresent()) {
        setExecutorServiceInternal(createInternalExecutorService(DEFAULT_THREAD_POOL_SIZE));
        setExecutorServiceCreatedByBundleTool(true);
      }

      BuildApksCommand command = autoBuild();
      if (!command.getOptimizationDimensions().isEmpty()
          && !command.getApkBuildMode().equals(DEFAULT)) {
        throw new ValidationException(
            String.format(
                "Optimization dimension can be only set when running with '%s' mode flag.",
                DEFAULT.getLowerCaseName()));
      }

      if (command.getGenerateOnlyForConnectedDevice()
          && !command.getApkBuildMode().equals(DEFAULT)) {
        throw new ValidationException(
            String.format(
                "Optimizing for connected device only possible when running with '%s' mode flag.",
                DEFAULT.getLowerCaseName()));
      }

      if (command.getDeviceSpec().isPresent()) {
        switch (command.getApkBuildMode()) {
          case UNIVERSAL:
            throw new ValidationException(
                String.format(
                    "Optimizing for device spec not possible when running with '%s' mode flag.",
                    UNIVERSAL.getLowerCaseName()));
          case SYSTEM:
          case SYSTEM_COMPRESSED:
            DeviceSpec deviceSpec = command.getDeviceSpec().get();
            if (deviceSpec.getScreenDensity() == 0 || deviceSpec.getSupportedAbisList().isEmpty()) {
              throw new ValidationException(
                  String.format(
                      "Device spec must have screen density and ABIs set when running with "
                          + "'%s' or '%s' mode flag. ",
                      SYSTEM.getLowerCaseName(), SYSTEM_COMPRESSED.getLowerCaseName()));
            }
            break;
          case DEFAULT:
        }
      } else {
        if (command.getApkBuildMode().isAnySystemMode()) {
          throw ValidationException.builder()
              .withMessage(
                  "Device spec must always be set when running with '%s' or '%s' mode flag.",
                  SYSTEM.getLowerCaseName(), SYSTEM_COMPRESSED.getLowerCaseName())
              .build();
        }
      }

      if (command.getGenerateOnlyForConnectedDevice() && command.getDeviceSpec().isPresent()) {
        throw new ValidationException(
            "Cannot optimize for the device spec and connected device at the same time.");
      }

      if (command.getDeviceId().isPresent() && !command.getGenerateOnlyForConnectedDevice()) {
        throw new ValidationException(
            "Setting --device-id requires using the --connected-device flag.");
      }

      if (command.getCreateApkSetArchive()) {
        if (!APK_SET_ARCHIVE_EXTENSION.equals(
            MoreFiles.getFileExtension(command.getOutputFile()))) {
          throw ValidationException.builder()
              .withMessage(
                  "Flag --output should be the path where to generate the APK Set. "
                      + "Its extension must be '.apks'.")
              .build();
        }
      }

      return command;
    }
  }

  public static BuildApksCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, System.out, DEFAULT_PROVIDER, adbServer);
  }

  static BuildApksCommand fromFlags(
      ParsedFlags flags,
      PrintStream out,
      SystemEnvironmentProvider systemEnvironmentProvider,
      AdbServer adbServer) {
    BuildApksCommand.Builder buildApksCommand =
        BuildApksCommand.builder()
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags))
            .setOutputPrintStream(out);

    // Optional arguments.
    OVERWRITE_OUTPUT_FLAG.getValue(flags).ifPresent(buildApksCommand::setOverwriteOutput);
    AAPT2_PATH_FLAG
        .getValue(flags)
        .ifPresent(
            aapt2Path ->
                buildApksCommand.setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path)));

    MODE_FLAG.getValue(flags).ifPresent(buildApksCommand::setApkBuildMode);
    MAX_THREADS_FLAG
        .getValue(flags)
        .ifPresent(
            maxThreads ->
                buildApksCommand
                    .setExecutorService(createInternalExecutorService(maxThreads))
                    .setExecutorServiceCreatedByBundleTool(true));
    OPTIMIZE_FOR_FLAG.getValue(flags).ifPresent(buildApksCommand::setOptimizationDimensions);

    // Signing-related arguments.
    Optional<Path> keystorePath = KEYSTORE_FLAG.getValue(flags);
    Optional<String> keyAlias = KEY_ALIAS_FLAG.getValue(flags);
    Optional<Password> keystorePassword = KEYSTORE_PASSWORD.getValue(flags);
    Optional<Password> keyPassword = KEY_PASSWORD.getValue(flags);

    if (keystorePath.isPresent() && keyAlias.isPresent()) {
      buildApksCommand.setSigningConfiguration(
          SigningConfiguration.extractFromKeystore(
              keystorePath.get(), keyAlias.get(), keystorePassword, keyPassword));
    } else if (keystorePath.isPresent() && !keyAlias.isPresent()) {
      throw new CommandExecutionException("Flag --ks-key-alias is required when --ks is set.");
    } else if (!keystorePath.isPresent() && keyAlias.isPresent()) {
      throw new CommandExecutionException("Flag --ks is required when --ks-key-alias is set.");
    } else {
      // Try to use debug keystore if present.
      Optional<SigningConfiguration> debugConfig =
          DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider);
      if (debugConfig.isPresent()) {
        out.printf(
            "INFO: The APKs will be signed with the debug keystore found at '%s'.%n",
            DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.getUnchecked(systemEnvironmentProvider).get());
        buildApksCommand.setSigningConfiguration(debugConfig.get());
      } else {
        out.println(
            "WARNING: The APKs won't be signed and thus not installable unless you also pass a "
                + "keystore via the flag --ks. See the command help for more information.");
      }
    }

    boolean connectedDeviceMode = CONNECTED_DEVICE_FLAG.getValue(flags).orElse(false);
    CONNECTED_DEVICE_FLAG
        .getValue(flags)
        .ifPresent(buildApksCommand::setGenerateOnlyForConnectedDevice);

    Optional<String> deviceSerialName = DEVICE_ID_FLAG.getValue(flags);
    if (connectedDeviceMode && !deviceSerialName.isPresent()) {
      deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
    }
    deviceSerialName.ifPresent(buildApksCommand::setDeviceId);

    // Applied only when --connected-device flag is set, because we don't want to fail command
    // if ADB cannot be found in a normal mode.
    Optional<Path> adbPathFromFlag = ADB_PATH_FLAG.getValue(flags);
    if (connectedDeviceMode) {
      Path adbPath =
          adbPathFromFlag.orElseGet(
              () ->
                  systemEnvironmentProvider
                      .getVariable(ANDROID_HOME_VARIABLE)
                      .flatMap(path -> new SdkToolsLocator().locateAdb(Paths.get(path)))
                      .orElseThrow(
                          () ->
                              new CommandExecutionException(
                                  "Unable to determine the location of ADB. Please set the "
                                      + "--adb flag or define ANDROID_HOME environment "
                                      + "variable.")));
      buildApksCommand.setAdbPath(adbPath).setAdbServer(adbServer);
    }

    ApkBuildMode apkBuildMode = MODE_FLAG.getValue(flags).orElse(DEFAULT);
    boolean supportsPartialDeviceSpecs = apkBuildMode.isAnySystemMode();

    Function<Path, DeviceSpec> deviceSpecParser =
        supportsPartialDeviceSpecs
            ? DeviceSpecParser::parsePartialDeviceSpec
            : DeviceSpecParser::parseDeviceSpec;

    DEVICE_SPEC_FLAG
        .getValue(flags)
        .map(deviceSpecParser)
        .ifPresent(buildApksCommand::setDeviceSpec);

    flags.checkNoUnknownFlags();

    return buildApksCommand.build();
  }

  public Path execute() {
    try (TempDirectory tempDirectory = new TempDirectory()) {
      return new BuildApksManager(this).execute(tempDirectory.getPath());
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
                .setShortDescription(
                    "Generates an APK Set archive containing either all possible split APKs and "
                        + "standalone APKs or APKs optimized for the connected device "
                        + "(see %s flag).",
                    CONNECTED_DEVICE_FLAG)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("bundle.aab")
                .setDescription("Path to the Android App Bundle to generate APKs from.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FILE_FLAG.getName())
                .setExampleValue("output.apks")
                .setDescription("Path to where the APK Set archive should be created.")
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
                .setFlagName(MODE_FLAG.getName())
                .setExampleValue(joinFlagOptions(ApkBuildMode.values()))
                .setOptional(true)
                .setDescription(
                    "Specifies which mode to run '%s' command against. Acceptable values are '%s'. "
                        + "If not set or set to '%s' we generate split, standalone and instant "
                        + "APKs. If set to '%s' we generate universal APK. If set to '%s' we "
                        + "generate APKs for system image. If set to '%s' we generate compressed "
                        + "APK and an additional uncompressed stub APK (containing only Android "
                        + "manifest) for the system image.",
                    BuildApksCommand.COMMAND_NAME,
                    joinFlagOptions(ApkBuildMode.values()),
                    DEFAULT.getLowerCaseName(),
                    UNIVERSAL.getLowerCaseName(),
                    SYSTEM.getLowerCaseName(),
                    SYSTEM_COMPRESSED.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MAX_THREADS_FLAG.getName())
                .setExampleValue("num-threads")
                .setOptional(true)
                .setDescription(
                    "Sets the maximum number of threads to use (default: %d).",
                    DEFAULT_THREAD_POOL_SIZE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OPTIMIZE_FOR_FLAG.getName())
                .setExampleValue(joinFlagOptions(OptimizationDimension.values()))
                .setOptional(true)
                .setDescription(
                    "If set, will generate APKs with optimizations for the given dimensions. "
                        + "Acceptable values are '%s'. This flag should be only be set with "
                        + "--%s=%s flag.",
                    joinFlagOptions(OptimizationDimension.values()),
                    MODE_FLAG.getName(),
                    DEFAULT.getLowerCaseName())
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
                .setFlagName(KEYSTORE_PASSWORD.getName())
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
                .setFlagName(KEY_PASSWORD.getName())
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
        .addFlag(
            FlagDescription.builder()
                .setFlagName(CONNECTED_DEVICE_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "If set, will generate APK Set optimized for the connected device. The "
                        + "generated APK Set will only be installable on that specific class of "
                        + "devices. This flag should be only be set with --%s=%s flag.",
                    MODE_FLAG.getName(), DEFAULT.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. If absent, an attempt will be made to locate it if "
                        + "the %s environment variable is set. Used only if %s flag is set.",
                    ANDROID_HOME_VARIABLE, CONNECTED_DEVICE_FLAG)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. If absent, this uses the %s environment variable. Either "
                        + "this flag or the environment variable is required when more than one "
                        + "device or emulator is connected. Used only if %s flag is set.",
                    ANDROID_SERIAL_VARIABLE, CONNECTED_DEVICE_FLAG)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_SPEC_FLAG.getName())
                .setExampleValue("device-spec.json")
                .setOptional(true)
                .setDescription(
                    "Path to the device spec file generated by the '%s' command. If present, "
                        + "it will generate an APK Set optimized for the specified device spec. "
                        + "This flag should be only be set with --%s=%s flag.",
                    GetDeviceSpecCommand.COMMAND_NAME,
                    MODE_FLAG.getName(),
                    DEFAULT.getLowerCaseName())
                .build())
        .build();
  }

  private static String joinFlagOptions(Enum<?>... flagOptions) {
    return Arrays.stream(flagOptions)
        .map(Enum::name)
        .map(String::toLowerCase)
        .collect(Collectors.joining("|"));
  }
}
