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

package com.android.tools.build.bundletool.manifest;

import static com.android.tools.build.bundletool.manifest.AndroidManifest.ANDROID_NAMESPACE;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.APPLICATION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.EXTRACT_NATIVE_LIBS_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.GL_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.MAX_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.MAX_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.META_DATA_ELEMENT_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.META_DATA_KEY_FUSED_MODULE_NAMES;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.MIN_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.MIN_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.SUPPORTS_GL_TEXTURE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.USES_FEATURE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.USES_SDK_ELEMENT_NAME;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.VALUE_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.AndroidManifest.VERSION_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findOrCreateAndroidAttributeBuilder;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.findOrCreateAttributeBuilder;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.getExactlyOneElementBuilder;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.getFirstOrCreateElementBuilder;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.removeAttribute;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.setAttributeValueAsBoolean;
import static com.android.tools.build.bundletool.manifest.ProtoXmlHelper.setAttributeValueAsDecimalInteger;
import static java.util.stream.Collectors.joining;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.google.common.collect.ImmutableList;

/** Modifies the manifest in the protocol buffer format. */
public class ManifestEditor {

  private static final int OPEN_GL_VERSION_MULTIPLIER = 0x10000;

  private final XmlNode.Builder rootNodeBuilder;

  public ManifestEditor(XmlNode rootNode) {
    rootNodeBuilder = rootNode.toBuilder();
  }

  /** Sets the minSdkVersion attribute. */
  public ManifestEditor setMinSdkVersion(int minSdkVersion) {
    return setUsesSdkAttribute(
        MIN_SDK_VERSION_ATTRIBUTE_NAME, minSdkVersion, MIN_SDK_VERSION_RESOURCE_ID);
  }

  /** Sets the maxSdkVersion attribute. */
  public ManifestEditor setMaxSdkVersion(int maxSdkVersion) {
    return setUsesSdkAttribute(
        MAX_SDK_VERSION_ATTRIBUTE_NAME, maxSdkVersion, MAX_SDK_VERSION_RESOURCE_ID);
  }

  /** Sets split id and related manifest entries for feature/master split. */
  public ManifestEditor setSplitIdForFeatureSplit(String splitId) {
    XmlElement.Builder manifestBuilder = getExactlyOneElementBuilder(rootNodeBuilder, "manifest");
    if (isBaseSplit(splitId)) {
      removeAttribute(manifestBuilder, NO_NAMESPACE_URI, "split");
    } else {
      XmlAttribute.Builder splitAttributeBuilder =
          ProtoXmlHelper.findOrCreateAttributeBuilder(manifestBuilder, "split");
      splitAttributeBuilder.setValue(splitId);
    }
    removeAttribute(manifestBuilder, NO_NAMESPACE_URI, "configForSplit");
    XmlAttribute.Builder isFeatureSplitAttribute =
        ProtoXmlHelper.findOrCreateAttributeBuilder(
            manifestBuilder, ANDROID_NAMESPACE, "isFeatureSplit");
    setAttributeValueAsBoolean(isFeatureSplitAttribute, true);
    return this;
  }

  public ManifestEditor setHasCode(boolean value) {
    // Stamp hasCode="false" on the Application element in the Manifest.
    // This attribute's default is "true" even if absent.
    XmlElement.Builder applicationBuilder =
        getFirstOrCreateElementBuilder(rootNodeBuilder, "application");

    XmlAttribute.Builder hasCodeBuilder =
        ProtoXmlHelper.findOrCreateAttributeBuilder(
            applicationBuilder, ANDROID_NAMESPACE, "hasCode");
    hasCodeBuilder.setResourceId(HAS_CODE_RESOURCE_ID);
    setAttributeValueAsBoolean(hasCodeBuilder, value);
    return this;
  }

  public ManifestEditor setPackage(String packageName) {
    XmlElement.Builder manifestBuilder = getExactlyOneElementBuilder(rootNodeBuilder, "manifest");
    XmlAttribute.Builder packageAttributeBuilder =
        ProtoXmlHelper.findOrCreateAttributeBuilder(manifestBuilder, "package");
    packageAttributeBuilder.setValue(packageName);
    return this;
  }

