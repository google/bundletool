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

/** Information required to sign an APK. */
@SuppressWarnings("Immutable") // PrivateKey and X509Certificate are considered immutable.
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class SigningConfiguration {

  SigningConfiguration() {}

  public abstract SignerConfig getSignerConfig();

  /**
   * Returns the minimum required platform API version for which v3 signing/rotation should be
   * performed.
   *
   * <p>Returns {@link Optional#empty()} if there is no minimum, meaning rotation can occur in all
   * platforms levels, if specified.
   */
  public abstract Optional<Integer> getMinimumV3SigningApiVersion();


  public SignerConfig getSignerConfigForV1AndV2() {
    SignerConfig signerConfig = getSignerConfig();
    return signerConfig;
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_SigningConfiguration.Builder();
  }

  /** Builder of {@link SigningConfiguration} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the {@link SignerConfig} to use to sign the APK. */
    public abstract Builder setSignerConfig(SignerConfig signerConfig);

    /** Sets the private key and corresponding certificate to use to sign the APK. */
    public Builder setSignerConfig(PrivateKey privateKey, X509Certificate certificate) {
      return setSignerConfig(privateKey, ImmutableList.of(certificate));
    }

    /** Sets the private key and corresponding certificates to use to sign the APK. */
    public Builder setSignerConfig(
        PrivateKey privateKey, ImmutableList<X509Certificate> certificates) {
      return setSignerConfig(
          SignerConfig.builder().setPrivateKey(privateKey).setCertificates(certificates).build());
    }

    /** Sets whether v3 signing should be restricted to an API level, if any. */
    public abstract Builder setMinimumV3SigningApiVersion(
        Optional<Integer> minimumV3SigningApiVersion);


    abstract SigningConfiguration autoBuild();

    public SigningConfiguration build() {
      SigningConfiguration signingConfiguration = autoBuild();
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
