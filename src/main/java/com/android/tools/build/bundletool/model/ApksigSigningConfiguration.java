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

import com.android.apksig.SigningCertificateLineage;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/** Information required to sign an APK. */
@SuppressWarnings("Immutable") // SigningCertificateLineage is considered immutable.
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ApksigSigningConfiguration {

  /** Config of the signers to sign the APK. */
  public abstract ImmutableList<SignerConfig> getSignerConfigs();

  /** Signing certificate lineage used for key rotation with APK Signature Scheme v3. */
  public abstract Optional<SigningCertificateLineage> getSigningCertificateLineage();

  /** Whether the APK should be signed using JAR signing (aka v1 signature scheme). */
  public abstract boolean getV1SigningEnabled();

  /** Whether the APK should be signed using APK Signature Scheme v2 (aka v2 signature scheme). */
  public abstract boolean getV2SigningEnabled();

  /** Whether the APK should be signed using APK Signature Scheme v3 (aka v3 signature scheme). */
  public abstract boolean getV3SigningEnabled();

  /**
   * Minimum Android platform version (API Level) for which an APK's rotated signing key should be
   * used to produce the APK's signature. The original signing key for the APK will be used for all
   * previous platform versions.
   */
  public abstract Optional<Integer> getRotationMinSdkVersion();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_ApksigSigningConfiguration.Builder()
        .setV1SigningEnabled(true)
        .setV2SigningEnabled(true)
        .setV3SigningEnabled(true);
  }

  /** Builder of {@link ApksigSigningConfiguration} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSignerConfigs(ImmutableList<SignerConfig> signerConfigs);

    public abstract Builder setSigningCertificateLineage(
        SigningCertificateLineage signingCertificateLineage);

    public abstract Builder setV1SigningEnabled(boolean v1SigningEnabled);

    public abstract Builder setV2SigningEnabled(boolean v2SigningEnabled);

    public abstract Builder setV3SigningEnabled(boolean v3SigningEnabled);

    public abstract Builder setRotationMinSdkVersion(int rotationMinSdkVersion);

    public abstract ApksigSigningConfiguration build();
  }
}
