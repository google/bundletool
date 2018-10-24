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

import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_DEVICE_FEATURE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_MIN_SDK_VERSION_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_ATTRIBUTE_NAME;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNode;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Parses and provides business logic utilities for <dist:delivery> element. */
@AutoValue
public abstract class ManifestDeliveryElement {

  abstract XmlProtoElement getDeliveryElement();

  /**
   * Returns if this <dist:delivery> element is well-formed.
   *
   * <p>A well-formed <dist:delivery> has at least one of the 'on-demand' or 'install-time'
   * elements.
   */
  public boolean isWellFormed() {
    return hasOnDemandElement() || hasInstallTimeElement();
  }

  public boolean hasModuleConditions() {
    return !getModuleConditions().isEmpty();
  }

  @Memoized
  public boolean hasOnDemandElement() {
    return getDeliveryElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "on-demand")
        .isPresent();
  }

  @Memoized
  public boolean hasInstallTimeElement() {
    return getDeliveryElement()
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
        .isPresent();
  }

  /**
   * Returns all module conditions.
   *
   * <p>We support <dist:min-sdk-version> and <dist:device-feature> conditions today. Any other
   * conditions types are not supported and will result in {@link ValidationException}.
   */
  @Memoized
  public ModuleConditions getModuleConditions() {
    ImmutableList<XmlProtoElement> conditionElements = getModuleConditionElements();

    ModuleConditions.Builder moduleConditions = ModuleConditions.builder();
    for (XmlProtoElement conditionElement : conditionElements) {
      if (!conditionElement.getNamespaceUri().equals(DISTRIBUTION_NAMESPACE_URI)) {
        throw ValidationException.builder()
            .withMessage(
                "Invalid namespace found in the module condition element. "
                    + "Expected '%s'; found '%s'.",
                DISTRIBUTION_NAMESPACE_URI, conditionElement.getNamespaceUri())
            .build();
      }
      switch (conditionElement.getName()) {
        case CONDITION_DEVICE_FEATURE_NAME:
          moduleConditions.addDeviceFeatureCondition(parseDeviceFeatureCondition(conditionElement));
          break;
        case CONDITION_MIN_SDK_VERSION_NAME:
          moduleConditions.setMinSdkVersion(parseMinSdkVersionCondition(conditionElement));
          break;
        default:
          throw new ValidationException(
              String.format("Unrecognized module condition: '%s'", conditionElement.getName()));
      }
    }
    return moduleConditions.build();
  }

  private ImmutableList<XmlProtoElement> getModuleConditionElements() {
    Optional<XmlProtoElement> installTimeElement =
        getDeliveryElement().getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time");
    return installTimeElement
        .flatMap(
            installTime ->
                installTime.getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions"))
        .map(conditions -> conditions.getChildrenElements().collect(toImmutableList()))
        .orElse(ImmutableList.of());
  }

  private DeviceFeatureCondition parseDeviceFeatureCondition(XmlProtoElement conditionElement) {
    return DeviceFeatureCondition.create(
        conditionElement
            .getAttribute(DISTRIBUTION_NAMESPACE_URI, NAME_ATTRIBUTE_NAME)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "Missing required 'name' attribute in the 'device-feature' condition "
                            + "element."))
            .getValueAsString());
  }

  private int parseMinSdkVersionCondition(XmlProtoElement conditionElement) {
    return conditionElement
        .getAttribute(DISTRIBUTION_NAMESPACE_URI, VALUE_ATTRIBUTE_NAME)
        .orElseThrow(
            () ->
                new ValidationException(
                    "Missing required 'value' attribute in the 'min-sdk' condition element."))
        .getValueAsDecimalInteger();
  }

  /** Returns the instance if Android Manifest contains the <dist:delivery> element. */
  public static Optional<ManifestDeliveryElement> fromManifestElement(
      XmlProtoElement manifestElement) {
    return manifestElement
        .getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
        .flatMap(elem -> elem.getOptionalChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery"))
        .map((XmlProtoElement elem) -> new AutoValue_ManifestDeliveryElement(elem));
  }

  @VisibleForTesting
  static Optional<ManifestDeliveryElement> fromManifestRootNode(XmlNode xmlNode) {
    return fromManifestElement(new XmlProtoNode(xmlNode).getElement());
  }
}
