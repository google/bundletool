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

import static com.android.tools.build.bundletool.archive.ArchivedAndroidManifestUtils.BACKGROUND_DIM_ENABLED;
import static com.android.tools.build.bundletool.archive.ArchivedAndroidManifestUtils.HOLO_LIGHT_NO_ACTION_BAR_FULSCREEN_THEME_RESOURCE_ID;
import static com.android.tools.build.bundletool.archive.ArchivedAndroidManifestUtils.SCREEN_BACKGROUND_DARK_TRANSPARENT_THEME_RESOURCE_ID;
import static com.android.tools.build.bundletool.archive.ArchivedAndroidManifestUtils.WINDOW_BACKGROUND_RESOURCE_ID;
import static com.android.tools.build.bundletool.archive.ArchivedAndroidManifestUtils.WINDOW_IS_TRANSLUCENT_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.DRAWABLE_RESOURCE_ID;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.reverseOrder;

import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Style.Entry;
import com.android.tools.build.bundletool.io.ResourceReader;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ResourceInjector;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Helper methods for managing extra resources for archived apps. */
public final class ArchivedResourcesHelper {
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
  public static final String ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME =
      "com_android_vending_archive_splash_screen_layout";
  public static final String ARCHIVED_TV_THEME_NAME = "com_android_vending_archive_tv_theme";

  public static final String ARCHIVED_CLASSES_DEX_PATH_PREFIX =
      "/com/android/tools/build/bundletool/archive/dex";
  private static final String ARCHIVED_CLASSES_DEX_PATH_SUFFIX = "classes.dex";
  public static final String CLOUD_SYMBOL_PATH =
      "/com/android/tools/build/bundletool/archive/drawable/cloud_symbol_xml.pb";
  public static final String OPACITY_LAYER_PATH =
      "/com/android/tools/build/bundletool/archive/drawable/opacity_layer_xml.pb";

  private static final Logger logger = Logger.getLogger(ArchivedResourcesHelper.class.getName());
  private final ResourceReader resourceReader;

  public ArchivedResourcesHelper(ResourceReader resourceReader) {
    this.resourceReader = resourceReader;
  }

  /**
   * Returns a path to the appropriate DEX file based on the bundletool {@link Version} and if
   * transparency is enabled.
   *
   * @throws IOException if an error occurs during reading resources from {@value *
   *     #ARCHIVED_CLASSES_DEX_PATH_PREFIX} path
   */
  public String findArchivedClassesDexPath(Version bundleToolVersion, boolean transparencyEnabled)
      throws IOException {
    try {
      ImmutableList<Path> allResources =
          resourceReader.listResourceFilesInFolder(
              ArchivedResourcesHelper.ARCHIVED_CLASSES_DEX_PATH_PREFIX);
      ImmutableList<Version> availableVersions =
          allResources.stream()
              .map(dir -> dir.getFileName().toString().replace('_', '.'))
              .map(Version::of)
              .sorted(reverseOrder())
              .collect(toImmutableList());
      checkState(!availableVersions.isEmpty(), "Archived DEXes are not present in bundletool JAR.");

      Optional<Version> resultVersion;

      if (transparencyEnabled) {
        resultVersion =
            availableVersions.stream()
                .filter(version -> version.compareTo(bundleToolVersion) <= 0)
                .findFirst();
      } else {
        resultVersion = Optional.of(availableVersions.get(0));
      }

      checkState(
          resultVersion.isPresent(),
          "Not found the appropriate version of DEX file for bundletool version: %s",
          bundleToolVersion.toString());
      return getArchivedClassesDexPath(resultVersion.get());
    } catch (URISyntaxException e) {
      logger.warning("Exception occurred while finding the right archived classes.dex file: " + e);
      throw InvalidBundleException.builder()
          .withUserMessage("Failed to find a DEX file for an archived APK")
          .build();
    }
  }

