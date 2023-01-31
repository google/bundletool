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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ALIAS_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.APPLICATION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CERTIFICATE_DIGEST_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.CERTIFICATE_DIGEST_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.CONDITION_DEVICE_GROUPS_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DEBUGGABLE_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DEBUGGABLE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.DESCRIPTION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DESCRIPTION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.DEVICE_GROUP_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.DISTRIBUTION_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.EXTRACT_NATIVE_LIBS_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.HAS_CODE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ICON_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ICON_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.INSTALL_LOCATION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.INSTALL_LOCATION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ISOLATED_SPLITS_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MAX_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_KEY_FUSED_MODULE_NAMES;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.MIN_SDK_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_ASSET_VALUE;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.NATIVE_ACTIVITY_LIB_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_GROUP_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PERMISSION_TREE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROPERTY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.RECEIVER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.RESOURCE_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ROUND_ICON_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ROUND_ICON_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_LIBRARY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_VERSION_MAJOR_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SPLIT_NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SUPPORTS_GL_TEXTURE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SANDBOX_VERSION_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SDK_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.TARGET_SDK_VERSION_RESOURCE_ID;
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

import com.android.aapt.Resources;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNamespace;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.DeliveryType;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNodeBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Helper methods for proto based Android Manifest tests. */
public final class ManifestProtoUtils {

  private static final int NO_RESOURCE_ID = 0;

  public static XmlNode xmlNode(XmlElement element) {
    return XmlNode.newBuilder().setElement(element).build();
  }

  public static XmlElement xmlElement(String name, XmlNode... children) {
    return xmlElement(NO_NAMESPACE_URI, name, children);
  }

  public static XmlElement xmlElement(String name, XmlAttribute attribute, XmlNode... children) {
    return xmlElement(
        NO_NAMESPACE_URI,
        name,
        /* namespaces= */ ImmutableList.of(),
        ImmutableList.of(attribute),
        children);
  }

  public static XmlElement xmlElement(
      String name,
      ImmutableList<XmlNamespace> namespaces,
      ImmutableList<XmlAttribute> attributes,
      XmlNode... children) {
    return xmlElement(NO_NAMESPACE_URI, name, namespaces, attributes, children);
  }

  public static XmlElement xmlElement(
      String name, ImmutableList<XmlAttribute> attributes, XmlNode... children) {
    return xmlElement(
        NO_NAMESPACE_URI, name, /* namespaces= */ ImmutableList.of(), attributes, children);
  }

  public static XmlElement xmlElement(
      String namespaceUri,
      String name,
      ImmutableList<XmlNamespace> namespaces,
      ImmutableList<XmlAttribute> attributes,
      XmlNode... children) {
    return XmlElement.newBuilder()
        .setNamespaceUri(namespaceUri)
        .setName(name)
        .addAllAttribute(attributes)
        .addAllChild(Arrays.asList(children))
        .addAllNamespaceDeclaration(namespaces)
        .build();
  }

  public static XmlElement xmlElement(
      String namespaceUri, String name, XmlAttribute attribute, XmlNode... children) {
    return XmlElement.newBuilder()
        .setNamespaceUri(namespaceUri)
        .setName(name)
        .addAttribute(attribute)
        .addAllChild(Arrays.asList(children))
        .build();
  }

  public static XmlElement xmlElement(String namespaceUri, String name, XmlNode... children) {
    return XmlElement.newBuilder()
        .setNamespaceUri(namespaceUri)
        .setName(name)
        .addAllChild(Arrays.asList(children))
        .build();
  }

  public static XmlNamespace xmlNamespace(String prefix, String uri) {
    return XmlNamespace.newBuilder().setPrefix(prefix).setUri(uri).build();
  }

  public static XmlAttribute xmlAttribute(String name) {
    return xmlAttribute(NO_NAMESPACE_URI, name, NO_RESOURCE_ID, "");
  }

  public static XmlAttribute xmlAttribute(String name, String value) {
    return xmlAttribute(NO_NAMESPACE_URI, name, NO_RESOURCE_ID, value);
  }

  public static XmlAttribute xmlAttribute(String namespaceUri, String name, String value) {
    return xmlAttribute(namespaceUri, name, NO_RESOURCE_ID, value);
  }

