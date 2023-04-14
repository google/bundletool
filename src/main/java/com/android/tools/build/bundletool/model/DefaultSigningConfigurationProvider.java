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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.NO_V1_SIGNING_WHEN_POSSIBLE;

import com.android.apksig.SigningCertificateLineage;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Default {@link SigningConfigurationProvider}. */
public class DefaultSigningConfigurationProvider implements SigningConfigurationProvider {

  private final SigningConfiguration signingConfiguration;
  private final Version bundletoolVersion;

  public DefaultSigningConfigurationProvider(
      SigningConfiguration signingConfiguration, Version bundletoolVersion) {
    this.signingConfiguration = signingConfiguration;
    this.bundletoolVersion = bundletoolVersion;
  }

  @Override
  public ApksigSigningConfiguration getSigningConfiguration(ApkDescription apkDescription) {
    ApksigSigningConfiguration.Builder apksigSigningConfig =
        ApksigSigningConfiguration.builder()
            .setSignerConfigs(getSignerConfigs(apkDescription))
            .setV1SigningEnabled(shouldSignWithV1(apkDescription))
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true);
    getSigningCertificateLineage(apkDescription)
        .ifPresent(apksigSigningConfig::setSigningCertificateLineage);
    signingConfiguration
        .getRotationMinSdkVersion()
        .ifPresent(apksigSigningConfig::setRotationMinSdkVersion);
    return apksigSigningConfig.build();
  }

  @Override
  public boolean hasRestrictedV3SigningConfig() {
    return signingConfiguration.getSigningCertificateLineage().isPresent()
        && signingConfiguration.getEffectiveMinimumV3RotationApiVersion() > 1;
  }

  private ImmutableList<SignerConfig> getSignerConfigs(ApkDescription apkDescription) {
    ImmutableList.Builder<SignerConfig> signerConfigs = ImmutableList.builder();
    Optional<SignerConfig> oldestSigner = signingConfiguration.getOldestSigner();
    SignerConfig newestSigner = signingConfiguration.getSignerConfig();
    if (shouldSignWithV3Rotation(apkDescription)) {
      oldestSigner.ifPresent(signerConfigs::add);
      signerConfigs.add(newestSigner);
    } else {
      signerConfigs.add(oldestSigner.orElse(newestSigner));
    }
    return signerConfigs.build();
  }

  private boolean shouldSignWithV1(ApkDescription apkDescription) {
    return apkDescription.getMinSdkVersionFromManifest() < ANDROID_N_API_VERSION
        || !NO_V1_SIGNING_WHEN_POSSIBLE.enabledForVersion(bundletoolVersion);
  }

  private boolean shouldSignWithV3Rotation(ApkDescription apkDescription) {
    return apkDescription.getMinSdkVersionTargeting()
        >= signingConfiguration.getEffectiveMinimumV3RotationApiVersion();
  }

  private Optional<SigningCertificateLineage> getSigningCertificateLineage(
      ApkDescription apkDescription) {
    return signingConfiguration
        .getSigningCertificateLineage()
        .filter(lineage -> shouldSignWithV3Rotation(apkDescription));
  }
}
