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
package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkState;

import com.android.apksig.SigningCertificateLineage;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;

/** Information required to sign the generated APKs. */
@SuppressWarnings("Immutable") // PrivateKey and X509Certificate are considered immutable.
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class SigningConfiguration {

  /**
   * Config of the signer to sign the generated APKs.
   *
   * <p>If {@link getSigningCertificateLineage} is empty, then this is used for signing with all
   * supported signature schemes.
   *
   * <p>If {@link getSigningCertificateLineage} is present, then this corresponds to the newest
   * certificate in the lineage, and is used for v3 signing on qualifying APKs (see {@link
   * getMinimumV3RotationApiVersion}).
   */
  public abstract SignerConfig getSignerConfig();

  /**
   * Minimum platform API version for which v3 signing should be performed. If no value is present,
   * then v3 signing is performed for all versions.
   */
  public abstract Optional<Integer> getMinimumV3RotationApiVersion();

  /**
   * Signing certificate lineage used for v3 signing on qualifying APKs (see {@link
   * getMinimumV3RotationApiVersion}).
   */
  public abstract Optional<SigningCertificateLineage> getSigningCertificateLineage();

  /**
   * Config of the signer corresponsing to the oldest certificate in the {@link
   * getSigningCertificateLineage} (this can only be set if a lineage is present).
   *
   * <p>This is used for v1 and v2 signing on qualifying APKs that are signed with v3 key rotation
   * (see {@link getMinimumV3RotationApiVersion}). This is never used for v3 signing.
   */
  public abstract Optional<SignerConfig> getOldestSigner();

  /**
   * Minimum Android platform version (API Level) for which an APK's rotated signing key should be
   * used to produce the APK's signature. The original signing key for the APK will be used for all
   * previous platform versions.
   */
  public abstract Optional<Integer> getRotationMinSdkVersion();

  public abstract Builder toBuilder();

  public int getEffectiveMinimumV3RotationApiVersion() {
    return getMinimumV3RotationApiVersion().orElse(1);
  }

  public static Builder builder() {
    return new AutoValue_SigningConfiguration.Builder();
  }

  /** Builder of {@link SigningConfiguration} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSignerConfig(SignerConfig signerConfig);

    public Builder setSignerConfig(PrivateKey privateKey, X509Certificate certificate) {
      return setSignerConfig(privateKey, ImmutableList.of(certificate));
    }

    public Builder setSignerConfig(
        PrivateKey privateKey, ImmutableList<X509Certificate> certificates) {
      return setSignerConfig(
          SignerConfig.builder().setPrivateKey(privateKey).setCertificates(certificates).build());
    }

    public abstract Builder setMinimumV3RotationApiVersion(
        Optional<Integer> minimumV3RotationApiVersion);

    public abstract Builder setRotationMinSdkVersion(Optional<Integer> rotationMinSdkVersion);

    public abstract Builder setSigningCertificateLineage(
        SigningCertificateLineage signingCertificateLineage);

    public abstract Builder setOldestSigner(SignerConfig oldestSigner);

    abstract SigningConfiguration autoBuild();

    public SigningConfiguration build() {
      SigningConfiguration signingConfiguration = autoBuild();
      if (signingConfiguration.getOldestSigner().isPresent()) {
        checkState(
            signingConfiguration.getSigningCertificateLineage().isPresent(),
            "Oldest signer should not be provided without signing certificate lineage.");
      }
      return signingConfiguration;
    }
  }

  /**
   * Extract the signing configuration (private key and certificates) associated with a key stored
   * in a keystore on disk.
   *
   * @param keystorePath Path to the keystore on disk (JKS and PKCS12 supported).
   * @param keyAlias Alias of the key in the keystore.
   * @param optionalKeystorePassword Password of the keystore. If not set, user will be prompted.
   * @param optionalKeyPassword Password of the key in the keystore. If not set, user will be
   *     prompted.
   */
  public static SigningConfiguration extractFromKeystore(
      Path keystorePath,
      String keyAlias,
      Optional<Password> optionalKeystorePassword,
      Optional<Password> optionalKeyPassword) {
    SignerConfig signerConfig =
        SignerConfig.extractFromKeystore(
            keystorePath, keyAlias, optionalKeystorePassword, optionalKeyPassword);

    return SigningConfiguration.builder().setSignerConfig(signerConfig).build();
  }
}
