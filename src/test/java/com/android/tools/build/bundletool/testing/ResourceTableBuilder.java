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
package com.android.tools.build.bundletool.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.EntryId;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.android.aapt.Resources.TypeId;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.Visibility;
import com.android.aapt.Resources.Visibility.Level;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/** Helper to build resource tables for tests. */
public final class ResourceTableBuilder {

  private final ResourceTable.Builder table;

  private Package.Builder currentPackage;
  private int highestPackageId = 0x7F;
  private int highestTypeId;

  public ResourceTableBuilder() {
    table = ResourceTable.newBuilder();
  }

  public ResourceTableBuilder addPackage(String packageName) {
    return addPackage(packageName, highestPackageId++);
  }

  public ResourceTableBuilder addPackage(String packageName, int packageId) {
    checkArgument(
        table.getPackageList().stream().noneMatch(pkg -> pkg.getPackageId().getId() == packageId),
        "Package ID %s already in use.",
        packageId);

    currentPackage =
        table
            .addPackageBuilder()
            .setPackageName(packageName)
            .setPackageId(PackageId.newBuilder().setId(packageId));

    // Type IDs start at 1.
    highestTypeId = 1;

    return this;
  }

  public ResourceTableBuilder addStringResource(String resourceName, String value) {
    return addResource(
        "string", resourceName, ConfigValue.newBuilder().setValue(newStringValue(value)).build());
  }

  /** Use empty string for default locale. */
  public ResourceTableBuilder addStringResourceForMultipleLocales(
      String resourceName, ImmutableMap<String, String> stringByLocale) {
    return addResourceForMultipleConfigs(
        "string",
        resourceName,
        stringByLocale,
        locale ->
            locale.isEmpty()
                ? Configuration.getDefaultInstance()
                : Configuration.newBuilder().setLocale(locale).build(),
        ResourceTableBuilder::newStringValue);
  }

  public ResourceTableBuilder addFileResource(
      String resourceType, String resourceName, String resFilePath) {
    return addResource(
        resourceType,
        resourceName,
        ConfigValue.newBuilder().setValue(newFileReferenceValue(resFilePath)).build());
  }

  public ResourceTableBuilder addFileResourceForMultipleConfigs(
      String resourceType,
      String resourceName,
      ImmutableMap<Configuration, String> filePathByConfiguration) {
    return addResourceForMultipleConfigs(
        resourceType,
        resourceName,
        filePathByConfiguration,
        Function.identity(),
        ResourceTableBuilder::newFileReferenceValue);
  }

  public ResourceTableBuilder addXmlResource(String resourceName, String resFilePath) {
    return addFileResource("xml", resourceName, resFilePath);
  }

  public ResourceTableBuilder addMipmapResource(String resourceName, String resFilePath) {
    return addFileResource("mipmap", resourceName, resFilePath);
  }

  /** Use 0 for default density. */
  public ResourceTableBuilder addMipmapResourceForMultipleDensities(
      String resourceName, ImmutableMap<Integer, String> filePathByDensity) {
    return addFileResourceForMultipleDensities("mipmap", resourceName, filePathByDensity);
  }

  public ResourceTableBuilder addDrawableResource(String resourceName, String resFilePath) {
    return addFileResource("drawable", resourceName, resFilePath);
  }

  /** Use 0 for default density. */
  public ResourceTableBuilder addDrawableResourceForMultipleDensities(
      String resourceName, ImmutableMap<Integer, String> filePathByDensity) {
    return addFileResourceForMultipleDensities("drawable", resourceName, filePathByDensity);
  }

  private ResourceTableBuilder addFileResourceForMultipleDensities(
      String resourceType, String resourceName, ImmutableMap<Integer, String> filePathByDensity) {
    return addFileResourceForMultipleConfigs(
        resourceType,
        resourceName,
        filePathByDensity.entrySet().stream()
            .collect(
                toImmutableMap(
                    entry ->
                        entry.getKey() == 0
                            ? Configuration.getDefaultInstance()
                            : Configuration.newBuilder().setDensity(entry.getKey()).build(),
                    entry -> entry.getValue(),
                    (u, v) -> {
                      throw new IllegalStateException("Duplicate key: " + u);
                    })));
  }

  private <K, V> ResourceTableBuilder addResourceForMultipleConfigs(
      String resourceType,
      String resourceName,
      ImmutableMap<K, V> sourceMap,
      Function<K, Configuration> configMaker,
      Function<V, Value> valueMaker) {
    ConfigValue[] configValues =
        sourceMap.entrySet().stream()
            .map(
                entry ->
                    ConfigValue.newBuilder()
                        .setConfig(configMaker.apply(entry.getKey()))
                        .setValue(valueMaker.apply(entry.getValue()))
                        .build())
            .toArray(ConfigValue[]::new);

    return addResource(resourceType, resourceName, configValues);
  }

  public ResourceTableBuilder addResource(
      String resourceType, String resourceName, ConfigValue... configValues) {
    checkState(currentPackage != null, "A package must be created before a resource can be added.");

    Type.Builder type = getResourceType(resourceType);
    int entryId = type.getEntryCount();
    type.addEntry(
        Entry.newBuilder()
            .setEntryId(EntryId.newBuilder().setId(entryId))
            .setVisibility(Visibility.newBuilder().setLevel(Level.PUBLIC))
            .setName(resourceName)
            .addAllConfigValue(Arrays.asList(configValues)));

    return this;
  }

  private Type.Builder getResourceType(String resourceType) {
    return currentPackage.getTypeBuilderList().stream()
        .filter(type -> type.getName().equals(resourceType))
        .findFirst()
        .orElseGet(
            () ->
                currentPackage
                    .addTypeBuilder()
                    .setName(resourceType)
                    .setTypeId(TypeId.newBuilder().setId(highestTypeId++)));
  }

  private static Value newStringValue(String value) {
    return Value.newBuilder()
        .setItem(Item.newBuilder().setStr(Resources.String.newBuilder().setValue(value)))
        .build();
  }

  private static Value newFileReferenceValue(String resFilePath) {
    FileReference.Builder fileReference = FileReference.newBuilder().setPath(resFilePath);
    getFileType(resFilePath).ifPresent(fileReference::setType);

    return Value.newBuilder().setItem(Item.newBuilder().setFile(fileReference)).build();
  }

  private static Optional<FileReference.Type> getFileType(String resFilePath) {
    switch (Files.getFileExtension(resFilePath).toLowerCase()) {
      case "png":
        return Optional.of(FileReference.Type.PNG);
      case "xml":
        return Optional.of(FileReference.Type.PROTO_XML);
      default:
        return Optional.empty();
    }
  }

  public ResourceTable build() {
    return table.build();
  }
}