  public static String getArchivedClassesDexPath(Version version) {
    return String.format(
        "%s/%s/%s",
        ARCHIVED_CLASSES_DEX_PATH_PREFIX,
        version.toString().replace('.', '_'),
        ARCHIVED_CLASSES_DEX_PATH_SUFFIX);
  }

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
                    .getFullResourceId())
            .put(
                ARCHIVED_TV_THEME_NAME,
                resourceInjector
                    .addStyleResource(ARCHIVED_TV_THEME_NAME, buildArchivedTvActivityTheme())
                    .getFullResourceId());
    iconAttribute.ifPresent(
        attribute -> {
          resourceNameToIdMapBuilder.put(
              ARCHIVED_ICON_DRAWABLE_NAME,
              resourceInjector
                  .addXmlDrawableResource(
                      ARCHIVED_ICON_DRAWABLE_NAME,
                      BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                          .resolve(String.format("%s.xml", ARCHIVED_ICON_DRAWABLE_NAME))
                          .toString())
                  .getFullResourceId());
          resourceNameToIdMapBuilder.put(
              ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME,
              resourceInjector
                  .addLayoutResource(
                      ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME,
                      BundleModule.RESOURCES_DIRECTORY
                          .resolve(
                              String.format("layout/%s.xml", ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME))
                          .toString())
                  .getFullResourceId());
        });
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

  public ImmutableMap<ZipPath, ByteSource> buildAdditionalResourcesByByteSourceMap(
      int cloudResourceId,
      int opacityLayerResourceId,
      Optional<XmlProtoAttribute> iconAttribute,
      Optional<XmlProtoAttribute> roundIconAttribute,
      String archivedClassesDexPath)
      throws IOException {
    ImmutableMap.Builder<ZipPath, ByteSource> additionalResourcesByDestinationPathBuilder =
        new ImmutableMap.Builder<ZipPath, ByteSource>()
            .put(
                BundleModule.DEX_DIRECTORY.resolve("classes.dex"),
                resourceReader.getResourceByteSource(archivedClassesDexPath))
            .put(
                BundleModule.DRAWABLE_RESOURCE_DIRECTORY.resolve(
                    String.format("%s.xml", OPACITY_LAYER_DRAWABLE_NAME)),
                resourceReader.getResourceByteSource(OPACITY_LAYER_PATH))
            .put(
                BundleModule.DRAWABLE_RESOURCE_DIRECTORY.resolve(
                    String.format("%s.xml", CLOUD_SYMBOL_DRAWABLE_NAME)),
                resourceReader.getResourceByteSource(CLOUD_SYMBOL_PATH));
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

    if (roundIconAttribute.isPresent() || iconAttribute.isPresent()) {
      int iconResId =
          iconAttribute.isPresent()
              ? iconAttribute.get().getValueAsRefId()
              : roundIconAttribute.get().getValueAsRefId();
      XmlProtoNode node = buildFrameLayoutXmlNode(iconResId);
      additionalResourcesByDestinationPathBuilder.put(
          BundleModule.RESOURCES_DIRECTORY.resolve(
              String.format("layout/%s.xml", ARCHIVED_SPLASH_SCREEN_LAYOUT_NAME)),
          ByteSource.wrap(node.getProto().toByteArray()));
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

  private static XmlProtoNode buildFrameLayoutXmlNode(int imageResId) {
    // These attributes were carried over from the splashscreen SDK implementation.
    return XmlProtoNode.createElementNode(
        ArchivedSplashScreenLayout.builder()
            .setLayoutWidth(ArchivedSplashScreenLayout.MATCH_PARENT)
            .setLayoutHeight(ArchivedSplashScreenLayout.MATCH_PARENT)
            .setAnimateLayoutChanges(false)
            .setFitsSystemWindows(false)
            .setImageResourceId(imageResId)
            .build()
            .asXmlProtoElement());
  }

  private static Style buildArchivedTvActivityTheme() {
    return Style.newBuilder()
        .setParent(
            Reference.newBuilder().setId(HOLO_LIGHT_NO_ACTION_BAR_FULSCREEN_THEME_RESOURCE_ID))
        // To make the background of the activity transparent.
        .addEntry(
            createStyleEntryBuilder(
                WINDOW_IS_TRANSLUCENT_RESOURCE_ID,
                item -> item.setPrim(Primitive.newBuilder().setBooleanValue(true))))
        // A black background.
        .addEntry(
            createStyleEntryBuilder(
                WINDOW_BACKGROUND_RESOURCE_ID,
                item ->
                    item.setRef(
                        Reference.newBuilder()
                            .setId(SCREEN_BACKGROUND_DARK_TRANSPARENT_THEME_RESOURCE_ID))))
        // Add a dimmed effect to background.
        .addEntry(
            createStyleEntryBuilder(
                BACKGROUND_DIM_ENABLED,
                item -> item.setPrim(Primitive.newBuilder().setBooleanValue(true))))
        .build();
  }

  private static Entry.Builder createStyleEntryBuilder(
      int resourceId, Consumer<Item.Builder> itemConsumer) {
    Item.Builder itemBuilder = Item.newBuilder();
    itemConsumer.accept(itemBuilder);
    return Entry.newBuilder().setKey(Reference.newBuilder().setId(resourceId)).setItem(itemBuilder);
  }

  private static XmlProtoAttributeBuilder createDrawableAttribute(int referenceId) {
    return XmlProtoAttributeBuilder.create("drawable")
        .setResourceId(DRAWABLE_RESOURCE_ID)
        .setValueAsRefId(referenceId);
  }

  public static String getAppStorePackageName(Optional<String> customAppStorePackageName) {
    return customAppStorePackageName.orElse(PLAY_STORE_PACKAGE_NAME);
  }
}
