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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Optional;

/** Verifies APK signature for the set of device-specific APKs. */
final class ApkSignatureVerifier {

  /** Verifies signature of each APK and returns the public certificate of the app signing key. */
  static Result verify(ImmutableList<Path> deviceSpecificApks) {
    checkArgument(
        !deviceSpecificApks.isEmpty(), "Expected non-empty list of device-specific APKs.");

    Optional<X509Certificate> apkSigningKeyCertificate = Optional.empty();
    try {
      for (Path apkPath : deviceSpecificApks) {
        ApkVerifier.Result apkSignatureVerificationResult =
            new ApkVerifier.Builder(apkPath.toFile()).build().verify();
        if (!apkSignatureVerificationResult.isVerified()) {
          return Result.failure("APK signature invalid for " + apkPath.getFileName());
        }
        X509Certificate currentCertificate =
            apkSignatureVerificationResult.getSignerCertificates().get(0);
        if (apkSigningKeyCertificate.isPresent()) {
          if (!apkSigningKeyCertificate.get().equals(currentCertificate)) {
            return Result.failure(
                "APK signature verification failed: the keys used to sign the given set of device"
                    + " specific APKs do not match.");
          }
        } else {
          apkSigningKeyCertificate = Optional.of(currentCertificate);
        }
      }
    } catch (IOException | ApkFormatException | NoSuchAlgorithmException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Exception during APK signature verification.")
          .withCause(e)
          .build();
    }
    return Result.success(
        CodeTransparencyCryptoUtils.getCertificateFingerprint(apkSigningKeyCertificate.get()));
  }

  /** Represents result of {@link ApkSignatureVerifier#verify}. */
  @AutoValue
  abstract static class Result {
    abstract Optional<String> apkSigningKeyCertificateFingerprint();

    abstract Optional<String> errorMessage();

    /** Returns true if the APK signatures were successfully verified, and false otherwise. */
    boolean verified() {
      return apkSigningKeyCertificateFingerprint().isPresent() && !errorMessage().isPresent();
    }

    /**
     * If {@link #verified()} is true, returns APK signing key certificate fingerprint. Returns an
     * empty string otherwise.
     */
    String getApkSigningKeyCertificateFingerprint() {
      return apkSigningKeyCertificateFingerprint().orElse("");
    }

    /**
     * If {@link #verified()} is false, returns error message explaining why verification failed.
     * Returns an empty string otherwise.
     */
    String getErrorMessage() {
      return errorMessage().orElse("");
    }

    static Result success(String apkSigningKeyCertificateFingerprint) {
      return new AutoValue_ApkSignatureVerifier_Result(
          Optional.of(apkSigningKeyCertificateFingerprint), /* errorMessage= */ Optional.empty());
    }

    static Result failure(String errorMessage) {
      return new AutoValue_ApkSignatureVerifier_Result(
          /* apkSigningKeyCertificateFingerprint= */ Optional.empty(), Optional.of(errorMessage));
    }
  }

  private ApkSignatureVerifier() {}
}