  public static XmlAttribute xmlAttribute(
      String namespaceUri, String name, int resourceId, String value) {
    return XmlAttribute.newBuilder()
        .setNamespaceUri(namespaceUri)
        .setName(name)
        .setValue(value)
        .setResourceId(resourceId)
        .setCompiledItem(Item.newBuilder().setStr(Resources.String.newBuilder().setValue(value)))
        .build();
  }

  public static XmlAttribute xmlBooleanAttribute(String name, boolean value) {
    return xmlBooleanAttribute(NO_NAMESPACE_URI, name, NO_RESOURCE_ID, value);
  }

  public static XmlAttribute xmlBooleanAttribute(String namespaceUri, String name, boolean value) {
    return xmlBooleanAttribute(namespaceUri, name, NO_RESOURCE_ID, value);
  }

  public static XmlAttribute xmlBooleanAttribute(
      String namespaceUri, String name, int resourceId, boolean value) {
    return xmlPrimitiveAttribute(
        namespaceUri, name, resourceId, Primitive.newBuilder().setBooleanValue(value).build(), "");
  }

  public static XmlAttribute xmlDecimalIntegerAttribute(String name, int value) {
    return xmlDecimalIntegerAttribute(NO_NAMESPACE_URI, name, NO_RESOURCE_ID, value);
  }

  public static XmlAttribute xmlDecimalIntegerAttribute(
      String namespaceUri, String name, int value) {
    return xmlDecimalIntegerAttribute(namespaceUri, name, NO_RESOURCE_ID, value);
  }

  public static XmlAttribute xmlDecimalIntegerAttribute(
      String namespaceUri, String name, int resourceId, int value) {
    return xmlPrimitiveAttribute(
        namespaceUri,
        name,
        resourceId,
        Primitive.newBuilder().setIntDecimalValue(value).build(),
        String.valueOf(value));
  }

  private static XmlAttribute xmlPrimitiveAttribute(
      String namespaceUri, String name, int resourceId, Primitive primitive, String value) {
    return xmlCompiledItemAttribute(
        namespaceUri, name, resourceId, Item.newBuilder().setPrim(primitive).build(), value);
  }

  public static XmlAttribute xmlResourceReferenceAttribute(
      String namespaceUri, String name, int attrResourceId, int valueResourceId) {
    return xmlCompiledItemAttribute(
        namespaceUri,
        name,
        attrResourceId,
        Item.newBuilder().setRef(Reference.newBuilder().setId(valueResourceId)).build());
  }

  public static XmlAttribute xmlCompiledItemAttribute(
      String namespaceUri, String name, Item compiledItem) {
    return xmlCompiledItemAttribute(
        namespaceUri, name, /* resourceId= */ null, compiledItem, /* value= */ null);
  }

  private static XmlAttribute xmlCompiledItemAttribute(
      String namespaceUri, String name, int resourceId, Item compiledItem) {
    return xmlCompiledItemAttribute(
        namespaceUri, name, resourceId, compiledItem, /* value= */ null);
  }

  private static XmlAttribute xmlCompiledItemAttribute(
      String namespaceUri,
      String name,
      @Nullable Integer resourceId,
      Item compiledItem,
      @Nullable String value) {
    XmlAttribute.Builder attribute =
        XmlAttribute.newBuilder()
            .setNamespaceUri(namespaceUri)
            .setName(name)
            .setCompiledItem(compiledItem);
    if (resourceId != null) {
      attribute.setResourceId(resourceId);
    }
    if (value != null) {
      attribute.setValue(value);
    }
    return attribute.build();
  }

  /**
   * Creates an Android Manifest.
   *
   * <p>Without providing any {@code manifestMutator} creates a minimal valid manifest.
   */
  public static XmlNode androidManifest(String packageName, ManifestMutator... manifestMutators) {
    XmlNode manifestNode =
        xmlNode(
            xmlElement(
                "manifest",
                ImmutableList.of(xmlNamespace("android", ANDROID_NAMESPACE_URI)),
                ImmutableList.of(
                    xmlAttribute("package", packageName),
                    xmlDecimalIntegerAttribute(
                        ANDROID_NAMESPACE_URI, "versionCode", 0x0101021b, 1))));

    XmlProtoNodeBuilder xmlProtoNode = new XmlProtoNode(manifestNode).toBuilder();
    withHasCode(false).accept(xmlProtoNode.getElement());
    for (ManifestMutator manifestMutator : manifestMutators) {
      manifestMutator.accept(xmlProtoNode.getElement());
    }
    return xmlProtoNode.build().getProto();
  }

