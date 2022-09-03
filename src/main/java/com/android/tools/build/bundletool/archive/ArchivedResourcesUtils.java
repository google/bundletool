/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.build.bundletool.archive;

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.DRAWABLE_RESOURCE_ID;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ResourceInjector;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Utility methods for managing extra resources for archived apps. */
public final class ArchivedResourcesUtils {
  public static final String APP_STORE_PACKAGE_NAME_RESOURCE_NAME =
      "reactivation_app_store_package_name";
  public static final String PLAY_STORE_PACKAGE_NAME = "com.android.vending";
  public static final String OPACITY_LAYER_DRAWABLE_NAME =
      "com_android_vending_archive_icon_opacity_layer";
  public static final String CLOUD_SYMBOL_DRAWABLE_NAME =
      "com_android_vending_archive_icon_cloud_symbol";
  public static final String ARCHIVED_ICON_DRAWABLE_NAME =
      "com_android_vending_archive_application_icon";
  public static final String ARCHIVED_ROUND_ICON_DRAWABLE_NAME =
      "com_android_vending_archive_application_round_icon";

  public static final String ARCHIVED_CLASSES_DEX_PATH = "dex/classes.dex";
  public static final String CLOUD_SYMBOL_PATH = "drawable/cloud_symbol_xml.pb";
  public static final String OPACITY_LAYER_PATH = "drawable/opacity_layer_xml.pb";

  public static ImmutableMap<String, Integer> injectExtraResources(
      ResourceInjector resourceInjector,
      Optional<String> customAppStorePackageName,
      Optional<XmlProtoAttribute> iconAttribute,
      Optional<XmlProtoAttribute> roundIconAttribute) {
    ImmutableMap.Builder<String, Integer> resourceNameToIdMapBuilder =
        ImmutableMap.<String, Integer>builder()
            .put(
                APP_STORE_PACKAGE_NAME_RESOURCE_NAME,
                resourceInjector
                    .addStringResource(
                        APP_STORE_PACKAGE_NAME_RESOURCE_NAME,
                        getAppStorePackageName(customAppStorePackageName))
                    .getFullResourceId())
            .put(
                OPACITY_LAYER_DRAWABLE_NAME,
                resourceInjector
                    .addXmlDrawableResource(
                        OPACITY_LAYER_DRAWABLE_NAME,
                        BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                            .resolve(String.format("%s.xml", OPACITY_LAYER_DRAWABLE_NAME))
                            .toString())
                    .getFullResourceId())
            .put(
                CLOUD_SYMBOL_DRAWABLE_NAME,
                resourceInjector
                    .addXmlDrawableResource(
                        CLOUD_SYMBOL_DRAWABLE_NAME,
                        BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                            .resolve(String.format("%s.xml", CLOUD_SYMBOL_DRAWABLE_NAME))
                            .toString())
                    .getFullResourceId());
    iconAttribute.ifPresent(
        attribute ->
            resourceNameToIdMapBuilder.put(
                ARCHIVED_ICON_DRAWABLE_NAME,
                resourceInjector
                    .addXmlDrawableResource(
                        ARCHIVED_ICON_DRAWABLE_NAME,
                        BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                            .resolve(String.format("%s.xml", ARCHIVED_ICON_DRAWABLE_NAME))
                            .toString())
                    .getFullResourceId()));
    roundIconAttribute.ifPresent(
        attribute ->
            resourceNameToIdMapBuilder.put(
                ARCHIVED_ROUND_ICON_DRAWABLE_NAME,
                resourceInjector
                    .addXmlDrawableResource(
                        ARCHIVED_ROUND_ICON_DRAWABLE_NAME,
                        BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                            .resolve(String.format("%s.xml", ARCHIVED_ROUND_ICON_DRAWABLE_NAME))
                            .toString())
                    .getFullResourceId()));
    return resourceNameToIdMapBuilder.build();
  }

