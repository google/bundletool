/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;

/** Misc bundle validation functions. */
public final class BundleValidationUtils {

  public static void checkHasValuesOrAlternatives(AbiTargeting targeting, String directoryPath) {
    if (targeting.getValueCount() == 0 && targeting.getAlternativesCount() == 0) {
      throw InvalidBundleException.builder()
          .withUserMessage("Directory '%s' has set but empty ABI targeting.", directoryPath)
          .build();
    }
  }

  public static void checkHasValuesOrAlternatives(
      LanguageTargeting targeting, String directoryPath) {
    if (targeting.getValueCount() == 0 && targeting.getAlternativesCount() == 0) {
      throw InvalidBundleException.builder()
          .withUserMessage("Directory '%s' has set but empty language targeting.", directoryPath)
          .build();
    }
  }

  public static void checkHasValuesOrAlternatives(
      TextureCompressionFormatTargeting targeting, String directoryPath) {
    if (targeting.getValueCount() == 0 && targeting.getAlternativesCount() == 0) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Directory '%s' has set but empty Texture Compression Format targeting.",
              directoryPath)
          .build();
    }
  }

  /** Checks whether directory inside the specified module contains any files. */
  public static boolean directoryContainsNoFiles(BundleModule module, ZipPath dir) {
    return module.findEntriesUnderPath(dir).count() == 0;
  }

  public static boolean isAssetOnlyBundle(ImmutableList<BundleModule> modules) {
    return modules.stream()
        .map(BundleModule::getBundleType)
        // All modules are assumed to be part of the same bundle, with identical BundleType.
        .distinct()
        .collect(toOptional())
        .orElseThrow(
            () ->
                InvalidBundleException.builder().withUserMessage("Bundle without modules.").build())
        .equals(BundleConfig.BundleType.ASSET_ONLY);
  }

  public static BundleModule expectBaseModule(ImmutableList<BundleModule> modules) {
    return modules.stream()
        .filter(BundleModule::isBaseModule)
        .findFirst()
        .orElseThrow(BundleValidationUtils::createNoBaseModuleException);
  }

  public static InvalidBundleException createNoBaseModuleException() {
    return InvalidBundleException.createWithUserMessage(
        "App Bundle does not contain a mandatory 'base' module.");
  }

  // Do not instantiate.
  private BundleValidationUtils() {}
}
