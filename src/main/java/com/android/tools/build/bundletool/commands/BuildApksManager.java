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

import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.Compression;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
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
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.optimizations.OptimizationsMerger;
import com.android.tools.build.bundletool.splitters.BundleSharder;
import com.android.tools.build.bundletool.splitters.ModuleSplitter;
import com.android.tools.build.bundletool.targeting.AlternativeVariantTargetingPopulator;
import com.android.tools.build.bundletool.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.utils.Versions;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
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
    Optional<DeviceSpec> deviceSpec = Optional.empty();
    if (command.getGenerateOnlyForConnectedDevice()) {
      deviceSpec = Optional.of(getDeviceSpec());
    } else if (command.getDeviceSpecPath().isPresent()) {
      deviceSpec = Optional.of(DeviceSpecParser.parseDeviceSpec(command.getDeviceSpecPath().get()));
    }

    try (ZipFile bundleZip = new ZipFile(command.getBundlePath().toFile())) {
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
              aapt2Command,
              command.getSigningConfiguration(),
              bundleConfig.getCompression(),
              tempDir);

      ApkOptimizations apkOptimizations =
          command.getGenerateOnlyUniversalApk()
              ? ApkOptimizations.getOptimizationsForUniversalApk()
              : new OptimizationsMerger()
                  .mergeWithDefaults(bundleConfig, command.getOptimizationDimensions());

      boolean generateSplitApks =
          !command.getGenerateOnlyUniversalApk() && !targetsOnlyPreL(appBundle);
      boolean generateStandaloneApks =
          command.getGenerateOnlyUniversalApk() || targetsPreL(appBundle);
      // For now, do not generate instant apks when targeting a device.
      boolean generateInstantApks = generateSplitApks && !deviceSpec.isPresent();

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

      GeneratedApks.Builder generatedApksBuilder = GeneratedApks.builder();
      if (generateSplitApks) {
        generatedApksBuilder.setSplitApks(
            generateSplitApks(
                allModules, apkOptimizations, bundleVersion, ModuleSplitter::splitModule));
      }
      if (generateInstantApks) {
        ImmutableList<BundleModule> instantModules =
            allModules.stream().filter(BundleModule::isInstantModule).collect(toImmutableList());
        generatedApksBuilder.setInstantApks(
            generateSplitApks(
                instantModules,
                apkOptimizations,
                bundleVersion,
                ModuleSplitter::splitInstantModule));
      }
      if (generateStandaloneApks) {
        // Note: Universal APK is a special type of standalone, with no optimization dimensions.
        ImmutableList<BundleModule> modulesForFusing =
            allModules.stream().filter(BundleModule::isIncludedInFusing).collect(toImmutableList());
        generatedApksBuilder.setStandaloneApks(
            generateStandaloneApks(
                modulesForFusing,
                appBundle.getBundleMetadata(),
                tempDir,
                apkOptimizations,
                bundleVersion));
      }
      // Populate alternative targeting based on variant targeting of all APKs.
      GeneratedApks generatedApks =
          AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(
              generatedApksBuilder.build());

      // Create variants and serialize APKs.
      ApkSerializerManager apkSerializerManager =
          new ApkSerializerManager(
              appBundle,
              apkSetBuilder,
              command.getExecutorService(),
              command.getApkListener().orElse(ApkListener.NO_OP),
              command.getApkModifier().orElse(ApkModifier.NO_OP),
              command.getFirstVariantNumber().orElse(0));
      ImmutableList<Variant> allVariantsWithTargeting;
      if (deviceSpec.isPresent()) {
        allVariantsWithTargeting =
            apkSerializerManager.serializeApksForDevice(generatedApks, deviceSpec.get());
      } else if (command.getGenerateOnlyUniversalApk()) {
        allVariantsWithTargeting = apkSerializerManager.serializeUniversalApk(generatedApks);
      } else {
        allVariantsWithTargeting = apkSerializerManager.serializeApks(generatedApks);
      }
      // Finalize the output archive.
      apkSetBuilder.setTableOfContentsFile(
          BuildApksResult.newBuilder()
              .addAllVariant(allVariantsWithTargeting)
              .setBundletool(
                  Bundletool.newBuilder()
                      .setVersion(BundleToolVersion.getCurrentVersion().toString()))
              .build());
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

  private DeviceSpec getDeviceSpec() {
    AdbServer adbServer = command.getAdbServer().get();
    adbServer.init(command.getAdbPath().get());

    return new DeviceAnalyzer(adbServer).getDeviceSpec(command.getDeviceId());
  }

  private ApkSetBuilder createApkSetBuilder(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfiguration,
      Compression compression,
      Path tempDir) {
    ApkPathManager apkPathmanager = new ApkPathManager();
    SplitApkSerializer splitApkSerializer =
        new SplitApkSerializer(apkPathmanager, aapt2Command, signingConfiguration, compression);
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            apkPathmanager, aapt2Command, signingConfiguration, compression);

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
    checkFileExistsAndReadable(command.getBundlePath());
    if (!command.getOverwriteOutput()) {
      checkFileDoesNotExist(command.getOutputFile());
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

  private ImmutableList<ModuleSplit> generateSplitApks(
      ImmutableList<BundleModule> modules,
      ApkOptimizations apkOptimizations,
      Version bundleVersion,
      Function<ModuleSplitter, ImmutableList<ModuleSplit>> splitter) {
    ImmutableList.Builder<ModuleSplit> builder = ImmutableList.builder();
    for (BundleModule module : modules) {
      ModuleSplitter moduleSplitter =
          new ModuleSplitter(module, apkOptimizations.getSplitDimensions(), bundleVersion);
      boolean enableNativeLibraryCompressionSplitter =
          apkOptimizations.getUncompressNativeLibraries();
      moduleSplitter.setEnableNativeLibraryCompressionSplitter(
          enableNativeLibraryCompressionSplitter);
      ImmutableList<ModuleSplit> splitApks = splitter.apply(moduleSplitter);
      builder.addAll(splitApks);
    }
    return builder.build();
  }

  private ImmutableList<ModuleSplit> generateStandaloneApks(
      ImmutableList<BundleModule> modules,
      BundleMetadata bundleMetadata,
      Path tempDir,
      ApkOptimizations apkOptimizations,
      Version bundleVersion) {

    ImmutableList<ModuleSplit> standaloneApks =
        new BundleSharder(tempDir, bundleVersion)
            .shardBundle(modules, apkOptimizations.getSplitDimensions(), bundleMetadata);

    return standaloneApks.stream()
        .map(
            moduleSplit ->
                moduleSplit
                    .toBuilder()
                    .setVariantTargeting(standaloneApkVariantTargeting(moduleSplit))
                    .setSplitType(SplitType.STANDALONE)
                    .build())
        .collect(toImmutableList());
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

  private static VariantTargeting standaloneApkVariantTargeting(ModuleSplit standaloneApk) {
    ApkTargeting apkTargeting = standaloneApk.getApkTargeting();

    VariantTargeting.Builder variantTargeting = VariantTargeting.newBuilder();
    if (apkTargeting.hasAbiTargeting()) {
      variantTargeting.setAbiTargeting(apkTargeting.getAbiTargeting());
    }
    if (apkTargeting.hasScreenDensityTargeting()) {
      variantTargeting.setScreenDensityTargeting(apkTargeting.getScreenDensityTargeting());
    }
    variantTargeting.setSdkVersionTargeting(sdkVersionTargeting(standaloneApk));

    return variantTargeting.build();
  }

  private static SdkVersionTargeting sdkVersionTargeting(ModuleSplit moduleSplit) {
    return SdkVersionTargeting.newBuilder()
        .addValue(sdkVersionFrom(moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion()))
        .build();
  }
}
