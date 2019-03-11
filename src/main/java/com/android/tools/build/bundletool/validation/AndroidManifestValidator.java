/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.NO_INITIAL_INSTALL;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ManifestDeliveryElement;
import com.android.tools.build.bundletool.model.ModuleConditions;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestDuplicateAttributeException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestFusingException.BaseModuleExcludedFromFusingException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestFusingException.ModuleFusingConfigurationMissingException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MaxSdkInvalidException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MaxSdkLessThanMinInstantSdk;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MinSdkGreaterThanMaxSdkException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestSdkTargetingException.MinSdkInvalidException;
import com.android.tools.build.bundletool.model.exceptions.manifest.ManifestVersionCodeConflictException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/** Validates {@code AndroidManifest.xml} file of each module. */
public class AndroidManifestValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    validateSameVersionCode(modules);
    validateInstant(modules);
    validateNoVersionCodeInAssetModules(modules);
  }

  public void validateSameVersionCode(ImmutableList<BundleModule> modules) {
    ImmutableList<Integer> versionCodes =
        modules.stream()
            .map(BundleModule::getAndroidManifest)
            .filter(manifest -> !manifest.getModuleType().equals(ModuleType.ASSET_MODULE))
            .map(AndroidManifest::getVersionCode)
            .distinct()
            .sorted()
            .collect(toImmutableList());

    if (versionCodes.size() > 1) {
      throw new ManifestVersionCodeConflictException(versionCodes.toArray(new Integer[0]));
    }
  }

  private void validateNoVersionCodeInAssetModules(ImmutableList<BundleModule> modules) {
    Optional<BundleModule> assetModuleWithVersionCode =
        modules.stream()
            .filter(
                module ->
                    module.getModuleType().equals(ModuleType.ASSET_MODULE)
                        && module
                            .getAndroidManifest()
                            .getManifestRoot()
                            .getElement()
                            .getAndroidAttribute(VERSION_CODE_RESOURCE_ID)
                            .isPresent())
            .findFirst();
    if (assetModuleWithVersionCode.isPresent()) {
      throw ValidationException.builder()
          .withMessage(
              "Asset packs cannot specify a version code, but '%s' does.",
              assetModuleWithVersionCode.get().getName())
          .build();
    }
  }

  @Override
  public void validateModule(BundleModule module) {
    validateInstant(module);
    validateDeliverySettings(module);
    validateFusingConfig(module);
    validateMinMaxSdk(module);
    validateNumberOfDistinctSplitIds(module);
    validateOnDemandIsInstantMutualExclusion(module);
    validateAssetModuleManifest(module);
    validateMinSdkCondition(module);
  }

  private void validateInstant(ImmutableList<BundleModule> modules) {
    // If any module is 'instant' validate that 'base' is instant too.
    BundleModule baseModule =
        modules.stream()
            .filter(BundleModule::isBaseModule)
            .findFirst()
            .orElseThrow(
                () ->
                    new ValidationException(
                        "App Bundle does not contain a mandatory 'base' module."));
    if (modules.stream().anyMatch(BundleModule::isInstantModule) && !baseModule.isInstantModule()) {
      throw ValidationException.builder()
          .withMessage(
              "App Bundle contains instant modules but the 'base' module is not marked 'instant'.")
          .build();
    }
  }

  private void validateInstant(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    Optional<Boolean> isInstantModule = manifest.isInstantModule();
    if (isInstantModule.orElse(false)) {
      // if it is an instant module, ensure that max sdk is > 21, as we cannot serve anything less
      Optional<Integer> maxSdk = manifest.getMaxSdkVersion();
      if (maxSdk.isPresent()
          && maxSdk.get() < MaxSdkLessThanMinInstantSdk.MIN_INSTANT_SDK_VERSION) {
        throw new MaxSdkLessThanMinInstantSdk(maxSdk.get());
      }
    }
  }

  private void validateDeliverySettings(BundleModule module) {

    boolean deliveryTypeDeclared = module.getAndroidManifest().isDeliveryTypeDeclared();
    ModuleDeliveryType deliveryType = module.getDeliveryType();

    if (module.getAndroidManifest().getOnDemandAttribute().isPresent()
        && module.getAndroidManifest().getManifestDeliveryElement().isPresent()) {
      throw ValidationException.builder()
          .withMessage(
              "Module '%s' cannot use <dist:delivery> settings and legacy dist:onDemand "
                  + "attribute at the same time",
              module.getName())
          .build();
    }

    if (module.isBaseModule()) {
      // In the base module, onDemand must be either not set or false
      if (deliveryType.equals(ModuleDeliveryType.NO_INITIAL_INSTALL)) {
        throw new ValidationException(
            "The base module cannot be marked on-demand since it will always be served.");
      }
      if (deliveryType.equals(ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL)) {
        throw new ValidationException(
            "The base module cannot have conditions since it will always be served.");
      }
    } else if (!deliveryTypeDeclared) {
      throw ValidationException.builder()
          .withMessage(
              "The module must explicitly set its delivery options using the "
                  + "<dist:delivery> element (module: '%s').",
              module.getName())
          .build();
        }
    }

  private void validateOnDemandIsInstantMutualExclusion(BundleModule module) {
    boolean isInstant = module.getAndroidManifest().isInstantModule().orElse(false);

    if (module.getDeliveryType().equals(NO_INITIAL_INSTALL) && isInstant) {
      throw ValidationException.builder()
          .withMessage(
              "Module cannot be on-demand and 'instant' at the same time (module '%s').",
              module.getName())
          .build();
    }
    if (module.getDeliveryType().equals(CONDITIONAL_INITIAL_INSTALL) && isInstant) {
      throw ValidationException.builder()
          .withMessage(
              "The attribute 'instant' cannot be true for conditional module (module '%s').",
              module.getName())
          .build();
    }
  }

  private void validateFusingConfig(BundleModule module) {
    Optional<Boolean> isInstant = module.getAndroidManifest().isInstantModule();
    // Skip validations for instant modules. This is only relevant for Pre-L,
    // where instant apps are not available
    if (isInstant.isPresent() && isInstant.get()) {
      return;
    }

    Optional<Boolean> includedInFusingByManifest =
        module.getAndroidManifest().getIsModuleIncludedInFusing();

    if (module.isBaseModule()) {
      if (includedInFusingByManifest.isPresent() && !includedInFusingByManifest.get()) {
        throw new BaseModuleExcludedFromFusingException();
      }
    } else {
      if (!includedInFusingByManifest.isPresent()) {
        throw new ModuleFusingConfigurationMissingException(module.getName().getName());
      }
    }
  }

  private void validateMinMaxSdk(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    Optional<Integer> maxSdk = manifest.getMaxSdkVersion();
    Optional<Integer> minSdk = manifest.getMinSdkVersion();

    maxSdk
        .filter(sdk -> sdk < 0)
        .ifPresent(
            sdk -> {
              throw new MaxSdkInvalidException(sdk);
            });

    minSdk
        .filter(sdk -> sdk < 0)
        .ifPresent(
            sdk -> {
              throw new MinSdkInvalidException(sdk);
            });

    if (maxSdk.isPresent() && minSdk.isPresent() && maxSdk.get() < minSdk.get()) {
      throw new MinSdkGreaterThanMaxSdkException(minSdk.get(), maxSdk.get());
    }
  }

  private void validateNumberOfDistinctSplitIds(BundleModule module) {
    ImmutableSet<XmlProtoAttribute> splitIds =
        module
            .getAndroidManifest()
            .getManifestRoot()
            .getElement()
            .getAttributes()
            .filter(
                attr ->
                    attr.getName().equals("split")
                        && attr.getNamespaceUri().equals(NO_NAMESPACE_URI))
            .collect(toImmutableSet());
    if (splitIds.size() > 1) {
      throw new ManifestDuplicateAttributeException("split", splitIds, module.getName().toString());
    }
  }

  private void validateAssetModuleManifest(BundleModule module) {
    ImmutableMultimap<String, String> allowedManifestElementChildren =
        ImmutableMultimap.of(DISTRIBUTION_NAMESPACE_URI, "module", NO_NAMESPACE_URI, "uses-split");

    AndroidManifest manifest = module.getAndroidManifest();
    if (!manifest.getModuleType().equals(ModuleType.ASSET_MODULE)) {
      return;
    }
    if (manifest
        .getManifestRoot()
        .getElement()
        .getChildrenElements()
        .anyMatch(
            child ->
                !allowedManifestElementChildren.containsEntry(
                    child.getNamespaceUri(), child.getName()))) {
      throw ValidationException.builder()
          .withMessage(
              "Unexpected element declaration in manifest of asset pack '%s'.", module.getName())
          .build();
    }
  }

  /** Validates that if min-sdk condition is present it is >= than the effective minSdk version. */
  private void validateMinSdkCondition(BundleModule module) {
    int effectiveMinSdkVersion = module.getAndroidManifest().getEffectiveMinSdkVersion();
    Optional<Integer> minSdkCondition =
        module
            .getAndroidManifest()
            .getManifestDeliveryElement()
            .map(ManifestDeliveryElement::getModuleConditions)
            .flatMap(ModuleConditions::getMinSdkVersion);

    if (minSdkCondition.isPresent() && minSdkCondition.get() < effectiveMinSdkVersion) {
      throw ValidationException.builder()
          .withMessage(
              "Module '%s' has <dist:min-sdk> condition (%d) lower than the "
                  + "minSdkVersion(%d) of the module.",
              module.getName(), minSdkCondition.get(), effectiveMinSdkVersion)
          .build();
    }
  }
}
