/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.ModuleDependenciesUtils.getModulesIncludingDependencies;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.device.IncompatibleDeviceException;
import com.android.tools.build.bundletool.io.ApkPathManager;
import com.android.tools.build.bundletool.io.ApkSerializerManager;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.io.SplitApkSerializer;
import com.android.tools.build.bundletool.io.StandaloneApkSerializer;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.GeneratedAssetSlices;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.targeting.AlternativeVariantTargetingPopulator;
import com.android.tools.build.bundletool.model.utils.SplitsXmlInjector;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.optimizations.OptimizationsMerger;
import com.android.tools.build.bundletool.splitters.ApkGenerationConfiguration;
import com.android.tools.build.bundletool.splitters.AssetSlicesGenerator;
import com.android.tools.build.bundletool.splitters.ResourceAnalyzer;
import com.android.tools.build.bundletool.splitters.ShardedApksGenerator;
import com.android.tools.build.bundletool.splitters.SplitApksGenerator;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/** Executes the "build-apks" command. */
final class BuildApksManager {

  private static final Logger logger = Logger.getLogger(BuildApksManager.class.getName());

  private final BuildApksCommand command;
  private final Aapt2Command aapt2Command;
  private final Path tempDir;

  BuildApksManager(BuildApksCommand command, Aapt2Command aapt2Command, Path tempDir) {
    this.command = command;
    this.aapt2Command = aapt2Command;
    this.tempDir = tempDir;
  }

