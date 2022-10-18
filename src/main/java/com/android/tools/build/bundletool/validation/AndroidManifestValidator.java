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
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.ALWAYS_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.NO_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractAssetsTargetedDirectories;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractTextureCompressionFormats;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ManifestDeliveryElement;
import com.android.tools.build.bundletool.model.ModuleConditions;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.exceptions.InvalidVersionCodeException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Optional;

/** Validates {@code AndroidManifest.xml} file of each module. */
public class AndroidManifestValidator extends SubValidator {
  private static final int MIN_INSTANT_SDK_VERSION = 21;
  private static final Joiner COMMA_JOINER = Joiner.on(',');

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    validateSameVersionCode(modules);
    validateNoVersionCodeInAssetModules(modules);
    validateTargetSandboxVersion(modules);
    validateTcfTargetingNotMixedWithSupportsGlTexture(modules);
    validateConditionalModulesAreRemovable(modules);
    if (!BundleValidationUtils.isAssetOnlyBundle(modules)) {
      validateInstant(modules);
      validateMinSdk(modules);
    }
  }

  public void validateSameVersionCode(ImmutableList<BundleModule> modules) {
    ImmutableList<Integer> versionCodes =
        modules.stream()
            .map(BundleModule::getAndroidManifest)
            .filter(manifest -> !manifest.getModuleType().equals(ModuleType.ASSET_MODULE))
            .map(AndroidManifest::getVersionCode)
            .map(
                optVersionCode ->
                    optVersionCode.orElseThrow(
                        InvalidVersionCodeException::createMissingVersionCodeException))
            .distinct()
            .sorted()
            .collect(toImmutableList());

    if (versionCodes.size() > 1) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "App Bundle modules should have the same version code but found [%s].",
              versionCodes.stream().map(Object::toString).collect(joining(", ")))
          .build();
    }
  }

  private static void validateNoVersionCodeInAssetModules(ImmutableList<BundleModule> modules) {
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
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Asset packs cannot specify a version code, but '%s' does.",
              assetModuleWithVersionCode.get().getName())
          .build();
    }
  }

  void validateTargetSandboxVersion(ImmutableList<BundleModule> modules) {
    ImmutableList<Integer> targetSandboxVersion =
        modules.stream()
            .map(BundleModule::getAndroidManifest)
            .filter(manifest -> !manifest.getModuleType().equals(ModuleType.ASSET_MODULE))
            .map(AndroidManifest::getTargetSandboxVersion)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .sorted()
            .collect(toImmutableList());

    if (targetSandboxVersion.size() > 1) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The attribute 'targetSandboxVersion' should have the same value across modules, but "
                  + "found [%s]",
              COMMA_JOINER.join(targetSandboxVersion))
          .build();
    } else if (targetSandboxVersion.size() == 1
        && Iterables.getOnlyElement(targetSandboxVersion) > 2) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The attribute 'targetSandboxVersion' cannot have a value greater than 2, but found "
                  + "%d",
              Iterables.getOnlyElement(targetSandboxVersion))
          .build();
    }
  }

  private static void validateMinSdk(ImmutableList<BundleModule> modules) {
    int baseMinSdk =
        modules.stream()
            .filter(BundleModule::isBaseModule)
            .map(BundleModule::getAndroidManifest)
            .mapToInt(AndroidManifest::getEffectiveMinSdkVersion)
            .findFirst()
            .orElseThrow(BundleValidationUtils::createNoBaseModuleException);

    if (modules.stream()
        .filter(m -> m.getAndroidManifest().getMinSdkVersion().isPresent())
        .anyMatch(m -> m.getAndroidManifest().getEffectiveMinSdkVersion() < baseMinSdk)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Modules cannot have a minSdkVersion attribute with a value lower than "
                  + "the one from the base module.")
          .build();
    }
  }

  private static void validateTcfTargetingNotMixedWithSupportsGlTexture(
      ImmutableList<BundleModule> modules) {
    // Check for the existence of supports-gl-texture element(s).
    ImmutableSet<String> supportsGlTextureStrings =
        modules.stream()
            .map(BundleModule::getAndroidManifest)
            .flatMap(manifest -> manifest.getSupportsGlTextures().stream())
            .collect(toImmutableSet());

    // Extract texture compression formats from assets (like done when generating assets targeting).
    ImmutableSet<TextureCompressionFormatAlias> allModuleTextureFormats =
        modules.stream()
            .flatMap(
                module ->
                    extractTextureCompressionFormats(extractAssetsTargetedDirectories(module))
                        .stream())
            .collect(toImmutableSet());

    if (!supportsGlTextureStrings.isEmpty() && !allModuleTextureFormats.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Modules cannot have supports-gl-texture in their manifest (found: %s) and texture"
                  + " targeted directories in modules (found: %s).",
              supportsGlTextureStrings, allModuleTextureFormats)
          .build();
    }
  }

  private static void validateConditionalModulesAreRemovable(ImmutableList<BundleModule> modules) {
    boolean hasConditionalAndPermanent =
        modules.stream()
            .anyMatch(
                module ->
                    module.getDeliveryType().equals(CONDITIONAL_INITIAL_INSTALL)
                        && !module
                            .getAndroidManifest()
                            .getManifestDeliveryElement()
                            .flatMap(ManifestDeliveryElement::getInstallTimeRemovableValue)
                            .orElse(true));
    if (hasConditionalAndPermanent) {
      throw InvalidBundleException.builder()
          .withUserMessage("Conditional modules cannot be set to non-removable.")
          .build();
    }
  }

  @Override
  public void validateModule(BundleModule module) {
    validateInstant(module);
    validateDeliverySettings(module);
    validateInstantDeliverySettings(module);
    validateFusingConfig(module);
    validateMinMaxSdk(module);
    validateNumberOfDistinctSplitIds(module);
    validateAssetModuleManifest(module);
    validateMinSdkCondition(module);
    validateNoConditionalTargetingInAssetModules(module);
    validateInstantAndPersistentDeliveryCombinationsForAssetModules(module);
    validateNoUsesSdkLibraryTags(module);
  }

  private static void validateInstant(ImmutableList<BundleModule> modules) {
    // If any module is 'instant' validate that 'base' is instant too.
    BundleModule baseModule = BundleValidationUtils.expectBaseModule(modules);
    if (modules.stream().anyMatch(BundleModule::isInstantModule) && !baseModule.isInstantModule()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "App Bundle contains instant modules but the 'base' module is not marked 'instant'.")
          .build();
    }
  }

  private static void validateInstant(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    Optional<Boolean> isInstantModule = manifest.isInstantModule();
    if (isInstantModule.orElse(false)) {
      // if it is an instant module, ensure that max sdk is > 21, as we cannot serve anything less
      Optional<Integer> maxSdk = manifest.getMaxSdkVersion();
      if (maxSdk.isPresent() && maxSdk.get() < MIN_INSTANT_SDK_VERSION) {

        throw InvalidBundleException.builder()
            .withUserMessage(
                "maxSdkVersion (%d) is less than minimum sdk allowed for instant apps (%d).",
                maxSdk.get(), MIN_INSTANT_SDK_VERSION)
            .build();
      }
    }
  }

  private static void validateDeliverySettings(BundleModule module) {

    boolean deliveryTypeDeclared = module.getAndroidManifest().isDeliveryTypeDeclared();
    ModuleDeliveryType deliveryType = module.getDeliveryType();

    if (module.getAndroidManifest().getOnDemandAttribute().isPresent()
        && module.getAndroidManifest().getManifestDeliveryElement().isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' cannot use <dist:delivery> settings and legacy dist:onDemand "
                  + "attribute at the same time",
              module.getName())
          .build();
    }

    if (module.isBaseModule()) {
      // In the base module, onDemand must be either not set or false
      if (deliveryType.equals(ModuleDeliveryType.NO_INITIAL_INSTALL)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "The base module cannot be marked on-demand since it will always be served.")
            .build();
      }
      if (deliveryType.equals(ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "The base module cannot have conditions since it will always be served.")
            .build();
      }
    } else if (!deliveryTypeDeclared) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The module must explicitly set its delivery options using the "
                  + "<dist:delivery> element (module: '%s').",
              module.getName())
          .build();
    }
  }

  private static void validateInstantDeliverySettings(BundleModule module) {
    if (module.getAndroidManifest().getInstantManifestDeliveryElement().isPresent()
        && module.getAndroidManifest().getInstantAttribute().isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The <dist:instant-delivery> element and dist:instant attribute cannot be used"
                  + " together (module: '%s').",
              module.getName())
          .build();
    }
  }

  private static void validateFusingConfig(BundleModule module) {
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
        throw InvalidBundleException.builder()
            .withUserMessage("The base module cannot be excluded from fusing.")
            .build();
      }
    } else {
      if (!includedInFusingByManifest.isPresent()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Module '%s' must specify its fusing configuration in AndroidManifest.xml.",
                module.getName().getName())
            .build();
      }
    }
  }

  private static void validateMinMaxSdk(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    Optional<Integer> maxSdk = manifest.getMaxSdkVersion();
    Optional<Integer> minSdk = manifest.getMinSdkVersion();

    maxSdk
        .filter(sdk -> sdk < 0)
        .ifPresent(
            sdk -> {
              throw InvalidBundleException.builder()
                  .withUserMessage("maxSdkVersion must be nonnegative, found: (%d).", sdk)
                  .build();
            });

    minSdk
        .filter(sdk -> sdk < 0)
        .ifPresent(
            sdk -> {
              throw InvalidBundleException.builder()
                  .withUserMessage("minSdkVersion must be nonnegative, found: (%d).", sdk)
                  .build();
            });

    if (maxSdk.isPresent() && minSdk.isPresent() && maxSdk.get() < minSdk.get()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "minSdkVersion (%d) is greater than maxSdkVersion (%d).", minSdk.get(), maxSdk.get())
          .build();
    }
  }

  private static void validateNumberOfDistinctSplitIds(BundleModule module) {
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
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The attribute 'split' cannot be declared more than once (module '%s', values %s).",
              module.getName().toString(),
              splitIds.stream()
                  .map(attr -> "'" + attr.getValueAsString() + "'")
                  .collect(toImmutableSet()))
          .build();
    }
  }

  private static void validateAssetModuleManifest(BundleModule module) {
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
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Unexpected element declaration in manifest of asset pack '%s'.", module.getName())
          .build();
    }
  }

  /** Validates that if min-sdk condition is present it is >= than the effective minSdk version. */
  private static void validateMinSdkCondition(BundleModule module) {
    int effectiveMinSdkVersion = module.getAndroidManifest().getEffectiveMinSdkVersion();
    Optional<Integer> minSdkCondition =
        module
            .getAndroidManifest()
            .getManifestDeliveryElement()
            .map(ManifestDeliveryElement::getModuleConditions)
            .flatMap(ModuleConditions::getMinSdkVersion);

    if (minSdkCondition.isPresent() && minSdkCondition.get() < effectiveMinSdkVersion) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' has <dist:min-sdk> condition (%d) lower than the "
                  + "minSdkVersion(%d) of the module.",
              module.getName(), minSdkCondition.get(), effectiveMinSdkVersion)
          .build();
    }
  }

  private static void validateNoConditionalTargetingInAssetModules(BundleModule module) {
    if (module.getModuleType().equals(ModuleType.ASSET_MODULE)
        && !module
            .getModuleMetadata()
            .getTargeting()
            .equals(ModuleTargeting.getDefaultInstance())) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Conditional targeting is not allowed in asset packs, but found in '%s'.",
              module.getName())
          .build();
    }
  }

  private static void validateInstantAndPersistentDeliveryCombinationsForAssetModules(
      BundleModule module) {
    if (!module.getAndroidManifest().getModuleType().equals(ModuleType.ASSET_MODULE)
        || !module.isInstantModule()) {
      return;
    }
    // The two delivery combinations not allowed for asset modules are:
    // - Persistent install-time delivery + any instant delivery.
    // - Any persistent delivery + install-time instant delivery.
    if (!module.getDeliveryType().equals(NO_INITIAL_INSTALL)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Instant asset packs cannot have install-time delivery (module '%s').",
              module.getName())
          .build();
    }
    ModuleDeliveryType instantDelivery = module.getInstantDeliveryType().get();
    if (instantDelivery.equals(ALWAYS_INITIAL_INSTALL)
        || instantDelivery.equals(CONDITIONAL_INITIAL_INSTALL)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Instant delivery cannot be install-time (module '%s').", module.getName())
          .build();
    }
  }

  private static void validateNoUsesSdkLibraryTags(BundleModule module) {
    if (module.getAndroidManifest().hasApplicationElement()
        && !module.getAndroidManifest().getUsesSdkLibraryElements().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "<uses-sdk-library> element not allowed in the manifest of App Bundle. An App Bundle"
                  + " should depend on a runtime-enabled SDK by specifying its details in the"
                  + " runtime_enabled_sdk_config.pb, not in its manifest.")
          .build();
    }
  }
}
