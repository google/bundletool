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

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
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
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.GeneratedAssetSlices;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.targeting.AlternativeVariantTargetingPopulator;
import com.android.tools.build.bundletool.model.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.model.utils.SplitsXmlInjector;
import com.android.tools.build.bundletool.model.utils.Versions;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipFile;

/** Executes the "build-apks" command. */
final class BuildApksManager {

  private final BuildApksCommand command;

  BuildApksManager(BuildApksCommand command) {
    this.command = command;
  }

  public Path execute(Path tempDir) {
    validateInput();

    Aapt2Command aapt2Command =
        command.getAapt2Command().orElseGet(() -> extractAapt2FromJar(tempDir));

    // Fail fast with ADB before generating any APKs.
    Optional<DeviceSpec> deviceSpec = command.getDeviceSpec();
    if (command.getGenerateOnlyForConnectedDevice()) {
      deviceSpec = Optional.of(getDeviceSpecFromConnectedDevice());
    }

    try (ZipFile bundleZip = new ZipFile(command.getBundlePath().toFile())) {
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

      BundleConfig bundleConfig = appBundle.getBundleConfig();
      Version bundleVersion = BundleToolVersion.getVersionFromBundleConfig(bundleConfig);

      ImmutableList<BundleModule> allFeatureModules =
          ImmutableList.copyOf(appBundle.getFeatureModules().values());

      ImmutableList<BundleModule> allAssetModules =
          ImmutableList.copyOf(appBundle.getAssetModules().values());

      GeneratedApks.Builder generatedApksBuilder = GeneratedApks.builder();
      GeneratedAssetSlices.Builder generatedAssetSlicesBuilder = GeneratedAssetSlices.builder();
      boolean isApexBundle = appBundle.isApex();
      switch (command.getApkBuildMode()) {
        case DEFAULT:
          boolean generateSplitApks = !targetsOnlyPreL(appBundle) && !isApexBundle;
          boolean generateStandaloneApks = targetsPreL(appBundle) || isApexBundle;

          if (deviceSpec.isPresent()) {
            if (deviceSpec.get().getSdkVersion() >= Versions.ANDROID_L_API_VERSION) {
              generateStandaloneApks = false;
              if (!generateSplitApks) {
                throw new CommandExecutionException(
                    "App Bundle targets pre-L devices, but the device has SDK version higher "
                        + "or equal to L.");
              }
            } else {
              generateSplitApks = false;
              if (!generateStandaloneApks) {
                throw new CommandExecutionException(
                    "App Bundle targets L+ devices, but the device has SDK version lower than L.");
              }
            }
          }

          if (generateSplitApks) {
            ApkGenerationConfiguration commonApkGenerationConfiguration =
                getCommonSplitApkGenerationConfiguration(appBundle, bundleConfig, bundleVersion);

            ApkGenerationConfiguration.Builder splitApkGenerationConfiguration =
                commonApkGenerationConfiguration.toBuilder();
            if (!bundleVersion.isOlderThan(Version.of("0.8.1"))) {
              // Make sure that resources reachable from the manifest of the base module will be
              // represented in the master split (by at least one config). This prevents the app
              // from crashing too soon (before reaching Application#onCreate), in case when only
              // the base master split is installed.
              splitApkGenerationConfiguration.setBaseManifestReachableResources(
                  new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest());
            }
            generatedApksBuilder.setSplitApks(
                new SplitApksGenerator(
                        allFeatureModules, bundleVersion, splitApkGenerationConfiguration.build())
                    .generateSplits());

            // Generate instant splits for any instant compatible feature modules.
            ImmutableList<BundleModule> instantModules =
                allFeatureModules.stream()
                    .filter(BundleModule::isInstantModule)
                    .collect(toImmutableList());
            ApkGenerationConfiguration instantApkGenerationConfiguration =
                commonApkGenerationConfiguration
                    .toBuilder()
                    .setForInstantAppVariants(true)
                    // We can't enable this splitter for instant APKs, as currently they
                    // only support one variant.
                    .setEnableDexCompressionSplitter(false)
                    .build();
            generatedApksBuilder.setInstantApks(
                new SplitApksGenerator(
                        instantModules, bundleVersion, instantApkGenerationConfiguration)
                    .generateSplits());
            ApkGenerationConfiguration assetSlicesGenerationConfiguration =
                getAssetSliceGenerationConfiguration();
            AssetSlicesGenerator assetSlicesGenerator =
                new AssetSlicesGenerator(allAssetModules, assetSlicesGenerationConfiguration);
            generatedAssetSlicesBuilder.setAssetSlices(assetSlicesGenerator.generateAssetSlices());
          }
          if (generateStandaloneApks) {
            ShardedApksGenerator shardedApksGenerator =
                new ShardedApksGenerator(
                    tempDir,
                    bundleVersion,
                    /* generate64BitShards= */ !appBundle.has32BitRenderscriptCode());
            generatedApksBuilder.setStandaloneApks(
                isApexBundle
                    ? shardedApksGenerator.generateApexSplits(modulesToFuse(allFeatureModules))
                    : shardedApksGenerator.generateSplits(
                        modulesToFuse(allFeatureModules),
                        appBundle.getBundleMetadata(),
                        getApkOptimizations(bundleConfig)));
          }
          break;
        case UNIVERSAL:
          if (isApexBundle) {
            throw new CommandExecutionException("APEX bundles do not support universal apks.");
          }
          // Note: Universal APK is a special type of standalone, with no optimization dimensions.
          generatedApksBuilder.setStandaloneApks(
              new ShardedApksGenerator(tempDir, bundleVersion)
                  .generateSplits(
                      modulesToFuse(allFeatureModules),
                      appBundle.getBundleMetadata(),
                      ApkOptimizations.getOptimizationsForUniversalApk()));
          break;
        case SYSTEM_COMPRESSED:
        case SYSTEM:
          // Generate system APKs.
          generatedApksBuilder.setSystemApks(
              new ShardedApksGenerator(
                      tempDir,
                      bundleVersion,
                      /* generate64BitShards= */ !appBundle.has32BitRenderscriptCode())
                  .generateSystemSplits(
                      modulesToFuse(allFeatureModules),
                      appBundle.getBundleMetadata(),
                      getApkOptimizations(bundleConfig),
                      deviceSpec));
          break;
      }

      // Populate alternative targeting based on variant targeting of all APKs.
      GeneratedApks generatedApks =
          AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(
              generatedApksBuilder.build(),
              appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion());

      SplitsXmlInjector splitsXmlInjector = new SplitsXmlInjector();
      generatedApks = splitsXmlInjector.process(generatedApks);

      GeneratedAssetSlices generatedAssetSlices = generatedAssetSlicesBuilder.build();

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
          generatedApks, generatedAssetSlices, command.getApkBuildMode(), deviceSpec);

      if (command.getOverwriteOutput()) {
        Files.deleteIfExists(command.getOutputFile());
      }
      apkSetBuilder.writeTo(command.getOutputFile());
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

  private ApkGenerationConfiguration getCommonSplitApkGenerationConfiguration(
      AppBundle appBundle, BundleConfig bundleConfig, Version bundleToolVersion) {

    ApkOptimizations apkOptimizations = getApkOptimizations(bundleConfig);

    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setOptimizationDimensions(apkOptimizations.getSplitDimensions());

    boolean enableNativeLibraryCompressionSplitter =
        apkOptimizations.getUncompressNativeLibraries();
    apkGenerationConfiguration.setEnableNativeLibraryCompressionSplitter(
        enableNativeLibraryCompressionSplitter);

    if (appBundle.has32BitRenderscriptCode()) {
      apkGenerationConfiguration.setInclude64BitLibs(false);
    }

    apkGenerationConfiguration.setMasterPinnedResources(appBundle.getMasterPinnedResources());

    return apkGenerationConfiguration.build();
  }

  private ApkGenerationConfiguration getAssetSliceGenerationConfiguration() {
    ApkOptimizations apkOptimizations = ApkOptimizations.getOptimizationsForAssetSlices();

    return ApkGenerationConfiguration.builder()
        .setOptimizationDimensions(apkOptimizations.getSplitDimensions())
        .build();
  }

  private ImmutableList<BundleModule> modulesToFuse(ImmutableList<BundleModule> modules) {
    return modules.stream().filter(BundleModule::isIncludedInFusing).collect(toImmutableList());
  }

  private ApkOptimizations getApkOptimizations(BundleConfig bundleConfig) {
    return new OptimizationsMerger()
        .mergeWithDefaults(bundleConfig, command.getOptimizationDimensions());
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
    checkFileExistsAndReadable(command.getBundlePath());
    if (command.getCreateApkSetArchive()) {
      if (!command.getOverwriteOutput()) {
        checkFileDoesNotExist(command.getOutputFile());
      }
    } else {
      checkDirectoryExists(command.getOutputFile());
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
}
