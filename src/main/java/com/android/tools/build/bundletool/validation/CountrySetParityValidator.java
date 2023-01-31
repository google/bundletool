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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractAssetsTargetedDirectories;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractCountrySets;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectory;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;

/** Validates that all modules that use country set targeting contain the same country sets. */
public class CountrySetParityValidator extends SubValidator {

  /**
   * Represents a set of country sets (with fallback presence) in a module or a targeted asset
   * directory.
   */
  @AutoValue
  public abstract static class SupportedCountrySets {

    public static CountrySetParityValidator.SupportedCountrySets create(
        ImmutableSet<String> countrySets, boolean hasFallback) {
      return new AutoValue_CountrySetParityValidator_SupportedCountrySets(countrySets, hasFallback);
    }

    public abstract ImmutableSet<String> getCountrySets();

    public abstract boolean getHasFallback();

    @Override
    public final String toString() {
      return getCountrySets()
          + (getHasFallback() ? " (with fallback directories)" : " (without fallback directories)");
    }
  }

  @Override
  public void validateBundle(AppBundle bundle) {
    BundleConfig bundleConfig = bundle.getBundleConfig();
    Optimizations optimizations = bundleConfig.getOptimizations();
    List<SplitDimension> splitDimensions = optimizations.getSplitsConfig().getSplitDimensionList();

    Optional<String> countrySetDefaultSuffix =
        splitDimensions.stream()
            .filter(dimension -> dimension.getValue().equals(Value.COUNTRY_SET))
            .map(dimension -> dimension.getSuffixStripping().getDefaultSuffix())
            .collect(toOptional());

    if (countrySetDefaultSuffix.isPresent()) {
      validateDefaultCountrySetSupportedByAllModules(bundle, countrySetDefaultSuffix.get());
    }
  }

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    BundleModule referenceModule = null;
    SupportedCountrySets referenceCountrySets = null;
    for (BundleModule module : modules) {
      SupportedCountrySets moduleCountrySets = getSupportedCountrySets(module);
      if (moduleCountrySets.getCountrySets().isEmpty()) {
        continue;
      }

      validateModuleHasFallbackCountrySet(module, moduleCountrySets);
      if (referenceCountrySets == null) {
        referenceModule = module;
        referenceCountrySets = moduleCountrySets;
      } else if (!referenceCountrySets.equals(moduleCountrySets)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "All modules with country set targeting must support the same country"
                    + " sets, but module '%s' supports %s and module '%s' supports %s.",
                referenceModule.getName(),
                referenceCountrySets,
                module.getName(),
                moduleCountrySets)
            .build();
      }
    }
  }

  private void validateDefaultCountrySetSupportedByAllModules(
      AppBundle bundle, String defaultCountrySet) {

    bundle
        .getModules()
        .values()
        .forEach(
            module -> {
              SupportedCountrySets supportedCountrySets = getSupportedCountrySets(module);
              if (supportedCountrySets.getCountrySets().isEmpty()) {
                return;
              }

              if (!defaultCountrySet.isEmpty()) {
                if (!supportedCountrySets.getCountrySets().contains(defaultCountrySet)) {
                  throw InvalidBundleException.builder()
                      .withUserMessage(
                          "When a standalone or universal APK is built, the country set"
                              + " folders corresponding to country set '%s' will be used, but"
                              + " module '%s' has no such folders. Either add"
                              + " missing folders or change the configuration for the"
                              + " COUNTRY_SET optimization to specify a default"
                              + " suffix corresponding to country set to use in the standalone and"
                              + " universal APKs.",
                          defaultCountrySet, module.getName())
                      .build();
                }
              }

              validateModuleHasFallbackCountrySet(module, supportedCountrySets);
            });
  }

  private SupportedCountrySets getSupportedCountrySets(BundleModule module) {
    // Extract targeted directories from entries (like done when generating assets targeting)
    ImmutableSet<TargetedDirectory> targetedDirectories = extractAssetsTargetedDirectories(module);

    // Inspect the targetings to extract country sets.
    ImmutableSet<String> countrySets = extractCountrySets(targetedDirectories);

    // Check if one or more targeted directories have "fallback" sibling directories.
    boolean hasFallback =
        targetedDirectories.stream()
            .anyMatch(
                directory -> {
                  Optional<AssetsDirectoryTargeting> targeting =
                      directory.getTargeting(TargetingDimension.COUNTRY_SET);
                  if (targeting.isPresent()) {
                    // Check if a sibling folder without country set targeting exists. If yes, this
                    // is called a "fallback".
                    TargetedDirectory siblingFallbackDirectory =
                        directory.removeTargeting(TargetingDimension.COUNTRY_SET);
                    return module
                        .findEntriesUnderPath(siblingFallbackDirectory.toZipPath())
                        .findAny()
                        .isPresent();
                  }

                  return false;
                });

    return SupportedCountrySets.create(countrySets, hasFallback);
  }

  private static void validateModuleHasFallbackCountrySet(
      BundleModule module, SupportedCountrySets supportedCountrySets) {
    if (!supportedCountrySets.getHasFallback()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' targets content based on country set but doesn't have fallback"
                  + " folders (folders without #countries suffixes). Fallback folders"
                  + " will be used to generate split for rest of the countries which are"
                  + " not targeted by existing country sets. Please add missing folders"
                  + " and try again.",
              module.getName())
          .build();
    }
  }
}
