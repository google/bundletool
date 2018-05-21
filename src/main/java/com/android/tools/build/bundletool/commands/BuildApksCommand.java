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

import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.io.SplitApkSerializer;
import com.android.tools.build.bundletool.io.StandaloneApkSerializer;
import com.android.tools.build.bundletool.io.TempFiles;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.optimizations.OptimizationsMerger;
import com.android.tools.build.bundletool.splitters.BundleSharder;
import com.android.tools.build.bundletool.splitters.ModuleSplitter;
import com.android.tools.build.bundletool.targeting.AlternativeVariantTargetingPopulator;
import com.android.tools.build.bundletool.utils.ConcurrencyUtils;
import com.android.tools.build.bundletool.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.utils.Versions;
import com.android.tools.build.bundletool.utils.flags.Flag;
import com.android.tools.build.bundletool.utils.flags.Flag.Password;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.android.tools.build.bundletool.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Int32Value;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/** Implementation of the command to generate module split APKs and bundle shard APKs. */
@AutoValue
public abstract class BuildApksCommand {

  private static final int DEFAULT_THREAD_POOL_SIZE = 4;

  public static final String COMMAND_NAME = "build-apks";

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");
  private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");
  private static final Flag<ImmutableSet<OptimizationDimension>> OPTIMIZE_FOR_FLAG =
      Flag.enumSet("optimize-for", OptimizationDimension.class);
  private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");
  private static final Flag<Boolean> GENERATE_UNIVERSAL_APK_FLAG = Flag.booleanFlag("universal");
  private static final Flag<Integer> MAX_THREADS_FLAG = Flag.positiveInteger("max-threads");

  // Signing-related flags: should match flags from apksig library.
  private static final Flag<Path> KEYSTORE_FLAG = Flag.path("ks");
  private static final Flag<String> KEY_ALIAS_FLAG = Flag.string("ks-key-alias");
  private static final Flag<Password> KEYSTORE_PASSWORD = Flag.password("ks-pass");
  private static final Flag<Password> KEY_PASSWORD = Flag.password("key-pass");

  private static final ModuleMetadata STANDALONE_MODULE_METADATA =
      ModuleMetadata.newBuilder().setName(BundleModuleName.BASE_MODULE_NAME).build();
  private static final String APK_SET_ARCHIVE_EXTENSION = "apks";

  public abstract Path getBundlePath();

  public abstract Path getOutputFile();

  public abstract ImmutableSet<OptimizationDimension> getOptimizationDimensions();

  public abstract boolean getGenerateOnlyUniversalApk();

  public abstract Optional<Aapt2Command> getAapt2Command();

  public abstract Optional<SigningConfiguration> getSigningConfiguration();

  public abstract ListeningExecutorService getExecutorService();


  public static Builder builder() {
    return new AutoValue_BuildApksCommand.Builder()
        .setGenerateOnlyUniversalApk(false)
        .setOptimizationDimensions(ImmutableSet.of())
        .setExecutorService(createInternalExecutorService(DEFAULT_THREAD_POOL_SIZE));
  }

  /** Builder for the {@link BuildApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the bundle. Must have the extension ".aab". */
    public abstract Builder setBundlePath(Path bundlePath);

    /** Sets the path to where the APK Set must be generated. Must have the extension ".apks". */
    public abstract Builder setOutputFile(Path outputFile);

    /** List of config dimensions to split the APKs by. */
    @Deprecated // Use setBundleConfig() instead.
    public abstract Builder setOptimizationDimensions(
        ImmutableSet<OptimizationDimension> optimizationDimensions);

    /**
     * Sets whether a universal APK should be generated.
     *
     * <p>The default is false. If this is set to {@code true}, no other APKs will be generated.
     */
    public abstract Builder setGenerateOnlyUniversalApk(boolean universalOnly);

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
    public abstract Builder setExecutorService(ListeningExecutorService executorService);


    abstract BuildApksCommand autoBuild();

