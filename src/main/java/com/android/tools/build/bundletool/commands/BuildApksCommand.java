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

import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.ARCHIVE;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.DEFAULT;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.APK_SET;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.OutputFormat.DIRECTORY;
import static com.android.tools.build.bundletool.commands.CommandUtils.ANDROID_SERIAL_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.BundleParser.getModulesZip;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.RuntimeEnabledSdkConfigProto.LocalDeploymentRuntimeEnabledSdkConfig;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.androidtools.P7ZipCommand;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.KeystoreProperties;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.SdkAsar;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.SignerConfig;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.preprocessors.AppBundlePreprocessorManager;
import com.android.tools.build.bundletool.preprocessors.DaggerAppBundlePreprocessorComponent;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.android.tools.build.bundletool.validation.SdkAsarValidator;
import com.android.tools.build.bundletool.validation.SdkBundleValidator;
import com.android.tools.build.bundletool.validation.SubValidator;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Closer;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.JsonFormat;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Command to generate APKs from an Android App Bundle.
 */
@AutoValue
public abstract class BuildApksCommand {

    private static final int DEFAULT_THREAD_POOL_SIZE = 4;

    public static final String COMMAND_NAME = "build-apks";

    private static final Logger logger = Logger.getLogger(BuildApksCommand.class.getName());

    /**
     * Modes to run {@link BuildApksCommand} against to generate APKs.
     */
    public enum ApkBuildMode {

        /**
         * DEFAULT mode generates split, standalone and instant APKs.
         */
        DEFAULT,
        /**
         * UNIVERSAL mode generates universal APK.
         */
        UNIVERSAL,
        /**
         * SYSTEM mode generates APKs for the system image.
         */
        SYSTEM,
        /**
         * PERSISTENT mode only generates non-instant APKs (i.e. splits and standalone APKs).
         */
        PERSISTENT,
        /**
         * INSTANT mode only generates instant APKs, assuming at least one module is instant-enabled.
         */
        INSTANT,
        /**
         * ARCHIVE mode only generates the archived APK of an app.
         */
        ARCHIVE;

        public final String getLowerCaseName() {
            return Ascii.toLowerCase(name());
        }
    }

    /**
     * Options to customize generated system APKs.
     */
    public enum SystemApkOption {

        UNCOMPRESSED_NATIVE_LIBRARIES, UNCOMPRESSED_DEX_FILES
    }

    /**
     * Output format for generated APKs.
     */
    public enum OutputFormat {

        /**
         * Generated APKs are stored inside created APK Set archive.
         */
        APK_SET,
        /**
         * Generated APKs are stored inside specified directory.
         */
        DIRECTORY
    }

    private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");

    private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");

    private static final Flag<OutputFormat> OUTPUT_FORMAT_FLAG = Flag.enumFlag("output-format", OutputFormat.class);

    private static final Flag<Boolean> OVERWRITE_OUTPUT_FLAG = Flag.booleanFlag("overwrite");

    private static final Flag<ImmutableSet<OptimizationDimension>> OPTIMIZE_FOR_FLAG = Flag.enumSet("optimize-for", OptimizationDimension.class);

    private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");

    private static final Flag<Integer> MAX_THREADS_FLAG = Flag.positiveInteger("max-threads");

    private static final Flag<ApkBuildMode> BUILD_MODE_FLAG = Flag.enumFlag("mode", ApkBuildMode.class);

    private static final Flag<Boolean> LOCAL_TESTING_MODE_FLAG = Flag.booleanFlag("local-testing");

    private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");

    private static final Flag<Boolean> CONNECTED_DEVICE_FLAG = Flag.booleanFlag("connected-device");

    private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");

    private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");

    private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");

    private static final Flag<Boolean> FUSE_ONLY_DEVICE_MATCHING_MODULES_FLAG = Flag.booleanFlag("fuse-only-device-matching-modules");

    private static final Flag<ImmutableSet<SystemApkOption>> SYSTEM_APK_OPTIONS = Flag.enumSet("system-apk-options", SystemApkOption.class);

    private static final Flag<Integer> DEVICE_TIER_FLAG = Flag.nonNegativeInteger("device-tier");

    private static final Flag<String> COUNTRY_SET_FLAG = Flag.string("country-set");

    private static final Flag<Boolean> VERBOSE_FLAG = Flag.booleanFlag("verbose");

    private static final Flag<Path> P7ZIP_PATH_FLAG = Flag.path("7zip");

    // Signing-related flags: should match flags from apksig library.
    private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");

    private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");

    private static final Flag<Password> KEYSTORE_PASSWORD_FLAG = Flag.password("ks-pass");

    private static final Flag<Password> KEY_PASSWORD_FLAG = Flag.password("key-pass");

    // SourceStamp-related flags.
    private static final Flag<Boolean> CREATE_STAMP_FLAG = Flag.booleanFlag("create-stamp");

    private static final Flag<Path> STAMP_KEYSTORE_FLAG = Flag.path("stamp-ks");

    private static final Flag<Password> STAMP_KEYSTORE_PASSWORD_FLAG = Flag.password("stamp-ks-pass");

    private static final Flag<String> STAMP_KEY_ALIAS_FLAG = Flag.string("stamp-key-alias");

    private static final Flag<Password> STAMP_KEY_PASSWORD_FLAG = Flag.password("stamp-key-pass");

    private static final Flag<String> STAMP_SOURCE_FLAG = Flag.string("stamp-source");

    private static final Flag<Boolean> STAMP_EXCLUDE_TIMESTAMP = Flag.booleanFlag("stamp-exclude-timestamp");

    // Key-rotation-related flags.
    private static final Flag<Integer> MINIMUM_V3_ROTATION_API_VERSION_FLAG = Flag.positiveInteger("min-v3-rotation-api-version");

    private static final Flag<Integer> ROTATION_MINIMUM_SDK_VERSION_FLAG = Flag.positiveInteger("rotation-min-sdk-version");

    private static final Flag<Path> LINEAGE_FLAG = Flag.path("lineage");

    private static final Flag<Path> OLDEST_SIGNER_FLAG = Flag.path("oldest-signer");

    // Runtime-enabled-SDK-related flags.
    private static final Flag<ImmutableSet<Path>> RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG = Flag.pathSet("sdk-bundles");

    private static final Flag<ImmutableSet<Path>> RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG = Flag.pathSet("sdk-archives");

    private static final Flag<Path> LOCAL_DEPLOYMENT_RUNTIME_ENABLED_SDK_CONFIG_FLAG = Flag.path("local-runtime-enabled-sdk-config");

    // Archive APK related flags.
    private static final Flag<String> APP_STORE_PACKAGE_NAME_FLAG = Flag.string("store-package");

    private static final String APK_SET_ARCHIVE_EXTENSION = "apks";

    private static final SystemEnvironmentProvider DEFAULT_PROVIDER = new DefaultSystemEnvironmentProvider();

    // Number embedded at the beginning of a zip file to indicate its file format.
    private static final int ZIP_MAGIC = 0x04034b50;

    public abstract Path getBundlePath();

    public abstract Path getOutputFile();

    public abstract boolean getOverwriteOutput();

    public abstract ImmutableSet<OptimizationDimension> getOptimizationDimensions();

    public abstract ImmutableSet<String> getModules();

    public abstract Optional<DeviceSpec> getDeviceSpec();

    public abstract boolean getFuseOnlyDeviceMatchingModules();

    public abstract Optional<Integer> getDeviceTier();

    public abstract Optional<String> getCountrySet();

    public abstract ImmutableSet<SystemApkOption> getSystemApkOptions();

    public abstract boolean getGenerateOnlyForConnectedDevice();

    public abstract Optional<String> getDeviceId();

    /**
     * Required when getGenerateOnlyForConnectedDevice is true.
     */
    abstract Optional<AdbServer> getAdbServer();

    /**
     * Required when getGenerateOnlyForConnectedDevice is true.
     */
    public abstract Optional<Path> getAdbPath();

    public abstract ApkBuildMode getApkBuildMode();

    public abstract boolean getLocalTestingMode();

    public abstract boolean getVerbose();

    public abstract Optional<Aapt2Command> getAapt2Command();

    public abstract Optional<SigningConfiguration> getSigningConfiguration();

    public abstract Optional<SigningConfigurationProvider> getSigningConfigurationProvider();

    public abstract Optional<Integer> getMinSdkForAdditionalVariantWithV3Rotation();

    ListeningExecutorService getExecutorService() {
        return getExecutorServiceInternal();
    }

