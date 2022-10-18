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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Collection;

/** Validates runtime-enabled SDK config. */
public final class RuntimeEnabledSdkConfigValidator extends SubValidator {

  private static final int RESOURCES_PACKAGE_ID_MIN_VALUE = 2;
  private static final int RESOURCES_PACKAGE_ID_MAX_VALUE = 255;

  /**
   * Validates that there is exactly 1 dependency on each runtime-enabled SDK, and that all required
   * fields are set inside each {@link RuntimeEnabledSdkConfig}.
   */
  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    ImmutableList<RuntimeEnabledSdk> runtimeEnabledSdks =
        modules.stream()
            .filter(module -> module.getRuntimeEnabledSdkConfig().isPresent())
            .map(module -> module.getRuntimeEnabledSdkConfig().get())
            .flatMap(
                runtimeEnabledSdkConfig ->
                    runtimeEnabledSdkConfig.getRuntimeEnabledSdkList().stream())
            .collect(toImmutableList());
    runtimeEnabledSdks.forEach(
        runtimeEnabledSdk ->
            // Validate that all expected fields are set.
            validateRuntimeEnabledSdk(runtimeEnabledSdk));
    validateUniqueSdkPackageNames(runtimeEnabledSdks);
    validateUniqueResourcePackageId(runtimeEnabledSdks);
  }

  private static void validateUniqueSdkPackageNames(
      ImmutableList<RuntimeEnabledSdk> runtimeEnabledSdks) {
    ImmutableMap<String, Collection<RuntimeEnabledSdk>> runtimeEnabledSdksByPackageName =
        runtimeEnabledSdks.stream()
            .collect(toImmutableListMultimap(RuntimeEnabledSdk::getPackageName, identity()))
            .asMap();
    runtimeEnabledSdksByPackageName.forEach(
        (key, value) ->
            validate(
                value.size() == 1,
                "Found multiple dependencies on the same runtime-enabled SDK '%s'.",
                key));
  }

  private static void validateUniqueResourcePackageId(
      ImmutableList<RuntimeEnabledSdk> runtimeEnabledSdks) {
    ImmutableMap<Integer, Collection<RuntimeEnabledSdk>> runtimeEnabledSkdsPerResourcesPackageId =
        runtimeEnabledSdks.stream()
            .collect(toImmutableListMultimap(RuntimeEnabledSdk::getResourcesPackageId, identity()))
            .asMap();
    runtimeEnabledSkdsPerResourcesPackageId
        .entrySet()
        .forEach(
            sdksPerResourcesPackageId ->
                validate(
                    sdksPerResourcesPackageId.getValue().size() == 1,
                    "Found dependencies on runtime-enabled SDKs '%s', which specify the"
                        + " same 'resources_package_id' value %d. resources_package_id values must"
                        + " be unique across all runtime-enabled SDK dependencies.",
                    sdksPerResourcesPackageId.getValue().stream()
                        .map(RuntimeEnabledSdk::getPackageName)
                        .collect(joining(", ")),
                    sdksPerResourcesPackageId.getKey()));
  }

  private static void validateRuntimeEnabledSdk(RuntimeEnabledSdk runtimeEnabledSdk) {
    validate(
        !runtimeEnabledSdk.getPackageName().isEmpty(),
        "Found dependency on runtime-enabled SDK with an empty package name.");
    validate(
        runtimeEnabledSdk.getVersionMajor() >= 0,
        "Found dependency on runtime-enabled SDK '%s' with a negative major version.",
        runtimeEnabledSdk.getPackageName());
    validate(
        runtimeEnabledSdk.getVersionMajor() <= VERSION_MAJOR_MAX_VALUE,
        "Found dependency on runtime-enabled SDK '%s' with illegal major version. Major version"
            + " must be <= %d.",
        runtimeEnabledSdk.getPackageName(),
        VERSION_MAJOR_MAX_VALUE);
    validate(
        runtimeEnabledSdk.getVersionMinor() >= 0,
        "Found dependency on runtime-enabled SDK '%s' with a negative minor version.",
        runtimeEnabledSdk.getPackageName());
    validate(
        runtimeEnabledSdk.getVersionMinor() <= VERSION_MINOR_MAX_VALUE,
        "Found dependency on runtime-enabled SDK '%s' with illegal minor version. Minor version"
            + " must be <= %d.",
        runtimeEnabledSdk.getPackageName(),
        VERSION_MINOR_MAX_VALUE);
    validate(
        runtimeEnabledSdk.getBuildTimeVersionPatch() >= 0,
        "Found dependency on runtime-enabled SDK '%s' with a negative patch version.",
        runtimeEnabledSdk.getPackageName());
    validate(
        FingerprintDigestValidator.isValidFingerprintDigest(
            runtimeEnabledSdk.getCertificateDigest()),
        "Found dependency on runtime-enabled SDK '%s' with a signing certificate digest of"
            + " unexpected format.",
        runtimeEnabledSdk.getPackageName());
    validate(
        runtimeEnabledSdk.getResourcesPackageId() >= RESOURCES_PACKAGE_ID_MIN_VALUE
            && runtimeEnabledSdk.getResourcesPackageId() <= RESOURCES_PACKAGE_ID_MAX_VALUE,
        "Illegal value of resources_package_id in RuntimeEnabledSdkConfig for SDK '%s': value must"
            + " be an integer between %d and %d, but was %d",
        runtimeEnabledSdk.getPackageName(),
        RESOURCES_PACKAGE_ID_MIN_VALUE,
        RESOURCES_PACKAGE_ID_MAX_VALUE,
        runtimeEnabledSdk.getResourcesPackageId());
  }

  @FormatMethod
  private static void validate(boolean condition, @FormatString String message, Object... args) {
    if (!condition) {
      throw InvalidBundleException.builder().withUserMessage(message, args).build();
    }
  }
}
