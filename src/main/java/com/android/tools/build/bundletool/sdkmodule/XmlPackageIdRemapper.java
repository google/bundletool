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
package com.android.tools.build.bundletool.sdkmodule;

import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.isAndroidResourceId;
import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.remapPackageIdInResourceId;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.partitioningBy;

import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Remaps resource IDs in the android manifest and XML resources of the given {@link BundleModule}
 * with a new package ID.
 */
final class XmlPackageIdRemapper {

  private final int newPackageId;

  XmlPackageIdRemapper(int newPackageId) {
    this.newPackageId = newPackageId;
  }

  /**
   * Updates resource IDs in the XML resources of the given module with the {@code newPackageId}.
   */
  BundleModule remap(BundleModule module) {
    ImmutableSet<ZipPath> xmlResourcePaths = getXmlResourcePaths(module);
    // Separate XML resource entries from all other entries.
    Map<Boolean, ImmutableSet<ModuleEntry>> partitionedEntries =
        module.getEntries().stream()
            .collect(
                partitioningBy(
                    entry -> xmlResourcePaths.contains(entry.getPath()), toImmutableSet()));
    // Remap resource IDs in XML resource entries, and keep all other entries unchanged.
    ImmutableList<ModuleEntry> newEntries =
        ImmutableList.<ModuleEntry>builder()
            .addAll(
                partitionedEntries.get(true).stream()
                    .map(this::remapInModuleEntry)
                    .collect(toImmutableSet()))
            .addAll(partitionedEntries.get(false))
            .build();
    // Remap resource IDs in AndroidManifest.xml
    XmlNode newAndroidManifest = remapInXmlNode(module.getAndroidManifestProto());
    return module.toBuilder()
        .setAndroidManifestProto(newAndroidManifest)
        .setRawEntries(newEntries)
        .build();
  }

  private static ImmutableSet<ZipPath> getXmlResourcePaths(BundleModule module) {
    if (module.getResourceTable().isPresent()) {
      return ResourcesUtils.getAllProtoXmlFileReferences(module.getResourceTable().get());
    }
    return ImmutableSet.of();
  }

  private ModuleEntry remapInModuleEntry(ModuleEntry moduleEntry) {
    try (InputStream inputStream = moduleEntry.getContent().openStream()) {
      XmlNode remappedXmlNode = remapInXmlNode(XmlNode.parseFrom(inputStream));
      return moduleEntry.toBuilder()
          .setContent(ByteSource.wrap(remappedXmlNode.toByteArray()))
          .build();
    } catch (InvalidProtocolBufferException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Error parsing XML file '%s'.", moduleEntry.getPath())
          .withCause(e)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when parsing an XML file.", e);
    }
  }

  private XmlNode remapInXmlNode(XmlNode xmlNode) {
    XmlNode.Builder remappedXmlNodeBuilder = xmlNode.toBuilder();
    remapInXmlNode(remappedXmlNodeBuilder);
    return remappedXmlNodeBuilder.build();
  }

  private void remapInXmlNode(XmlNode.Builder xmlNode) {
    if (xmlNode.hasElement()) {
      remapInElement(xmlNode.getElementBuilder());
    }
  }

  private void remapInElement(XmlElement.Builder xmlElement) {
    xmlElement.getAttributeBuilderList().forEach(this::remapInAttribute);
    xmlElement.getChildBuilderList().forEach(this::remapInXmlNode);
  }

  private void remapInAttribute(XmlAttribute.Builder xmlAttribute) {
    // Do not change resource IDs of Android framework attributes.
    if (!isAndroidResourceId(xmlAttribute.getResourceId())) {
      xmlAttribute.setResourceId(
          remapPackageIdInResourceId(xmlAttribute.getResourceId(), newPackageId));
    }
    if (xmlAttribute.hasCompiledItem()) {
      remapInCompiledItem(xmlAttribute.getCompiledItemBuilder());
    }
  }

  private void remapInCompiledItem(Item.Builder compiledItem) {
    // Do not change resource IDs of Android framework resources.
    if (!isAndroidResourceId(compiledItem.getRef().getId())) {
      compiledItem
          .getRefBuilder()
          .setId(remapPackageIdInResourceId(compiledItem.getRef().getId(), newPackageId));
    }
  }
}