    abstract ListeningExecutorService getExecutorServiceInternal();

    abstract boolean isExecutorServiceCreatedByBundleTool();

    public abstract OutputFormat getOutputFormat();

    public abstract Optional<ApkListener> getApkListener();

    public abstract Optional<ApkModifier> getApkModifier();

    public abstract ImmutableList<SubValidator> getExtraValidators();

    public abstract Optional<Integer> getFirstVariantNumber();

    public abstract Optional<PrintStream> getOutputPrintStream();

    public abstract Optional<SourceStamp> getSourceStamp();

    public abstract Optional<Long> getAssetModulesVersionOverride();

    public abstract boolean getEnableApkSerializerWithoutBundleRecompression();

    public abstract Optional<P7ZipCommand> getP7ZipCommand();

    public abstract ImmutableSet<Path> getRuntimeEnabledSdkBundlePaths();

    public abstract ImmutableSet<Path> getRuntimeEnabledSdkArchivePaths();

    public abstract Optional<LocalDeploymentRuntimeEnabledSdkConfig> getLocalDeploymentRuntimeEnabledSdkConfig();

    public abstract Optional<String> getAppStorePackageName();

    public abstract boolean getEnableBaseModuleMinSdkAsDefaultTargeting();

    public static Builder builder() {
        return new AutoValue_BuildApksCommand.Builder().setOverwriteOutput(false).setApkBuildMode(DEFAULT).setLocalTestingMode(false).setGenerateOnlyForConnectedDevice(false).setOutputFormat(APK_SET).setVerbose(false).setOptimizationDimensions(ImmutableSet.of()).setModules(ImmutableSet.of()).setFuseOnlyDeviceMatchingModules(false).setExtraValidators(ImmutableList.of()).setSystemApkOptions(ImmutableSet.of()).setEnableApkSerializerWithoutBundleRecompression(true).setRuntimeEnabledSdkBundlePaths(ImmutableSet.of()).setRuntimeEnabledSdkArchivePaths(ImmutableSet.of()).setEnableBaseModuleMinSdkAsDefaultTargeting(false);
    }

    /**
     * Builder for the {@link BuildApksCommand}.
     */
    @AutoValue.Builder
    public abstract static class Builder {

        /**
         * Sets the path to the bundle. Must have the extension ".aab".
         */
        public abstract Builder setBundlePath(Path bundlePath);

        /**
         * Sets the output format.
         */
        public abstract Builder setOutputFormat(OutputFormat outputFormat);

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

        /**
         * List of config dimensions to split the APKs by.
         */
        // Use setBundleConfig() instead.
        @Deprecated
        public abstract Builder setOptimizationDimensions(ImmutableSet<OptimizationDimension> optimizationDimensions);

        /**
         * Sets against which mode APK should be generated.
         *
         * <p>By default we generate split, standalone ans instant APKs.
         */
        public abstract Builder setApkBuildMode(ApkBuildMode mode);

        /**
         * Sets whether the APKs should be built in local testing mode.
         *
         * <p>The default is {@code false}.
         */
        public abstract Builder setLocalTestingMode(boolean enableLocalTesting);

        /**
         * Sets if the generated APK Set will contain APKs compatible only with the connected device.
         */
        public abstract Builder setGenerateOnlyForConnectedDevice(boolean onlyForConnectedDevice);

        public abstract Builder setModules(ImmutableSet<String> modules);

        /**
         * Sets the {@link DeviceSpec} for which the only the matching APKs will be generated.
         */
        public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

        /**
         * Sets the {@link DeviceSpec} for which the only the matching APKs will be generated.
         */
        public Builder setDeviceSpec(Path deviceSpecFile) {
            // Parse as partial and fully validate later.
            return setDeviceSpec(DeviceSpecParser.parsePartialDeviceSpec(deviceSpecFile));
        }

        public abstract Builder setFuseOnlyDeviceMatchingModules(boolean enabled);

        /**
         * Sets the device tier to use for APK matching. This will override the device tier of the given
         * device spec.
         */
        public abstract Builder setDeviceTier(Integer deviceTier);

        /**
         * Sets the country set to use for APK matching. This will override the country set of the given
         * device spec.
         */
        public abstract Builder setCountrySet(String countrySet);

        /**
         * Sets options to generated APKs in system mode.
         */
        public abstract Builder setSystemApkOptions(ImmutableSet<SystemApkOption> options);

        /**
         * Sets whether to display verbose information about what is happening during the command
         * execution.
         */
        public abstract Builder setVerbose(boolean enableVerbose);

        /**
         * Sets the device serial number. Required if more than one device including emulators is
         * connected.
         */
        public abstract Builder setDeviceId(String deviceId);

        /**
         * Path to the ADB binary. Required if ANDROID_HOME environment variable is not set.
         */
        public abstract Builder setAdbPath(Path adbPath);

        /**
         * The caller is responsible for the lifecycle of the {@link AdbServer}.
         */
        public abstract Builder setAdbServer(AdbServer adbServer);

        /**
         * Provides a wrapper around the execution of the aapt2 command.
         */
        public abstract Builder setAapt2Command(Aapt2Command aapt2Command);

        /**
         * Sets the signing configuration to be used for all generated APKs.
         *
         * <p>Optional. Only one of {@link SigningConfiguration} or {@link SigningConfigurationProvider}
         * should be set. If neither is set, then the generated APKs will not be signed.
         */
        public abstract Builder setSigningConfiguration(SigningConfiguration signingConfiguration);

        abstract Optional<SigningConfiguration> getSigningConfiguration();

        /**
         * Sets the provider to provide signing configurations for the generated APKs.
         *
         * <p>Optional. Only one of {@link SigningConfiguration} or {@link SigningConfigurationProvider}
         * should be set. If neither is set, then the generated APKs will not be signed.
         */
        public abstract Builder setSigningConfigurationProvider(SigningConfigurationProvider signingConfigurationProvider);

        abstract Optional<SigningConfigurationProvider> getSigningConfigurationProvider();

        /**
         * Minimum SDK version for which signing with v3 key rotation is intended to be performed.
         *
         * <p>Optional. Setting a value for this field will force an additional variant to be generated
         * which targets the specified SDK version. It is still the responsibility of the {@link
         * SigningConfigurationProvider} to provide the appropriate signing configuration for each of
         * the generated APKs based on their SDK version targeting.
         */
        public abstract Builder setMinSdkForAdditionalVariantWithV3Rotation(int minSdkForAdditionalVariantWithV3Rotation);

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

        /**
         * If false will extract the APK set to the output directory without creating the final archive.
         *
         * @deprecated use {@link Builder#setOutputFormat} instead.
         */
        @Deprecated
        public Builder setCreateApkSetArchive(boolean createApkSetArchive) {
            return setOutputFormat(createApkSetArchive ? APK_SET : DIRECTORY);
        }

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
         * Provides additional {@link SubValidator}s that will be invoked during validation.
         */
        public abstract Builder setExtraValidators(ImmutableList<SubValidator> extraValidators);

        /**
         * Provides the lowest variant number to use.
         *
         * <p>By default, variants are numbered from 0 to {@code variantNum - 1}. By setting a value
         * here, the variants will be numbered from {@code firstVariantNumber} and up.
         */
        public abstract Builder setFirstVariantNumber(int firstVariantNumber);

        /**
         * For command line, sets the {@link PrintStream} to use for outputting the warnings.
         */
        public abstract Builder setOutputPrintStream(PrintStream outputPrintStream);

        /**
         * Provides a {@link SourceStamp} to be included in the generated APKs.
         */
        public abstract Builder setSourceStamp(SourceStamp sourceStamp);

        /**
         * If present, will replace the version of the asset modules with the provided value.
         */
        public abstract Builder setAssetModulesVersionOverride(long value);

        public abstract Builder setEnableApkSerializerWithoutBundleRecompression(boolean value);

        /**
         * Provides a wrapper around the execution of the 7zip commands.
         */
        public abstract Builder setP7ZipCommand(P7ZipCommand value);

        /**
         * Provides paths to {@link SdkBundle}s for the runtime-enabled SDKs that the {@link AppBundle}
         * depends on. Each file must have extension ".asb".
         *
         * <p>Can not be set together with {@link #setRuntimeEnabledSdkArchivePaths(ImmutableSet)}.
         */
        public abstract Builder setRuntimeEnabledSdkBundlePaths(ImmutableSet<Path> sdkBundlePaths);

