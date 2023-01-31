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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ALLOW_BACKUP_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ALLOW_BACKUP_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.APPLICATION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CERTIFICATE_DIGEST_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CERTIFICATE_DIGEST_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.COMPAT_SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DELIVERY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.FUSING_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ICON_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ICON_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.INCLUDE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.INSTALL_TIME_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_FEATURE_SPLIT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_SPLIT_REQUIRED_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_SPLIT_REQUIRED_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.LOCALE_CONFIG_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LOCALE_CONFIG_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_KEY_FUSED_MODULE_NAMES;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_KEY_SPLITS_REQUIRED;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROPERTY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REMOVABLE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.RESOURCE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ROUND_ICON_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ROUND_ICON_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_LIBRARY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_VERSION_MAJOR_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.TOOLS_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.USES_FEATURE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.USES_SDK_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.USES_SDK_LIBRARY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_MAJOR_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder.createAndroidAttribute;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.model.manifestelements.Activity;
import com.android.tools.build.bundletool.model.manifestelements.Provider;
import com.android.tools.build.bundletool.model.manifestelements.Receiver;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNodeBuilder;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;

/** Modifies the manifest in the protocol buffer format. */
public class ManifestEditor {

  private static final ImmutableList<String> SPLIT_NAME_ELEMENT_NAMES =
      ImmutableList.of(ACTIVITY_ELEMENT_NAME, SERVICE_ELEMENT_NAME, PROVIDER_ELEMENT_NAME);

  private final XmlProtoNodeBuilder rootNode;
  private final XmlProtoElementBuilder manifestElement;
  private final Version bundleToolVersion;

  public ManifestEditor(XmlProtoNode rootNode, Version bundleToolVersion) {
    this.rootNode = rootNode.toBuilder();
    this.manifestElement = this.rootNode.getElement();
    this.bundleToolVersion = bundleToolVersion;
  }

  public XmlProtoElementBuilder getRawProto() {
    return manifestElement;
  }

  /** Sets the minSdkVersion attribute. */
  @CanIgnoreReturnValue
  public ManifestEditor setMinSdkVersion(int minSdkVersion) {
    return setUsesSdkAttribute(
        MIN_SDK_VERSION_ATTRIBUTE_NAME, MIN_SDK_VERSION_RESOURCE_ID, minSdkVersion);
  }

  /** Sets split id and related manifest entries for feature/master split. */
  @CanIgnoreReturnValue
  public ManifestEditor setSplitIdForFeatureSplit(String splitId) {
    if (isBaseSplit(splitId)) {
      manifestElement.removeAttribute(NO_NAMESPACE_URI, "split");
      manifestElement.removeAttribute(ANDROID_NAMESPACE_URI, "isFeatureSplit");
    } else {
      manifestElement.getOrCreateAttribute("split").setValueAsString(splitId);
      manifestElement
          .getOrCreateAndroidAttribute("isFeatureSplit", IS_FEATURE_SPLIT_RESOURCE_ID)
          .setValueAsBoolean(true);
    }
    manifestElement.removeAttribute(NO_NAMESPACE_URI, "configForSplit");
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setHasCode(boolean value) {
    // Stamp hasCode="false" on the Application element in the Manifest.
    // This attribute's default is "true" even if absent.
    return setApplcationAttributeBoolean("hasCode", HAS_CODE_RESOURCE_ID, value);
  }

  @CanIgnoreReturnValue
  public ManifestEditor setPackage(String packageName) {
    manifestElement.getOrCreateAttribute("package").setValueAsString(packageName);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setVersionCode(int versionCode) {
    manifestElement
        .getOrCreateAndroidAttribute("versionCode", VERSION_CODE_RESOURCE_ID)
        .setValueAsDecimalInteger(versionCode);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setVersionName(String versionName) {
    manifestElement
        .getOrCreateAndroidAttribute(VERSION_NAME_ATTRIBUTE_NAME, VERSION_NAME_RESOURCE_ID)
        .setValueAsString(versionName);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setConfigForSplit(String featureSplitId) {
    manifestElement.getOrCreateAttribute("configForSplit").setValueAsString(featureSplitId);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setSplitId(String splitId) {
    manifestElement.getOrCreateAttribute("split").setValueAsString(splitId);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setTargetSandboxVersion(int version) {
    manifestElement
        .getOrCreateAndroidAttribute(
            TARGET_SANDBOX_VERSION_ATTRIBUTE_NAME, TARGET_SANDBOX_VERSION_RESOURCE_ID)
        .setValueAsDecimalInteger(version);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setLocaleConfig(int resourceId) {
    return setApplcationAttributeRefId(
        LOCALE_CONFIG_ATTRIBUTE_NAME, LOCALE_CONFIG_RESOURCE_ID, resourceId);
  }

  @CanIgnoreReturnValue
  public ManifestEditor addMetaDataString(String key, String value) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsString(value));
  }

  @CanIgnoreReturnValue
  public ManifestEditor addMetaDataInteger(String key, int value) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsDecimalInteger(value));
  }

  @CanIgnoreReturnValue
  public ManifestEditor addMetaDataBoolean(String key, boolean value) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsBoolean(value));
  }

  @CanIgnoreReturnValue
  public ManifestEditor addMetaDataResourceId(String key, int resourceId) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", RESOURCE_RESOURCE_ID).setValueAsRefId(resourceId));
  }