  public Path execute() {
    validateInput();

    Path outputDirectory =
        command.getCreateApkSetArchive()
            ? command.getOutputFile().getParent()
            : command.getOutputFile();
    if (outputDirectory != null && Files.notExists(outputDirectory)) {
      logger.info("Output directory '" + outputDirectory + "' does not exist, creating it.");
      FileUtils.createDirectories(outputDirectory);
    }

    // Fail fast with ADB before generating any APKs.
    Optional<DeviceSpec> deviceSpec = command.getDeviceSpec();
    if (command.getGenerateOnlyForConnectedDevice()) {
      deviceSpec = Optional.of(getDeviceSpecFromConnectedDevice());
    }

    try (ZipFile bundleZip = new ZipFile(command.getBundlePath().toFile())) {
      executeWithZip(bundleZip, deviceSpec);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "An error occurred when processing the bundle '%s'.", command.getBundlePath()),
          e);
    } finally {
      if (command.isExecutorServiceCreatedByBundleTool()) {
        command.getExecutorService().shutdown();
      }
    }

    return command.getOutputFile();
  }

  private void executeWithZip(ZipFile bundleZip, Optional<DeviceSpec> deviceSpec)
      throws IOException {
    AppBundleValidator bundleValidator = new AppBundleValidator();

    bundleValidator.validateFile(bundleZip);
    AppBundle appBundle = AppBundle.buildFromZip(bundleZip);
    bundleValidator.validate(appBundle);

    if (appBundle.has32BitRenderscriptCode()) {
      printWarning(
          "App Bundle contains 32-bit RenderScript bitcode file (.bc) which disables 64-bit "
              + "support in Android. 64-bit native libraries won't be included in generated "
              + "APKs.");
    }

    ImmutableSet<BundleModule> requestedModules =
        command.getModules().isEmpty()
            ? ImmutableSet.of()
            : getModulesIncludingDependencies(
                appBundle, getBundleModules(appBundle, command.getModules()));

    BundleConfig bundleConfig = appBundle.getBundleConfig();
    Version bundleVersion = BundleToolVersion.getVersionFromBundleConfig(bundleConfig);

    GeneratedApks.Builder generatedApksBuilder = GeneratedApks.builder();
    GeneratedAssetSlices.Builder generatedAssetSlices = GeneratedAssetSlices.builder();

    ApksToGenerate apksToGenerate =
        new ApksToGenerate(appBundle, command.getApkBuildMode(), deviceSpec);

    // Split APKs
    if (apksToGenerate.generateSplitApks()) {
      generatedApksBuilder.setSplitApks(generateSplitApks(appBundle));
    }

    // Instant APKs
    if (apksToGenerate.generateInstantApks()) {
      generatedApksBuilder.setInstantApks(generateInstantApks(appBundle));
    }

    // Standalone APKs
    if (apksToGenerate.generateStandaloneApks()) {
      generatedApksBuilder.setStandaloneApks(generateStandaloneApks(tempDir, appBundle));
    }

    // Universal APK
    if (apksToGenerate.generateUniversalApk()) {
      // Note: Universal APK is a special type of standalone, with no optimization dimensions.
      ImmutableList<BundleModule> modulesToFuse =
          requestedModules.isEmpty()
              ? modulesToFuse(appBundle.getFeatureModules().values().asList())
              : requestedModules.asList();
      generatedApksBuilder.setStandaloneApks(
          new ShardedApksGenerator(tempDir, bundleVersion)
              .generateSplits(
                  modulesToFuse,
                  appBundle.getBundleMetadata(),
                  ApkOptimizations.getOptimizationsForUniversalApk()));
    }

    // System APKs
    if (apksToGenerate.generateSystemApks()) {
      generatedApksBuilder.setSystemApks(
          generateSystemApks(appBundle, deviceSpec, requestedModules));
    }

    // Asset Slices
    if (apksToGenerate.generateAssetSlices()) {
      generatedAssetSlices.setAssetSlices(generateAssetSlices(appBundle));
    }

    // Populate alternative targeting based on variant targeting of all APKs.
    GeneratedApks generatedApks =
        AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(
            generatedApksBuilder.build(),
            appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion());

    SplitsXmlInjector splitsXmlInjector = new SplitsXmlInjector();
    generatedApks = splitsXmlInjector.process(generatedApks);

    if (deviceSpec.isPresent()) {
      // It is easier to fully check device compatibility once the splits have been generated (in
      // memory). Note that no costly I/O happened up until this point, so it's not too late for
      // this check.
      checkDeviceCompatibilityWithBundle(generatedApks, deviceSpec.get());
    }

    ApkSetBuilder apkSetBuilder =
        createApkSetBuilder(
            aapt2Command,
            command.getSigningConfiguration(),
            bundleVersion,
            bundleConfig.getCompression(),
            tempDir);

    // Create variants and serialize APKs.
    ApkSerializerManager apkSerializerManager =
        new ApkSerializerManager(
            appBundle,
            apkSetBuilder,
            command.getExecutorService(),
            command.getApkListener().orElse(ApkListener.NO_OP),
            command.getApkModifier().orElse(ApkModifier.NO_OP),
            command.getFirstVariantNumber().orElse(0));
    apkSerializerManager.populateApkSetBuilder(
        generatedApks, generatedAssetSlices.build(), command.getApkBuildMode(), deviceSpec);

    if (command.getOverwriteOutput()) {
      Files.deleteIfExists(command.getOutputFile());
    }
    apkSetBuilder.writeTo(command.getOutputFile());
  }

  private ImmutableList<ModuleSplit> generateStandaloneApks(Path tempDir, AppBundle appBundle) {
    ImmutableList<BundleModule> allFeatureModules = appBundle.getFeatureModules().values().asList();
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ShardedApksGenerator shardedApksGenerator =
        new ShardedApksGenerator(
            tempDir,
            bundleVersion,
            /* generate64BitShards= */ !appBundle.has32BitRenderscriptCode());
    return appBundle.isApex()
        ? shardedApksGenerator.generateApexSplits(modulesToFuse(allFeatureModules))
        : shardedApksGenerator.generateSplits(
            modulesToFuse(allFeatureModules),
            appBundle.getBundleMetadata(),
            getApkOptimizations(appBundle.getBundleConfig()));
  }

  private ImmutableList<ModuleSplit> generateAssetSlices(AppBundle appBundle) {
    ApkGenerationConfiguration assetSlicesGenerationConfiguration =
        getAssetSliceGenerationConfiguration();
    AssetSlicesGenerator assetSlicesGenerator =
        new AssetSlicesGenerator(appBundle, assetSlicesGenerationConfiguration);
    return assetSlicesGenerator.generateAssetSlices();
  }

  private ImmutableList<ModuleSplit> generateInstantApks(AppBundle appBundle) {
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ImmutableList<BundleModule> allFeatureModules = appBundle.getFeatureModules().values().asList();
    ImmutableList<BundleModule> instantModules =
        allFeatureModules.stream().filter(BundleModule::isInstantModule).collect(toImmutableList());
    ApkGenerationConfiguration instantApkGenerationConfiguration =
        getCommonSplitApkGenerationConfiguration(appBundle)
            .setForInstantAppVariants(true)
            // We can't enable this splitter for instant APKs, as currently they
            // only support one variant.
            .setEnableDexCompressionSplitter(false)
            .build();
    return new SplitApksGenerator(instantModules, bundleVersion, instantApkGenerationConfiguration)
        .generateSplits();
  }

  private ImmutableList<ModuleSplit> generateSplitApks(AppBundle appBundle) throws IOException {
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        getCommonSplitApkGenerationConfiguration(appBundle);
    if (!bundleVersion.isOlderThan(Version.of("0.8.1"))) {
      // Make sure that resources reachable from the manifest of the base module will be
      // represented in the master split (by at least one config). This prevents the app
      // from crashing too soon (before reaching Application#onCreate), in case when only
      // the base master split is installed.
      apkGenerationConfiguration.setBaseManifestReachableResources(
          new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest());
    }

    ImmutableList<BundleModule> featureModules = appBundle.getFeatureModules().values().asList();
    return new SplitApksGenerator(featureModules, bundleVersion, apkGenerationConfiguration.build())
        .generateSplits();
  }

  private ImmutableList<ModuleSplit> generateSystemApks(
      AppBundle appBundle,
      Optional<DeviceSpec> deviceSpec,
      ImmutableSet<BundleModule> requestedModules) {
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ImmutableList<BundleModule> featureModules = appBundle.getFeatureModules().values().asList();
    ImmutableList<BundleModule> modulesToFuse =
        requestedModules.isEmpty() ? modulesToFuse(featureModules) : requestedModules.asList();
    return new ShardedApksGenerator(
            tempDir,
            bundleVersion,
            /* generate64BitShards= */ !appBundle.has32BitRenderscriptCode())
        .generateSystemSplits(
            /* modules= */ featureModules,
            /* modulesToFuse= */ modulesToFuse.stream()
                .map(BundleModule::getName)
                .collect(toImmutableSet()),
            appBundle.getBundleMetadata(),
            getApkOptimizations(appBundle.getBundleConfig()),
            deviceSpec);
  }

  private static void checkDeviceCompatibilityWithBundle(
      GeneratedApks generatedApks, DeviceSpec deviceSpec) {
    ApkMatcher apkMatcher = new ApkMatcher(deviceSpec);
    generatedApks.getAllApksStream().forEach(apkMatcher::checkCompatibleWithApkTargeting);
  }

  private void printWarning(String message) {
    command.getOutputPrintStream().ifPresent(out -> out.println("WARNING: " + message));
  }

  private DeviceSpec getDeviceSpecFromConnectedDevice() {
    AdbServer adbServer = command.getAdbServer().get();
    adbServer.init(command.getAdbPath().get());

    return new DeviceAnalyzer(adbServer).getDeviceSpec(command.getDeviceId());
  }

  private ApkSetBuilder createApkSetBuilder(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfiguration,
      Version bundleVersion,
      Compression compression,
      Path tempDir) {
    ApkPathManager apkPathmanager = new ApkPathManager();
    SplitApkSerializer splitApkSerializer =
        new SplitApkSerializer(
            apkPathmanager, aapt2Command, signingConfiguration, bundleVersion, compression);
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            apkPathmanager, aapt2Command, signingConfiguration, bundleVersion, compression);

    if (!command.getCreateApkSetArchive()) {
      return ApkSetBuilderFactory.createApkSetWithoutArchiveBuilder(
          splitApkSerializer, standaloneApkSerializer, command.getOutputFile());
    }
    return ApkSetBuilderFactory.createApkSetBuilder(
        splitApkSerializer, standaloneApkSerializer, tempDir);
  }

  private ApkGenerationConfiguration.Builder getCommonSplitApkGenerationConfiguration(
      AppBundle appBundle) {
    BundleConfig bundleConfig = appBundle.getBundleConfig();
    Version bundleToolVersion = Version.of(bundleConfig.getBundletool().getVersion());

    ApkOptimizations apkOptimizations = getApkOptimizations(bundleConfig);

    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setOptimizationDimensions(apkOptimizations.getSplitDimensions());

    boolean enableNativeLibraryCompressionSplitter =
        apkOptimizations.getUncompressNativeLibraries();
    apkGenerationConfiguration.setEnableNativeLibraryCompressionSplitter(
        enableNativeLibraryCompressionSplitter);

    apkGenerationConfiguration.setInstallableOnExternalStorage(
        appBundle
            .getBaseModule()
            .getAndroidManifest()
            .getInstallLocationValue()
            .map(
                installLocation ->
                    installLocation.equals("auto") || installLocation.equals("preferExternal"))
            .orElse(false));

    if (appBundle.has32BitRenderscriptCode()) {
      apkGenerationConfiguration.setInclude64BitLibs(false);
    }

    apkGenerationConfiguration.setMasterPinnedResources(appBundle.getMasterPinnedResources());

    return apkGenerationConfiguration;
  }

  private ApkGenerationConfiguration getAssetSliceGenerationConfiguration() {
    ApkOptimizations apkOptimizations = ApkOptimizations.getOptimizationsForAssetSlices();

    return ApkGenerationConfiguration.builder()
        .setOptimizationDimensions(apkOptimizations.getSplitDimensions())
        .build();
  }

  private static ImmutableList<BundleModule> modulesToFuse(ImmutableList<BundleModule> modules) {
    return modules.stream().filter(BundleModule::isIncludedInFusing).collect(toImmutableList());
  }

  private ApkOptimizations getApkOptimizations(BundleConfig bundleConfig) {
    return new OptimizationsMerger()
        .mergeWithDefaults(bundleConfig, command.getOptimizationDimensions());
  }

  private void validateInput() {
    checkFileExistsAndReadable(command.getBundlePath());

    if (command.getCreateApkSetArchive()) {
      if (!command.getOverwriteOutput()) {
        checkFileDoesNotExist(command.getOutputFile());
      }
    }

    if (command.getGenerateOnlyForConnectedDevice()) {
      checkArgument(
          command.getAdbServer().isPresent(),
          "Property 'adbServer' is required when 'generateOnlyForConnectedDevice' is true.");
      checkArgument(
          command.getAdbPath().isPresent(),
          "Property 'adbPath' is required when 'generateOnlyForConnectedDevice' is true.");
      checkFileExistsAndExecutable(command.getAdbPath().get());
    }
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

  private static ImmutableList<BundleModule> getBundleModules(
      AppBundle appBundle, ImmutableSet<String> moduleNames) {
    return moduleNames.stream()
        .map(BundleModuleName::create)
        .map(appBundle::getModule)
        .collect(toImmutableList());
  }

  private static class ApksToGenerate {
    private final AppBundle appBundle;
    private final ApkBuildMode apkBuildMode;
    private final Optional<DeviceSpec> deviceSpec;

    private ApksToGenerate(
        AppBundle appBundle, ApkBuildMode apkBuildMode, Optional<DeviceSpec> deviceSpec) {
      this.appBundle = appBundle;
      this.apkBuildMode = apkBuildMode;
      this.deviceSpec = deviceSpec;
      validate();
    }

    private void validate() {
      if (appBundle.isApex() && apkBuildMode.equals(ApkBuildMode.UNIVERSAL)) {
        throw new CommandExecutionException("APEX bundles do not support universal apks.");
      }

      if (deviceSpec.isPresent()) {
        int deviceSdk = deviceSpec.get().getSdkVersion();
        Optional<Integer> appMaxSdk =
            appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion();

        if ((apkBuildMode.equals(ApkBuildMode.DEFAULT)
            || apkBuildMode.equals(ApkBuildMode.PERSISTENT))) {
          if (deviceSdk >= Versions.ANDROID_L_API_VERSION) {
            if (!generateSplitApks()) {
              throw new IncompatibleDeviceException(
                  "App Bundle targets pre-L devices, but the device has SDK version higher "
                      + "or equal to L.");
            }
          } else {
            if (!generateStandaloneApks()) {
              throw new IncompatibleDeviceException(
                  "App Bundle targets L+ devices, but the device has SDK version lower than L.");
            }
          }
        }

        if (appMaxSdk.isPresent() && deviceSdk > appMaxSdk.get()) {
          throw new IncompatibleDeviceException(
              "Max SDK version of the App Bundle is lower than SDK version of the device");
        }
      }

      checkState(
          generateStandaloneApks()
              || generateSplitApks()
              || generateInstantApks()
              || generateUniversalApk()
              || generateSystemApks(),
          "No APKs to generate.");
    }

    public boolean generateSplitApks() {
      if (appBundle.isApex()) {
        return false;
      }
      if (!apkBuildMode.equals(ApkBuildMode.DEFAULT)
          && !apkBuildMode.equals(ApkBuildMode.PERSISTENT)) {
        return false;
      }
      return !targetsOnlyPreL(appBundle)
          && deviceSpec
              .map(spec -> spec.getSdkVersion() >= Versions.ANDROID_L_API_VERSION)
              .orElse(true);
    }

    public boolean generateStandaloneApks() {
      if (!apkBuildMode.equals(ApkBuildMode.DEFAULT)
          && !apkBuildMode.equals(ApkBuildMode.PERSISTENT)) {
        return false;
      }
      if (appBundle.isApex()) {
        return true;
      }
      return targetsPreL(appBundle)
          && deviceSpec
              .map(spec -> spec.getSdkVersion() < Versions.ANDROID_L_API_VERSION)
              .orElse(true);
    }

    public boolean generateInstantApks() {
      if (appBundle.isApex()) {
        return false;
      }
      return apkBuildMode.equals(ApkBuildMode.DEFAULT) || apkBuildMode.equals(ApkBuildMode.INSTANT);
    }

    public boolean generateUniversalApk() {
      if (appBundle.isApex()) {
        return false;
      }
      return apkBuildMode.equals(ApkBuildMode.UNIVERSAL);
    }

    public boolean generateSystemApks() {
      if (appBundle.isApex()) {
        return false;
      }
      return apkBuildMode.isAnySystemMode();
    }

    public boolean generateAssetSlices() {
      if (appBundle.isApex()) {
        return false;
      }
      return apkBuildMode.equals(ApkBuildMode.DEFAULT)
          || apkBuildMode.equals(ApkBuildMode.INSTANT)
          || apkBuildMode.equals(ApkBuildMode.PERSISTENT);
    }
  }
}
