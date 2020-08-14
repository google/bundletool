/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.optimizations.OptimizationsMerger;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import java.util.Optional;
import javax.inject.Qualifier;

/** Dagger module for the build-apks command. */
@Module(includes = {BundleConfigModule.class, BundletoolModule.class, AppBundleModule.class})
public final class BuildApksModule {

  @CommandScoped
  @Provides
  @ApkSigningConfig
  static Optional<SigningConfiguration> provideApkSigningConfiguration(BuildApksCommand command) {
    return command.getSigningConfiguration();
  }

  @CommandScoped
  @Provides
  @StampSigningConfig
  static Optional<SigningConfiguration> provideStampSigningConfiguration(BuildApksCommand command) {
    return command.getSourceStamp().map(SourceStamp::getSigningConfiguration);
  }

  @CommandScoped
  @Provides
  static Optional<SourceStamp> provideStampSource(BuildApksCommand command) {
    return command.getSourceStamp();
  }

  @CommandScoped
  @Provides
  static ListeningExecutorService provideExecutorService(BuildApksCommand command) {
    return command.getExecutorService();
  }

  @CommandScoped
  @Provides
  static Optional<ApkListener> provideApkListener(BuildApksCommand command) {
    return command.getApkListener();
  }

  @CommandScoped
  @Provides
  static Optional<ApkModifier> provideApkModifier(BuildApksCommand command) {
    return command.getApkModifier();
  }

  @CommandScoped
  @Provides
  static ApkOptimizations provideApkOptimizations(
      BundleConfig bundleConfig,
      BuildApksCommand command,
      OptimizationsMerger optimizationsMerger) {
    return optimizationsMerger.mergeWithDefaults(bundleConfig, command.getOptimizationDimensions());
  }

  @CommandScoped
  @Provides
  static ApkBuildMode provideApkBuildMode(BuildApksCommand command) {
    return command.getApkBuildMode();
  }

  @CommandScoped
  @FirstVariantNumber
  @Provides
  static Optional<Integer> provideFirstVariantNumber(BuildApksCommand command) {
    return command.getFirstVariantNumber();
  }

  @CommandScoped
  @Provides
  static Optional<DeviceSpec> provideDeviceSpec(BuildApksCommand command) {
    if (command.getGenerateOnlyForConnectedDevice()) {
      AdbServer adbServer = command.getAdbServer().get();
      adbServer.init(command.getAdbPath().get());

      DeviceSpec connectedDeviceSpec =
          new DeviceAnalyzer(adbServer).getDeviceSpec(command.getDeviceId());
      return Optional.of(connectedDeviceSpec);
    }
    return command.getDeviceSpec();
  }

  @CommandScoped
  @Provides
  @VerboseLogs
  static boolean provideVerbose(BuildApksCommand command) {
    return command.getVerbose();
  }

  /**
   * Qualifying annotation of an {@code Optional<Integer>} for the first variant number to use when
   * numbering the generated variants.
   */
  @Qualifier
  @Retention(RUNTIME)
  public @interface FirstVariantNumber {}

  /** Qualifying annotation of a {@code boolean} on whether to be verbose with logs. */
  @Qualifier
  @Retention(RUNTIME)
  public @interface VerboseLogs {}

  /** Qualifying annotation of a {@code SigningConfiguration} for the APK signing configuration. */
  @Qualifier
  @Retention(RUNTIME)
  public @interface ApkSigningConfig {}

  /**
   * Qualifying annotation of a {@code SigningConfiguration} for the Stamp signing configuration.
   */
  @Qualifier
  @Retention(RUNTIME)
  public @interface StampSigningConfig {}

  private BuildApksModule() {}
}
