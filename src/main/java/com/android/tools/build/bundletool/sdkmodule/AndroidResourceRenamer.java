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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Renames android resource entries of the given SDK module, to avoid file name clashes between the
 * app and the SDK resources in non-sdk-runtime variant.
 */
final class AndroidResourceRenamer {

  private final String sdkPackageNameHash;

  AndroidResourceRenamer(SdkModulesConfig sdkModulesConfig) {
    this.sdkPackageNameHash = getAlphaNumericHash(sdkModulesConfig.getSdkPackageName());
  }

  BundleModule renameAndroidResources(BundleModule module) {
    if (!module.getResourceTable().isPresent()) {
      return module;
    }
    ImmutableSet<ZipPath> androidResourcePaths =
        ResourcesUtils.getAllFileReferences(module.getResourceTable().get());
    // Rename resource entries, and keep all other entries unchanged.
    ImmutableList<ModuleEntry> newEntries =
        module.getEntries().stream()
            .map(
                entry ->
                    androidResourcePaths.contains(entry.getPath())
                        ? updateAndroidResourceEntryPath(entry)
                        : entry)
            .collect(toImmutableList());
    return module.toBuilder()
        // Rename files in the resource table.
        .setResourceTable(renameInResourceTable(module.getResourceTable().get()))
        .setRawEntries(newEntries)
        .build();
  }

  private ModuleEntry updateAndroidResourceEntryPath(ModuleEntry androidResourceEntry) {
    return androidResourceEntry.toBuilder()
        .setPath(renamedResourcePath(androidResourceEntry.getPath()))
        .build();
  }

  private ResourceTable renameInResourceTable(ResourceTable resourceTable) {
    ResourceTable.Builder renamedResourceTableBuilder = resourceTable.toBuilder();
    renameInResourceTable(renamedResourceTableBuilder);
    return renamedResourceTableBuilder.build();
  }

  private void renameInResourceTable(ResourceTable.Builder resourceTable) {
    resourceTable.getPackageBuilderList().forEach(this::renameInPackage);
  }

  private void renameInPackage(Package.Builder resourceTablePackage) {
    resourceTablePackage.getTypeBuilderList().forEach(this::renameInType);
  }

  private void renameInType(Type.Builder type) {
    type.getEntryBuilderList().forEach(this::renameInEntry);
  }

  private void renameInEntry(Entry.Builder entry) {
    entry.getConfigValueBuilderList().forEach(this::renameInConfigValue);
  }

  private void renameInConfigValue(ConfigValue.Builder configValue) {
    if (!configValue.getValue().getItem().hasFile()) {
      return;
    }
    renameInFile(configValue.getValueBuilder().getItemBuilder().getFileBuilder());
  }

  private void renameInFile(FileReference.Builder file) {
    file.setPath(renamedResourcePath(ZipPath.create(file.getPath())).toString());
  }

  private ZipPath renamedResourcePath(ZipPath oldPath) {
    if (oldPath == null
        || oldPath.getParent() == null
        || oldPath.getParent().equals(ZipPath.ROOT)) {
      throw InvalidBundleException.createWithUserMessage(
          "Android resource entry with unexpected path: " + oldPath);
    }
    return oldPath.getParent().resolve(sdkPackageNameHash + oldPath.getFileName());
  }

  private static String getAlphaNumericHash(String packageName) {
    String result;
    try {
      result =
          ByteSource.wrap(packageName.getBytes(UTF_8))
              .hash(Hashing.farmHashFingerprint64())
              .toString();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when calculating the hash.", e);
    }
    return result;
  }
}
