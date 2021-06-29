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

package com.android.tools.build.bundletool.transparency;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Represents result of code transparency verification. */
@AutoValue
public abstract class TransparencyCheckResult {

  /** Whether code transparency signature was successfully verified. */
  abstract boolean transparencySignatureVerified();

  /** Whether code transparency file contents were successfully verified. */
  abstract boolean fileContentsVerified();

  /**
   * SHA-256 Fingerprint of the public key certificate that was used for verifying code transparency
   * signature. Only set when {@link #transparencySignatureVerified()} is true.
   */
  public abstract Optional<String> transparencyKeyCertificateFingerprint();

  /**
   * SHA-256 Fingerprint of the public key certificate corresponding to the APK signature. Only set
   * if the transparency was verified in CONNECTED_DEVICE or APK mode. Empty in BUNDLE mode and when
   * APK signature verification fails.
   */
  public abstract Optional<String> apkSigningKeyCertificateFingerprint();

  /**
   * Error message containint information about the cause of code transparency verification failure.
   * Only set when code transparency verification fails.
   */
  public abstract Optional<String> errorMessage();

  /** Returns true if code transparency signature and file contents were successfully verified. */
  public boolean verified() {
    return transparencySignatureVerified() && fileContentsVerified();
  }

  /**
   * Returns SHA-256 fingerprint of the public key certificate that was used to successfully verify
   * transparency signature, or an empty string if verification failed.
   */
  public String getTransparencyKeyCertificateFingerprint() {
    return transparencyKeyCertificateFingerprint().orElse("");
  }

  /**
   * Returns SHA-256 fingerprint of the APK signing key certificate. Returns an empty string if the
   * fingerprint is not set.
   */
  public String getApkSigningKeyCertificateFingerprint() {
    return apkSigningKeyCertificateFingerprint().orElse("");
  }

  /**
   * Returns an error message containing information about the cause of code transparency
   * verification failure, or an empty string if code transparency was successfully verified.
   */
  public String getErrorMessage() {
    return errorMessage().orElse("");
  }

  /** Creates a builder for TransparencyCheckResult. */
  public static Builder builder() {
    return new AutoValue_TransparencyCheckResult.Builder()
        .transparencySignatureVerified(false)
        .fileContentsVerified(false);
  }

  /** Returns empty TransparencyCheckResult. */
  public static TransparencyCheckResult empty() {
    return builder().build();
  }

  /** Builder for TransparencyCheckResult. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder transparencySignatureVerified(boolean transparencySignatureVerified);

    public abstract Builder fileContentsVerified(boolean fileContentsVerified);

    public abstract Builder transparencyKeyCertificateFingerprint(
        String transparencyKeyCertificateFingerprint);

    public abstract Builder apkSigningKeyCertificateFingerprint(
        String apkSigningKeyCertificateFingerprint);

    public abstract Builder errorMessage(String errorMessage);

    public abstract TransparencyCheckResult build();
  }
}