        /**
         * Provides paths to {@link SdkAsar}s for the runtime-enabled SDKs that the {@link AppBundle}
         * depends on. Each file must have extension ".asar".
         *
         * <p>Can not be set together with {@link #setRuntimeEnabledSdkBundlePaths(ImmutableSet)}.
         */
        public abstract Builder setRuntimeEnabledSdkArchivePaths(ImmutableSet<Path> sdkArchivePaths);

        /**
         * Options for overriding parts of runtime-enabled SDK dependency configuration of the app for
         * local deployment and testing.
         */
        public abstract Builder setLocalDeploymentRuntimeEnabledSdkConfig(LocalDeploymentRuntimeEnabledSdkConfig localDeploymentRuntimeEnabledSdkConfig);

        /**
         * Sets package name of an app store that will be called by archived app to redownload the
         * application.
         *
         * <p>Can only be used in ARCHIVE mode.
         *
         * <p>PlayStore package (com.android.vending) is used by default.
         */
        public abstract Builder setAppStorePackageName(String appStorePackageName);

        /**
         * If true, will set default min sdk version targeting for generated splits as min sdk of base
         * module.
         */
        public abstract Builder setEnableBaseModuleMinSdkAsDefaultTargeting(boolean value);

        abstract BuildApksCommand autoBuild();

        public BuildApksCommand build() {
            if (!getExecutorServiceInternal().isPresent()) {
                setExecutorServiceInternal(createInternalExecutorService(DEFAULT_THREAD_POOL_SIZE));
                setExecutorServiceCreatedByBundleTool(true);
            }
            checkState(!getSigningConfiguration().isPresent() || !getSigningConfigurationProvider().isPresent(), "Only one of SigningConfiguration or SigningConfigurationProvider should be set.");
            getSigningConfiguration().flatMap(SigningConfiguration::getMinimumV3RotationApiVersion).ifPresent(this::setMinSdkForAdditionalVariantWithV3Rotation);
            BuildApksCommand command = autoBuild();
            if (!command.getOptimizationDimensions().isEmpty() && !command.getApkBuildMode().equals(DEFAULT)) {
                throw InvalidCommandException.builder().withInternalMessage("Optimization dimension can be only set when running with '%s' mode flag.", DEFAULT.getLowerCaseName()).build();
            }
            if (command.getGenerateOnlyForConnectedDevice() && !command.getApkBuildMode().equals(DEFAULT)) {
                throw InvalidCommandException.builder().withInternalMessage("Optimizing for connected device only possible when running with '%s' mode flag.", DEFAULT.getLowerCaseName()).build();
            }
            if (command.getDeviceSpec().isPresent()) {
                boolean supportsPartialDeviceSpecs = command.getApkBuildMode().equals(SYSTEM);
                DeviceSpecParser.validateDeviceSpec(command.getDeviceSpec().get(), /* canSkipFields= */
                supportsPartialDeviceSpecs);
                switch(command.getApkBuildMode()) {
                    case UNIVERSAL:
                        throw InvalidCommandException.builder().withInternalMessage("Optimizing for device spec is not possible when running with '%s' mode flag.", UNIVERSAL.getLowerCaseName()).build();
                    case SYSTEM:
                        DeviceSpec deviceSpec = command.getDeviceSpec().get();
                        if (deviceSpec.getScreenDensity() == 0 || deviceSpec.getSupportedAbisList().isEmpty()) {
                            throw InvalidCommandException.builder().withInternalMessage("Device spec must have screen density and ABIs set when running with " + "'%s' mode flag. ", SYSTEM.getLowerCaseName()).build();
                        }
                        break;
                    case DEFAULT:
                    case INSTANT:
                    case PERSISTENT:
                    case ARCHIVE:
                }
            } else {
                if (command.getApkBuildMode().equals(SYSTEM)) {
                    throw InvalidCommandException.builder().withInternalMessage("Device spec must always be set when running with '%s' mode flag.", SYSTEM.getLowerCaseName()).build();
                }
            }
            if (command.getFuseOnlyDeviceMatchingModules() && !command.getDeviceSpec().isPresent()) {
                throw InvalidCommandException.builder().withInternalMessage("Device spec must be provided when using '%s' flag.", FUSE_ONLY_DEVICE_MATCHING_MODULES_FLAG.getName()).build();
            }
            if (command.getGenerateOnlyForConnectedDevice() && command.getDeviceSpec().isPresent()) {
                throw InvalidCommandException.builder().withInternalMessage("Cannot optimize for the device spec and connected device at the same time.").build();
            }
            if (command.getDeviceId().isPresent() && !command.getGenerateOnlyForConnectedDevice()) {
                throw InvalidCommandException.builder().withInternalMessage("Setting --device-id requires using the --connected-device flag.").build();
            }
            if (command.getDeviceTier().isPresent() && !command.getGenerateOnlyForConnectedDevice() && !command.getDeviceSpec().isPresent()) {
                throw InvalidCommandException.builder().withInternalMessage("Setting --device-tier requires using either the --connected-device or the" + " --device-spec flag.").build();
            }
            if (command.getCountrySet().isPresent() && !command.getGenerateOnlyForConnectedDevice() && !command.getDeviceSpec().isPresent()) {
                throw InvalidCommandException.builder().withInternalMessage("Setting --country-set requires using either the --connected-device or the" + " --device-spec flag.").build();
            }
            if (command.getOutputFormat().equals(APK_SET) && !APK_SET_ARCHIVE_EXTENSION.equals(MoreFiles.getFileExtension(command.getOutputFile()))) {
                throw InvalidCommandException.builder().withInternalMessage("Flag --output should be the path where to generate the APK Set. " + "Its extension must be '.apks'.").build();
            }
            if (!command.getModules().isEmpty() && !command.getApkBuildMode().equals(SYSTEM) && !command.getApkBuildMode().equals(UNIVERSAL)) {
                throw InvalidCommandException.builder().withInternalMessage("Modules can be only set when running with '%s' or '%s' mode flag.", UNIVERSAL.getLowerCaseName(), SYSTEM.getLowerCaseName()).build();
            }
            if (command.getAppStorePackageName().isPresent() && !command.getApkBuildMode().equals(ARCHIVE)) {
                throw InvalidCommandException.builder().withInternalMessage("Providing custom store package is only possible when running with '%s' mode flag.", ARCHIVE.getLowerCaseName()).build();
            }
            if (!command.getRuntimeEnabledSdkBundlePaths().isEmpty() && !command.getRuntimeEnabledSdkArchivePaths().isEmpty()) {
                throw InvalidCommandException.builder().withInternalMessage("Command can only set either runtime-enabled SDK bundles or runtime-enabled SDK" + " archives, but both were set.").build();
            }
            if (command.getLocalDeploymentRuntimeEnabledSdkConfig().isPresent() && command.getRuntimeEnabledSdkBundlePaths().isEmpty() && command.getRuntimeEnabledSdkArchivePaths().isEmpty()) {
                throw InvalidCommandException.builder().withInternalMessage("Using --local-deployment-runtime-enabled-sdk-config flag requires either" + " --sdk-bundles or --sdk-archives flag to be also present.").build();
            }
            return command;
        }
    }