  public ManifestEditor setVersionCode(int versionCode) {
    XmlElement.Builder manifestBuilder = getExactlyOneElementBuilder(rootNodeBuilder, "manifest");
    XmlAttribute.Builder versionCodeAttributeBuilder =
        ProtoXmlHelper.findOrCreateAttributeBuilder(
            manifestBuilder, ANDROID_NAMESPACE, "versionCode");
    ProtoXmlHelper.setAttributeValueAsDecimalInteger(versionCodeAttributeBuilder, versionCode);
    versionCodeAttributeBuilder.setResourceId(VERSION_CODE_RESOURCE_ID);
    return this;
  }

  public ManifestEditor setConfigForSplit(String featureSplitId) {
    XmlElement.Builder manifestBuilder = getExactlyOneElementBuilder(rootNodeBuilder, "manifest");
    XmlAttribute.Builder configForSplitAttributeBuilder =
        ProtoXmlHelper.findOrCreateAttributeBuilder(manifestBuilder, "configForSplit");
    configForSplitAttributeBuilder.setValue(featureSplitId);
    return this;
  }

  public ManifestEditor setSplitId(String splitId) {
    XmlElement.Builder manifestBuilder = getExactlyOneElementBuilder(rootNodeBuilder, "manifest");
    XmlAttribute.Builder splitAttributeBuilder =
        ProtoXmlHelper.findOrCreateAttributeBuilder(manifestBuilder, "split");
    splitAttributeBuilder.setValue(splitId);
    return this;
  }


  /**
   * Sets the 'android:extractNativeLibs' value in the {@code application} tag.
   *
   * <p>Note: the {@code application} tag is created if not found.
   */
  public ManifestEditor setExtractNativeLibsValue(boolean value) {
    XmlElement.Builder applicationEl =
        getFirstOrCreateElementBuilder(rootNodeBuilder, "application");
    XmlAttribute.Builder attribute =
        findOrCreateAndroidAttributeBuilder(
            applicationEl,
            AndroidManifest.EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME,
            EXTRACT_NATIVE_LIBS_RESOURCE_ID);
    setAttributeValueAsBoolean(attribute, value);
    return this;
  }

  /**
   * Sets names of the fused modules as a {@code <meta-data android:name="..."
   * android:value="module1,module2,..."/>} element inside the {@code <application>} element.
   */
  public ManifestEditor setFusedModuleNames(ImmutableList<String> moduleNames) {
    // Make sure the names are unique and sort for deterministic behavior.
    String moduleNamesString = moduleNames.stream().sorted().distinct().collect(joining(","));

    getExactlyOneElementBuilder(rootNodeBuilder, APPLICATION_ELEMENT_NAME)
        .addChild(
            XmlNode.newBuilder()
                .setElement(
                    XmlElement.newBuilder()
                        .setName(META_DATA_ELEMENT_NAME)
                        .addAttribute(
                            XmlAttribute.newBuilder()
                                .setNamespaceUri(ANDROID_NAMESPACE)
                                .setName("name")
                                .setResourceId(NAME_RESOURCE_ID)
                                .setValue(META_DATA_KEY_FUSED_MODULE_NAMES))
                        .addAttribute(
                            XmlAttribute.newBuilder()
                                .setNamespaceUri(ANDROID_NAMESPACE)
                                .setResourceId(VALUE_RESOURCE_ID)
                                .setName("value")
                                .setValue(moduleNamesString))));
    return this;
  }

  /** Generates the modified manifest. */
  public AndroidManifest save() {
    return AndroidManifest.create(rootNodeBuilder.build());
  }

  private ManifestEditor setUsesSdkAttribute(String attributeName, int value, int resourceId) {
    XmlElement.Builder usesSdk =
        getFirstOrCreateElementBuilder(rootNodeBuilder, USES_SDK_ELEMENT_NAME);
    XmlAttribute.Builder attributeBuilder =
        findOrCreateAttributeBuilder(usesSdk, ANDROID_NAMESPACE, attributeName);
    attributeBuilder.setResourceId(resourceId);
    ProtoXmlHelper.setAttributeValueAsDecimalInteger(attributeBuilder, value);
    return this;
  }

  private static boolean isBaseSplit(String splitId) {
    return splitId.isEmpty();
  }
}