  public static XmlNode androidManifestForFeature(
      String packageName, ManifestMutator... manifestMutators) {
    return androidManifest(
        packageName,
        ObjectArrays.concat(
            new ManifestMutator[] {withOnDemandAttribute(true), withFusingAttribute(true)},
            manifestMutators,
            ManifestMutator.class));
  }

  public static XmlNode androidManifestForMlModule(
      String packageName, ManifestMutator... manifestMutators) {
    return androidManifest(
        packageName,
        ObjectArrays.concat(
            new ManifestMutator[] {
              withOnDemandAttribute(true), withFusingAttribute(true), withTypeAttribute("ml-pack")
            },
            manifestMutators,
            ManifestMutator.class));
  }

  public static XmlNode androidManifestForAssetModule(
      String packageName, ManifestMutator... manifestMutators) {
    XmlNode manifestNode =
        xmlNode(
            xmlElement(
                "manifest",
                ImmutableList.of(xmlNamespace("android", ANDROID_NAMESPACE_URI)),
                ImmutableList.of(xmlAttribute("package", packageName))));

    XmlProtoNodeBuilder xmlProtoNode = new XmlProtoNode(manifestNode).toBuilder();
    // Default mutators
    withTypeAttribute(MODULE_TYPE_ASSET_VALUE).accept(xmlProtoNode.getElement());
    withFusingAttribute(true).accept(xmlProtoNode.getElement());
    // Additional mutators and overrides of defaults.
    for (ManifestMutator manifestMutator : manifestMutators) {
      manifestMutator.accept(xmlProtoNode.getElement());
    }
    // Set a delivery mode if none was set.
    if (!AndroidManifest.create(xmlProtoNode.build().getProto()).isDeliveryTypeDeclared()) {
      withOnDemandDelivery().accept(xmlProtoNode.getElement());
    }
    return xmlProtoNode.build().getProto();
  }

  public static ManifestMutator withDebuggableAttribute(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateAndroidAttribute(DEBUGGABLE_ATTRIBUTE_NAME, DEBUGGABLE_RESOURCE_ID)
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withAppIcon(int refId) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addAttribute(
                XmlProtoAttributeBuilder.createAndroidAttribute(
                        ICON_ATTRIBUTE_NAME, ICON_RESOURCE_ID)
                    .setValueAsRefId(refId));
  }

