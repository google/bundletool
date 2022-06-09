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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MAJOR_MAX_VALUE;
import static com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder.VERSION_MINOR_MAX_VALUE;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.SdkBundleConfigProto;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;

/** Validator for the {@code SdkBundleConfig.pb} file. */
public class SdkBundleConfigValidator extends SubValidator {

  @Override
  public void validateSdkBundle(SdkBundle sdkBundle) {
    validateSdkBundleConfig(sdkBundle.getSdkBundleConfig());
  }

  @VisibleForTesting
  void validateSdkBundleConfig(SdkBundleConfig sdkBundleConfig) {
    validateNonEmptyPackageNames(sdkBundleConfig);
    validateUniqueDependencies(sdkBundleConfig);
    validateDependencyVersionsAreValid(sdkBundleConfig);
    validateDependencyCertificatesAreValid(sdkBundleConfig);
  }

  private void validateNonEmptyPackageNames(SdkBundleConfig sdkBundleConfig) {
    if (sdkBundleConfig.getSdkDependenciesList().stream()
        .map(SdkBundleConfigProto.SdkBundle::getPackageName)
        .anyMatch(String::isEmpty)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Package names of SDK dependencies in the SdkBundleConfig file cannot be empty.")
          .build();
    }
  }

  private void validateUniqueDependencies(SdkBundleConfig sdkBundleConfig) {
    Set<String> existingPackageNames = new HashSet<>();
    ImmutableSet<String> duplicatePackageNames =
        sdkBundleConfig.getSdkDependenciesList().stream()
            .map(SdkBundleConfigProto.SdkBundle::getPackageName)
            // Set::add will return true if the element is not present.
            .filter(not(existingPackageNames::add))
            .collect(toImmutableSet());
    if (!duplicatePackageNames.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "SDK dependencies in the SdkBundleConfig file have repeated package names: %s.",
              duplicatePackageNames)
          .build();
    }
  }

  private void validateDependencyVersionsAreValid(SdkBundleConfig sdkBundleConfig) {
    for (SdkBundleConfigProto.SdkBundle dependency : sdkBundleConfig.getSdkDependenciesList()) {
      if (dependency.getVersionMajor() < 0
          || dependency.getVersionMajor() > VERSION_MAJOR_MAX_VALUE) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "SDK major version for dependency '%s' must be an integer between 0 and %d.",
                dependency.getPackageName(), VERSION_MAJOR_MAX_VALUE)
            .build();
      }

      if (dependency.getVersionMinor() < 0
          || dependency.getVersionMinor() > VERSION_MINOR_MAX_VALUE) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "SDK minor version for dependency '%s' must be an integer between 0 and %d.",
                dependency.getPackageName(), VERSION_MINOR_MAX_VALUE)
            .build();
      }

      if (dependency.getBuildTimeVersionPatch() < 0) {
        throw InvalidBundleException.builder()
            .withUserMessage("SDK patch version must be a non-negative integer.")
            .build();
      }
    }
  }

  private void validateDependencyCertificatesAreValid(SdkBundleConfig sdkBundleConfig) {
    for (SdkBundleConfigProto.SdkBundle dependency : sdkBundleConfig.getSdkDependenciesList()) {
      if (!FingerprintDigestValidator.isValidFingerprintDigest(dependency.getCertificateDigest())) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Certificate digest for dependency '%s' has an invalid format.",
                dependency.getPackageName())
            .build();
      }
    }
  }
}
