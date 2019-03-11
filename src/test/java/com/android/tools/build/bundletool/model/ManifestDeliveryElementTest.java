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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withEmptyDeliveryElement;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFeatureConditionHexVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withFusingAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withInstallTimeDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemandDelivery;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUnsupportedCondition;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUserCountriesCondition;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ManifestDeliveryElementTest {

  private static final String DISTRIBUTION_NAMESPACE_URI =
      "http://schemas.android.com/apk/distribution";

  @Test
  public void emptyDeliveryElement_notWellFormed() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest("com.test.app", withEmptyDeliveryElement()));

    assertThat(deliveryElement).isPresent();
    assertThat(deliveryElement.get().hasInstallTimeElement()).isFalse();
    assertThat(deliveryElement.get().hasOnDemandElement()).isFalse();
    assertThat(deliveryElement.get().hasModuleConditions()).isFalse();
    assertThat(deliveryElement.get().isWellFormed()).isFalse();
  }

  @Test
  public void installTimeDeliveryOnly() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest("com.test.app", withInstallTimeDelivery()));

    assertThat(deliveryElement).isPresent();
    assertThat(deliveryElement.get().hasInstallTimeElement()).isTrue();
    assertThat(deliveryElement.get().hasOnDemandElement()).isFalse();
    assertThat(deliveryElement.get().hasModuleConditions()).isFalse();
    assertThat(deliveryElement.get().isWellFormed()).isTrue();
  }

  @Test
  public void onDemandDeliveryOnly() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest("com.test.app", withOnDemandDelivery()));

    assertThat(deliveryElement).isPresent();
    assertThat(deliveryElement.get().hasInstallTimeElement()).isFalse();
    assertThat(deliveryElement.get().hasOnDemandElement()).isTrue();
    assertThat(deliveryElement.get().hasModuleConditions()).isFalse();
    assertThat(deliveryElement.get().isWellFormed()).isTrue();
  }

  @Test
  public void onDemandAndInstallTimeDelivery() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest("com.test.app", withInstallTimeDelivery(), withOnDemandDelivery()));

    assertThat(deliveryElement).isPresent();
    assertThat(deliveryElement.get().hasInstallTimeElement()).isTrue();
    assertThat(deliveryElement.get().hasOnDemandElement()).isTrue();
    assertThat(deliveryElement.get().hasModuleConditions()).isFalse();
    assertThat(deliveryElement.get().isWellFormed()).isTrue();
  }

  @Test
  public void getModuleConditions_returnsAllConditions() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest(
                "com.test.app",
                withFeatureCondition("android.hardware.camera.ar"),
                withMinSdkCondition(24)));

    assertThat(deliveryElement).isPresent();

    assertThat(deliveryElement.get().hasModuleConditions()).isTrue();
    assertThat(deliveryElement.get().getModuleConditions())
        .isEqualTo(
            ModuleConditions.builder()
                .addDeviceFeatureCondition(
                    DeviceFeatureCondition.create("android.hardware.camera.ar"))
                .setMinSdkVersion(24)
                .build());
  }

  @Test
  public void getDeviceFeatureConditions_returnsAllConditions() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest(
                "com.test.app",
                withFeatureCondition("android.hardware.camera.ar"),
                withFeatureCondition("android.software.vr.mode"),
                withMinSdkVersion(24)));

    assertThat(deliveryElement).isPresent();

    assertThat(deliveryElement.get().hasModuleConditions()).isTrue();
    assertThat(deliveryElement.get().getModuleConditions().getDeviceFeatureConditions())
        .containsExactly(
            DeviceFeatureCondition.create("android.hardware.camera.ar"),
            DeviceFeatureCondition.create("android.software.vr.mode"));
  }

  @Test
  public void moduleConditions_deviceFeatureVersions() {
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest(
                "com.test.app",
                withFeatureConditionHexVersion("android.software.opengl", 0x30000),
                withFeatureCondition("android.hardware.vr.headtracking", 1)));

    assertThat(deliveryElement).isPresent();

    assertThat(deliveryElement.get().hasModuleConditions()).isTrue();
    assertThat(deliveryElement.get().getModuleConditions().getDeviceFeatureConditions())
        .containsExactly(
            DeviceFeatureCondition.create("android.software.opengl", Optional.of(0x30000)),
            DeviceFeatureCondition.create("android.hardware.vr.headtracking", Optional.of(1)));
  }

  @Test
  public void moduleConditions_unsupportedCondition_throws() throws Exception {
    Optional<ManifestDeliveryElement> manifestDeliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest(
                "com.test.app", withFusingAttribute(false), withUnsupportedCondition()));

    assertThat(manifestDeliveryElement).isPresent();

    Throwable exception =
        assertThrows(
            ValidationException.class, () -> manifestDeliveryElement.get().getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Unrecognized module condition: 'unsupportedCondition'");
  }

  @Test
  public void moduleConditions_missingNameOfFeature_throws() throws Exception {
    // Name attribute doesn't use distribution namespace.
    XmlProtoElement badCondition =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "device-feature")
            .addAttribute(
                XmlProtoAttributeBuilder.create("name")
                    .setValueAsString("android.hardware.camera.ar"))
            .build();

    Optional<ManifestDeliveryElement> manifestDeliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            createAndroidManifestWithConditions(badCondition));

    assertThat(manifestDeliveryElement).isPresent();

    Throwable exception =
        assertThrows(
            ValidationException.class, () -> manifestDeliveryElement.get().getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Missing required 'dist:name' attribute in the 'device-feature' condition element.");
  }

  @Test
  public void moduleConditions_missingMinSdkValue_throws() {
    // Value attribute doesn't use distribution namespace.
    XmlProtoElement badCondition =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "min-sdk")
            .addAttribute(XmlProtoAttributeBuilder.create("value").setValueAsDecimalInteger(26))
            .build();

    Optional<ManifestDeliveryElement> manifestDeliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(
            createAndroidManifestWithConditions(badCondition));

    assertThat(manifestDeliveryElement).isPresent();

    Throwable exception =
        assertThrows(
            ValidationException.class, () -> manifestDeliveryElement.get().getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Missing required 'dist:value' attribute in the 'min-sdk' condition element.");
  }

  @Test
  public void getModuleConditions_multipleMinSdkCondition_throws() {
    Optional<ManifestDeliveryElement> element =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest("com.test.app", withMinSdkCondition(24), withMinSdkCondition(28)));
    assertThat(element).isPresent();

    ValidationException exception =
        assertThrows(ValidationException.class, () -> element.get().getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Multiple '<dist:min-sdk>' conditions are not supported.");
  }

  @Test
  public void moduleConditions_typoInElement_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "install-time")
                        .addChildElement(
                            XmlProtoElementBuilder.create(
                                DISTRIBUTION_NAMESPACE_URI, "condtions"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected <dist:install-time> element to contain only <dist:conditions> "
                + "element but found: 'condtions' with namespace URI: "
                + "'http://schemas.android.com/apk/distribution'");
  }

  @Test
  public void deliveryElement_typoInChildElement_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "instal-time")));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected <dist:delivery> element to contain only <dist:install-time> or "
                + "<dist:on-demand> elements but found: 'instal-time' with namespace URI: "
                + "'http://schemas.android.com/apk/distribution'");
  }

  @Test
  public void onDemandElement_childElement_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "on-demand")
                        .addChildElement(
                            XmlProtoElementBuilder.create(
                                DISTRIBUTION_NAMESPACE_URI, "conditions"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected <dist:on-demand> element to have no child elements but found: "
                + "'conditions' with namespace URI: "
                + "'http://schemas.android.com/apk/distribution'");
  }

  @Test
  public void onDemandElement_missingNamespace_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(XmlProtoElementBuilder.create("on-demand")));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected <dist:delivery> element to contain only <dist:install-time> or "
                + "<dist:on-demand> elements but found: 'on-demand' with namespace not provided");
  }

  @Test
  public void installTimeElement_missingNamespace_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(XmlProtoElementBuilder.create("install-time")));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected <dist:delivery> element to contain only <dist:install-time> or "
                + "<dist:on-demand> elements but found: 'install-time' with namespace not "
                + "provided");
  }

  @Test
  public void conditionsElement_missingNamespace_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "install-time")
                        .addChildElement(XmlProtoElementBuilder.create("conditions"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected <dist:install-time> element to contain only <dist:conditions> element but "
                + "found: 'conditions' with namespace not provided.");
  }

  @Test
  public void minSdkCondition_missingNamespace_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "install-time")
                        .addChildElement(
                            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "conditions")
                                .addChildElement(
                                    XmlProtoElementBuilder.create(
                                            DISTRIBUTION_NAMESPACE_URI, "min-sdk")
                                        .addAttribute(
                                            XmlProtoAttributeBuilder.create("value")
                                                .setValueAsDecimalInteger(21))))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () ->
                ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo)
                    .get()
                    .getModuleConditions());

    assertThat(exception)
        .hasMessageThat()
        .contains("Missing required 'dist:value' attribute in the 'min-sdk' condition element.");
  }

  @Test
  public void deviceFeatureCondition_missingNamespace_throws() {
    XmlNode nodeWithTypo =
        createAndroidManifestWithDeliveryElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "install-time")
                        .addChildElement(
                            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "conditions")
                                .addChildElement(
                                    XmlProtoElementBuilder.create(
                                            DISTRIBUTION_NAMESPACE_URI, "device-feature")
                                        .addAttribute(
                                            XmlProtoAttributeBuilder.create("name")
                                                .setValueAsString("android.hardware.feature"))))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () ->
                ManifestDeliveryElement.fromManifestRootNode(nodeWithTypo)
                    .get()
                    .getModuleConditions());

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Missing required 'dist:name' attribute in the 'device-feature' condition element.");
  }

  @Test
  public void userCountriesCondition_parsesOk() {
    XmlNode manifest =
        createAndroidManifestWithConditions(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "user-countries")
                .addChildElement(createCountryCodeEntry("pl"))
                .addChildElement(createCountryCodeEntry("GB"))
                .build());
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(manifest);
    assertThat(deliveryElement).isPresent();

    Optional<UserCountriesCondition> userCountriesCondition =
        deliveryElement.get().getModuleConditions().getUserCountriesCondition();
    assertThat(userCountriesCondition).isPresent();

    assertThat(userCountriesCondition.get().getCountries()).containsExactly("PL", "GB");
    assertThat(userCountriesCondition.get().getExclude()).isFalse();
  }

  @Test
  public void userCountriesCondition_parsesExclusionOk() {
    XmlNode manifest =
        createAndroidManifestWithConditions(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "user-countries")
                .addAttribute(
                    XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "exclude")
                        .setValueAsBoolean(true))
                .addChildElement(createCountryCodeEntry("FR"))
                .addChildElement(createCountryCodeEntry("SN"))
                .build());
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(manifest);
    assertThat(deliveryElement).isPresent();

    Optional<UserCountriesCondition> userCountriesCondition =
        deliveryElement.get().getModuleConditions().getUserCountriesCondition();
    assertThat(userCountriesCondition).isPresent();

    assertThat(userCountriesCondition.get().getCountries()).containsExactly("FR", "SN");
    assertThat(userCountriesCondition.get().getExclude()).isTrue();
  }

  @Test
  public void userCountriesCondition_badCountryElementName_throws() {
    XmlNode manifest =
        createAndroidManifestWithConditions(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "user-countries")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "country-typo")
                        .addAttribute(
                            XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "code")
                                .setValueAsString("DE")))
                .build());
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(manifest);
    assertThat(deliveryElement).isPresent();

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> deliveryElement.get().getModuleConditions().getUserCountriesCondition());

    assertThat(exception)
        .hasMessageThat()
        .contains("Expected only <dist:country> elements inside <dist:user-countries>");
  }

  @Test
  public void userCountriesCondition_missingCodeAttribute_throws() {
    XmlNode manifest =
        createAndroidManifestWithConditions(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "user-countries")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "country")
                        .addAttribute(
                            XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "code-typo")
                                .setValueAsString("DE")))
                .build());
    Optional<ManifestDeliveryElement> deliveryElement =
        ManifestDeliveryElement.fromManifestRootNode(manifest);
    assertThat(deliveryElement).isPresent();

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> deliveryElement.get().getModuleConditions().getUserCountriesCondition());

    assertThat(exception)
        .hasMessageThat()
        .contains("<dist:country> element is expected to have 'dist:code' attribute");
  }

  @Test
  public void getModuleConditions_multipleUserCountriesConditions_throws() {
    Optional<ManifestDeliveryElement> element =
        ManifestDeliveryElement.fromManifestRootNode(
            androidManifest(
                "com.test.app",
                withUserCountriesCondition(ImmutableList.of("en", "us")),
                withUserCountriesCondition(ImmutableList.of("sg"), /* exclude= */ true)));
    assertThat(element).isPresent();

    ValidationException exception =
        assertThrows(ValidationException.class, () -> element.get().getModuleConditions());
    assertThat(exception)
        .hasMessageThat()
        .contains("Multiple '<dist:user-countries>' conditions are not supported.");
  }

  private static XmlNode createAndroidManifestWithDeliveryElement(
      XmlProtoElementBuilder deliveryElement) {
    return XmlProtoNode.createElementNode(
            XmlProtoElementBuilder.create("manifest")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "module")
                        .addChildElement(deliveryElement))
                .build())
        .getProto();
  }

  private static XmlNode createAndroidManifestWithConditions(XmlProtoElement... conditions) {
    XmlProtoElementBuilder conditionsBuilder =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "conditions");
    for (XmlProtoElement condition : conditions) {
      conditionsBuilder.addChildElement(condition.toBuilder());
    }

    return XmlProtoNode.createElementNode(
            XmlProtoElementBuilder.create("manifest")
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "module")
                        .addChildElement(
                            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "delivery")
                                .addChildElement(
                                    XmlProtoElementBuilder.create(
                                            DISTRIBUTION_NAMESPACE_URI, "install-time")
                                        .addChildElement(conditionsBuilder))))
                .build())
        .getProto();
  }

  private static XmlProtoElementBuilder createCountryCodeEntry(String countryCode) {
    return XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "country")
        .addAttribute(
            XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "code")
                .setValueAsString(countryCode));
  }
}
