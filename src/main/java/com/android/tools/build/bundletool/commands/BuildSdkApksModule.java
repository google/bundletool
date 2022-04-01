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

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.androidtools.P7ZipCommand;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.io.ApkSerializer;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.io.ZipFlingerApkSerializer;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.Bundle;
import com.android.tools.build.bundletool.model.DefaultSigningConfigurationProvider;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SigningConfigurationProvider;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.google.common.util.concurrent.ListeningExecutorService;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;
import java.util.Optional;

/** Dagger module for the build-sdk-apks command. */
@Module
public abstract class BuildSdkApksModule {

  @Provides
  static Aapt2Command provideAapt2Command(BuildSdkApksCommand command, TempDirectory tempDir) {
    return command
        .getAapt2Command()
        .orElseGet(() -> CommandUtils.extractAapt2FromJar(tempDir.getPath()));
  }

  @Provides
  static Version provideBundletoolVersion(BundleConfig bundleConfig) {
    return Version.of(bundleConfig.getBundletool().getVersion());
  }

  @Provides
  static BundleConfig provideBundleConfig(SdkBundle sdkBundle) {
    return sdkBundle.getBundleConfig();
  }

  @Binds
  abstract Bundle bundle(SdkBundle bundle);

  @BindsOptionalOf
  @BuildApksModule.StampSigningConfig
  abstract SigningConfiguration bindOptionalSigningConfiguration();

  @Provides
  @BuildApksModule.ApkSigningConfigProvider
  static Optional<SigningConfigurationProvider> provideApkSigningConfigurationProvider(
      BuildSdkApksCommand command, Version version) {
    return command
        .getSigningConfiguration()
        .map(signingConfig -> new DefaultSigningConfigurationProvider(signingConfig, version));
  }

  @BindsOptionalOf
  abstract DeviceSpec bindOptionalDeviceSpec();

  @Provides
  static ListeningExecutorService provideExecutorService(BuildSdkApksCommand command) {
    return command.getExecutorService();
  }

  @BindsOptionalOf
  abstract ApkListener bindOptionalApkListener();

  @BindsOptionalOf
  abstract ApkModifier bindOptionalApkModifier();

  @BindsOptionalOf
  abstract P7ZipCommand bindOptionalP7ZipCommand();

  @Provides
  static ApkOptimizations provideApkOptimizations() {
    return ApkOptimizations.getOptimizationsForUniversalApk();
  }

  @Provides
  static ApkBuildMode provideApkBuildMode() {
    return ApkBuildMode.DEFAULT;
  }

  @BuildApksModule.FirstVariantNumber
  @Provides
  static Optional<Integer> provideFirstVariantNumber() {
    return Optional.of(0);
  }

  @Provides
  @BuildApksModule.VerboseLogs
  static boolean provideVerbose(BuildSdkApksCommand command) {
    return command.getVerbose();
  }

  @Binds
  abstract ApkSerializer apkSerializerHelper(ZipFlingerApkSerializer apkSerializerHelper);
}
