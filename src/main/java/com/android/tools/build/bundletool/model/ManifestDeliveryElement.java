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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.AndroidManifest.CODE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITIONS_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_DEVICE_FEATURE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_DEVICE_GROUPS_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_MAX_SDK_VERSION_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_MIN_SDK_VERSION_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_USER_COUNTRIES_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.COUNTRY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DEVICE_GROUP_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXCLUDE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.FAST_FOLLOW_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.INSTALL_TIME_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ON_DEMAND_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingByDeterministic;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.counting;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Targeting.AssetModuleTargeting;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.DeviceTargetingUtils;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import java.util.stream.Collectors;

/** Parses and provides business logic utilities for <dist:delivery> element. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ManifestDeliveryElement {

  private static final String VERSION_ATTRIBUTE_NAME = "version";
  private static final ImmutableList<String> ASSET_MODULE_DELIVERY_ELEMENTS =
      ImmutableList.of(
          INSTALL_TIME_ELEMENT_NAME,
          ON_DEMAND_ELEMENT_NAME,
          FAST_FOLLOW_ELEMENT_NAME,
          CONDITIONS_ELEMENT_NAME);
  private static final ImmutableList<String> FEATURE_MODULE_DELIVERY_ELEMENTS =
      ImmutableList.of(INSTALL_TIME_ELEMENT_NAME, ON_DEMAND_ELEMENT_NAME);
  private static final ImmutableList<String> KNOWN_INSTALL_TIME_ATTRIBUTES =
      ImmutableList.of(CONDITIONS_ELEMENT_NAME, "removable");
  private static final ImmutableList<String> CONDITIONS_ALLOWED_ONLY_ONCE =
      ImmutableList.of(
          CONDITION_MIN_SDK_VERSION_NAME,
          CONDITION_MAX_SDK_VERSION_NAME,
          CONDITION_USER_COUNTRIES_NAME,
          CONDITION_DEVICE_GROUPS_NAME);

  abstract XmlProtoElement getDeliveryElement();

  abstract ModuleType getModuleType();

  /**
   * Returns if this <dist:delivery> element is well-formed.
   *
   * <p>A well-formed <dist:delivery> has at least one of the 'on-demand' or 'install-time'
   * elements.
   */
  public boolean isWellFormed() {
    return hasOnDemandElement()
        || hasInstallTimeElement()
        || (getModuleType() == ModuleType.ASSET_MODULE && hasFastFollowElement());
  }

  public boolean hasModuleConditions() {
    return !getModuleConditions().isEmpty();
  }

  @Memoized
  public boolean hasOnDemandElement() {
    return getDeliveryElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, ON_DEMAND_ELEMENT_NAME)
        .isPresent();
  }

  public boolean hasFastFollowElement() {
    return getDeliveryElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, FAST_FOLLOW_ELEMENT_NAME)
        .isPresent();
  }

  @Memoized
  public boolean hasInstallTimeElement() {
    return getInstallTimeElement().isPresent();
  }

  private Optional<XmlProtoElement> getInstallTimeElement() {
    return getDeliveryElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, INSTALL_TIME_ELEMENT_NAME);
  }

  /**
   * Returns "removable" attribute value of "install-time" element.
   *
   * <p>Unconditional install-time modules in bundles built via bundletool version >= 1.0.0 are
   * non-removable by default. Use {@link #isInstallTimeRemovable(Version)} to check if a module is
   * removable.
   */
  public Optional<Boolean> getInstallTimeRemovableValue() {
    return getInstallTimeElement()
        .flatMap(
            installTime ->
                installTime
                    .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "removable")
                    .map(
                        removable ->
                            removable
                                .getAttribute(DISTRIBUTION_NAMESPACE_URI, "value")
                                .map(XmlProtoAttribute::getValueAsBoolean)
                                .orElseThrow(
                                    () ->
                                        InvalidBundleException.createWithUserMessage(
                                            "No attribute 'dist:value' found in element"
                                                + " <dist:removable> of manifest. Make sure the"
                                                + " namespace is also set."))));
  }

  /** Returns all module conditions for install-time feature modules. */
  @Memoized
  public ModuleConditions getModuleConditions() {
    ImmutableList<XmlProtoElement> conditionElements =
        getModuleConditionElements(getInstallTimeElement());
    verifyUniqueConditions(conditionElements);

    ModuleConditions.Builder moduleConditions = ModuleConditions.builder();
    for (XmlProtoElement conditionElement : conditionElements) {
      verifyDistributionNamespace(conditionElement);
      switch (conditionElement.getName()) {
        case CONDITION_DEVICE_FEATURE_NAME:
          moduleConditions.addDeviceFeatureCondition(parseDeviceFeatureCondition(conditionElement));
          break;
        case CONDITION_MIN_SDK_VERSION_NAME:
          moduleConditions.setMinSdkVersion(parseMinSdkVersionCondition(conditionElement));
          break;
        case CONDITION_MAX_SDK_VERSION_NAME:
          moduleConditions.setMaxSdkVersion(parseMaxSdkVersionCondition(conditionElement));
          break;
        case CONDITION_USER_COUNTRIES_NAME:
          moduleConditions.setUserCountriesCondition(parseUserCountriesCondition(conditionElement));
          break;
        case CONDITION_DEVICE_GROUPS_NAME:
          moduleConditions.setDeviceGroupsCondition(parseDeviceGroupsCondition(conditionElement));
          break;
        default:
          throw InvalidBundleException.builder()
              .withUserMessage("Unrecognized module condition: '%s'", conditionElement.getName())
              .build();
      }
    }

    ModuleConditions processedModuleConditions = moduleConditions.build();

    if (processedModuleConditions.getMinSdkVersion().isPresent()
        && processedModuleConditions.getMaxSdkVersion().isPresent()) {
      if (processedModuleConditions.getMinSdkVersion().get()
          > processedModuleConditions.getMaxSdkVersion().get()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Illegal SDK-based conditional module targeting (min SDK must be less than or"
                    + " equal to max SD). Provided min and max values, respectively, are %s and %s",
                processedModuleConditions.getMinSdkVersion(),
                processedModuleConditions.getMaxSdkVersion())
            .build();
      }
    }

    return processedModuleConditions;
  }

  /** Returns all module conditions for asset modules. */
  public AssetModuleTargeting getAssetModuleConditions() {
    ImmutableList<XmlProtoElement> conditionElements =
        getModuleConditionElements(Optional.of(getDeliveryElement()));
    verifyUniqueConditions(conditionElements);

    AssetModuleTargeting.Builder targetingBuilder = AssetModuleTargeting.newBuilder();
    for (XmlProtoElement conditionElement : conditionElements) {
      verifyDistributionNamespace(conditionElement);
      switch (conditionElement.getName()) {
        case CONDITION_USER_COUNTRIES_NAME:
          targetingBuilder.setUserCountriesTargeting(
              parseUserCountriesCondition(conditionElement).toTargeting());
          break;
        case CONDITION_DEVICE_GROUPS_NAME:
          targetingBuilder.setDeviceGroupTargeting(
              parseDeviceGroupsCondition(conditionElement).toTargeting());
          break;
        default:
          throw InvalidBundleException.builder()
              .withUserMessage("Unrecognized module condition: '%s'", conditionElement.getName())
              .build();
      }
    }

    return targetingBuilder.build();
  }

  private static void verifyDistributionNamespace(XmlProtoElement conditionElement) {
    if (!conditionElement.getNamespaceUri().equals(DISTRIBUTION_NAMESPACE_URI)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Invalid namespace found in the module condition element. "
                  + "Expected '%s'; found '%s'.",
              DISTRIBUTION_NAMESPACE_URI, conditionElement.getNamespaceUri())
          .build();
    }
  }

  /** Verifies that unique delivery conditions are only specified once. */
  private static void verifyUniqueConditions(ImmutableList<XmlProtoElement> conditionElements) {
    ImmutableMap<String, Long> conditionCounts =
        conditionElements.stream()
            .collect(groupingByDeterministic(XmlProtoElement::getName, counting()));
    for (String conditionName : CONDITIONS_ALLOWED_ONLY_ONCE) {
      if (conditionCounts.getOrDefault(conditionName, 0L) > 1) {
        throw InvalidBundleException.builder()
            .withUserMessage("Multiple '<dist:%s>' conditions are not supported.", conditionName)
            .build();
      }
    }
  }

  private UserCountriesCondition parseUserCountriesCondition(XmlProtoElement conditionElement) {
    ImmutableList.Builder<String> countryCodes = ImmutableList.builder();
    for (XmlProtoElement countryElement :
        conditionElement.getChildrenElements().collect(toImmutableList())) {
      if (!countryElement.getName().equals(COUNTRY_ELEMENT_NAME)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Expected only <dist:country> elements inside <dist:user-countries>, but found %s",
                printElement(conditionElement))
            .build();
      }
      countryCodes.add(
          countryElement
              .getAttribute(DISTRIBUTION_NAMESPACE_URI, CODE_ATTRIBUTE_NAME)
              .map(XmlProtoAttribute::getValueAsString)
              .map(String::toUpperCase)
              .orElseThrow(
                  () ->
                      InvalidBundleException.builder()
                          .withUserMessage(
                              "<dist:country> element is expected to have 'dist:code' attribute "
                                  + "but found none.")
                          .build()));
    }
    boolean exclude =
        conditionElement
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, EXCLUDE_ATTRIBUTE_NAME)
            .map(XmlProtoAttribute::getValueAsBoolean)
            .orElse(false);
    return UserCountriesCondition.create(countryCodes.build(), exclude);
  }

  private DeviceGroupsCondition parseDeviceGroupsCondition(XmlProtoElement conditionElement) {
    ImmutableList<XmlProtoElement> children =
        conditionElement.getChildrenElements().collect(toImmutableList());

    if (children.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "At least one device group should be specified in '<dist:%s>' element.",
              CONDITION_DEVICE_GROUPS_NAME)
          .build();
    }

    ImmutableSet.Builder<String> deviceGroups = ImmutableSet.builder();
    for (XmlProtoElement deviceGroupElement : children) {
      if (!deviceGroupElement.getName().equals(DEVICE_GROUP_ELEMENT_NAME)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Expected only '<dist:%s>' elements inside '<dist:%s>', but found %s.",
                DEVICE_GROUP_ELEMENT_NAME,
                CONDITION_DEVICE_GROUPS_NAME,
                printElement(deviceGroupElement))
            .build();
      }
      String groupName =
          deviceGroupElement
              .getAttribute(DISTRIBUTION_NAMESPACE_URI, NAME_ATTRIBUTE_NAME)
              .map(XmlProtoAttribute::getValueAsString)
              .orElseThrow(
                  () ->
                      InvalidBundleException.builder()
                          .withUserMessage(
                              "'<dist:%s>' element is expected to have 'dist:%s' attribute "
                                  + "but found none.",
                              DEVICE_GROUP_ELEMENT_NAME, NAME_ATTRIBUTE_NAME)
                          .build());
      DeviceTargetingUtils.validateDeviceGroupForConditionalModule(groupName);
      deviceGroups.add(groupName);
    }
    return DeviceGroupsCondition.create(deviceGroups.build());
  }

  private static void validateDeliveryElement(
      XmlProtoElement deliveryElement, ModuleType moduleType) {
    validateDeliveryElementChildren(deliveryElement, moduleType);
    validateInstallTimeElement(
        deliveryElement.getOptionalChildElement(
            DISTRIBUTION_NAMESPACE_URI, INSTALL_TIME_ELEMENT_NAME));
    validateOnDemandElement(
        deliveryElement.getOptionalChildElement(
            DISTRIBUTION_NAMESPACE_URI, ON_DEMAND_ELEMENT_NAME));
    if (moduleType == ModuleType.ASSET_MODULE) {
      validateFastFollowElement(
          deliveryElement.getOptionalChildElement(
              DISTRIBUTION_NAMESPACE_URI, FAST_FOLLOW_ELEMENT_NAME));
    }
  }

  private static void validateDeliveryElementChildren(
      XmlProtoElement deliveryElement, ModuleType moduleType) {
    ImmutableList<String> allowedDeliveryElements =
        moduleType == ModuleType.ASSET_MODULE
            ? ASSET_MODULE_DELIVERY_ELEMENTS
            : FEATURE_MODULE_DELIVERY_ELEMENTS;
    Optional<XmlProtoElement> offendingElement =
        deliveryElement
            .getChildrenElements(
                child ->
                    !(child.getNamespaceUri().equals(DISTRIBUTION_NAMESPACE_URI)
                        && allowedDeliveryElements.contains(child.getName())))
            .findAny();

    if (offendingElement.isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Expected <dist:delivery> element to contain only %s elements but found: %s",
              allowedDeliveryElements.stream()
                  .map(name -> String.format("<dist:%s>", name))
                  .collect(Collectors.joining(", ")),
              printElement(offendingElement.get()))
          .build();
    }
  }

  private static void validateInstallTimeElement(Optional<XmlProtoElement> installTimeElement) {
    Optional<XmlProtoElement> offendingElement =
        installTimeElement.flatMap(
            installTime ->
                installTime
                    .getChildrenElements(
                        child ->
                            !(child.getNamespaceUri().equals(DISTRIBUTION_NAMESPACE_URI)
                                && KNOWN_INSTALL_TIME_ATTRIBUTES.contains(child.getName())))
                    .findAny());

    if (offendingElement.isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Expected <dist:install-time> element to contain only <dist:conditions> or "
                  + "<dist:removable> element but found: %s.",
              printElement(offendingElement.get()))
          .build();
    }
  }

  private static void validateOnDemandElement(Optional<XmlProtoElement> onDemandElement) {
    Optional<XmlProtoElement> offendingChild =
        onDemandElement.flatMap(element -> element.getChildrenElements().findAny());
    if (offendingChild.isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Expected <dist:on-demand> element to have no child elements but found: %s.",
              printElement(offendingChild.get()))
          .build();
    }
  }

  private static void validateFastFollowElement(Optional<XmlProtoElement> fastFollowElement) {
    Optional<XmlProtoElement> offendingChild =
        fastFollowElement.flatMap(element -> element.getChildrenElements().findAny());
    if (offendingChild.isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Expected <dist:fast-follow> element to have no child elements but found: %s.",
              printElement(offendingChild.get()))
          .build();
    }
  }

  private ImmutableList<XmlProtoElement> getModuleConditionElements(
      Optional<XmlProtoElement> parentElement) {
    return parentElement
        .flatMap(
            installTime ->
                installTime.getOptionalChildElement(
                    DISTRIBUTION_NAMESPACE_URI, CONDITIONS_ELEMENT_NAME))
        .map(conditions -> conditions.getChildrenElements().collect(toImmutableList()))
        .orElse(ImmutableList.of());
  }

  private DeviceFeatureCondition parseDeviceFeatureCondition(XmlProtoElement conditionElement) {
    return DeviceFeatureCondition.create(
        conditionElement
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, NAME_ATTRIBUTE_NAME)
            .orElseThrow(
                () ->
                    InvalidBundleException.createWithUserMessage(
                        "Missing required 'dist:name' attribute in the 'device-feature' condition "
                            + "element."))
            .getValueAsString(),
        conditionElement
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, VERSION_ATTRIBUTE_NAME)
            .map(XmlProtoAttribute::getValueAsInteger));
  }

  private static int parseMinSdkVersionCondition(XmlProtoElement conditionElement) {
    return conditionElement
        .getAttribute(DISTRIBUTION_NAMESPACE_URI, VALUE_ATTRIBUTE_NAME)
        .orElseThrow(
            () ->
                InvalidBundleException.createWithUserMessage(
                    "Missing required 'dist:value' attribute in the 'min-sdk' condition element."))
        .getValueAsDecimalInteger();
  }

  private static int parseMaxSdkVersionCondition(XmlProtoElement conditionElement) {
    return conditionElement
        .getAttribute(DISTRIBUTION_NAMESPACE_URI, VALUE_ATTRIBUTE_NAME)
        .orElseThrow(
            () ->
                InvalidBundleException.createWithUserMessage(
                    "Missing required 'dist:value' attribute in the 'max-sdk' condition element."))
        .getValueAsDecimalInteger();
  }

  private static String printElement(XmlProtoElement element) {
    if (element.getNamespaceUri().isEmpty()) {
      return String.format("'%s' with namespace not provided", element.getName());
    }
    return String.format(
        "'%s' with namespace URI: '%s'", element.getName(), element.getNamespaceUri());
  }

  /**
   * Returns the instance of the delivery element for persistent delivery if Android Manifest
   * contains the <dist:delivery> element.
   */
  public static Optional<ManifestDeliveryElement> fromManifestElement(
      XmlProtoElement manifestElement, ModuleType moduleType) {
    return fromManifestElement(manifestElement, "delivery", moduleType);
  }

  private static Optional<ManifestDeliveryElement> fromManifestElement(
      XmlProtoElement manifestElement, String deliveryTag, ModuleType moduleType) {
    return manifestElement
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
        .flatMap(elem -> elem.getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, deliveryTag))
        .map(
            (XmlProtoElement elem) -> {
              validateDeliveryElement(elem, moduleType);
              return new AutoValue_ManifestDeliveryElement(elem, moduleType);
            });
  }

  /**
   * Returns the instance of the delivery element for instant delivery if Android Manifest contains
   * the <dist:instant-delivery> element.
   */
  public static Optional<ManifestDeliveryElement> instantFromManifestElement(
      XmlProtoElement manifestElement, ModuleType moduleType) {
    return fromManifestElement(manifestElement, "instant-delivery", moduleType);
  }

  @VisibleForTesting
  static Optional<ManifestDeliveryElement> fromManifestRootNode(
      XmlNode xmlNode, ModuleType moduleType) {
    return fromManifestElement(new XmlProtoNode(xmlNode).getElement(), moduleType);
  }

  @VisibleForTesting
  static Optional<ManifestDeliveryElement> instantFromManifestRootNode(
      XmlNode xmlNode, ModuleType moduleType) {
    return instantFromManifestElement(new XmlProtoNode(xmlNode).getElement(), moduleType);
  }
}
