/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMinSdk;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NO_V1_SIGNING_WHEN_POSSIBLE;
import static java.lang.Math.max;

import com.android.apksig.ApkSigner.SignerConfig;
import com.android.apksig.SigningCertificateLineage;
import com.android.tools.build.bundletool.commands.BuildApksModule.ApkSigningConfig;
import com.android.tools.build.bundletool.commands.BuildApksModule.StampSigningConfig;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import javax.inject.Inject;

class SigningConfigurationHelper {
  /** Name identifying uniquely the {@link SignerConfig}. */
  private static final String SIGNER_CONFIG_NAME = "BNDLTOOL";

  private final Optional<SigningConfiguration> signingConfig;
  private final Optional<SigningConfiguration> sourceStampSigningConfig;
  private final Version bundletoolVersion;

  @Inject
  SigningConfigurationHelper(
      @ApkSigningConfig Optional<SigningConfiguration> signingConfig,
      @StampSigningConfig Optional<SigningConfiguration> sourceStampSigningConfig,
      Version bundletoolVersion) {
    this.signingConfig = signingConfig;
    this.sourceStampSigningConfig = sourceStampSigningConfig;
    this.bundletoolVersion = bundletoolVersion;
  }

  public boolean shouldSignGeneratedApks() {
    return signingConfig.isPresent();
  }

  public ImmutableList<SignerConfig> getSignerConfigsForSplit(ModuleSplit moduleSplit) {
    if (!signingConfig.isPresent()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<SignerConfig> signerConfigs = ImmutableList.builder();
    Optional<SignerConfig> oldestSigner =
        signingConfig
            .flatMap(SigningConfiguration::getOldestSigner)
            .map(SigningConfigurationHelper::convertToApksigSignerConfig);
    SignerConfig newestSigner = convertToApksigSignerConfig(signingConfig.get().getSignerConfig());
    if (shouldSignWithV3Rotation(moduleSplit)) {
      signerConfigs.add(newestSigner);
      oldestSigner.ifPresent(signerConfigs::add);
    } else {
      signerConfigs.add(oldestSigner.orElse(newestSigner));
    }
    return signerConfigs.build();
  }

  public boolean shouldSignWithV1(ModuleSplit moduleSplit) {
    if (!signingConfig.isPresent()) {
      return false;
    }
    int minSdkVersion = moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion();
    return minSdkVersion < ANDROID_N_API_VERSION
        || !NO_V1_SIGNING_WHEN_POSSIBLE.enabledForVersion(bundletoolVersion);
  }

  public boolean shouldSignWithV2() {
    return signingConfig.isPresent();
  }

  public boolean shouldSignWithV3Rotation(ModuleSplit moduleSplit) {
    if (!signingConfig.isPresent()) {
      return false;
    }
    return getMinSdkVersionTargeting(moduleSplit)
        >= signingConfig.get().getEffectiveMinimumV3RotationApiVersion();
  }

  public Optional<SigningCertificateLineage> getSigningCertificateLineageForSplit(
      ModuleSplit moduleSplit) {
    return signingConfig
        .flatMap(SigningConfiguration::getSigningCertificateLineage)
        .filter(lineage -> shouldSignWithV3Rotation(moduleSplit));
  }

  public Optional<SignerConfig> getSourceStampSignerConfig() {
    return sourceStampSigningConfig
        .map(SigningConfiguration::getSignerConfig)
        .map(SigningConfigurationHelper::convertToApksigSignerConfig);
  }


  private static int getMinSdkVersionTargeting(ModuleSplit moduleSplit) {
    int minManifestSdkVersion = moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion();
    int minApkTargetingSdkVersion =
        getMinSdk(moduleSplit.getApkTargeting().getSdkVersionTargeting());
    return max(minManifestSdkVersion, minApkTargetingSdkVersion);
  }

  private static SignerConfig convertToApksigSignerConfig(
      com.android.tools.build.bundletool.model.SignerConfig signerConfig) {
    return new SignerConfig.Builder(
            SIGNER_CONFIG_NAME, signerConfig.getPrivateKey(), signerConfig.getCertificates())
        .build();
  }
}
