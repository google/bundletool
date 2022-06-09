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
import static com.android.tools.build.bundletool.model.utils.BundleParser.readSdkModulesConfig;

import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.Version;
import java.util.zip.ZipFile;

/** Validator of the SdkModulesConfig.pb file. */
public class SdkModulesConfigValidator extends SubValidator {

  @Override
  public void validateSdkBundle(SdkBundle sdkBundle) {
    validateSdkModulesConfig(sdkBundle.getSdkModulesConfig());
  }

  @Override
  public void validateSdkModulesZipFile(ZipFile modulesFile) {
    SdkModulesConfig sdkModulesConfig = readSdkModulesConfig(modulesFile);

    validateSdkModulesConfig(sdkModulesConfig);
  }

  private void validateSdkModulesConfig(SdkModulesConfig sdkModulesConfig) {
    validateSdkVersion(sdkModulesConfig);
    validateBundletoolVersion(sdkModulesConfig);
    validateSdkPackageName(sdkModulesConfig);
    validateSdkProviderClassName(sdkModulesConfig);
  }

  private void validateSdkVersion(SdkModulesConfig sdkModulesConfig) {
    RuntimeEnabledSdkVersion sdkVersion = sdkModulesConfig.getSdkVersion();

    if (sdkVersion.getMajor() < 0 || sdkVersion.getMajor() > VERSION_MAJOR_MAX_VALUE) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "SDK major version must be an integer between 0 and %d", VERSION_MAJOR_MAX_VALUE)
          .build();
    }

    if (sdkVersion.getMinor() < 0 || sdkVersion.getMinor() > VERSION_MINOR_MAX_VALUE) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "SDK minor version must be an integer between 0 and %d", VERSION_MINOR_MAX_VALUE)
          .build();
    }

    if (sdkVersion.getPatch() < 0) {
      throw InvalidBundleException.builder()
          .withUserMessage("SDK patch version must be a non-negative integer")
          .build();
    }
  }

  private void validateBundletoolVersion(SdkModulesConfig sdkModulesConfig) {
    try {
      Version.of(sdkModulesConfig.getBundletool().getVersion());
    } catch (IllegalArgumentException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage(
              "Invalid Bundletool version in the SdkModulesConfig.pb file: '%s'",
              sdkModulesConfig.getBundletool().getVersion())
          .build();
    }
  }

  private void validateSdkPackageName(SdkModulesConfig sdkModulesConfig) {
    if (sdkModulesConfig.getSdkPackageName().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("SDK package name cannot be an empty string")
          .build();
    }
  }

  private void validateSdkProviderClassName(SdkModulesConfig sdkModulesConfig) {
    if (sdkModulesConfig.getSdkProviderClassName().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("SDK provider class name cannot be an empty string")
          .build();
    }
  }
}
