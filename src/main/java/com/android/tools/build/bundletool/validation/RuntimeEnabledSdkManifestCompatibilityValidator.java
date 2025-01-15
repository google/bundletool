/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableMap;

/** Validates that the app manifest is compatible with the runtime-enabled SDKs it depends on. */
public final class RuntimeEnabledSdkManifestCompatibilityValidator extends SubValidator {

  /**
   * Validates that the app manifest is compatible with the manifest of the runtime-enabled SDKs it
   * depends on.
   */
  @Override
  public void validateBundleWithSdkModules(
      AppBundle bundle, ImmutableMap<String, BundleModule> sdkModules) {
    validateMinSdkVersionBetweeenAppAndSdks(bundle, sdkModules);
    validateMinAndTargetSdkVersionAcrossSdks(sdkModules);
  }

  /**
   * Checks that the minSdkVersion of the app is higher or equal to the minSdkVersion of all
   * dependent runtime-enabled SDKs.
   */
  private static void validateMinSdkVersionBetweeenAppAndSdks(
      AppBundle bundle, ImmutableMap<String, BundleModule> sdkModules) {
    int baseMinSdk =
        bundle.getModules().values().stream()
            .filter(BundleModule::isBaseModule)
            .map(BundleModule::getAndroidManifest)
            .mapToInt(AndroidManifest::getEffectiveMinSdkVersion)
            .findFirst()
            .orElseThrow(BundleValidationUtils::createNoBaseModuleException);

    sdkModules
        .entrySet()
        .forEach(
            sdkModule -> {
              if (sdkModule.getValue().getAndroidManifest().getEffectiveMinSdkVersion()
                  > baseMinSdk) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Runtime-enabled SDKs must not have a minSdkVersion greater than the app,"
                            + " but found SDK '%s' with minSdkVersion (%d) higher than the app's"
                            + " minSdkVersion (%d).",
                        sdkModule.getKey(),
                        sdkModule.getValue().getAndroidManifest().getEffectiveMinSdkVersion(),
                        baseMinSdk)
                    .build();
              }
            });
  }

  /**
   * Checks that the minSdkVersion of an SDK is never higher than the targetSdkVersion of another
   * SDK.
   */
  private static void validateMinAndTargetSdkVersionAcrossSdks(
      ImmutableMap<String, BundleModule> sdkModules) {
    sdkModules
        .entrySet()
        .forEach(
            nameToModule1 -> {
              AndroidManifest manifest1 = nameToModule1.getValue().getAndroidManifest();
              sdkModules
                  .entrySet()
                  .forEach(
                      nameToModule2 -> {
                        AndroidManifest manifest2 = nameToModule2.getValue().getAndroidManifest();
                        if (manifest1.getEffectiveMinSdkVersion()
                            > manifest2.getEffectiveTargetSdkVersion()) {
                          throw InvalidBundleException.builder()
                              .withUserMessage(
                                  "Runtime-enabled SDKs must have a minSdkVersion lower or equal to"
                                      + " the targetSdkVersion of another SDK, but found SDK '%s'"
                                      + " with minSdkVersion (%d) higher than the targetSdkVersion"
                                      + " (%d) of SDK '%s'.",
                                  nameToModule1.getKey(),
                                  manifest1.getEffectiveMinSdkVersion(),
                                  manifest2.getEffectiveTargetSdkVersion(),
                                  nameToModule2.getKey())
                              .build();
                        }
                      });
            });
  }
}
