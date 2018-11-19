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

import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.NO_INITIAL_INSTALL;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestDuplicateAttributeException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.BaseModuleExcludedFromFusingException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.ModuleFusingConfigurationMissingException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MaxSdkInvalidException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MaxSdkLessThanMinInstantSdk;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkGreaterThanMaxSdkException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkInvalidException;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoAttribute;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/** Validates {@code AndroidManifest.xml} file of each module. */
public class AndroidManifestValidator extends SubValidator {

  private static final Joiner COMMA_JOINER = Joiner.on(',');

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    validateSameVersionCode(modules);
    validateInstant(modules);
  }

  public void validateSameVersionCode(ImmutableList<BundleModule> modules) {
    ImmutableList<Integer> versionCodes =
        modules.stream()
            .map(BundleModule::getAndroidManifest)
            .map(AndroidManifest::getVersionCode)
            .distinct()
            .sorted()
            .collect(toImmutableList());

    if (versionCodes.size() > 1) {
      throw ValidationException.builder()
          .withMessage(
              "App Bundle modules should have the same version code but found [%s].",
              COMMA_JOINER.join(versionCodes))
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
}
