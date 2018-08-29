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
import static com.android.tools.build.bundletool.model.AndroidManifest.APPLICATION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.GL_ES_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.GL_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.IS_FEATURE_SPLIT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_KEY_FUSED_MODULE_NAMES;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SUPPORTS_GL_TEXTURE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.USES_FEATURE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.USES_SDK_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.VALUE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.utils.xmlproto.XmlProtoAttributeBuilder.createAndroidAttribute;
import static com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElement.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElement.NO_NAMESPACE_URI;
import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNodeBuilder;
import com.google.common.collect.ImmutableList;
import javax.annotation.CheckReturnValue;

/** Modifies the manifest in the protocol buffer format. */
public class ManifestEditor {

  private static final int OPEN_GL_VERSION_MULTIPLIER = 0x10000;
  private static final ImmutableList<String> SPLIT_NAME_ELEMENT_NAMES =
      ImmutableList.of(ACTIVITY_ELEMENT_NAME, SERVICE_ELEMENT_NAME, PROVIDER_ELEMENT_NAME);

  private final XmlProtoNodeBuilder rootNode;
  private final XmlProtoElementBuilder manifestElement;

  public ManifestEditor(XmlProtoNode rootNode) {
    this.rootNode = rootNode.toBuilder();
    this.manifestElement = this.rootNode.getElement();
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
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute("hasCode", HAS_CODE_RESOURCE_ID)
        .setValueAsBoolean(value);
    return this;
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
        .getOrCreateAndroidAttribute("targetSandboxVersion", TARGET_SANDBOX_VERSION_RESOURCE_ID)
        .setValueAsDecimalInteger(version);
    return this;
  }

  public ManifestEditor addMetaDataString(String key, String value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create("meta-data")
                .addAttribute(
                    createAndroidAttribute("name", NAME_RESOURCE_ID).setValueAsString(key))
                .addAttribute(
                    createAndroidAttribute("value", VALUE_RESOURCE_ID).setValueAsString(value)));
    return this;
  }

  public ManifestEditor addMetaDataInteger(String key, int value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create("meta-data")
                .addAttribute(
                    createAndroidAttribute("name", NAME_RESOURCE_ID).setValueAsString(key))
                .addAttribute(
                    createAndroidAttribute("value", VALUE_RESOURCE_ID)
                        .setValueAsDecimalInteger(value)));
    return this;
  }


  /**
   * Sets the 'android:extractNativeLibs' value in the {@code application} tag.
   *
   * <p>Note: the {@code application} tag is created if not found.
   */
  public ManifestEditor setExtractNativeLibsValue(boolean value) {
    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .getOrCreateAndroidAttribute(
            EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME, EXTRACT_NATIVE_LIBS_RESOURCE_ID)
        .setValueAsBoolean(value);
    return this;
  }

  /**
   * Sets names of the fused modules as a {@code <meta-data android:name="..."
   * android:value="module1,module2,..."/>} element inside the {@code <application>} element.
   */
  public ManifestEditor setFusedModuleNames(ImmutableList<String> moduleNames) {
    // Make sure the names are unique and sort for deterministic behavior.
    String moduleNamesString = moduleNames.stream().sorted().distinct().collect(joining(","));

    manifestElement
        .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
        .addChildElement(
            XmlProtoElementBuilder.create(META_DATA_ELEMENT_NAME)
                .addAttribute(
                    createAndroidAttribute("name", NAME_RESOURCE_ID)
                        .setValueAsString(META_DATA_KEY_FUSED_MODULE_NAMES))
                .addAttribute(
                    createAndroidAttribute("value", VALUE_RESOURCE_ID)
                        .setValueAsString(moduleNamesString)));
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

  /** Generates the modified manifest. */
  @CheckReturnValue
  public AndroidManifest save() {
    return AndroidManifest.create(rootNode.build());
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