  @CanIgnoreReturnValue
  private ManifestEditor addMetaDataValue(String key, XmlProtoAttributeBuilder valueAttribute) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create("meta-data")
                .addAttribute(
                    createAndroidAttribute("name", NAME_RESOURCE_ID).setValueAsString(key))
                .addAttribute(valueAttribute));
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor addApplicationChildElement(XmlProtoElement element) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(element.toBuilder());
    return this;
  }

  /**
   * Sets the 'android:extractNativeLibs' value in the {@code application} tag.
   *
   * <p>Note: the {@code application} tag is created if not found.
   */
  @CanIgnoreReturnValue
  public ManifestEditor setExtractNativeLibsValue(boolean value) {
    setApplcationAttributeBoolean(
        EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME, EXTRACT_NATIVE_LIBS_RESOURCE_ID, value);
    return this;
  }

  /**
   * Sets names of the fused modules as a {@code <meta-data android:name="..."
   * android:value="module1,module2,..."/>} element inside the {@code <application>} element.
   */
  @CanIgnoreReturnValue
  public ManifestEditor setFusedModuleNames(ImmutableList<String> moduleNames) {
    // Make sure the names are unique and sort for deterministic behavior.
    String moduleNamesString = moduleNames.stream().sorted().distinct().collect(joining(","));

    setMetadataValue(
        META_DATA_KEY_FUSED_MODULE_NAMES,
        createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsString(moduleNamesString));

    return this;
  }

  /**
   * Sets a flag whether the app is able to run without any config splits.
   *
   * <p>The information is stored as:
   *
   * <ul>
   *   <li>{@code <meta-data android:name="..." android:value="..."/>} element inside the {@code
   *       <application>} element (read by the PlayCore library).
   *   <li>{@code <application android:isSplitRequired="..."/>} attribute (read by the Android
   *       Platform since Q).
   * </ul>
   */
  @CanIgnoreReturnValue
  public ManifestEditor setSplitsRequired(boolean value) {
    setMetadataValue(
        META_DATA_KEY_SPLITS_REQUIRED,
        createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsBoolean(value));

    return setApplcationAttributeBoolean(
        IS_SPLIT_REQUIRED_ATTRIBUTE_NAME, IS_SPLIT_REQUIRED_RESOURCE_ID, value);
  }

  /** Adds an empty {@code <application>} element in the manifest if none is present. */
  @CanIgnoreReturnValue
  public ManifestEditor addApplicationElementIfMissing() {
    manifestElement.getOrCreateChildElement(APPLICATION_ELEMENT_NAME);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setAllowBackup(Boolean value) {
    return setApplcationAttributeBoolean(
        ALLOW_BACKUP_ATTRIBUTE_NAME, ALLOW_BACKUP_RESOURCE_ID, value);
  }

  @CanIgnoreReturnValue
  private ManifestEditor setApplcationAttributeBoolean(
      String attributeName, int resourceId, boolean value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(attributeName, resourceId)
        .setValueAsBoolean(value);
    return this;
  }

  @CanIgnoreReturnValue
  private ManifestEditor setApplcationAttributeRefId(
      String attributeName, int resourceId, int value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(attributeName, resourceId)
        .setValueAsRefId(value);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setIcon(int resourceId) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(ICON_ATTRIBUTE_NAME, ICON_RESOURCE_ID)
        .setValueAsRefId(resourceId);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setRoundIcon(int resourceId) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(ROUND_ICON_ATTRIBUTE_NAME, ROUND_ICON_RESOURCE_ID)
        .setValueAsRefId(resourceId);
    return this;
  }

  /**
   * Removes the {@code splitName} attribute from activities, services and providers.
   *
   * <p>This is useful for converting between install and instant splits.
   */
  @CanIgnoreReturnValue
  public ManifestEditor removeSplitName() {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getChildrenElements(el -> SPLIT_NAME_ELEMENT_NAMES.contains(el.getName()))
        .forEach(element -> element.removeAndroidAttribute(SPLIT_NAME_RESOURCE_ID));

    return this;
  }

  /**
   * Removes the activities, services, and providers that contain an unknown {@code splitName}.
   *
   * <p>This is useful for converting between install and instant splits.
   */
  @CanIgnoreReturnValue
  public ManifestEditor removeUnknownSplitComponents(ImmutableSet<String> allModuleNames) {
    Optional<XmlProtoElementBuilder> applicationElement =
        manifestElement.getOptionalChildElement(APPLICATION_ELEMENT_NAME);
    if (!applicationElement.isPresent()) {
      return this;
    }
    applicationElement
        .get()
        .removeChildrenElementsIf(
            el ->
                el.isElement()
                    && el.getElement()
                        .getAndroidAttribute(SPLIT_NAME_RESOURCE_ID)
                        .filter(attr -> !allModuleNames.contains(attr.getValueAsString()))
                        .isPresent());
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor addActivity(Activity activity) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(activity.asXmlProtoElement().toBuilder());
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor setActivityTheme(String activityName, int themeResId) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateChildElement(ACTIVITY_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(THEME_ATTRIBUTE_NAME, THEME_RESOURCE_ID)
        .setValueAsRefId(themeResId);
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor addReceiver(Receiver receiver) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(receiver.asXmlProtoElement().toBuilder());
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor addProvider(Provider provider) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(provider.asXmlProtoElement().toBuilder());
    return this;
  }

  /** Adds uses-sdk-library tag to the manifest. */
  @CanIgnoreReturnValue
  public ManifestEditor addUsesSdkLibraryElement(
      String name, long versionMajor, String certDigest) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(USES_SDK_LIBRARY_ELEMENT_NAME)
                .addAttribute(
                    createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                        .setValueAsString(name))
                .addAttribute(
                    createAndroidAttribute(
                            SDK_VERSION_MAJOR_ATTRIBUTE_NAME, VERSION_MAJOR_RESOURCE_ID)
                        .setValueAsString(String.valueOf(versionMajor)))
                .addAttribute(
                    createAndroidAttribute(
                            CERTIFICATE_DIGEST_ATTRIBUTE_NAME, CERTIFICATE_DIGEST_RESOURCE_ID)
                        .setValueAsString(certDigest)));
    return this;
  }

  /**
   * Creates an <sdk-library> element and populates it with SDK package name and Android version
   * major.
   */
  @CanIgnoreReturnValue
  public ManifestEditor setSdkLibraryElement(String sdkPackageName, int versionMajor) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateChildElement(SDK_LIBRARY_ELEMENT_NAME)
        .addAttribute(
            createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                .setValueAsString(sdkPackageName))
        .addAttribute(
            createAndroidAttribute(SDK_VERSION_MAJOR_ATTRIBUTE_NAME, VERSION_MAJOR_RESOURCE_ID)
                .setValueAsDecimalInteger(versionMajor));
    return this;
  }

  /** Creates a <property> element and populates it with SDK patch version. */
  @CanIgnoreReturnValue
  public ManifestEditor setSdkPatchVersionProperty(int patchVersion) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(PROPERTY_ELEMENT_NAME)
                .addAttribute(
                    createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                        .setValueAsString(SDK_PATCH_VERSION_ATTRIBUTE_NAME))
                .addAttribute(
                    createAndroidAttribute(VALUE_ATTRIBUTE_NAME, VALUE_RESOURCE_ID)
                        .setValueAsDecimalInteger(patchVersion)));
    return this;
  }

  /** Creates a <property> element and populates it with the SDK provider class name. */
  @CanIgnoreReturnValue
  public ManifestEditor setSdkProviderClassName(String sdkProviderClassName) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(PROPERTY_ELEMENT_NAME)
                .addAttribute(
                    createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                        .setValueAsString(SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME))
                .addAttribute(
                    createAndroidAttribute(VALUE_ATTRIBUTE_NAME, VALUE_RESOURCE_ID)
                        .setValueAsString(sdkProviderClassName)));
    return this;
  }

  /** Creates a <property> element and populates it with the compat SDK provider class name. */
  @CanIgnoreReturnValue
  public ManifestEditor setCompatSdkProviderClassName(String compatSdkProviderClassName) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(PROPERTY_ELEMENT_NAME)
                .addAttribute(
                    createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                        .setValueAsString(COMPAT_SDK_PROVIDER_CLASS_NAME_ATTRIBUTE_NAME))
                .addAttribute(
                    createAndroidAttribute(VALUE_ATTRIBUTE_NAME, VALUE_RESOURCE_ID)
                        .setValueAsString(compatSdkProviderClassName)));
    return this;
  }

  /**
   * Sets delivery options that are required for a removable install-time module, which will be
   * fused in standalone variants.
   */
  @CanIgnoreReturnValue
  public ManifestEditor setDeliveryOptionsForRuntimeEnabledSdkModule() {
    manifestElement
        .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, MODULE_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, DELIVERY_ELEMENT_NAME)
                .addChildElement(
                    XmlProtoElementBuilder.create(
                            DISTRIBUTION_NAMESPACE_URI, INSTALL_TIME_ELEMENT_NAME)
                        .addChildElement(
                            XmlProtoElementBuilder.create(
                                    DISTRIBUTION_NAMESPACE_URI, REMOVABLE_ELEMENT_NAME)
                                .addAttribute(
                                    XmlProtoAttributeBuilder.create(
                                            DISTRIBUTION_NAMESPACE_URI, VALUE_ATTRIBUTE_NAME)
                                        .setResourceId(VALUE_RESOURCE_ID)
                                        .setValueAsBoolean(true)))))
        .addChildElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, FUSING_ELEMENT_NAME)
                .addAttribute(
                    XmlProtoAttributeBuilder.create(
                            DISTRIBUTION_NAMESPACE_URI, INCLUDE_ATTRIBUTE_NAME)
                        .setValueAsBoolean(true)));
    return this;
  }

  /** Sets distribution module that is required for a recovery module. */
  @CanIgnoreReturnValue
  public ManifestEditor setDistributionModuleForRecoveryModule() {
    manifestElement
        .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, MODULE_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, DELIVERY_ELEMENT_NAME)
                .addChildElement(
                    XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "on-demand")))
        .addChildElement(
            XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, FUSING_ELEMENT_NAME)
                .addAttribute(
                    XmlProtoAttributeBuilder.create(
                            DISTRIBUTION_NAMESPACE_URI, INCLUDE_ATTRIBUTE_NAME)
                        .setValueAsBoolean(false)));
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor removeUsesSdkElement() {
    manifestElement.removeChildrenElementsIf(
        childElement ->
            childElement.isElement()
                && childElement.getElement().getName().equals(USES_SDK_ELEMENT_NAME));
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor addUsesFeatureElement(String featureName, boolean isRequired) {
    manifestElement.addChildElement(
        XmlProtoElementBuilder.create(USES_FEATURE_ELEMENT_NAME)
            .addAttribute(
                createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                    .setValueAsString(featureName))
            .addAttribute(
                createAndroidAttribute(REQUIRED_ATTRIBUTE_NAME, REQUIRED_RESOURCE_ID)
                    .setValueAsBoolean(isRequired)));
    return this;
  }

  @CanIgnoreReturnValue
  public ManifestEditor addManifestChildElement(XmlProtoElement element) {
    manifestElement.addChildElement(element.toBuilder());
    return this;
  }

  /**
   * Copies an android attribute with resource id {@code attrResourceId} from 'manifest' element of
   * android manifest {@code from} into current.
   */
  @CanIgnoreReturnValue
  public ManifestEditor copyManifestElementAndroidAttribute(
      AndroidManifest from, int attrResourceId) {
    Optional<XmlProtoAttribute> attribute =
        from.getManifestElement().getAndroidAttribute(attrResourceId);
    if (attribute.isPresent()) {
      manifestElement.addAttribute(attribute.get().toBuilder());
    }

    return this;
  }

  /**
   * Copies an android attribute with resource id {@code attrResourceId} from 'application' element
   * of android manifest {@code from} into current manifest.
   */
  @CanIgnoreReturnValue
  public ManifestEditor copyApplicationElementAndroidAttribute(
      AndroidManifest from, int attrResourceId) {
    Optional<XmlProtoAttribute> attribute =
        from.getManifestElement()
            .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
            .flatMap(applicationElement -> applicationElement.getAndroidAttribute(attrResourceId));
    if (attribute.isPresent()) {
      manifestElement
          .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
          .addAttribute(attribute.get().toBuilder());
    }

    return this;
  }

  /**
   * Copies children elements with name {@code elementsName} from 'manifest' element of android
   * manifest {@code from}.
   */
  @CanIgnoreReturnValue
  public ManifestEditor copyChildrenElements(AndroidManifest from, String elementsName) {
    from.getManifestElement()
        .getChildrenElements(elementsName)
        .forEach(element -> manifestElement.addChildElement(element.toBuilder()));
    return this;
  }

  /**
   * Deletes elements that have {@link
   * AndroidManifest#REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME} attribute with value {@code
   * true}.
   */
  @CanIgnoreReturnValue
  public ManifestEditor removeElementsRequiredByPrivacySandboxSdk() {
    removeElementsRequiredByPrivacySandboxSdkRecursively(manifestElement);
    return this;
  }

  /**
   * Deletes {@link AndroidManifest#REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME} attributes.
   *
   * <p>This is different from the method {@link #removeElementsRequiredByPrivacySandboxSdk()} as it
   * removes just the attribute, rather than the element itself.
   */
  @CanIgnoreReturnValue
  public ManifestEditor removeRequiredByPrivacySandboxSdkAttributes() {
    removeRequiredByPrivacySandboxSdkAttributesRecursively(manifestElement);
    return this;
  }

  private void removeElementsRequiredByPrivacySandboxSdkRecursively(
      XmlProtoElementBuilder element) {
    element.removeChildrenElementsIf(
        childElement -> childElement.isElement() && isRequiredByPrivacySandboxSdk(childElement));
    element
        .getChildrenElements()
        .forEach(this::removeElementsRequiredByPrivacySandboxSdkRecursively);
  }

  private void removeRequiredByPrivacySandboxSdkAttributesRecursively(
      XmlProtoElementBuilder element) {
    element
        .getChildrenElements()
        .forEach(
            childElement -> {
              if (hasRequiredByPrivacySandboxSdkAttr(childElement)) {
                childElement.removeAttribute(
                    TOOLS_NAMESPACE_URI, REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME);
              }
              removeRequiredByPrivacySandboxSdkAttributesRecursively(childElement);
            });
  }

  private boolean isRequiredByPrivacySandboxSdk(XmlProtoNodeBuilder element) {
    return hasRequiredByPrivacySandboxSdkAttr(element)
        && element
            .getElement()
            .getAttribute(TOOLS_NAMESPACE_URI, REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME)
            .get()
            .getValueAsBoolean();
  }

  private boolean hasRequiredByPrivacySandboxSdkAttr(XmlProtoNodeBuilder element) {
    return hasRequiredByPrivacySandboxSdkAttr(element.getElement());
  }

  private boolean hasRequiredByPrivacySandboxSdkAttr(XmlProtoElementBuilder element) {
    return element
        .getAttribute(TOOLS_NAMESPACE_URI, REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME)
        .isPresent();
  }

  /** Generates the modified manifest. */
  @CheckReturnValue
  public AndroidManifest save() {
    return AndroidManifest.create(rootNode.build(), bundleToolVersion);
  }

  @CanIgnoreReturnValue
  private ManifestEditor setMetadataValue(String name, XmlProtoAttributeBuilder valueAttr) {
    XmlProtoElementBuilder applicationEl =
        manifestElement.getOrCreateChildElement(APPLICATION_ELEMENT_NAME);

    Optional<XmlProtoElementBuilder> existingMetadataEl =
        applicationEl
            .getChildrenElements(META_DATA_ELEMENT_NAME)
            .filter(
                metadataEl ->
                    metadataEl
                        .getAndroidAttribute(NAME_RESOURCE_ID)
                        .map(nameAttr -> name.equals(nameAttr.getValueAsString()))
                        .orElse(false))
            .collect(toOptional());

    if (existingMetadataEl.isPresent()) {
      existingMetadataEl.get().removeAndroidAttribute(VALUE_RESOURCE_ID).addAttribute(valueAttr);
    } else {
      applicationEl.addChildElement(
          XmlProtoElementBuilder.create(META_DATA_ELEMENT_NAME)
              .addAttribute(createAndroidAttribute("name", NAME_RESOURCE_ID).setValueAsString(name))
              .addAttribute(valueAttr));
    }

    return this;
  }

  @CanIgnoreReturnValue
  private ManifestEditor setUsesSdkAttribute(String attributeName, int attributeResId, int value) {
    manifestElement
        .getOrCreateChildElement(USES_SDK_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(attributeName, attributeResId)
        .setValueAsDecimalInteger(value);
    return this;
  }

  private static boolean isBaseSplit(String splitId) {
    return splitId.isEmpty();
  }
}