  public static ImmutableMap<ZipPath, ByteSource> buildAdditionalResourcesByByteSourceMap(
      int cloudResourceId,
      int opacityLayerResourceId,
      Optional<XmlProtoAttribute> iconAttribute,
      Optional<XmlProtoAttribute> roundIconAttribute)
      throws IOException {
    ImmutableMap.Builder<ZipPath, ByteSource> additionalResourcesByDestinationPathBuilder =
        new ImmutableMap.Builder<ZipPath, ByteSource>()
            .put(
                BundleModule.DEX_DIRECTORY.resolve("classes.dex"),
                getResourceByteSource(ARCHIVED_CLASSES_DEX_PATH))
            .put(
                BundleModule.DRAWABLE_RESOURCE_DIRECTORY.resolve(
                    String.format("%s.xml", OPACITY_LAYER_DRAWABLE_NAME)),
                getResourceByteSource(OPACITY_LAYER_PATH))
            .put(
                BundleModule.DRAWABLE_RESOURCE_DIRECTORY.resolve(
                    String.format("%s.xml", CLOUD_SYMBOL_DRAWABLE_NAME)),
                getResourceByteSource(CLOUD_SYMBOL_PATH));
    if (iconAttribute.isPresent()) {
      additionalResourcesByDestinationPathBuilder.put(
          BundleModule.DRAWABLE_RESOURCE_DIRECTORY.resolve(
              String.format("%s.xml", ARCHIVED_ICON_DRAWABLE_NAME)),
          ByteSource.wrap(
              buildArchiveIconXmlNode(
                      cloudResourceId,
                      opacityLayerResourceId,
                      iconAttribute.get().getValueAsRefId())
                  .getProto()
                  .toByteArray()));
    }
    if (roundIconAttribute.isPresent()) {
      additionalResourcesByDestinationPathBuilder.put(
          BundleModule.DRAWABLE_RESOURCE_DIRECTORY.resolve(
              String.format("%s.xml", ARCHIVED_ROUND_ICON_DRAWABLE_NAME)),
          ByteSource.wrap(
              buildArchiveIconXmlNode(
                      cloudResourceId,
                      opacityLayerResourceId,
                      roundIconAttribute.get().getValueAsRefId())
                  .getProto()
                  .toByteArray()));
    }
    return additionalResourcesByDestinationPathBuilder.build();
  }

  private static XmlProtoNode buildArchiveIconXmlNode(
      int cloudResourceId, int opacityLayerResourceId, int iconReferenceId) {
    return XmlProtoNode.createElementNode(
        XmlProtoElementBuilder.create("layer-list")
            .addNamespaceDeclaration("android", ANDROID_NAMESPACE_URI)
            .addChildElement(
                XmlProtoElementBuilder.create("item")
                    .addAttribute(createDrawableAttribute(iconReferenceId)))
            .addChildElement(
                XmlProtoElementBuilder.create("item")
                    .addAttribute(createDrawableAttribute(opacityLayerResourceId)))
            .addChildElement(
                XmlProtoElementBuilder.create("item")
                    .addAttribute(createDrawableAttribute(cloudResourceId)))
            .build());
  }

  private static XmlProtoAttributeBuilder createDrawableAttribute(int referenceId) {
    return XmlProtoAttributeBuilder.create("drawable")
        .setResourceId(DRAWABLE_RESOURCE_ID)
        .setValueAsRefId(referenceId);
  }

  static ByteSource getResourceByteSource(String resourcePath) throws IOException {
    try (InputStream fileContentStream =
        ArchivedApksGenerator.class.getResourceAsStream(resourcePath)) {
      return ByteSource.wrap(ByteStreams.toByteArray(fileContentStream));
    }
  }

  static String getAppStorePackageName(Optional<String> customAppStorePackageName) {
    return customAppStorePackageName.orElse(PLAY_STORE_PACKAGE_NAME);
  }

  private ArchivedResourcesUtils() {}
}
