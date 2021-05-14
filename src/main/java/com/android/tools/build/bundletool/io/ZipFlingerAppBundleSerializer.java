/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.AppBundle.BUNDLE_CONFIG_FILE_NAME;
import static com.android.tools.build.bundletool.model.AppBundle.METADATA_DIRECTORY;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableListMultimap.flatteningToImmutableListMultimap;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryBundleLocation;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.Source;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipSource;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.io.ByteSource;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializer of Bundle instances onto disk that sources unmodified files from on-disk bundles if
 * possible.
 */
public final class ZipFlingerAppBundleSerializer {

  /** Medium compression, see {@link java.util.zip.Deflater#Deflater(int)}. */
  private static final int DEFAULT_COMPRESSION_LEVEL = 6;

  /** Writes the App Bundle on disk at the given location. */
  public void writeToDisk(AppBundle bundle, Path destBundlePath) throws IOException {
    try (ZipArchive zipArchive = new ZipArchive(destBundlePath)) {
      zipArchive.add(
          protoToSource(
              ZipPath.create(BUNDLE_CONFIG_FILE_NAME),
              bundle.getBundleConfig(),
              DEFAULT_COMPRESSION_LEVEL));

      // APEX bundles do not have metadata files.
      if (bundle.getFeatureModules().isEmpty() || !bundle.isApex()) {
        for (Map.Entry<ZipPath, ByteSource> metadataEntry :
            bundle.getBundleMetadata().getFileContentMap().entrySet()) {
          zipArchive.add(
              new BytesSource(
                  metadataEntry.getValue().read(),
                  METADATA_DIRECTORY.resolve(metadataEntry.getKey()).toString(),
                  DEFAULT_COMPRESSION_LEVEL));
        }
      }

      addEntriesFromSourceBundles(zipArchive, getUnmodifiedModuleEntries(bundle));
      addNewEntries(zipArchive, getNewOrModifiedModuleEntries(bundle));

      // Special module files are not represented as module entries (above).
      for (BundleModule module : bundle.getModules().values()) {
        ZipPath moduleDir = ZipPath.create(module.getName().toString());
        zipArchive.add(
            protoToSource(
                moduleDir.resolve(SpecialModuleEntry.ANDROID_MANIFEST.getPath()),
                module.getAndroidManifest().getManifestRoot().getProto(),
                DEFAULT_COMPRESSION_LEVEL));
        if (module.getAssetsConfig().isPresent()) {
          zipArchive.add(
              protoToSource(
                  moduleDir.resolve(SpecialModuleEntry.ASSETS_TABLE.getPath()),
                  module.getAssetsConfig().get(),
                  DEFAULT_COMPRESSION_LEVEL));
        }
        if (module.getNativeConfig().isPresent()) {
          zipArchive.add(
              protoToSource(
                  moduleDir.resolve(SpecialModuleEntry.NATIVE_LIBS_TABLE.getPath()),
                  module.getNativeConfig().get(),
                  DEFAULT_COMPRESSION_LEVEL));
        }
        if (module.getResourceTable().isPresent()) {
          zipArchive.add(
              protoToSource(
                  moduleDir.resolve(SpecialModuleEntry.RESOURCE_TABLE.getPath()),
                  module.getResourceTable().get(),
                  DEFAULT_COMPRESSION_LEVEL));
        }
        if (module.getApexConfig().isPresent()) {
          zipArchive.add(
              protoToSource(
                  moduleDir.resolve(SpecialModuleEntry.APEX_TABLE.getPath()),
                  module.getApexConfig().get(),
                  DEFAULT_COMPRESSION_LEVEL));
        }
      }
    }
  }

  /** Does not consider special entries or metadata. */
  @VisibleForTesting
  static ImmutableListMultimap<BundleModule, ModuleEntry> getUnmodifiedModuleEntries(
      AppBundle bundle) {
    return bundle.getModules().values().stream()
        .collect(
            flatteningToImmutableListMultimap(
                module -> module,
                module ->
                    module.getEntries().stream()
                        .filter(entry -> entry.getBundleLocation().isPresent())));
  }

  /** Does not consider special entries or metadata. */
  private static ImmutableListMultimap<BundleModule, ModuleEntry> getNewOrModifiedModuleEntries(
      AppBundle bundle) {
    return bundle.getModules().values().stream()
        .collect(
            flatteningToImmutableListMultimap(
                module -> module,
                module ->
                    module.getEntries().stream()
                        .filter(entry -> !entry.getBundleLocation().isPresent())));
  }

  /** Adds umodified entries to an archive, sourcing them from their original on-disk location. */
  private static void addEntriesFromSourceBundles(
      ZipArchive archive, ImmutableListMultimap<BundleModule, ModuleEntry> entries)
      throws IOException {
    Map<Path, ZipSource> bundleSources = new HashMap<>();

    for (Map.Entry<BundleModule, ModuleEntry> moduleAndEntry : entries.entries()) {
      BundleModule module = moduleAndEntry.getKey();
      ModuleEntry moduleEntry = moduleAndEntry.getValue();
      ModuleEntryBundleLocation location =
          moduleEntry.getBundleLocation().orElseThrow(IllegalStateException::new);

      ZipPath entryFullPathInSourceBundle = location.entryPathInBundle();
      ZipPath moduleDir = ZipPath.create(module.getName().toString());
      ZipPath entryFullPathInDestBundle = moduleDir.resolve(moduleEntry.getPath());
      Path pathToBundle = location.pathToBundle();
      // We cannot use computeIfAbstent because new ZipSource may throw.
      ZipSource entrySource =
          bundleSources.containsKey(pathToBundle)
              ? bundleSources.get(pathToBundle)
              : new ZipSource(pathToBundle);
      bundleSources.putIfAbsent(pathToBundle, entrySource);
      entrySource.select(
          entryFullPathInSourceBundle.toString(),
          /* newName= */ entryFullPathInDestBundle.toString());
    }

    for (ZipSource source : bundleSources.values()) {
      archive.add(source);
    }
  }

  /** Adds new and modified entries to an archive, compressing them. */
  private static void addNewEntries(
      ZipArchive archive, ImmutableListMultimap<BundleModule, ModuleEntry> entries)
      throws IOException {
    for (Map.Entry<BundleModule, ModuleEntry> moduleAndEntry : entries.entries()) {
      BundleModule module = moduleAndEntry.getKey();
      ModuleEntry moduleEntry = moduleAndEntry.getValue();
      checkState(!moduleEntry.getBundleLocation().isPresent());

      ZipPath moduleDir = ZipPath.create(module.getName().toString());
      ZipPath destPath = moduleDir.resolve(moduleEntry.getPath());
      archive.add(
          new BytesSource(
              moduleEntry.getContent().read(), destPath.toString(), DEFAULT_COMPRESSION_LEVEL));
    }
  }

  private static Source protoToSource(ZipPath path, MessageLite proto, int compression)
      throws IOException {
    return new BytesSource(proto.toByteArray(), path.toString(), compression);
  }
}