  public static ManifestMutator withAppRoundIcon(int refId) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addAttribute(
                XmlProtoAttributeBuilder.createAndroidAttribute(
                        ROUND_ICON_ATTRIBUTE_NAME, ROUND_ICON_RESOURCE_ID)
                    .setValueAsRefId(refId));
  }

  public static ManifestMutator withTitle(String title, int refId) {
    if (title.isEmpty()) {
      return manifestElement -> {};
    }
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateAttribute(DISTRIBUTION_NAMESPACE_URI, "title")
            .setValueAsRefId(refId);
  }

  public static ManifestMutator withApplication() {
    return manifestElement -> manifestElement.getOrCreateChildElement(APPLICATION_ELEMENT_NAME);
  }

  public static ManifestMutator withHasCode(boolean hasCode) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateAndroidAttribute("hasCode", HAS_CODE_RESOURCE_ID)
            .setValueAsBoolean(hasCode);
  }

  public static ManifestMutator clearApplication() {
    return manifestElement ->
        manifestElement.removeChildrenElementsIf(
            element ->
                element.isElement()
                    && element.getElement().getNamespaceUri().equals(NO_NAMESPACE_URI)
                    && element.getElement().getName().equals(APPLICATION_ELEMENT_NAME));
  }

  public static ManifestMutator clearHasCode() {
    return manifestElement ->
        manifestElement
            .getOptionalChildElement(APPLICATION_ELEMENT_NAME)
            .ifPresent(
                application -> application.removeAttribute(ANDROID_NAMESPACE_URI, "hasCode"));
  }

  public static ManifestMutator withSplitId(String splitId) {
    if (splitId.isEmpty()) {
      return manifestElement -> {};
    }
    return manifestElement ->
        manifestElement.getOrCreateAttribute("split").setValueAsString(splitId);
  }

  public static ManifestMutator withTargetSandboxVersion(int version) {
    return manifestElement ->
        manifestElement
            .getOrCreateAndroidAttribute("targetSandboxVersion", TARGET_SANDBOX_VERSION_RESOURCE_ID)
            .setValueAsDecimalInteger(version);
  }

  public static ManifestMutator withOnDemandAttribute(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateAttribute(DISTRIBUTION_NAMESPACE_URI, "onDemand")
            .setValueAsBoolean(value);
  }

  /** Adds the type attribute under the dist:module tag. */
  public static ManifestMutator withTypeAttribute(String value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateAttribute(DISTRIBUTION_NAMESPACE_URI, "type")
            .setValueAsString(value);
  }

  /** Same as {@link #withOnDemandAttribute(boolean)} but with the attribute not namespaced. */
  public static ManifestMutator withLegacyOnDemand(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateAttribute(NO_NAMESPACE_URI, "onDemand")
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withEmptyDeliveryElement() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery");
  }

  public static ManifestMutator withInstallTimeRemovableElement(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "removable")
            .getOrCreateAttribute(DISTRIBUTION_NAMESPACE_URI, "value")
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withInstallTimeDelivery() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time");
  }

  public static ManifestMutator withOnDemandDelivery() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "on-demand");
  }

  public static ManifestMutator withFastFollowDelivery() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "fast-follow");
  }

  public static ManifestMutator withDelivery(DeliveryType deliveryType) {
    switch (deliveryType) {
      case INSTALL_TIME:
        return withInstallTimeDelivery();
      case ON_DEMAND:
        return withOnDemandDelivery();
      case FAST_FOLLOW:
        return withFastFollowDelivery();
      default:
        return withEmptyDeliveryElement();
    }
  }

  /** Adds the instant attribute under the dist:module tag. */
  public static ManifestMutator withInstant(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateAttribute(DISTRIBUTION_NAMESPACE_URI, "instant")
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withSharedUserId(String sharedUserId) {
    return manifestElement ->
        manifestElement
            .getOrCreateAttribute(ANDROID_NAMESPACE_URI, "sharedUserId")
            .setValueAsString(sharedUserId);
  }

  /**
   * Adds the dist:instant-delivery tag under the dist:module tag, with a dist:on-demand tag inside
   * it.
   */
  public static ManifestMutator withInstantOnDemandDelivery() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "instant-delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "on-demand");
  }

  /**
   * Adds the dist:instant-delivery tag under the dist:module tag, with a dist:install-time tag
   * inside it.
   */
  public static ManifestMutator withInstantInstallTimeDelivery() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "instant-delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time");
  }

  public static ManifestMutator withFusedModuleNames(String modulesString) {
    return withMetadataValue(META_DATA_KEY_FUSED_MODULE_NAMES, modulesString);
  }

  /** Same as {@link #withFusingAttribute(boolean)} but with the attribute not namespaced. */
  public static ManifestMutator withLegacyFusingAttribute(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "fusing")
            .getOrCreateAttribute(NO_NAMESPACE_URI, "include")
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withFusingAttribute(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "fusing")
            .getOrCreateAttribute(DISTRIBUTION_NAMESPACE_URI, "include")
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withTargetSdkVersion(String version) {
    return withUsesSdkAttribute(
        TARGET_SDK_VERSION_ATTRIBUTE_NAME, TARGET_SDK_VERSION_RESOURCE_ID, version);
  }

  public static ManifestMutator withMinSdkVersion(int version) {
    return withUsesSdkAttribute(
        MIN_SDK_VERSION_ATTRIBUTE_NAME, MIN_SDK_VERSION_RESOURCE_ID, version);
  }

  public static ManifestMutator withMinSdkVersion(String version) {
    return withUsesSdkAttribute(
        MIN_SDK_VERSION_ATTRIBUTE_NAME, MIN_SDK_VERSION_RESOURCE_ID, version);
  }

  public static ManifestMutator withMaxSdkVersion(int version) {
    return withUsesSdkAttribute(
        MAX_SDK_VERSION_ATTRIBUTE_NAME, MAX_SDK_VERSION_RESOURCE_ID, version);
  }

  private static ManifestMutator withUsesSdkAttribute(
      String attributeName, int resourceId, int value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(USES_SDK_ELEMENT_NAME)
            .addAttribute(
                XmlProtoAttributeBuilder.createAndroidAttribute(attributeName, resourceId)
                    .setValueAsDecimalInteger(value));
  }

  private static ManifestMutator withUsesSdkAttribute(
      String attributeName, int resourceId, String value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(USES_SDK_ELEMENT_NAME)
            .addAttribute(
                XmlProtoAttributeBuilder.createAndroidAttribute(attributeName, resourceId)
                    .setValueAsString(value));
  }

  public static ManifestMutator withSupportsGlTexture(String... glExtensionStrings) {
    return manifestElement ->
        Arrays.stream(glExtensionStrings)
            .forEach(
                glExtensionString ->
                    manifestElement.addChildElement(
                        XmlProtoElementBuilder.create(SUPPORTS_GL_TEXTURE_ELEMENT_NAME)
                            .addAttribute(
                                XmlProtoAttributeBuilder.createAndroidAttribute(
                                        NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                    .setValueAsString(glExtensionString))));
  }

  public static ManifestMutator withIsolatedSplits(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateAndroidAttribute("isolatedSplits", ISOLATED_SPLITS_ID)
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withoutVersionCode() {
    return manifestElement -> manifestElement.removeAttribute(ANDROID_NAMESPACE_URI, "versionCode");
  }

  public static ManifestMutator withVersionCode(int versionCode) {
    return manifestElement ->
        manifestElement
            .getOrCreateAndroidAttribute("versionCode", VERSION_CODE_RESOURCE_ID)
            .setValueAsDecimalInteger(versionCode);
  }

  public static ManifestMutator withVersionName(String versionName) {
    return manifestElement ->
        manifestElement
            .getOrCreateAndroidAttribute(VERSION_NAME_ATTRIBUTE_NAME, VERSION_NAME_RESOURCE_ID)
            .setValueAsString(versionName);
  }

  public static ManifestMutator withInstallLocation(String installLocation) {
    return manifestElement ->
        manifestElement
            .getOrCreateAndroidAttribute(
                INSTALL_LOCATION_ATTRIBUTE_NAME, INSTALL_LOCATION_RESOURCE_ID)
            .setValueAsString(installLocation);
  }

  public static ManifestMutator withUsesSplit(String... splitIds) {
    return manifestElement ->
        Arrays.stream(splitIds)
            .forEach(
                splitId ->
                    manifestElement.addChildElement(
                        XmlProtoElementBuilder.create("uses-split")
                            .addAttribute(
                                XmlProtoAttributeBuilder.createAndroidAttribute(
                                        "name", NAME_RESOURCE_ID)
                                    .setValueAsString(splitId))));
  }

  public static ManifestMutator withExtractNativeLibs(boolean value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateAndroidAttribute(
                EXTRACT_NATIVE_LIBS_ATTRIBUTE_NAME, EXTRACT_NATIVE_LIBS_RESOURCE_ID)
            .setValueAsBoolean(value);
  }

  public static ManifestMutator withDescription(int value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateAndroidAttribute(DESCRIPTION_ATTRIBUTE_NAME, DESCRIPTION_RESOURCE_ID)
            .setValueAsRefId(value);
  }

  public static ManifestMutator withCustomApplicationResourceReferenceAttribute(
      String attributeName, int resourceId, int value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateAndroidAttribute(attributeName, resourceId)
            .setValueAsRefId(value);
  }

  public static ManifestMutator withMetadataValue(String key, String value) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addChildElement(
                XmlProtoElementBuilder.create(META_DATA_ELEMENT_NAME)
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute("name", NAME_RESOURCE_ID)
                            .setValueAsString(key))
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute("value", VALUE_RESOURCE_ID)
                            .setValueAsString(value)));
  }

  public static ManifestMutator withMetadataResource(String key, int resId) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addChildElement(
                XmlProtoElementBuilder.create(META_DATA_ELEMENT_NAME)
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute("name", NAME_RESOURCE_ID)
                            .setValueAsString(key))
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute(
                                "resource", RESOURCE_RESOURCE_ID)
                            .setValueAsRefId(resId)));
  }

  public static ManifestMutator withFeatureCondition(String featureName) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(
                XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "device-feature")
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "name")
                            .setValueAsString(featureName)));
  }

  public static ManifestMutator withFeatureCondition(String featureName, int featureVersion) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(
                XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "device-feature")
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "name")
                            .setValueAsString(featureName))
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "version")
                            .setValueAsDecimalInteger(featureVersion)));
  }

  public static ManifestMutator withFeatureConditionHexVersion(
      String featureName, int featureVersion) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(
                XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "device-feature")
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "name")
                            .setValueAsString(featureName))
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "version")
                            .setValueAsHexInteger(featureVersion)));
  }

  public static ManifestMutator withMinSdkCondition(int minSdkVersion) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(
                XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "min-sdk")
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "value")
                            .setValueAsDecimalInteger(minSdkVersion)));
  }

  public static ManifestMutator withMaxSdkCondition(int maxSdkVersion) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(
                XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "max-sdk")
                    .addAttribute(
                        XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "value")
                            .setValueAsDecimalInteger(maxSdkVersion)));
  }

  public static ManifestMutator withDeviceGroupsCondition(ImmutableList<String> deviceGroups) {
    XmlProtoElementBuilder deviceGroupsElement =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, CONDITION_DEVICE_GROUPS_NAME);

    for (String deviceGroup : deviceGroups) {
      deviceGroupsElement.addChildElement(
          XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, DEVICE_GROUP_ELEMENT_NAME)
              .addAttribute(
                  XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, NAME_ATTRIBUTE_NAME)
                      .setValueAsString(deviceGroup)));
    }

    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(deviceGroupsElement);
  }

  /**
   * Creates a user countries condition for the supplied list of country codes and with the
   * dist:exclude attribute set to a given value.
   */
  public static ManifestMutator withUserCountriesCondition(
      ImmutableList<String> codes, boolean exclude) {
    return withUserCountriesConditionInternal(codes, Optional.of(exclude));
  }

  /**
   * Creates a user countries condition for the supplied list of country codes. The dist:exclude
   * element is not added.
   */
  public static ManifestMutator withUserCountriesCondition(ImmutableList<String> codes) {
    return withUserCountriesConditionInternal(codes, Optional.empty());
  }

  /**
   * Adds the given element with {@link
   * AndroidManifest#REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME} boolean attribute.
   */
  public static ManifestMutator withRequiredByPrivacySandboxElement(
      String elementName, boolean requiredByPrivacySandboxSdkValue) {
    return manifestElement ->
        manifestElement.addChildElement(
            XmlProtoElementBuilder.create(elementName)
                .addAttribute(
                    XmlProtoAttributeBuilder.create(
                            TOOLS_NAMESPACE_URI, REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME)
                        .setValueAsBoolean(requiredByPrivacySandboxSdkValue)));
  }

  private static ManifestMutator withUserCountriesConditionInternal(
      ImmutableList<String> codes, Optional<Boolean> exclude) {
    XmlProtoElementBuilder userCountries =
        XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "user-countries");

    exclude.ifPresent(
        excludeValue ->
            userCountries.addAttribute(
                XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "exclude")
                    .setValueAsBoolean(excludeValue)));

    for (String countryCode : codes) {
      userCountries.addChildElement(
          XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "country")
              .addAttribute(
                  XmlProtoAttributeBuilder.create(DISTRIBUTION_NAMESPACE_URI, "code")
                      .setValueAsString(countryCode)));
    }

    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(userCountries);
  }

  public static ManifestMutator withUnsupportedCondition() {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "module")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "delivery")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "install-time")
            .getOrCreateChildElement(DISTRIBUTION_NAMESPACE_URI, "conditions")
            .addChildElement(
                XmlProtoElementBuilder.create(DISTRIBUTION_NAMESPACE_URI, "unsupportedCondition"));
  }

  /** Adds an activity with the {@code splitName} attribute. */
  public static ManifestMutator withSplitNameActivity(String activityName, String splitName) {
    return withSplitNameElement(ACTIVITY_ELEMENT_NAME, activityName, splitName);
  }

  /** Adds an activity alias with the {@code splitName} attribute. */
  public static ManifestMutator withSplitNameActivityAlias(
      String activityAliasName, String splitName) {
    return withSplitNameElement(ACTIVITY_ALIAS_ELEMENT_NAME, activityAliasName, splitName);
  }

  /** Adds a receiver with the {@code splitName} attribute. */
  public static ManifestMutator withSplitNameReceiver(String serviceName, String splitName) {
    return withSplitNameElement(RECEIVER_ELEMENT_NAME, serviceName, splitName);
  }

  /** Adds a service with the {@code splitName} attribute. */
  public static ManifestMutator withSplitNameService(String serviceName, String splitName) {
    return withSplitNameElement(SERVICE_ELEMENT_NAME, serviceName, splitName);
  }

  /** Adds a provider with the {@code splitName} attribute. */
  public static ManifestMutator withSplitNameProvider(String providerName, String splitName) {
    return withSplitNameElement(PROVIDER_ELEMENT_NAME, providerName, splitName);
  }

  private static ManifestMutator withSplitNameElement(
      String elementName, String attributeName, String splitName) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addChildElement(
                XmlProtoElementBuilder.create(elementName)
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute(
                                NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                            .setValueAsString(attributeName))
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute(
                                SPLIT_NAME_ATTRIBUTE_NAME, SPLIT_NAME_RESOURCE_ID)
                            .setValueAsString(splitName)));
  }

  public static ManifestMutator withActivity(
      String name, Function<XmlProtoElementBuilder, XmlProtoElementBuilder> modifier) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addChildElement(
                modifier.apply(
                    XmlProtoElementBuilder.create(ACTIVITY_ELEMENT_NAME)
                        .addAttribute(
                            XmlProtoAttributeBuilder.createAndroidAttribute(
                                    NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                .setValueAsString(name))));
  }

  private static ManifestMutator withActivity(String activityName, String categoryName) {
    return withActivity(
        activityName,
        activity ->
            activity.addChildElement(
                XmlProtoElementBuilder.create("intent-filter")
                    .addChildElement(
                        XmlProtoElementBuilder.create("action")
                            .addAttribute(
                                XmlProtoAttributeBuilder.createAndroidAttribute(
                                        NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                    .setValueAsString("android.intent.action.MAIN")))
                    .addChildElement(
                        XmlProtoElementBuilder.create("category")
                            .addAttribute(
                                XmlProtoAttributeBuilder.createAndroidAttribute(
                                        NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                    .setValueAsString(categoryName)))));
  }

  public static ManifestMutator withCustomThemeActivity(String name, int themeRefId) {
    return withActivity(
        name,
        activityBuilder -> {
          activityBuilder
              .getOrCreateAndroidAttribute(THEME_ATTRIBUTE_NAME, THEME_RESOURCE_ID)
              .setValueAsRefId(themeRefId);
          return activityBuilder;
        });
  }

  public static ManifestMutator withMainActivity(String activityName) {
    return withActivity(activityName, "android.intent.category.LAUNCHER");
  }

  public static ManifestMutator withMainTvActivity(String activityName) {
    return withActivity(activityName, "android.intent.category.LEANBACK_LAUNCHER");
  }

  public static ManifestMutator withNativeActivity(String libName) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .addChildElement(
                XmlProtoElementBuilder.create(ACTIVITY_ELEMENT_NAME)
                    .addAttribute(
                        XmlProtoAttributeBuilder.createAndroidAttribute(
                                NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                            .setValueAsString("android.app.NativeActivity"))
                    .addChildElement(
                        XmlProtoElementBuilder.create(META_DATA_ELEMENT_NAME)
                            .addAttribute(
                                XmlProtoAttributeBuilder.createAndroidAttribute(
                                        NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                    .setValueAsString(NATIVE_ACTIVITY_LIB_NAME))
                            .addAttribute(
                                XmlProtoAttributeBuilder.createAndroidAttribute(
                                        VALUE_ATTRIBUTE_NAME, VALUE_RESOURCE_ID)
                                    .setValueAsString(libName))));
  }

  /** Adds an <sdk-library> element to an SDK Bundle manifest. */
  public static ManifestMutator withSdkLibraryElement(String sdkPackageName, int versionMajor) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateChildElement(SDK_LIBRARY_ELEMENT_NAME)
            .addAttribute(
                createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                    .setValueAsString(sdkPackageName))
            .addAttribute(
                createAndroidAttribute(SDK_VERSION_MAJOR_ATTRIBUTE_NAME, VERSION_MAJOR_RESOURCE_ID)
                    .setValueAsDecimalInteger(versionMajor));
  }

  /** Adds an <uses-sdk-library> element. */
  public static ManifestMutator withUsesSdkLibraryElement(
      String sdkPackageName, int versionMajor, String certDigest) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateChildElement(USES_SDK_LIBRARY_ELEMENT_NAME)
            .addAttribute(
                createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                    .setValueAsString(sdkPackageName))
            .addAttribute(
                createAndroidAttribute(SDK_VERSION_MAJOR_ATTRIBUTE_NAME, VERSION_MAJOR_RESOURCE_ID)
                    .setValueAsString(String.valueOf(versionMajor)))
            .addAttribute(
                createAndroidAttribute(
                        CERTIFICATE_DIGEST_ATTRIBUTE_NAME, CERTIFICATE_DIGEST_RESOURCE_ID)
                    .setValueAsString(certDigest));
  }

  /** Adds a <property> element to an SDK Bundle manifest. */
  public static ManifestMutator withSdkPatchVersionProperty(int patchVersion) {
    return manifestElement ->
        manifestElement
            .getOrCreateChildElement(APPLICATION_ELEMENT_NAME)
            .getOrCreateChildElement(PROPERTY_ELEMENT_NAME)
            .addAttribute(
                createAndroidAttribute(NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                    .setValueAsString(SDK_PATCH_VERSION_ATTRIBUTE_NAME))
            .addAttribute(
                createAndroidAttribute(VALUE_ATTRIBUTE_NAME, VALUE_RESOURCE_ID)
                    .setValueAsDecimalInteger(patchVersion));
  }

  /** Adds a {@value #PERMISSION_ELEMENT_NAME} element to the manifest. */
  public static ManifestMutator withPermission() {
    return manifestElement ->
        manifestElement.addChildElement(XmlProtoElementBuilder.create(PERMISSION_ELEMENT_NAME));
  }

  /** Adds a {@value #PERMISSION_GROUP_ELEMENT_NAME} element to the manifest. */
  public static ManifestMutator withPermissionGroup() {
    return manifestElement ->
        manifestElement.addChildElement(
            XmlProtoElementBuilder.create(PERMISSION_GROUP_ELEMENT_NAME));
  }

  /** Adds a {@value #PERMISSION_TREE_ELEMENT_NAME} element to the manifest. */
  public static ManifestMutator withPermissionTree() {
    return manifestElement ->
        manifestElement.addChildElement(
            XmlProtoElementBuilder.create(PERMISSION_TREE_ELEMENT_NAME));
  }

  public static ManifestMutator withCustomChildElement(XmlProtoElementBuilder applicationElement) {
    return manifestElement -> manifestElement.addChildElement(applicationElement);
  }

  /** Defined solely for readability. */
  public interface ManifestMutator extends Consumer<XmlProtoElementBuilder> {}

  /**
   * Compares manifest mutators by applying the mutators against same manifests and comparing the
   * edited manifest, as we can't compare two mutators (lambda expressions) directly.
   */
  public static boolean compareManifestMutators(
      ImmutableList<com.android.tools.build.bundletool.model.ManifestMutator> manifestMutators,
      com.android.tools.build.bundletool.model.ManifestMutator otherManifestMutator) {

    AndroidManifest defaultManifest = AndroidManifest.create(androidManifest("com.test.app"));

    return defaultManifest
        .applyMutators(manifestMutators)
        .equals(defaultManifest.applyMutators(ImmutableList.of(otherManifestMutator)));
  }

  /** Adds an <uses-feature> element. */
  public static ManifestMutator withUsesFeatureElement(String usesFeatureNameValue) {
    return manifestElement ->
        manifestElement.addChildElement(
            new XmlProtoElementBuilder(
                xmlElement(
                    USES_FEATURE_ELEMENT_NAME,
                    xmlAttribute(
                        ANDROID_NAMESPACE_URI,
                        NAME_ATTRIBUTE_NAME,
                        NAME_RESOURCE_ID,
                        usesFeatureNameValue))
                    .toBuilder()));
  }

  // Do not instantiate.
  private ManifestProtoUtils() {}
}