    public static BuildApksCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
        return fromFlags(flags, System.out, DEFAULT_PROVIDER, adbServer);
    }

    static BuildApksCommand fromFlags(ParsedFlags flags, PrintStream out, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
        BuildApksCommand.Builder buildApksCommand = BuildApksCommand.builder().setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags)).setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags)).setOutputPrintStream(out);
        // Optional arguments.
        OUTPUT_FORMAT_FLAG.getValue(flags).ifPresent(buildApksCommand::setOutputFormat);
        OVERWRITE_OUTPUT_FLAG.getValue(flags).ifPresent(buildApksCommand::setOverwriteOutput);
        AAPT2_PATH_FLAG.getValue(flags).ifPresent(aapt2Path -> buildApksCommand.setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path)));
        BUILD_MODE_FLAG.getValue(flags).ifPresent(buildApksCommand::setApkBuildMode);
        LOCAL_TESTING_MODE_FLAG.getValue(flags).ifPresent(buildApksCommand::setLocalTestingMode);
        MAX_THREADS_FLAG.getValue(flags).ifPresent(maxThreads -> buildApksCommand.setExecutorService(createInternalExecutorService(maxThreads)).setExecutorServiceCreatedByBundleTool(true));
        OPTIMIZE_FOR_FLAG.getValue(flags).ifPresent(buildApksCommand::setOptimizationDimensions);
        populateSigningConfigurationFromFlags(buildApksCommand, flags, out, systemEnvironmentProvider);
        populateSourceStampFromFlags(buildApksCommand, flags, out, systemEnvironmentProvider);
        boolean connectedDeviceMode = CONNECTED_DEVICE_FLAG.getValue(flags).orElse(false);
        CONNECTED_DEVICE_FLAG.getValue(flags).ifPresent(buildApksCommand::setGenerateOnlyForConnectedDevice);
        Optional<String> deviceSerialName = DEVICE_ID_FLAG.getValue(flags);
        if (connectedDeviceMode && !deviceSerialName.isPresent()) {
            deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
        }
        deviceSerialName.ifPresent(buildApksCommand::setDeviceId);
        // Applied only when --connected-device flag is set, because we don't want to fail command
        // if ADB cannot be found in a normal mode.
        if (connectedDeviceMode) {
            Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, systemEnvironmentProvider);
            buildApksCommand.setAdbPath(adbPath).setAdbServer(adbServer);
        }
        ApkBuildMode apkBuildMode = BUILD_MODE_FLAG.getValue(flags).orElse(DEFAULT);
        boolean supportsPartialDeviceSpecs = apkBuildMode.equals(SYSTEM);
        Function<Path, DeviceSpec> deviceSpecParser = supportsPartialDeviceSpecs ? DeviceSpecParser::parsePartialDeviceSpec : DeviceSpecParser::parseDeviceSpec;
        FUSE_ONLY_DEVICE_MATCHING_MODULES_FLAG.getValue(flags).ifPresent(buildApksCommand::setFuseOnlyDeviceMatchingModules);
        Optional<ImmutableSet<SystemApkOption>> systemApkOptions = SYSTEM_APK_OPTIONS.getValue(flags);
        if (systemApkOptions.isPresent() && !apkBuildMode.equals(SYSTEM)) {
            throw InvalidCommandException.builder().withInternalMessage("'%s' flag is available in system mode only.", SYSTEM_APK_OPTIONS.getName()).build();
        }
        systemApkOptions.ifPresent(buildApksCommand::setSystemApkOptions);
        DEVICE_SPEC_FLAG.getValue(flags).map(deviceSpecParser).ifPresent(buildApksCommand::setDeviceSpec);
        DEVICE_TIER_FLAG.getValue(flags).ifPresent(buildApksCommand::setDeviceTier);
        COUNTRY_SET_FLAG.getValue(flags).ifPresent(buildApksCommand::setCountrySet);
        MODULES_FLAG.getValue(flags).ifPresent(buildApksCommand::setModules);
        VERBOSE_FLAG.getValue(flags).ifPresent(buildApksCommand::setVerbose);
        P7ZIP_PATH_FLAG.getValue(flags).ifPresent(p7zipPath -> {
            int numThreads = MAX_THREADS_FLAG.getValue(flags).orElse(DEFAULT_THREAD_POOL_SIZE);
            buildApksCommand.setP7ZipCommand(P7ZipCommand.defaultP7ZipCommand(p7zipPath, numThreads));
        });
        if (RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getValue(flags).isPresent() && RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getValue(flags).isPresent()) {
            throw InvalidCommandException.builder().withInternalMessage("Only one of '%s' and '%s' flags can be set.", RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getName(), RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getName()).build();
        }
        RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getValue(flags).ifPresent(buildApksCommand::setRuntimeEnabledSdkBundlePaths);
        RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getValue(flags).ifPresent(buildApksCommand::setRuntimeEnabledSdkArchivePaths);
        if (LOCAL_DEPLOYMENT_RUNTIME_ENABLED_SDK_CONFIG_FLAG.getValue(flags).isPresent() && !RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getValue(flags).isPresent() && !RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getValue(flags).isPresent()) {
            throw InvalidCommandException.builder().withInternalMessage("'%s' flag can only be set together with '%s' or '%s' flags.", LOCAL_DEPLOYMENT_RUNTIME_ENABLED_SDK_CONFIG_FLAG.getName(), RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getName(), RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getName()).build();
        }
        LOCAL_DEPLOYMENT_RUNTIME_ENABLED_SDK_CONFIG_FLAG.getValue(flags).ifPresent(configPath -> buildApksCommand.setLocalDeploymentRuntimeEnabledSdkConfig(parseLocalRuntimeEnabledSdkConfig(configPath)));
        APP_STORE_PACKAGE_NAME_FLAG.getValue(flags).ifPresent(buildApksCommand::setAppStorePackageName);
        flags.checkNoUnknownFlags();
        return buildApksCommand.build();
    }

    public Path execute() {
        validateInput();
        Path outputDirectory = getOutputFormat().equals(APK_SET) ? getOutputFile().getParent() : getOutputFile();
        if (outputDirectory != null && Files.notExists(outputDirectory)) {
            logger.info("Output directory '" + outputDirectory + "' does not exist, creating it.");
            FileUtils.createDirectories(outputDirectory);
        }
        try (TempDirectory tempDir = new TempDirectory(getClass().getSimpleName());
            ZipFile bundleZip = new ZipFile(getBundlePath().toFile());
            Closer closer = Closer.create()) {
            AppBundleValidator bundleValidator = AppBundleValidator.create(getExtraValidators());
            bundleValidator.validateFile(bundleZip);
            AppBundle appBundle = AppBundle.buildFromZip(bundleZip);
            bundleValidator.validate(appBundle);
            ImmutableMap<String, BundleModule> sdkBundleModules = getValidatedSdkModules(closer, tempDir, appBundle);
            AppBundlePreprocessorManager appBundlePreprocessorManager = DaggerAppBundlePreprocessorComponent.builder().setBuildApksCommand(this).setSdkBundleModules(sdkBundleModules).build().create();
            AppBundle preprocessedAppBundle = appBundlePreprocessorManager.processAppBundle(appBundle);
            BuildApksManager buildApksManager = DaggerBuildApksManagerComponent.builder().setBuildApksCommand(this).setTempDirectory(tempDir).setAppBundle(preprocessedAppBundle).build().create();
            buildApksManager.execute();
        } catch (ZipException e) {
            throw InvalidBundleException.builder().withCause(e).withUserMessage("The App Bundle is not a valid zip file.").build();
        } catch (IOException e) {
            throw new UncheckedIOException("An error occurred when processing the App Bundle.", e);
        } finally {
            if (isExecutorServiceCreatedByBundleTool()) {
                getExecutorService().shutdown();
            }
        }
        return getOutputFile();
    }

    private void validateInput() {
        checkFileExistsAndReadable(getBundlePath());
        switch(getOutputFormat()) {
            case APK_SET:
                if (!getOverwriteOutput()) {
                    checkFileDoesNotExist(getOutputFile());
                }
                break;
            case DIRECTORY:
                if (getOverwriteOutput()) {
                    throw InvalidCommandException.builder().withInternalMessage("'%s' flag is not supported for '%s' output format.", OVERWRITE_OUTPUT_FLAG.getName(), DIRECTORY).build();
                }
                break;
        }
        if (getGenerateOnlyForConnectedDevice()) {
            checkArgument(getAdbServer().isPresent(), "Property 'adbServer' is required when 'generateOnlyForConnectedDevice' is true.");
            checkArgument(getAdbPath().isPresent(), "Property 'adbPath' is required when 'generateOnlyForConnectedDevice' is true.");
            checkFileExistsAndExecutable(getAdbPath().get());
        }
        if (!shouldGenerateSdkRuntimeVariant(getApkBuildMode())) {
            checkArgument(getRuntimeEnabledSdkBundlePaths().isEmpty(), "runtimeEnabledSdkBundlePaths can not be present in '%s' mode.", getApkBuildMode().name());
            checkArgument(getRuntimeEnabledSdkArchivePaths().isEmpty(), "runtimeEnabledSdkArchivePaths can not be present in '%s' mode.", getApkBuildMode().name());
        }
        getRuntimeEnabledSdkBundlePaths().forEach(path -> {
            checkFileExistsAndReadable(path);
            checkFileHasExtension("ASB file", path, ".asb");
        });
        getRuntimeEnabledSdkArchivePaths().forEach(path -> {
            checkFileExistsAndReadable(path);
            checkFileHasExtension("ASAR file", path, ".asar");
        });
    }

    private ImmutableMap<String, BundleModule> getValidatedSdkModules(Closer closer, TempDirectory tempDir, AppBundle appBundle) throws IOException {
        if (!shouldGenerateSdkRuntimeVariant(getApkBuildMode())) {
            return ImmutableMap.of();
        }
        if (!getRuntimeEnabledSdkArchivePaths().isEmpty()) {
            ImmutableMap<String, SdkAsar> sdkAsars = getValidatedSdkAsarsByPackageName(closer, tempDir);
            validateSdkAsarsMatchAppBundleDependencies(appBundle, sdkAsars);
            return sdkAsars.entrySet().stream().collect(toImmutableMap(Entry::getKey, entry -> entry.getValue().getModule()));
        }
        ImmutableMap<String, SdkBundle> sdkBundles = getValidatedSdkBundlesByPackageName(closer, tempDir);
        validateSdkBundlesMatchAppBundleDependencies(appBundle, sdkBundles);
        return sdkBundles.entrySet().stream().collect(toImmutableMap(Entry::getKey, entry -> entry.getValue().getModule()));
    }

    private ImmutableMap<String, SdkAsar> getValidatedSdkAsarsByPackageName(Closer closer, TempDirectory tempDir) throws IOException {
        ImmutableListMultimap.Builder<String, SdkAsar> sdkArchivesPerPackageNameBuilder = ImmutableListMultimap.builder();
        ImmutableList<Path> sdkArchivePaths = getRuntimeEnabledSdkArchivePaths().asList();
        for (int index = 0; index < sdkArchivePaths.size(); index++) {
            ZipFile sdkArchiveZip = closer.register(new ZipFile(sdkArchivePaths.get(index).toFile()));
            Path sdkModulesZipPath = tempDir.getPath().resolve("tmp" + index);
            ZipFile sdkModulesZip = closer.register(getModulesZip(sdkArchiveZip, sdkModulesZipPath));
            SdkAsarValidator.validateModulesFile(sdkModulesZip);
            SdkAsar sdkArchive = SdkAsar.buildFromZip(sdkArchiveZip, sdkModulesZip, sdkModulesZipPath);
            sdkArchivesPerPackageNameBuilder.put(sdkArchive.getPackageName(), sdkArchive);
        }
        ImmutableMap<String, Collection<SdkAsar>> sdkArchivesPerPackageName = sdkArchivesPerPackageNameBuilder.build().asMap();
        sdkArchivesPerPackageName.entrySet().forEach(entry -> {
            // App can not depend on multiple SDK archives with the same package name.
            if (entry.getValue().size() > 1) {
                throw InvalidCommandException.builder().withInternalMessage("Received multiple SDK archives with the same package name: %s.", entry.getKey()).build();
            }
        });
        return ImmutableMap.copyOf(Maps.transformValues(sdkArchivesPerPackageName, Iterables::getOnlyElement));
    }

    private static void validateSdkAsarsMatchAppBundleDependencies(AppBundle appBundle, ImmutableMap<String, SdkAsar> sdkArchives) {
        appBundle.getRuntimeEnabledSdkDependencies().keySet().forEach(sdkPackageName -> {
            if (!sdkArchives.containsKey(sdkPackageName)) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s', but no ASAR was provided.", sdkPackageName).build();
            }
            RuntimeEnabledSdk sdkDependencyFromAppBundle = appBundle.getRuntimeEnabledSdkDependencies().get(sdkPackageName);
            SdkAsar sdkArchive = sdkArchives.get(sdkPackageName);
            if (sdkDependencyFromAppBundle.getVersionMajor() != sdkArchive.getMajorVersion()) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s' with major version '%d', but provided SDK" + " archive has major version '%d'.", sdkPackageName, sdkDependencyFromAppBundle.getVersionMajor(), sdkArchive.getMajorVersion()).build();
            }
            if (sdkDependencyFromAppBundle.getVersionMinor() != sdkArchive.getMinorVersion()) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s' with minor version '%d', but provided SDK" + " archive has minor version '%d'.", sdkPackageName, sdkDependencyFromAppBundle.getVersionMinor(), sdkArchive.getMinorVersion()).build();
            }
            if (!sdkDependencyFromAppBundle.getCertificateDigest().equals(sdkArchive.getCertificateDigest())) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s' with signing certificate '%s', but provided" + " ASAR is for SDK with signing certificate '%s'.", sdkPackageName, sdkDependencyFromAppBundle.getCertificateDigest(), sdkArchive.getCertificateDigest()).build();
            }
        });
        sdkArchives.keySet().forEach(sdkPackageName -> {
            if (!appBundle.getRuntimeEnabledSdkDependencies().containsKey(sdkPackageName)) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle does not depend on SDK '%s', but SDK archive was provided.", sdkPackageName).build();
            }
        });
    }

    private ImmutableMap<String, SdkBundle> getValidatedSdkBundlesByPackageName(Closer closer, TempDirectory tempDir) throws IOException {
        SdkBundleValidator sdkBundleValidator = SdkBundleValidator.create();
        ImmutableListMultimap.Builder<String, SdkBundle> sdkBundlesPerPackageNameBuilder = ImmutableListMultimap.builder();
        ImmutableList<Path> sdkBundlePaths = getRuntimeEnabledSdkBundlePaths().asList();
        for (int index = 0; index < sdkBundlePaths.size(); index++) {
            Path sdkBundlePath = sdkBundlePaths.get(index);
            ZipFile sdkBundleZip = closer.register(new ZipFile(sdkBundlePath.toFile()));
            sdkBundleValidator.validateFile(sdkBundleZip);
            ZipFile sdkModulesZip = closer.register(getModulesZip(sdkBundleZip, tempDir.getPath().resolve("tmp" + index)));
            sdkBundleValidator.validateModulesFile(sdkModulesZip);
            // SdkBundle#getVersionCode is not used in `build-apks`. It does not matter what
            // value we set here, so we are just setting 0.
            SdkBundle sdkBundle = SdkBundle.buildFromZip(sdkBundleZip, sdkModulesZip, /* versionCode= */
            0);
            sdkBundlesPerPackageNameBuilder.put(sdkBundle.getPackageName(), sdkBundle);
        }
        ImmutableMap<String, Collection<SdkBundle>> sdkBundlesPerPackageName = sdkBundlesPerPackageNameBuilder.build().asMap();
        sdkBundlesPerPackageName.entrySet().forEach(entry -> {
            // App can not depend on multiple SDK bundles with the same package name.
            if (entry.getValue().size() > 1) {
                throw InvalidCommandException.builder().withInternalMessage("Received multiple SDK bundles with the same package name: %s.", entry.getKey()).build();
            }
            // Validate format of each SDK bundle.
            sdkBundleValidator.validate(Iterables.getOnlyElement(entry.getValue()));
        });
        return ImmutableMap.copyOf(Maps.transformValues(sdkBundlesPerPackageName, Iterables::getOnlyElement));
    }

    private static void validateSdkBundlesMatchAppBundleDependencies(AppBundle appBundle, ImmutableMap<String, SdkBundle> sdkBundles) {
        appBundle.getRuntimeEnabledSdkDependencies().keySet().forEach(sdkPackageName -> {
            if (!sdkBundles.containsKey(sdkPackageName)) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s', but no SDK bundle was provided.", sdkPackageName).build();
            }
            RuntimeEnabledSdk sdkDependencyFromAppBundle = appBundle.getRuntimeEnabledSdkDependencies().get(sdkPackageName);
            SdkBundle sdkBundle = sdkBundles.get(sdkPackageName);
            if (sdkDependencyFromAppBundle.getVersionMajor() != sdkBundle.getMajorVersion()) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s' with major version '%d', but provided SDK" + " bundle has major version '%d'.", sdkPackageName, sdkDependencyFromAppBundle.getVersionMajor(), sdkBundle.getMajorVersion()).build();
            }
            if (sdkDependencyFromAppBundle.getVersionMinor() != sdkBundle.getMinorVersion()) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle depends on SDK '%s' with minor version '%d', but provided SDK" + " bundle has minor version '%d'.", sdkPackageName, sdkDependencyFromAppBundle.getVersionMinor(), sdkBundle.getMinorVersion()).build();
            }
        });
        sdkBundles.keySet().forEach(sdkPackageName -> {
            if (!appBundle.getRuntimeEnabledSdkDependencies().containsKey(sdkPackageName)) {
                throw InvalidCommandException.builder().withInternalMessage("App bundle does not depend on SDK '%s', but SDK bundle was provided.", sdkPackageName).build();
            }
        });
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
        return CommandHelp.builder().setCommandName(COMMAND_NAME).setCommandDescription(CommandDescription.builder().setShortDescription("Generates an APK Set archive containing either all possible split APKs and " + "standalone APKs or APKs optimized for the connected device " + "(see %s flag).", CONNECTED_DEVICE_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(BUNDLE_LOCATION_FLAG.getName()).setExampleValue("bundle.aab").setDescription("Path to the Android App Bundle to generate APKs from.").build()).addFlag(FlagDescription.builder().setFlagName(OUTPUT_FILE_FLAG.getName()).setExampleValue("output.apks").setDescription("Path to where the APK Set archive should be created (default) or path to the" + " directory where generated APKs should be stored when flag --%s is set" + " to '%s'.", OUTPUT_FORMAT_FLAG.getName(), DIRECTORY).build()).addFlag(FlagDescription.builder().setFlagName(OUTPUT_FORMAT_FLAG.getName()).setExampleValue(joinFlagOptions(OutputFormat.values())).setOptional(true).setDescription("Specifies output format for generated APKs. If set to '%s' outputs APKs into" + " the created APK Set archive (default). If set to '%s' outputs APKs" + " into the specified directory.", APK_SET, DIRECTORY).build()).addFlag(FlagDescription.builder().setFlagName(OVERWRITE_OUTPUT_FLAG.getName()).setOptional(true).setDescription("If set, any previous existing output will be overwritten.").build()).addFlag(FlagDescription.builder().setFlagName(AAPT2_PATH_FLAG.getName()).setExampleValue("path/to/aapt2").setOptional(true).setDescription("Path to the aapt2 binary to use.").build()).addFlag(FlagDescription.builder().setFlagName(BUILD_MODE_FLAG.getName()).setExampleValue(joinFlagOptions(ApkBuildMode.values())).setOptional(true).setDescription("Specifies which mode to run '%s' command against. Acceptable values are '%s'." + " If not set or set to '%s' we generate split, standalone and instant" + " APKs. If set to '%s' we generate universal APK. If set to '%s' we" + " generate APKs for system image. If set to %s we generate the archived" + " APK.", BuildApksCommand.COMMAND_NAME, joinFlagOptions(ApkBuildMode.values()), DEFAULT.getLowerCaseName(), UNIVERSAL.getLowerCaseName(), SYSTEM.getLowerCaseName(), ARCHIVE.getLowerCaseName()).build()).addFlag(FlagDescription.builder().setFlagName(MAX_THREADS_FLAG.getName()).setExampleValue("num-threads").setOptional(true).setDescription("Sets the maximum number of threads to use (default: %d).", DEFAULT_THREAD_POOL_SIZE).build()).addFlag(FlagDescription.builder().setFlagName(OPTIMIZE_FOR_FLAG.getName()).setExampleValue(joinFlagOptions(OptimizationDimension.values())).setOptional(true).setDescription("If set, will generate APKs with optimizations for the given dimensions. " + "Acceptable values are '%s'. This flag should be only be set with " + "--%s=%s flag.", joinFlagOptions(OptimizationDimension.values()), BUILD_MODE_FLAG.getName(), DEFAULT.getLowerCaseName()).build()).addFlag(FlagDescription.builder().setFlagName(KEYSTORE_FLAG.getName()).setExampleValue("path/to/keystore").setOptional(true).setDescription("Path to the keystore that should be used to sign the generated APKs. If not " + "set, the default debug keystore will be used if it exists. If not found " + "the APKs will not be signed. If set, the flag '%s' must also be set.", KEY_ALIAS_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(KEY_ALIAS_FLAG.getName()).setExampleValue("key-alias").setOptional(true).setDescription("Alias of the key to use in the keystore to sign the generated APKs.").build()).addFlag(FlagDescription.builder().setFlagName(KEYSTORE_PASSWORD_FLAG.getName()).setExampleValue("[pass|file]:value").setOptional(true).setDescription("Password of the keystore to use to sign the generated APKs. If provided, must " + "be prefixed with either 'pass:' (if the password is passed in clear " + "text, e.g. 'pass:qwerty') or 'file:' (if the password is the first line " + "of a file, e.g. 'file:/tmp/myPassword.txt'). If this flag is not set, " + "the password will be requested on the prompt.").build()).addFlag(FlagDescription.builder().setFlagName(KEY_PASSWORD_FLAG.getName()).setExampleValue("key-password").setOptional(true).setDescription("Password of the key in the keystore to use to sign the generated APKs. If " + "provided, must be prefixed with either 'pass:' (if the password is " + "passed in clear text, e.g. 'pass:qwerty') or 'file:' (if the password " + "is the first line of a file, e.g. 'file:/tmp/myPassword.txt'). If this " + "flag is not set, the keystore password will be tried. If that fails, " + "the password will be requested on the prompt.").build()).addFlag(FlagDescription.builder().setFlagName(MINIMUM_V3_ROTATION_API_VERSION_FLAG.getName()).setExampleValue("30").setOptional(true).setDescription("The minimum API version for signing the generated APKs with rotation using V3" + " signature scheme.").build()).addFlag(FlagDescription.builder().setFlagName(ROTATION_MINIMUM_SDK_VERSION_FLAG.getName()).setExampleValue("30").setOptional(true).setDescription("The minimum Android version for signing the generated APKs with rotation. The" + " original signing key for the APK will be used for all previous platform" + " versions.").build()).addFlag(FlagDescription.builder().setFlagName(LINEAGE_FLAG.getName()).setExampleValue("path/to/existing/lineage").setOptional(true).setDescription("Input SigningCertificateLineage. This file contains a binary representation " + "of a SigningCertificateLineage object, which contains the " + "proof-of-rotation for different signing certificates. This can be used " + "with APK Signature Scheme v3 to rotate the signing certificate for an " + "APK. An APK previously signed with a SigningCertificateLineage can also " + "be specified; the lineage will then be read from the signed data in the " + "APK. If set, the flag '%s' must also be set.", OLDEST_SIGNER_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(OLDEST_SIGNER_FLAG.getName()).setExampleValue("path/to/keystore.properties").setOptional(true).setDescription("Path to a properties file containing the properties of the oldest keystore " + "that has previously been used for signing. This is used to sign the " + "generated APKs with signature scheme v1 and v2 for backward " + "compatibility when the key has been rotated using signature scheme " + "v3. If set, the flag '%s' must also be set.\n" + "\n" + "The file is required to include values for the following properties:\n" + "  * ks - Path to the keystore.\n" + "  * ks-key-alias - Alias of the key to use in the keystore.\n" + "\n" + "The file may also optionally include values for the following " + "properties:\n" + "  * ks-pass - Password of the keystore. If provided, must be prefixed " + "with either 'pass:' (if the password is passed in clear text, e.g. " + "'pass:qwerty') or 'file:' (if the password is the first line of a " + "file, e.g. 'file:/tmp/myPassword.txt'). If this property is not set, " + "the password will be requested on the prompt.\n" + "  * key-pass - Password of the key in the keystore. If provided, must " + "be prefixed with either 'pass:' (if the password is passed in clear " + "text, e.g. 'pass:qwerty') or 'file:' (if the password is the first " + "line of a file, e.g. 'file:/tmp/myPassword.txt'). If this property is " + "not set, the keystore password will be tried. If that fails, the " + "password will be requested on the prompt.\n" + "\n" + "Example keystore.properties file:\n" + "ks=/path/to/keystore.jks\n" + "ks-key-alias=keyAlias\n" + "ks-pass=pass:myPassword\n" + "key-pass=file:/path/to/myPassword.txt", LINEAGE_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(CONNECTED_DEVICE_FLAG.getName()).setOptional(true).setDescription("If set, will generate APK Set optimized for the connected device. The " + "generated APK Set will only be installable on that specific class of " + "devices. This flag should only be set with --%s=%s flag.", BUILD_MODE_FLAG.getName(), DEFAULT.getLowerCaseName()).build()).addFlag(FlagDescription.builder().setFlagName(ADB_PATH_FLAG.getName()).setExampleValue("path/to/adb").setOptional(true).setDescription("Path to the adb utility. If absent, an attempt will be made to locate it if " + "the %s or %s environment variable is set. Used only if %s flag is set.", ANDROID_HOME_VARIABLE, SYSTEM_PATH_VARIABLE, CONNECTED_DEVICE_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(DEVICE_ID_FLAG.getName()).setExampleValue("device-serial-name").setOptional(true).setDescription("Device serial name. If absent, this uses the %s environment variable. Either " + "this flag or the environment variable is required when more than one " + "device or emulator is connected. Used only if %s flag is set.", ANDROID_SERIAL_VARIABLE, CONNECTED_DEVICE_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(DEVICE_SPEC_FLAG.getName()).setExampleValue("device-spec.json").setOptional(true).setDescription("Path to the device spec file generated by the '%s' command. If present, " + "it will generate an APK Set optimized for the specified device spec. " + "This flag should be only be set with --%s=%s flag.", GetDeviceSpecCommand.COMMAND_NAME, BUILD_MODE_FLAG.getName(), DEFAULT.getLowerCaseName()).build()).addFlag(FlagDescription.builder().setFlagName(DEVICE_TIER_FLAG.getName()).setExampleValue("low").setOptional(true).setDescription("Device tier to use for APK matching. This flag should only be set with" + " --%s or --%s flags. If a device spec with a device tier is provided," + " the value specified here will override the value set in the device" + " spec.", DEVICE_SPEC_FLAG.getName(), CONNECTED_DEVICE_FLAG.getName()).build()).addFlag(FlagDescription.builder().setFlagName(COUNTRY_SET_FLAG.getName()).setExampleValue("country_set_name").setOptional(true).setDescription("Country set to use for APK matching. This flag should only be set with" + " --%s or --%s flags. If a device spec with a country set is provided," + " the value specified here will override the value set in the device" + " spec.", DEVICE_SPEC_FLAG.getName(), CONNECTED_DEVICE_FLAG.getName()).build()).addFlag(FlagDescription.builder().setFlagName(FUSE_ONLY_DEVICE_MATCHING_MODULES_FLAG.getName()).setOptional(true).setDescription("When set, conditional modules with fused attribute will be fused into the" + " base module if they match the device given by %s", DEVICE_SPEC_FLAG.getName()).build()).addFlag(FlagDescription.builder().setFlagName(MODULES_FLAG.getName()).setExampleValue("base,module1,module2").setOptional(true).setDescription("List of module names to include in the generated APK Set in modes %s and %s.", UNIVERSAL.getLowerCaseName(), SYSTEM.getLowerCaseName()).build()).addFlag(FlagDescription.builder().setFlagName(LOCAL_TESTING_MODE_FLAG.getName()).setOptional(true).setDescription("If enabled, the APK set will be built in local testing mode, which includes" + " additional metadata in the output. When `bundletool %s` is later used" + " to install APKs from this set on a device, it will additionally push" + " all dynamic module splits and asset packs to a location that can be" + " accessed by the Play Core API.", InstallApksCommand.COMMAND_NAME).build()).addFlag(FlagDescription.builder().setFlagName(VERBOSE_FLAG.getName()).setOptional(true).setDescription("If set, prints extra information about the command execution in the standard" + " output.").build()).addFlag(FlagDescription.builder().setFlagName(P7ZIP_PATH_FLAG.getName()).setOptional(true).setDescription("Path to the 7zip binary to use. Mandatory if Android App Bundle requires 7zip" + " compression.").build()).addFlag(FlagDescription.builder().setFlagName(CREATE_STAMP_FLAG.getName()).setOptional(true).setDescription("If set, a stamp will be generated and embedded in the generated APKs.").build()).addFlag(FlagDescription.builder().setFlagName(STAMP_KEYSTORE_FLAG.getName()).setExampleValue("path/to/keystore").setOptional(true).setDescription("Path to the stamp keystore that should be used to sign the APK contents hash." + " If not set, the '%s' keystore will be tried if present. Otherwise, the" + " default debug keystore will be used if it exists. If a default debug" + " keystore is not found, the stamp will fail to get generated. If" + " set, the flag '%s' must also be set.", KEYSTORE_FLAG, STAMP_KEY_ALIAS_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(STAMP_KEYSTORE_PASSWORD_FLAG.getName()).setExampleValue("[pass|file]:value").setOptional(true).setDescription("Password of the stamp keystore to use to sign the APK contents hash. If" + " provided, must be prefixed with either 'pass:' (if the password is" + " passed in clear text, e.g. 'pass:qwerty') or 'file:' (if the password" + " is the first line of a file, e.g. 'file:/tmp/myPassword.txt'). If this" + " flag is not set, the password will be requested on the prompt.").build()).addFlag(FlagDescription.builder().setFlagName(STAMP_KEY_ALIAS_FLAG.getName()).setExampleValue("stamp-key-alias").setOptional(true).setDescription("Alias of the stamp key to use in the keystore to sign the APK contents hash." + " If not set, the '%s' key alias will be tried if present.", KEY_ALIAS_FLAG).build()).addFlag(FlagDescription.builder().setFlagName(STAMP_KEY_PASSWORD_FLAG.getName()).setExampleValue("stamp-key-password").setOptional(true).setDescription("Password of the stamp key in the keystore to use to sign the APK contents" + " hash. if provided, must be prefixed with either 'pass:' (if the" + " password is passed in clear text, e.g. 'pass:qwerty') or 'file:' (if" + " the password is the first line of a file, e.g." + " 'file:/tmp/myPassword.txt'). If this flag is not set, the keystore" + " password will be tried. If that fails, the password will be requested" + " on the prompt.").build()).addFlag(FlagDescription.builder().setFlagName(STAMP_SOURCE_FLAG.getName()).setExampleValue("stamp-source").setOptional(true).setDescription("Name of source generating the stamp. For stores, it is their package names." + " For locally generated stamp, it is 'local'.").build()).addFlag(FlagDescription.builder().setFlagName(STAMP_EXCLUDE_TIMESTAMP.getName()).setExampleValue("true").setOptional(true).setDescription("If set, a timestamp indicating the time of signing will be excluded from the" + " generated stamp embedded in the generated APKs.").build()).addFlag(FlagDescription.builder().setFlagName(RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getName()).setExampleValue("path/to/bundle1.asb,path/to/bundle2.asb").setOptional(true).setDescription("Paths to SDK bundles for the runtime-enabled SDKs that the App Bundle depends" + " on, separated by commas. Each SDK bundle must have an extension .asb." + " Can not be used together with the '%s' flag.", RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getName()).build()).addFlag(FlagDescription.builder().setFlagName(RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getName()).setExampleValue("path/to/asar1.asar,path/to/asar2.asar").setOptional(true).setDescription("Paths to SDK archives for the runtime-enabled SDKs that the App Bundle depends" + " on, separated by commas. Each SDK archive must have an extension" + " .asar. Can not be used together with the '%s' flag.", RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getName()).build()).addFlag(FlagDescription.builder().setFlagName(LOCAL_DEPLOYMENT_RUNTIME_ENABLED_SDK_CONFIG_FLAG.getName()).setExampleValue("path/to/config.json").setOptional(true).setDescription("Path to config file that contains options for overriding parts of" + " runtime-enabled SDK dependency configuration of the app bundle. Can" + " only be used if either '%s' or '%s' is set.", RUNTIME_ENABLED_SDK_BUNDLE_LOCATIONS_FLAG.getName(), RUNTIME_ENABLED_SDK_ARCHIVE_LOCATIONS_FLAG.getName()).build()).addFlag(FlagDescription.builder().setFlagName(APP_STORE_PACKAGE_NAME_FLAG.getName()).setExampleValue("com.android.vending").setOptional(true).setDescription("Package name of an app store that will be called by archived app to redownload" + " the application. Play Store is called by default." + " Can only be provided for ARCHIVE mode.").build()).build();
    }

    private static String joinFlagOptions(Enum<?>... flagOptions) {
        return stream(flagOptions).map(Enum::name).map(String::toLowerCase).collect(joining("|"));
    }

    private static void populateSigningConfigurationFromFlags(Builder buildApksCommand, ParsedFlags flags, PrintStream out, SystemEnvironmentProvider systemEnvironmentProvider) {
        // Signing-related arguments.
        Optional<Path> keystorePath = KEYSTORE_FLAG.getValue(flags);
        Optional<String> keyAlias = KEY_ALIAS_FLAG.getValue(flags);
        Optional<Password> keystorePassword = KEYSTORE_PASSWORD_FLAG.getValue(flags);
        Optional<Password> keyPassword = KEY_PASSWORD_FLAG.getValue(flags);
        Optional<Integer> minV3RotationApi = MINIMUM_V3_ROTATION_API_VERSION_FLAG.getValue(flags);
        Optional<Integer> rotationMinSdkVersion = ROTATION_MINIMUM_SDK_VERSION_FLAG.getValue(flags);
        if (keystorePath.isPresent() && keyAlias.isPresent()) {
            SignerConfig signerConfig = SignerConfig.extractFromKeystore(keystorePath.get(), keyAlias.get(), keystorePassword, keyPassword);
            SigningConfiguration.Builder builder = SigningConfiguration.builder().setSignerConfig(signerConfig).setMinimumV3RotationApiVersion(minV3RotationApi).setRotationMinSdkVersion(rotationMinSdkVersion);
            populateLineageFromFlags(builder, flags);
            buildApksCommand.setSigningConfiguration(builder.build());
        } else if (keystorePath.isPresent() && !keyAlias.isPresent()) {
            throw InvalidCommandException.builder().withInternalMessage("Flag --ks-key-alias is required when --ks is set.").build();
        } else if (!keystorePath.isPresent() && keyAlias.isPresent()) {
            throw InvalidCommandException.builder().withInternalMessage("Flag --ks is required when --ks-key-alias is set.").build();
        } else {
            // Try to use debug keystore if present.
            Optional<SigningConfiguration> debugConfig = DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider);
            if (debugConfig.isPresent()) {
                out.printf("INFO: The APKs will be signed with the debug keystore found at '%s'.%n", DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.getUnchecked(systemEnvironmentProvider).get());
                buildApksCommand.setSigningConfiguration(debugConfig.get());
            } else {
                out.println("WARNING: The APKs won't be signed and thus not installable unless you also pass a " + "keystore via the flag --ks. See the command help for more information.");
            }
        }
    }

    private static void populateLineageFromFlags(SigningConfiguration.Builder signingConfiguration, ParsedFlags flags) {
        // Key-rotation-related arguments.
        Optional<Path> lineagePath = LINEAGE_FLAG.getValue(flags);
        Optional<Path> oldestSignerPropertiesPath = OLDEST_SIGNER_FLAG.getValue(flags);
        if (lineagePath.isPresent() && oldestSignerPropertiesPath.isPresent()) {
            signingConfiguration.setSigningCertificateLineage(getLineageFromInputFile(lineagePath.get().toFile()));
            KeystoreProperties oldestSignerProperties = KeystoreProperties.readFromFile(oldestSignerPropertiesPath.get());
            signingConfiguration.setOldestSigner(SignerConfig.extractFromKeystore(oldestSignerProperties.getKeystorePath(), oldestSignerProperties.getKeyAlias(), oldestSignerProperties.getKeystorePassword(), oldestSignerProperties.getKeyPassword()));
        } else if (lineagePath.isPresent() && !oldestSignerPropertiesPath.isPresent()) {
            throw InvalidCommandException.builder().withInternalMessage("Flag '%s' is required when '%s' is set.", OLDEST_SIGNER_FLAG, LINEAGE_FLAG).build();
        } else if (!lineagePath.isPresent() && oldestSignerPropertiesPath.isPresent()) {
            throw InvalidCommandException.builder().withInternalMessage("Flag '%s' is required when '%s' is set.", LINEAGE_FLAG, OLDEST_SIGNER_FLAG).build();
        }
    }

    /**
     * Extracts the Signing Certificate Lineage from the provided lineage or APK file.
     */
    private static SigningCertificateLineage getLineageFromInputFile(File inputLineageFile) {
        try (RandomAccessFile f = new RandomAccessFile(inputLineageFile, "r")) {
            if (f.length() < 4) {
                throw CommandExecutionException.builder().withInternalMessage("The input file is not a valid lineage file.").build();
            }
            DataSource apk = DataSources.asDataSource(f);
            int magicValue = apk.getByteBuffer(0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (magicValue == SigningCertificateLineage.MAGIC) {
                return SigningCertificateLineage.readFromFile(inputLineageFile);
            } else if (magicValue == ZIP_MAGIC) {
                return SigningCertificateLineage.readFromApkFile(inputLineageFile);
            } else {
                throw CommandExecutionException.builder().withInternalMessage("The input file is not a valid lineage file.").build();
            }
        } catch (IOException | ApkFormatException | IllegalArgumentException e) {
            throw CommandExecutionException.builder().withCause(e).build();
        }
    }

    private static void populateSourceStampFromFlags(Builder buildApksCommand, ParsedFlags flags, PrintStream out, SystemEnvironmentProvider systemEnvironmentProvider) {
        boolean createStamp = CREATE_STAMP_FLAG.getValue(flags).orElse(false);
        Optional<String> stampSource = STAMP_SOURCE_FLAG.getValue(flags);
        Optional<Boolean> stampExcludeTimestamp = STAMP_EXCLUDE_TIMESTAMP.getValue(flags);
        if (!createStamp) {
            return;
        }
        SourceStamp.Builder sourceStamp = SourceStamp.builder();
        sourceStamp.setSigningConfiguration(getStampSigningConfiguration(flags, out, systemEnvironmentProvider));
        stampSource.ifPresent(sourceStamp::setSource);
        stampExcludeTimestamp.ifPresent(excludeTimestamp -> sourceStamp.setIncludeTimestamp(!excludeTimestamp));
        buildApksCommand.setSourceStamp(sourceStamp.build());
    }

    private static SigningConfiguration getStampSigningConfiguration(ParsedFlags flags, PrintStream out, SystemEnvironmentProvider systemEnvironmentProvider) {
        // Signing-related flags.
        Optional<Path> signingKeystorePath = KEYSTORE_FLAG.getValue(flags);
        Optional<Password> signingKeystorePassword = KEYSTORE_PASSWORD_FLAG.getValue(flags);
        Optional<String> signingKeyAlias = KEY_ALIAS_FLAG.getValue(flags);
        Optional<Password> signingKeyPassword = KEY_PASSWORD_FLAG.getValue(flags);
        // Stamp-related flags.
        Optional<Path> stampKeystorePath = STAMP_KEYSTORE_FLAG.getValue(flags);
        Optional<Password> stampKeystorePassword = STAMP_KEYSTORE_PASSWORD_FLAG.getValue(flags);
        Optional<String> stampKeyAlias = STAMP_KEY_ALIAS_FLAG.getValue(flags);
        Optional<Password> stampKeyPassword = STAMP_KEY_PASSWORD_FLAG.getValue(flags);
        Path keystorePath = null;
        Optional<Password> keystorePassword = Optional.empty();
        if (stampKeystorePath.isPresent()) {
            keystorePath = stampKeystorePath.get();
            keystorePassword = stampKeystorePassword;
        } else if (signingKeystorePath.isPresent()) {
            keystorePath = signingKeystorePath.get();
            keystorePassword = signingKeystorePassword;
        }
        if (keystorePath == null) {
            // Try to use debug keystore if present.
            Optional<SigningConfiguration> debugConfig = DebugKeystoreUtils.getDebugSigningConfiguration(systemEnvironmentProvider);
            if (debugConfig.isPresent()) {
                out.printf("INFO: The stamp will be signed with the debug keystore found at '%s'.%n", DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.getUnchecked(systemEnvironmentProvider).get());
                return debugConfig.get();
            } else {
                throw InvalidCommandException.builder().withInternalMessage("No key was found to sign the stamp.").build();
            }
        }
        String keyAlias = null;
        Optional<Password> keyPassword = Optional.empty();
        if (stampKeyAlias.isPresent()) {
            keyAlias = stampKeyAlias.get();
            keyPassword = stampKeyPassword;
        } else if (signingKeyAlias.isPresent()) {
            keyAlias = signingKeyAlias.get();
            keyPassword = signingKeyPassword;
        }
        if (keyAlias == null) {
            throw InvalidCommandException.builder().withInternalMessage("Flag --stamp-key-alias or --ks-key-alias are required when --stamp-ks or --ks are" + " set.").build();
        }
        return SigningConfiguration.extractFromKeystore(keystorePath, keyAlias, keystorePassword, keyPassword);
    }

    private static LocalDeploymentRuntimeEnabledSdkConfig parseLocalRuntimeEnabledSdkConfig(Path configPath) {
        try (Reader configReader = BufferedIo.reader(configPath)) {
            LocalDeploymentRuntimeEnabledSdkConfig.Builder configBuilder = LocalDeploymentRuntimeEnabledSdkConfig.newBuilder();
            JsonFormat.parser().merge(configReader, configBuilder);
            return configBuilder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean shouldGenerateSdkRuntimeVariant(ApkBuildMode mode) {
        switch(mode) {
            case DEFAULT:
            case UNIVERSAL:
            case SYSTEM:
            case PERSISTENT:
                return true;
            case INSTANT:
            case ARCHIVE:
                return false;
        }
        throw new IllegalStateException();
    }
}
