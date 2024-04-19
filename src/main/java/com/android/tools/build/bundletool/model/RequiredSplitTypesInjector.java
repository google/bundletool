/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_T_API_VERSION;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Targeting.ApkTargeting;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;

/** Injector for required and provided split types. */
public class RequiredSplitTypesInjector {

  /**
   * Injects required and provided split types into the provided module split.
   *
   * @return the modified {@link ModuleSplit}
   */
  @CheckReturnValue
  public static ImmutableList<ModuleSplit> injectSplitTypeValidation(
      ImmutableList<ModuleSplit> splits,
      ImmutableList<BundleModuleName> requiredModules,
      boolean enableSystemAttribute) {
    return splits.stream()
        .map(
            split -> {
              // During the validation rollout, only inject system split types attribute for splits
              // targeting T+.
              boolean includeSystemAttribute = enableSystemAttribute && isTargetingAtLeastT(split);

              ManifestEditor apkManifest = split.getAndroidManifest().toEditor();

              apkManifest.setSplitTypes(
                  getProvidedSplitTypes(split).stream()
                      .map(RequiredSplitTypeName::toAttributeValue)
                      .collect(toImmutableList()),
                  includeSystemAttribute);

              // Only base/feature modules have required split types.
              if (split.isMasterSplit()) {
                apkManifest.setRequiredSplitTypes(
                    getRequiredSplitTypes(splits, requiredModules, split).stream()
                        .map(RequiredSplitTypeName::toAttributeValue)
                        .collect(toImmutableList()),
                    includeSystemAttribute);
              }

              return split.toBuilder().setAndroidManifest(apkManifest.save()).build();
            })
        .collect(toImmutableList());
  }

  private static ImmutableSet<RequiredSplitTypeName> getProvidedSplitTypes(
      ModuleSplit moduleSplit) {
    ApkTargeting apkTargeting = moduleSplit.getApkTargeting();
    BundleModuleName moduleName = moduleSplit.getModuleName();
    ImmutableSet.Builder<RequiredSplitTypeName> splitTypes = ImmutableSet.builder();

    if (apkTargeting.hasAbiTargeting()) {
      splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.ABI));
    }
    if (apkTargeting.hasScreenDensityTargeting()) {
      splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.DENSITY));
    }
    if (apkTargeting.hasTextureCompressionFormatTargeting()) {
      splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.TEXTURE_FORMAT));
    }
    if (apkTargeting.hasDeviceTierTargeting()) {
      splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.DEVICE_TIER));
    }
    if (apkTargeting.hasCountrySetTargeting()) {
      splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.COUNTRY_SET));
    }

    if (moduleSplit.isMasterSplit() && !moduleSplit.isBaseModuleSplit()) {
      splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.MODULE));
    }

    return splitTypes.build();
  }

  private static ImmutableSet<RequiredSplitTypeName> getRequiredSplitTypes(
      ImmutableList<ModuleSplit> allSplits,
      ImmutableList<BundleModuleName> requiredModules,
      ModuleSplit moduleSplit) {
    BundleModuleName moduleName = moduleSplit.getModuleName();
    ImmutableSet.Builder<RequiredSplitTypeName> splitTypes = ImmutableSet.builder();

    for (ModuleSplit split : allSplits) {
      if (!split.getModuleName().equals(moduleName)) {
        continue;
      }

      ApkTargeting apkTargeting = split.getApkTargeting();

      if (apkTargeting.hasAbiTargeting()) {
        splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.ABI));
      }
      if (apkTargeting.hasScreenDensityTargeting()) {
        splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.DENSITY));
      }
      if (apkTargeting.hasTextureCompressionFormatTargeting()) {
        splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.TEXTURE_FORMAT));
      }
      if (apkTargeting.hasDeviceTierTargeting()) {
        splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.DEVICE_TIER));
      }
      if (apkTargeting.hasCountrySetTargeting()) {
        splitTypes.add(RequiredSplitTypeName.create(moduleName, RequiredSplitType.COUNTRY_SET));
      }
    }

    if (moduleSplit.isBaseModuleSplit()) {
      requiredModules.stream()
          .filter(requiredModuleName -> !requiredModuleName.equals(moduleName))
          .forEach(
              requiredModuleName ->
                  splitTypes.add(
                      RequiredSplitTypeName.create(requiredModuleName, RequiredSplitType.MODULE)));
    }

    return splitTypes.build();
  }

  private static boolean isTargetingAtLeastT(ModuleSplit split) {
    return split.getVariantTargeting().getSdkVersionTargeting().getValueList().stream()
            .mapToInt(sdkVersion -> sdkVersion.getMin().getValue())
            .min()
            .orElse(1)
        >= ANDROID_T_API_VERSION;
  }

  private RequiredSplitTypesInjector() {}

  static enum RequiredSplitType {
    ABI("abi"),
    DENSITY("density"),
    TEXTURE_FORMAT("textures"),
    DEVICE_TIER("tier"),
    COUNTRY_SET("countries"),
    MODULE("module");

    private final String label;

    private RequiredSplitType(String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  @AutoValue
  abstract static class RequiredSplitTypeName {
    abstract BundleModuleName getModuleName();

    abstract RequiredSplitType getSplitType();

    public static RequiredSplitTypeName create(
        BundleModuleName moduleName, RequiredSplitType splitType) {
      return new AutoValue_RequiredSplitTypesInjector_RequiredSplitTypeName(moduleName, splitType);
    }

    public String toAttributeValue() {
      return String.format("%s__%s", getModuleName().getName(), getSplitType().getLabel());
    }
  }
}
