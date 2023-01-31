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

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.androidtools.P7ZipCommand;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.io.ApkSerializer;
import com.android.tools.build.bundletool.io.ModuleSplitSerializer;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.Bundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.DefaultSigningConfigurationProvider;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.Optional;

/** Dagger module for build-sdk-apks-for-app command. */
@Module
public abstract class BuildSdkApksForAppModule {

  @Provides
  static Aapt2Command provideAapt2Command(
      BuildSdkApksForAppCommand command, TempDirectory tempDir) {
    return command
        .getAapt2Command()
        .orElseGet(() -> CommandUtils.extractAapt2FromJar(tempDir.getPath()));
  }

  @Provides
  static BundleConfig provideBundleConfig() {
    return BundleConfig.newBuilder()
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
        .build();
  }

  @Provides
  static Version provideBundletoolVersion() {
    return BundleToolVersion.getCurrentVersion();
  }

  @Provides
  static Optional<DeviceSpec> provideDeviceSpec() {
    return Optional.empty();
  }

  @Provides
  static ApkOptimizations provideApkOptimizations() {
    return ApkOptimizations.getOptimizationsForUniversalApk();
  }

  @Provides
  static BuildApksCommand.ApkBuildMode provideApkBuildMode() {
    return ApkBuildMode.DEFAULT;
  }

  @Provides
  @BuildApksModule.ApkSigningConfigProvider
  static Optional<SigningConfigurationProvider> provideApkSigningConfigurationProvider(
      BuildSdkApksForAppCommand command, Version version) {
    return command
        .getSigningConfiguration()
        .map(signingConfig -> new DefaultSigningConfigurationProvider(signingConfig, version));
  }

  @Provides
  static ListeningExecutorService provideExecutorService(BuildSdkApksForAppCommand command) {
    return command.getExecutorService();
  }

  @Provides
  static Optional<ApkListener> provideApkListener() {
    return Optional.empty();
  }

  @Provides
  static Optional<ApkModifier> provideApkModifier() {
    return Optional.empty();
  }

  @Provides
  @BuildApksModule.VerboseLogs
  static boolean provideVerbose() {
    return false;
  }

  @Provides
  static Optional<SourceStamp> provideSourceStamp() {
    return Optional.empty();
  }

  @Provides
  static Optional<P7ZipCommand> privideP7ZipCommand() {
    return Optional.empty();
  }

  @BuildApksModule.FirstVariantNumber
  @Provides
  static Optional<Integer> provideFirstVariantNumber() {
    return Optional.empty();
  }

  @Provides
  static Bundle provideBundle(BundleModule module, BundleConfig bundleConfig) {
    return AppBundle.buildFromModules(
            ImmutableList.of(module), bundleConfig, BundleMetadata.builder().build())
        .toBuilder()
        .setPackageNameOptional(module.getAndroidManifest().getPackageName())
        .build();
  }

  @Binds
  abstract ApkSerializer apkSerializerHelper(ModuleSplitSerializer apkSerializerHelper);
}