    public BuildApksCommand build() {
      BuildApksCommand command = autoBuild();
      if (!command.getOptimizationDimensions().isEmpty() && command.getGenerateOnlyUniversalApk()) {
        throw new ValidationException(
            "Cannot generate universal APK and specify optimization dimensions at the same time.");
      }

      if (!APK_SET_ARCHIVE_EXTENSION.equals(MoreFiles.getFileExtension(command.getOutputFile()))) {
        throw ValidationException.builder()
            .withMessage(
                "Flag --output should be the path where to generate the APK Set. "
                    + "Its extension must be '.apks'.")
            .build();
      }

      return command;
    }
  }

  public static BuildApksCommand fromFlags(ParsedFlags flags) {
    return fromFlags(flags, System.out);
  }

  static BuildApksCommand fromFlags(ParsedFlags flags, PrintStream out) {
    BuildApksCommand.Builder buildApksCommand =
        BuildApksCommand.builder()
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags));

    // Optional arguments.
    AAPT2_PATH_FLAG
        .getValue(flags)
        .ifPresent(
            aapt2Path ->
                buildApksCommand.setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path)));
    GENERATE_UNIVERSAL_APK_FLAG
        .getValue(flags)
        .ifPresent(buildApksCommand::setGenerateOnlyUniversalApk);
    MAX_THREADS_FLAG
        .getValue(flags)
        .ifPresent(
            maxThreads ->
                buildApksCommand.setExecutorService(createInternalExecutorService(maxThreads)));
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
      out.println(
          "WARNING: The APKs won't be signed and thus not installable unless you also pass a "
              + "keystore via the flag --ks. See the command help for more information.");
    }

    flags.checkNoUnknownFlags();

    return buildApksCommand.build();
  }

  public Path execute() {
    return TempFiles.withTempDirectoryReturning(this::executeWithTempDir);
  }

  private Path executeWithTempDir(Path tempDir) {
    validateInput();

    Aapt2Command aapt2Command = getAapt2Command().orElseGet(() -> extractAapt2FromJar(tempDir));

    try (ZipFile bundleZip = new ZipFile(getBundlePath().toFile())) {
      AppBundleValidator bundleValidator = new AppBundleValidator();

      bundleValidator.validateFile(bundleZip);
      AppBundle appBundle = AppBundle.buildFromZip(bundleZip);
      bundleValidator.validate(appBundle);

      BundleConfig bundleConfig = appBundle.getBundleConfig();
      Version bundleVersion = BundleToolVersion.getVersionFromBundleConfig(bundleConfig);

      ImmutableList<BundleModule> allModules =
          ImmutableList.copyOf(appBundle.getModules().values());

      ApkSetBuilder apkSetBuilder =
          createApkSetBuilder(
              aapt2Command, getSigningConfiguration(), bundleConfig.getCompression(), tempDir);

      ApkOptimizations apkOptimizations =
          getGenerateOnlyUniversalApk()
              ? ApkOptimizations.getOptimizationsForUniversalApk()
              : new OptimizationsMerger()
                  .mergeWithDefaults(bundleConfig, getOptimizationDimensions());

      // Generate APK variants.
      ImmutableList<Variant> splitApkVariants = ImmutableList.of();
      ImmutableList<Variant> standaloneVariants = ImmutableList.of();

      boolean generateSplitApks = !getGenerateOnlyUniversalApk() && !targetsOnlyPreL(appBundle);
      boolean generateStandaloneApks = getGenerateOnlyUniversalApk() || targetsPreL(appBundle);

      if (generateSplitApks) {
        splitApkVariants =
            generateSplitApkVariants(allModules, apkSetBuilder, apkOptimizations, bundleVersion);
      }
      if (generateStandaloneApks) {
        // Note: Universal APK is a special type of standalone, with no optimization dimensions.
        ImmutableList<BundleModule> modulesForFusing =
            allModules.stream().filter(BundleModule::isIncludedInFusing).collect(toImmutableList());
        standaloneVariants =
            generateStandaloneApkVariants(
                modulesForFusing,
                appBundle.getBundleMetadata(),
                getGenerateOnlyUniversalApk(),
                tempDir,
                apkSetBuilder,
                apkOptimizations,
                bundleVersion);
      }

      // Populate alternative targeting based on targeting of all variants.
      ImmutableList<Variant> allVariantsWithTargeting =
          AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(
              splitApkVariants, standaloneVariants);

      // Finalize the output archive.
      apkSetBuilder.setTableOfContentsFile(
          BuildApksResult.newBuilder().addAllVariant(allVariantsWithTargeting).build());
      apkSetBuilder.writeTo(getOutputFile());
    } catch (IOException e) {
      throw ValidationException.builder()
          .withCause(e)
          .withMessage("Error reading zip file '%s'.", getBundlePath())
          .build();
    }


    return getOutputFile();
  }

  private ApkSetBuilder createApkSetBuilder(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfiguration,
      Compression compression,
      Path tempDir) {
    SplitApkSerializer splitApkSerializer =
        new SplitApkSerializer(aapt2Command, signingConfiguration, compression);
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(aapt2Command, signingConfiguration, compression);

    return ApkSetBuilderFactory.createApkSetBuilder(
        splitApkSerializer, standaloneApkSerializer, tempDir);
  }

  private static Aapt2Command extractAapt2FromJar(Path tempDir) {
    return new SdkToolsLocator()
        .extractAapt2(tempDir)
        .map(Aapt2Command::createFromExecutablePath)
        .orElseThrow(
            () ->
                new CommandExecutionException(
                    "Could not extract aapt2: consider updating bundletool to a more recent "
                        + "version or providing the path to aapt2 using the flag --aapt2."));
  }

  private void validateInput() {
    checkFileExistsAndReadable(getBundlePath());
    checkFileDoesNotExist(getOutputFile());
  }

  private ImmutableList<Variant> generateSplitApkVariants(
      ImmutableList<BundleModule> modules,
      ApkSetBuilder apkSetBuilder,
      ApkOptimizations apkOptimizations,
      Version bundleVersion) {
    // For now we build just a single variant with hard-coded L+ targeting.
    Variant.Builder variant = Variant.newBuilder().setTargeting(lPlusVariantTargeting());
    for (BundleModule module : modules) {
      ModuleSplitter moduleSplitter =
          new ModuleSplitter(module, apkOptimizations.getSplitDimensions(), bundleVersion);
      ImmutableList<ModuleSplit> splitApks = moduleSplitter.splitModule();

      List<ApkDescription> apkDescriptions =
          // Wait for all concurrent tasks to succeed, or any to fail.
          ConcurrencyUtils.waitForAll(
              splitApks
                  .stream()
                  .map(
                      splitApk ->
                          getExecutorService().submit(() -> apkSetBuilder.addSplitApk(splitApk)))
                  .collect(toImmutableList()));

      variant.addApkSet(
          ApkSet.newBuilder()
              .setModuleMetadata(module.getModuleMetadata())
              .addAllApkDescription(apkDescriptions)
              .build());
    }
    return ImmutableList.of(variant.build());
  }

  private ImmutableList<Variant> generateStandaloneApkVariants(
      ImmutableList<BundleModule> modules,
      BundleMetadata bundleMetadata,
      boolean isUniversalApk,
      Path tempDir,
      ApkSetBuilder apkSetBuilder,
      ApkOptimizations apkOptimizations,
      Version bundleVersion) {

    ImmutableList<ModuleSplit> standaloneApks =
        new BundleSharder(tempDir, bundleVersion)
            .shardBundle(modules, apkOptimizations.getSplitDimensions(), bundleMetadata);

    // Wait for all concurrent tasks to succeed, or any to fail.
    return ConcurrencyUtils.waitForAll(
        standaloneApks
            .stream()
            .map(
                standaloneApk ->
                    getExecutorService()
                        .submit(
                            () ->
                                writeStandaloneApkVariant(
                                    standaloneApk, isUniversalApk, apkSetBuilder)))
            .collect(toImmutableList()));
  }

  private Variant writeStandaloneApkVariant(
      ModuleSplit standaloneApk, boolean isUniversalApk, ApkSetBuilder apkSetBuilder) {

    ApkDescription apkDescription =
        isUniversalApk
            ? apkSetBuilder.addStandaloneUniversalApk(standaloneApk)
            : apkSetBuilder.addStandaloneApk(standaloneApk);

    // Each standalone APK is represented as a single variant.
    return Variant.newBuilder()
        .setTargeting(
            isUniversalApk
                ? VariantTargeting.getDefaultInstance()
                : standaloneApkVariantTargeting(standaloneApk))
        .addApkSet(
            ApkSet.newBuilder()
                .setModuleMetadata(STANDALONE_MODULE_METADATA)
                .addApkDescription(apkDescription))
        .build();
  }

  private static boolean targetsOnlyPreL(AppBundle bundle) {
    Optional<Integer> maxSdkVersion =
        bundle.getBaseModule().getAndroidManifest().getMaxSdkVersion();
    return maxSdkVersion.isPresent() && maxSdkVersion.get() < Versions.ANDROID_L_API_VERSION;
  }

  private static boolean targetsPreL(AppBundle bundle) {
    int baseMinSdkVersion = bundle.getBaseModule().getAndroidManifest().getEffectiveMinSdkVersion();
    return baseMinSdkVersion < Versions.ANDROID_L_API_VERSION;
  }

  private static VariantTargeting lPlusVariantTargeting() {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(
            SdkVersionTargeting.newBuilder()
                .addValue(
                    SdkVersion.newBuilder()
                        .setMin(Int32Value.newBuilder().setValue(Versions.ANDROID_L_API_VERSION))))
        .build();
  }

  private static VariantTargeting standaloneApkVariantTargeting(ModuleSplit standaloneApk) {
    ApkTargeting apkTargeting = standaloneApk.getApkTargeting();

    VariantTargeting.Builder variantTargeting = VariantTargeting.newBuilder();
    if (apkTargeting.hasAbiTargeting()) {
      variantTargeting.setAbiTargeting(apkTargeting.getAbiTargeting());
    }
    if (apkTargeting.hasScreenDensityTargeting()) {
      variantTargeting.setScreenDensityTargeting(apkTargeting.getScreenDensityTargeting());
    }
    // If a standalone variant was generated, then we may have also generated a splits variant with
    // some SDK targeting (splits run only on Android L+). When we later compute alternative
    // targeting across all variants, we don't allow some variants to have, and some variants not to
    // have targeting for a dimension (SDK in this case). That is why we need to set the default
    // instance of SDK targeting for the standalone variants.
    variantTargeting.setSdkVersionTargeting(
        SdkVersionTargeting.newBuilder().addValue(SdkVersion.getDefaultInstance()));

    return variantTargeting.build();
  }

  /**
   * Creates an internal executor service that uses at most the given number of threads.
   *
   * <p>The executor service will gracefully shutdown automatically.
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
                    "Generates an APK Set archive containing all possible split APKs and "
                        + "standalone APKs.")
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
                .setFlagName(AAPT2_PATH_FLAG.getName())
                .setExampleValue("path/to/aapt2")
                .setOptional(true)
                .setDescription("Path to the aapt2 binary to use.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(GENERATE_UNIVERSAL_APK_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "If set, will generate only a single universal APK. This flag is mutually "
                        + "exclusive with flag --%s.",
                    OPTIMIZE_FOR_FLAG.getName())
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
                        + "Acceptable values are '%s'. This flag is mutually exclusive with flag "
                        + "--%s.",
                    joinFlagOptions(OptimizationDimension.values()),
                    GENERATE_UNIVERSAL_APK_FLAG.getName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(KEYSTORE_FLAG.getName())
                .setExampleValue("path/to/keystore")
                .setOptional(true)
                .setDescription(
                    "Path to the keystore that should be used to sign the generated APKs. If not "
                        + "set, the APKs will not be signed. If set, the flag '%s' must also be "
                        + "set.",
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
        .build();
  }

  private static String joinFlagOptions(Enum<?>... flagOptions) {
    return Arrays.stream(flagOptions)
        .map(Enum::name)
        .map(String::toLowerCase)
        .collect(Collectors.joining("|"));
  }
}
