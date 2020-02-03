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

import static com.google.common.base.Preconditions.checkState;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.Iterables;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Helper class to locate an embedded Wear 1.x APK.
 *
 * <p>See https://developer.android.com/training/wearables/apps/packaging#PackageManually
 */
public final class WearApkLocator {

  static final String WEAR_APK_1_0_METADATA_KEY = "com.google.android.wearable.beta.app";

  /**
   * Locates the placement of the embedded Wear 1.x APK if present.
   *
   * <p>Follows the instructions from
   * https://developer.android.com/training/wearables/apps/packaging#PackageManually
   */
  public static Optional<ZipPath> findEmbeddedWearApkPath(ModuleSplit split) {
    if (!split.getResourceTable().isPresent()) {
      return Optional.empty();
    }

    ResourceTable resourceTable = split.getResourceTable().get();
    AndroidManifest manifest = split.getAndroidManifest();

    Optional<ZipPath> embeddedWearApkPath =
        manifest
            .getMetadataResourceId(WEAR_APK_1_0_METADATA_KEY)
            .map(resourceId -> findXmlDescriptionResourceEntry(resourceTable, resourceId))
            .map(entry -> getXmlDescriptionPath(entry))
            .map(xmlDescriptionPath -> findXmlDescriptionZipEntry(split, xmlDescriptionPath))
            .flatMap(xmlDescriptionEntry -> extractWearApkName(xmlDescriptionEntry))
            .map(apkName -> ZipPath.create(String.format("res/raw/%s.apk", apkName)));

    // Sanity check to ensure that the path we return actually points to an existing entry.
    embeddedWearApkPath.ifPresent(
        path -> {
          if (!split.findEntry(path).isPresent()) {
            throw ValidationException.builder()
                .withMessage("Wear APK expected at location '%s' but was not found.", path)
                .build();
          }
        });

    return embeddedWearApkPath;
  }

  private static Entry findXmlDescriptionResourceEntry(
      ResourceTable resourceTable, int resourceId) {
    return ResourcesUtils.lookupEntryByResourceId(resourceTable, resourceId)
        .orElseThrow(
            () ->
                ValidationException.builder()
                    .withMessage(
                        "Resource 0x%08x is referenced in the manifest in the '%s' metadata, but "
                            + "was not found in the resource table.",
                        resourceId, WEAR_APK_1_0_METADATA_KEY)
                    .build());
  }

  private static String getXmlDescriptionPath(Entry entry) {
    checkState(entry.getConfigValueCount() > 0, "Resource table entry without value: %s", entry);

    if (entry.getConfigValueCount() > 1) {
      throw new ValidationException("More than one embedded Wear APK is not supported.");
    }

    ConfigValue configValue = Iterables.getOnlyElement(entry.getConfigValueList());
    String xmlDescriptionPath = configValue.getValue().getItem().getFile().getPath();

    if (xmlDescriptionPath.isEmpty()) {
      throw new ValidationException("No XML description file path found for Wear APK.");
    }

    return xmlDescriptionPath;
  }

  private static ModuleEntry findXmlDescriptionZipEntry(ModuleSplit split, String xmlDescPath) {
    return split
        .findEntry(xmlDescPath)
        .orElseThrow(
            () ->
                ValidationException.builder()
                    .withMessage(
                        "Wear APK XML description file expected at '%s' but was not found.",
                        xmlDescPath)
                    .build());
  }

  /**
   * Parses the XML description file for the name of the wear APK.
   *
   * <p>According to
   * https://developer.android.com/training/wearables/apps/packaging#PackageManually, it is the
   * value inside the tag <rawPathResId>.
   */
  private static Optional<String> extractWearApkName(ModuleEntry wearApkDescriptionXmlEntry) {
    XmlProtoNode root;
    try (InputStream content = wearApkDescriptionXmlEntry.getContent()) {
      root = new XmlProtoNode(XmlNode.parseFrom(content));
    } catch (InvalidProtocolBufferException e) {
      throw ValidationException.builder()
          .withCause(e)
          .withMessage(
              "The wear APK description file '%s' could not be parsed.",
              wearApkDescriptionXmlEntry.getPath())
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "An unexpected error occurred while reading APK description file '%s'.",
              wearApkDescriptionXmlEntry.getPath()),
          e);
    }

    // If the Wear APK is unbundled, there is nothing to find.
    if (root.getElement().getOptionalChildElement("unbundled").isPresent()) {
      return Optional.empty();
    }

    // If 'unbundled' is not present, then 'rawPathResId' must be.
    Optional<XmlProtoElement> rawPathResId =
        root.getElement().getOptionalChildElement("rawPathResId");
    if (!rawPathResId.isPresent()) {
      throw ValidationException.builder()
          .withMessage(
              "The wear APK description file '%s' does not contain 'unbundled' or 'rawPathResId'.",
              wearApkDescriptionXmlEntry.getPath())
          .build();
    }

    return Optional.of(rawPathResId.get().getChildText().get().getText());
  }

  private WearApkLocator() {}
}
