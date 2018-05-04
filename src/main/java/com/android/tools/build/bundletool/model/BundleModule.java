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

package com.android.tools.build.bundletool.model;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.manifest.AndroidManifest;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Represents a single module inside App Bundle.
 *
 * <p>The ZipEntries of the instances of this class refer only to regular files (and no
 * directories).
 */
@AutoValue
public abstract class BundleModule {

  public static final String MANIFEST_FILENAME = "AndroidManifest.xml";

  public static final ZipPath ASSETS_DIRECTORY = ZipPath.create("assets");
  public static final ZipPath DEX_DIRECTORY = ZipPath.create("dex");
  public static final ZipPath LIB_DIRECTORY = ZipPath.create("lib");
  public static final ZipPath MANIFEST_DIRECTORY = ZipPath.create("manifest");
  public static final ZipPath RESOURCES_DIRECTORY = ZipPath.create("res");
  public static final ZipPath ROOT_DIRECTORY = ZipPath.create("root");

  public static final ZipPath ASSETS_PROTO_PATH = ZipPath.create("assets.pb");
  public static final ZipPath MANIFEST_PATH = MANIFEST_DIRECTORY.resolve(MANIFEST_FILENAME);
  public static final ZipPath NATIVE_PROTO_PATH = ZipPath.create("native.pb");
  public static final ZipPath RESOURCES_PROTO_PATH = ZipPath.create("resources.pb");

  public abstract BundleModuleName getName();

  public abstract AndroidManifest getAndroidManifest();

  public abstract Optional<ResourceTable> getResourceTable();

  public abstract Optional<Assets> getAssetsConfig();

  public abstract Optional<NativeLibraries> getNativeConfig();

  /**
   * Returns entries of the module, indexed by their module path.
   *
   * <p>Note that special module files (eg. {@code AndroidManifest.xml} are NOT represented as
   * entries.
   */
  abstract ImmutableMap<ZipPath, ModuleEntry> getEntryMap();

  /**
   * Returns entries of the module.
   *
   * <p>Note that special module files (eg. {@code AndroidManifest.xml} are NOT represented as
   * entries.
   */
  public ImmutableCollection<ModuleEntry> getEntries() {
    return getEntryMap().values();
  }

  public boolean isBaseModule() {
    return BundleModuleName.BASE_MODULE_NAME.equals(getName().getName());
  }

  public boolean isIncludedInFusing() {
    // The following should never throw if the module/bundle has been validated.
    return isBaseModule() || getAndroidManifest().getIsModuleIncludedInFusing().get();
  }

  /**
   * Returns all {@link ModuleEntry} of the module that match the predicate on the relative path of
   * the entries in the module.
   *
   * <p>Note that special module files (eg. {@code AndroidManifest.xml} are NOT represented as
   * entries.
   */
  public Stream<ModuleEntry> findEntries(Predicate<ZipPath> pathPredicate) {
    return getEntries().stream().filter(entry -> pathPredicate.test(entry.getPath()));
  }

  /**
   * Returns all {@link ModuleEntry} whose relative module path is under the given path.
   *
   * <p>Note that special module files (eg. {@code AndroidManifest.xml} are NOT represented as
   * entries.
   */
  public Stream<ModuleEntry> findEntriesUnderPath(ZipPath path) {
    return findEntries(p -> p.startsWith(path));
  }

  /** Returns entry with the given relative module path, if it exists. */
  public Optional<ModuleEntry> getEntry(ZipPath path) {
    return Optional.ofNullable(getEntryMap().get(path));
  }

  public ModuleMetadata getModuleMetadata() {
    return ModuleMetadata.newBuilder()
        .setName(getName().getName())
        .setOnDemand(getAndroidManifest().isOnDemandModule().orElse(false))
        .build();
  }

  public static Builder builder() {
    return new AutoValue_BundleModule.Builder();
  }

  abstract Builder toBuilder();

  /** Builder for BundleModule. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(BundleModuleName value);

    abstract ImmutableMap.Builder<ZipPath, ModuleEntry> entryMapBuilder();

    abstract Builder setEntryMap(ImmutableMap<ZipPath, ModuleEntry> entryMap);

    abstract Builder setAndroidManifest(AndroidManifest manifest);

    abstract Builder setResourceTable(ResourceTable resourceTable);

    abstract Builder setAssetsConfig(Assets assetsConfig);

    abstract Builder setNativeConfig(NativeLibraries nativeConfig);

    /** @see #addEntry(ModuleEntry) */
    public Builder addEntries(Collection<ModuleEntry> entries) throws IOException {
      for (ModuleEntry entry : entries) {
        addEntry(entry);
      }
      return this;
    }

    /**
     * Adds the given entry to the module.
     *
     * <p>Certain files (eg. AndroidManifest.xml and several module meta-data files) are immediately
     * parsed and stored in dedicated class fields instead of as entries.
     *
     * @throws IOException when the entry cannot be read or has invalid contents
     */
    public Builder addEntry(ModuleEntry moduleEntry) throws IOException {
      if (moduleEntry.getPath().equals(MANIFEST_PATH)) {
        try (InputStream inputStream = moduleEntry.getContent()) {
          setAndroidManifest(AndroidManifest.create(XmlNode.parseFrom(inputStream)));
        }
      } else if (moduleEntry.getPath().equals(RESOURCES_PROTO_PATH)) {
        try (InputStream inputStream = moduleEntry.getContent()) {
          setResourceTable(ResourceTable.parseFrom(inputStream));
        }
      } else if (moduleEntry.getPath().equals(ASSETS_PROTO_PATH)) {
        try (InputStream inputStream = moduleEntry.getContent()) {
          setAssetsConfig(Assets.parseFrom(inputStream));
        }
      } else if (moduleEntry.getPath().equals(NATIVE_PROTO_PATH)) {
        try (InputStream inputStream = moduleEntry.getContent()) {
          setNativeConfig(NativeLibraries.parseFrom(inputStream));
        }
      } else if (!moduleEntry.isDirectory()) {
        entryMapBuilder().put(moduleEntry.getPath(), moduleEntry);
      }

      return this;
    }

    public abstract BundleModule build();
  }
}
