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
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static java.util.function.Function.identity;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Collection;

/** Validates runtime-enabled SDK config. */
public final class RuntimeEnabledSdkConfigValidator extends SubValidator {

  private static final String HEX_FINGERPRINT_REGEX = "[0-9A-F]{2}(:[0-9A-F]{2}){31}";

  /**
   * Validates that there is exactly 1 dependency on each runtime-enabled SDK, and that all required
   * fields are set inside each {@link RuntimeEnabledSdkConfig}.
   */
  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    ImmutableMap<String, Collection<RuntimeEnabledSdk>> runtimeEnabledSdksPerPackageName =
        modules.stream()
            .filter(module -> module.getRuntimeEnabledSdkConfig().isPresent())
            .map(module -> module.getRuntimeEnabledSdkConfig().get())
            .flatMap(
                runtimeEnabledSdkConfig ->
                    runtimeEnabledSdkConfig.getRuntimeEnabledSdkList().stream())
            .collect(toImmutableListMultimap(RuntimeEnabledSdk::getPackageName, identity()))
            .asMap();
    runtimeEnabledSdksPerPackageName
        .entrySet()
        .forEach(
            sdksByPackageName -> {
              // Validation fails if App Bundle depends on multiple runtime-enabled SDKs with the
              // same package name.
              validate(
                  sdksByPackageName.getValue().size() == 1,
                  "Found multiple dependencies on the same runtime-enabled SDK '%s'.",
                  sdksByPackageName.getKey());
              // Validate that all expected fields are set.
              validateRuntimeEnabledSdk(Iterables.getOnlyElement(sdksByPackageName.getValue()));
            });
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
        runtimeEnabledSdk.getCertificateDigest().matches(HEX_FINGERPRINT_REGEX),
        "Found dependency on runtime-enabled SDK '%s' with a signing certificate digest of"
            + " unexpected format.",
        runtimeEnabledSdk.getPackageName());
  }

  @FormatMethod
  private static void validate(boolean condition, @FormatString String message, Object... args) {
    if (!condition) {
      throw InvalidBundleException.builder().withUserMessage(message, args).build();
    }
  }
}
