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
import static com.android.tools.build.bundletool.model.AndroidManifest.BANNER_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.BANNER_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.CERTIFICATE_DIGEST_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CERTIFICATE_DIGEST_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.DATA_EXTRACTION_RULES_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DATA_EXTRACTION_RULES_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.DESCRIPTION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DESCRIPTION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.FULL_BACKUP_CONTENT_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.FULL_BACKUP_CONTENT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.FULL_BACKUP_ONLY_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.FULL_BACKUP_ONLY_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_FRAGILE_USER_DATA_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_FRAGILE_USER_DATA_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ICON_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ICON_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_FEATURE_SPLIT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_GAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_GAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_SPLIT_REQUIRED_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_SPLIT_REQUIRED_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.LABEL_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.LARGE_HEAP_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LARGE_HEAP_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.LOCALE_CONFIG_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.LOCALE_CONFIG_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_KEY_FUSED_MODULE_NAMES;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_KEY_SPLITS_REQUIRED;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROPERTY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_ACCOUNT_TYPE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_ACCOUNT_TYPE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.RESOURCE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.RESTRICTED_ACCOUNT_TYPE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.RESTRICTED_ACCOUNT_TYPE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_LIBRARY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_VERSION_MAJOR_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SHARED_USER_ID_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SHARED_USER_ID_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SHARED_USER_LABEL_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SHARED_USER_LABEL_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SDK_VERSION_RESOURCE_ID;
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
import com.android.tools.build.bundletool.model.manifestelements.Receiver;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNodeBuilder;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
  public ManifestEditor setMinSdkVersion(int minSdkVersion) {
    return setUsesSdkAttribute(
        MIN_SDK_VERSION_ATTRIBUTE_NAME, MIN_SDK_VERSION_RESOURCE_ID, minSdkVersion);
  }

  /** Sets the maxSdkVersion attribute. */
  public ManifestEditor setMaxSdkVersion(int maxSdkVersion) {
    return setUsesSdkAttribute(
        MAX_SDK_VERSION_ATTRIBUTE_NAME, MAX_SDK_VERSION_RESOURCE_ID, maxSdkVersion);
  }

  /** Sets the targetSdkVersion attribute. */
  public ManifestEditor setTargetSdkVersion(int targetSdkVersion) {
    return setUsesSdkAttribute(
        TARGET_SDK_VERSION_ATTRIBUTE_NAME, TARGET_SDK_VERSION_RESOURCE_ID, targetSdkVersion);
  }

  /** Sets split id and related manifest entries for feature/master split. */
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

  public ManifestEditor setHasCode(boolean value) {
    // Stamp hasCode="false" on the Application element in the Manifest.
    // This attribute's default is "true" even if absent.
    return setApplcationAttributeBoolean("hasCode", HAS_CODE_RESOURCE_ID, value);
  }

  public ManifestEditor setPackage(String packageName) {
    manifestElement.getOrCreateAttribute("package").setValueAsString(packageName);
    return this;
  }

  public ManifestEditor setVersionCode(int versionCode) {
    manifestElement
        .getOrCreateAndroidAttribute("versionCode", VERSION_CODE_RESOURCE_ID)
        .setValueAsDecimalInteger(versionCode);
    return this;
  }

  public ManifestEditor setVersionName(String versionName) {
    manifestElement
        .getOrCreateAndroidAttribute(VERSION_NAME_ATTRIBUTE_NAME, VERSION_NAME_RESOURCE_ID)
        .setValueAsString(versionName);
    return this;
  }

  public ManifestEditor setConfigForSplit(String featureSplitId) {
    manifestElement.getOrCreateAttribute("configForSplit").setValueAsString(featureSplitId);
    return this;
  }

  public ManifestEditor setSplitId(String splitId) {
    manifestElement.getOrCreateAttribute("split").setValueAsString(splitId);
    return this;
  }

  public ManifestEditor setTargetSandboxVersion(int version) {
    manifestElement
        .getOrCreateAndroidAttribute(
            TARGET_SANDBOX_VERSION_ATTRIBUTE_NAME, TARGET_SANDBOX_VERSION_RESOURCE_ID)
        .setValueAsDecimalInteger(version);
    return this;
  }

  public ManifestEditor setSharedUserId(String value) {
    manifestElement
        .getOrCreateAndroidAttribute(SHARED_USER_ID_ATTRIBUTE_NAME, SHARED_USER_ID_RESOURCE_ID)
        .setValueAsString(value);
    return this;
  }

  public ManifestEditor setSharedUserLabel(Integer valueRefId) {
    manifestElement
        .getOrCreateAndroidAttribute(
            SHARED_USER_LABEL_ATTRIBUTE_NAME, SHARED_USER_LABEL_RESOURCE_ID)
        .setValueAsRefId(valueRefId);
    return this;
  }

  public ManifestEditor setLocaleConfig(int resourceId) {
    return setApplcationAttributeRefId(
        LOCALE_CONFIG_ATTRIBUTE_NAME, LOCALE_CONFIG_RESOURCE_ID, resourceId);
  }

  public ManifestEditor addMetaDataString(String key, String value) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsString(value));
  }

  public ManifestEditor addMetaDataInteger(String key, int value) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsDecimalInteger(value));
  }

  public ManifestEditor addMetaDataBoolean(String key, boolean value) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsBoolean(value));
  }

  public ManifestEditor addMetaDataResourceId(String key, int resourceId) {
    return addMetaDataValue(
        key, createAndroidAttribute("value", RESOURCE_RESOURCE_ID).setValueAsRefId(resourceId));
  }

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

  /**
   * Sets the 'android:extractNativeLibs' value in the {@code application} tag.
   *
   * <p>Note: the {@code application} tag is created if not found.
   */
  public ManifestEditor setExtractNativeLibsValue(boolean value) {
    setApplcationAttributeBoolean(
        EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME, EXTRACT_NATIVE_LIBS_RESOURCE_ID, value);
    return this;
  }

  /**
   * Sets names of the fused modules as a {@code <meta-data android:name="..."
   * android:value="module1,module2,..."/>} element inside the {@code <application>} element.
   */
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
  public ManifestEditor setSplitsRequired(boolean value) {
    setMetadataValue(
        META_DATA_KEY_SPLITS_REQUIRED,
        createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsBoolean(value));

    return setApplcationAttributeBoolean(
        IS_SPLIT_REQUIRED_ATTRIBUTE_NAME, IS_SPLIT_REQUIRED_RESOURCE_ID, value);
  }

  /** Adds an empty {@code <application>} element in the manifest if none is present. */
  public ManifestEditor addApplicationElementIfMissing() {
    manifestElement.getOrCreateChildElement(APPLICATION_ELEMENT_NAME);
    return this;
  }

  public ManifestEditor setDescription(Integer refIdValue) {
    return setApplcationAttributeRefId(
        DESCRIPTION_ATTRIBUTE_NAME, DESCRIPTION_RESOURCE_ID, refIdValue);
  }

  public ManifestEditor setHasFragileUserData(Boolean value) {
    return setApplcationAttributeBoolean(
        HAS_FRAGILE_USER_DATA_ATTRIBUTE_NAME, HAS_FRAGILE_USER_DATA_RESOURCE_ID, value);
  }

  public ManifestEditor setIsGame(Boolean value) {
    return setApplcationAttributeBoolean(IS_GAME_ATTRIBUTE_NAME, IS_GAME_RESOURCE_ID, value);
  }

  public ManifestEditor setLabelAsString(String value) {
    return setApplcationAttributeString(LABEL_ATTRIBUTE_NAME, LABEL_RESOURCE_ID, value);
  }

  public ManifestEditor setLabelAsRefId(Integer refIdValue) {
    return setApplcationAttributeRefId(LABEL_ATTRIBUTE_NAME, LABEL_RESOURCE_ID, refIdValue);
  }

  public ManifestEditor setIcon(Integer refIdValue) {
    return setApplcationAttributeRefId(ICON_ATTRIBUTE_NAME, ICON_RESOURCE_ID, refIdValue);
  }

  public ManifestEditor setBanner(Integer refIdValue) {
    return setApplcationAttributeRefId(BANNER_ATTRIBUTE_NAME, BANNER_RESOURCE_ID, refIdValue);
  }

  public ManifestEditor setAllowBackup(Boolean value) {
    return setApplcationAttributeBoolean(
        ALLOW_BACKUP_ATTRIBUTE_NAME, ALLOW_BACKUP_RESOURCE_ID, value);
  }

  public ManifestEditor setFullBackupOnly(Boolean value) {
    return setApplcationAttributeBoolean(
        FULL_BACKUP_ONLY_ATTRIBUTE_NAME, FULL_BACKUP_ONLY_RESOURCE_ID, value);
  }

  public ManifestEditor setFullBackupContent(Integer value) {
    return setApplcationAttributeRefId(
        FULL_BACKUP_CONTENT_ATTRIBUTE_NAME, FULL_BACKUP_CONTENT_RESOURCE_ID, value);
  }

  public ManifestEditor setDataExtractionRules(Integer value) {
    return setApplcationAttributeRefId(
        DATA_EXTRACTION_RULES_ATTRIBUTE_NAME, DATA_EXTRACTION_RULES_RESOURCE_ID, value);
  }

  public ManifestEditor setRestrictedAccountType(String value) {
    return setApplcationAttributeString(
        RESTRICTED_ACCOUNT_TYPE_ATTRIBUTE_NAME, RESTRICTED_ACCOUNT_TYPE_RESOURCE_ID, value);
  }

  public ManifestEditor setRequiredAccountType(String value) {
    return setApplcationAttributeString(
        REQUIRED_ACCOUNT_TYPE_ATTRIBUTE_NAME, REQUIRED_ACCOUNT_TYPE_RESOURCE_ID, value);
  }

  public ManifestEditor setLargeHeap(Boolean value) {
    return setApplcationAttributeBoolean(LARGE_HEAP_ATTRIBUTE_NAME, LARGE_HEAP_RESOURCE_ID, value);
  }

  private ManifestEditor setApplcationAttributeString(
      String attributeName, int resourceId, String value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(attributeName, resourceId)
        .setValueAsString(value);
    return this;
  }

  private ManifestEditor setApplcationAttributeBoolean(
      String attributeName, int resourceId, boolean value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(attributeName, resourceId)
        .setValueAsBoolean(value);
    return this;
  }

  private ManifestEditor setApplcationAttributeRefId(
      String attributeName, int resourceId, int value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(attributeName, resourceId)
        .setValueAsRefId(value);
    return this;
  }

  /**
   * Removes the {@code splitName} attribute from activities, services and providers.
   *
   * <p>This is useful for converting between install and instant splits.
   */
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

  public ManifestEditor addActivity(Activity activity) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(activity.asXmlProtoElement().toBuilder());
    return this;
  }

  public ManifestEditor addReceiver(Receiver receiver) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(receiver.asXmlProtoElement().toBuilder());
    return this;
  }

  public ManifestEditor copyPermissions(AndroidManifest manifest) {
    manifest
        .getPermissions()
        .forEach(permission -> manifestElement.addChildElement(permission.toBuilder()));
    return this;
  }

  public ManifestEditor copyPermissionGroups(AndroidManifest manifest) {
    manifest
        .getPermissionGroups()
        .forEach(permissionGroup -> manifestElement.addChildElement(permissionGroup.toBuilder()));
    return this;
  }

  public ManifestEditor copyPermissionTrees(AndroidManifest manifest) {
    manifest
        .getPermissionTrees()
        .forEach(permissionTree -> manifestElement.addChildElement(permissionTree.toBuilder()));
    return this;
  }

  /** Adds uses-sdk-library tag to the manifest. */
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

  /** Generates the modified manifest. */
  @CheckReturnValue
  public AndroidManifest save() {
    return AndroidManifest.create(rootNode.build(), bundleToolVersion);
  }

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
