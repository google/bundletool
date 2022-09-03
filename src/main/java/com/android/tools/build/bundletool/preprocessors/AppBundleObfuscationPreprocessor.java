/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.build.bundletool.preprocessors;

import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORY;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.Value.ValueCase;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.validation.ResourceTableValidator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Predicate;

/**
 * A bundle preprocessor that obfuscates the resource name paths.
 *
 * <p>Assumes that there is a 1:1 mapping between the files under {@code res/} and the files
 * referenced from the resource table (see {@link ResourceTableValidator}).
 *
 * <p>The preprocessor is fully deterministic. This is ensured by processing bundle modules and
 * resources in a fixed order.
 */
public class AppBundleObfuscationPreprocessor implements AppBundlePreprocessor {
  private static final int RESOURCE_NAME_LENGTH = 6;

  private final Predicate<ZipPath> shouldObfuscate;

  AppBundleObfuscationPreprocessor(Predicate<ZipPath> shouldObfuscate) {
    this.shouldObfuscate = shouldObfuscate;
  }

  @Override
  public AppBundle preprocess(AppBundle originalAppBundle) {
    HashMap<String, String> resourceNameMapping = new HashMap<>();
    AppBundle.Builder newAppBundle = originalAppBundle.toBuilder();
    ImmutableList.Builder<BundleModule> obfuscatedBundleModules = ImmutableList.builder();
    for (BundleModule originalModule : getSortedBundleModulelist(originalAppBundle)) {
      obfuscatedBundleModules.add(
          obfuscateBundleModule(originalModule, resourceNameMapping, shouldObfuscate));
    }
    return newAppBundle.setRawModules(obfuscatedBundleModules.build()).build();
  }

  private static ResourceTable obfuscateResourceTableEntries(
      ResourceTable initialResourceTable, ImmutableMap<String, String> resourceNameMapping) {
    ResourceTable.Builder modifiedResourceTable = initialResourceTable.toBuilder();
    for (Package.Builder pkg : modifiedResourceTable.getPackageBuilderList()) {
      for (Resources.Type.Builder type : pkg.getTypeBuilderList()) {
        for (Resources.Entry.Builder entry : type.getEntryBuilderList()) {
          for (ConfigValue.Builder config : entry.getConfigValueBuilderList()) {
            Value.Builder value = config.getValueBuilder();
            if (value.getValueCase() != ValueCase.ITEM) {
              continue;
            }
            Item.Builder item = value.getItemBuilder();
            if (item.getValueCase() != Item.ValueCase.FILE) {
              continue;
            }
            FileReference.Builder fileReference = item.getFileBuilder();
            String fileReferencePath = fileReference.getPath();
            if (!resourceNameMapping.containsKey(fileReferencePath)) {
              continue;
            }
            fileReference.setPath(resourceNameMapping.get(fileReferencePath));
          }
        }
      }
    }

    return modifiedResourceTable.build();
  }

  private static ImmutableList<ModuleEntry> obfuscateModuleEntries(
      ImmutableSet<ModuleEntry> toBeObfuscatedEntries,
      HashMap<String, String> resourceNameMapping) {
    ImmutableList<ModuleEntry> sortedEntries =
        ImmutableList.sortedCopyOf(
            Comparator.comparing(ModuleEntry::getPath), toBeObfuscatedEntries);
    ImmutableList.Builder<ModuleEntry> obfuscatedEntries = ImmutableList.builder();
    for (ModuleEntry moduleEntry : sortedEntries) {
      ZipPath newPath =
          obfuscateZipPath(moduleEntry.getPath(), ImmutableMap.copyOf(resourceNameMapping));
      resourceNameMapping.put(moduleEntry.getPath().toString(), newPath.toString());
      ModuleEntry newModuleEntry = moduleEntry.toBuilder().setPath(newPath).build();
      obfuscatedEntries.add(newModuleEntry);
    }
    return obfuscatedEntries.build();
  }

  private static BundleModule obfuscateBundleModule(
      BundleModule bundleModule,
      HashMap<String, String> resourceNameMapping,
      Predicate<ZipPath> shouldObfuscate) {
    ImmutableSet<ModuleEntry> toBeObfuscatedEntries =
        bundleModule
            .findEntriesUnderPath(RESOURCES_DIRECTORY)
            .filter(moduleEntry -> shouldObfuscate.test(moduleEntry.getPath()))
            .collect(toImmutableSet());

    ImmutableList<ModuleEntry> otherEntries =
        bundleModule.getEntries().stream()
            .filter(moduleEntry -> !toBeObfuscatedEntries.contains(moduleEntry))
            .collect(toImmutableList());

    BundleModule.Builder obfuscatedBundleModule =
        bundleModule.toBuilder()
            .setRawEntries(
                ImmutableList.<ModuleEntry>builder()
                    .addAll(obfuscateModuleEntries(toBeObfuscatedEntries, resourceNameMapping))
                    .addAll(otherEntries)
                    .build());
    if (bundleModule.getResourceTable().isPresent()) {
      obfuscatedBundleModule.setResourceTable(
          obfuscateResourceTableEntries(
              bundleModule.getResourceTable().get(), ImmutableMap.copyOf(resourceNameMapping)));
    }
    return obfuscatedBundleModule.build();
  }

  private static ImmutableList<BundleModule> getSortedBundleModulelist(AppBundle appBundle) {
    return ImmutableList.sortedCopyOf(
        Comparator.comparing(BundleModule::getName), appBundle.getModules().values());
  }

  private static ZipPath obfuscateZipPath(
      ZipPath oldZipPath, ImmutableMap<String, String> resourceNameMapping) {
    HashCode hashCode = Hashing.sha256().hashString(oldZipPath.toString(), StandardCharsets.UTF_8);
    String encodedString =
        Base64.getUrlEncoder()
            .encodeToString(Arrays.copyOf(hashCode.asBytes(), RESOURCE_NAME_LENGTH));

    while (resourceNameMapping.containsValue("res/" + encodedString)) {
      encodedString = handleCollision(hashCode.asBytes());
    }
    String fileExtension = FileUtils.getFileExtension(oldZipPath);
    // The "xml" extension has to be preserved, because the Android Platform requires it
    if (Ascii.equalsIgnoreCase(fileExtension, "xml")) {
      encodedString = encodedString + "." + fileExtension;
    }
    return RESOURCES_DIRECTORY.resolve(encodedString);
  }

  private static String handleCollision(byte[] hashedRepresentation) {
    hashedRepresentation = Arrays.copyOf(hashedRepresentation, RESOURCE_NAME_LENGTH);
    BigInteger bigInteger = new BigInteger(hashedRepresentation);
    byte[] newHashedRepresentation = bigInteger.toByteArray();
    if (newHashedRepresentation.length > RESOURCE_NAME_LENGTH) {
      newHashedRepresentation = new byte[6];
    }

    return Base64.getUrlEncoder().encodeToString(newHashedRepresentation);
  }

  @VisibleForTesting
  String hashFilePath(String stringPath) {
    HashCode hashCode = Hashing.sha256().hashString(stringPath, StandardCharsets.UTF_8);
    return Base64.getUrlEncoder()
        .encodeToString(Arrays.copyOf(hashCode.asBytes(), RESOURCE_NAME_LENGTH));
  }
}
