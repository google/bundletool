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

import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.ALWAYS_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType.NO_INITIAL_INSTALL;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
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

  /** The top-level directory of an App Bundle module that contains APEX image files. */
  public static final ZipPath APEX_DIRECTORY = ZipPath.create("apex");

  public static final ZipPath ASSETS_PROTO_PATH = ZipPath.create("assets.pb");
  public static final ZipPath MANIFEST_PATH = MANIFEST_DIRECTORY.resolve(MANIFEST_FILENAME);
  public static final ZipPath NATIVE_PROTO_PATH = ZipPath.create("native.pb");
  public static final ZipPath RESOURCES_PROTO_PATH = ZipPath.create("resources.pb");

  /** The top-level file of an App Bundle module that contains APEX targeting configuration. */
  public static final ZipPath APEX_PROTO_PATH = ZipPath.create("apex.pb");

  /** The file of an App Bundle module that contains the APEX manifest. */
  public static final ZipPath APEX_MANIFEST_PATH = ZipPath.create("root/apex_manifest.json");

  /** Used to parse file names in the apex/ directory, for multi-Abi targeting. */
  public static final Splitter ABI_SPLITTER = Splitter.on(".").omitEmptyStrings();

  public abstract BundleModuleName getName();

  /** Describes how the module is delivered at install-time. */
  public enum ModuleDeliveryType {
    ALWAYS_INITIAL_INSTALL,
    CONDITIONAL_INITIAL_INSTALL,
    NO_INITIAL_INSTALL
  };

  /** The version of Bundletool that built this module, taken from BundleConfig. */
  public abstract BundleConfig getBundleConfig();

  abstract XmlNode getAndroidManifestProto();

  @Memoized
  public AndroidManifest getAndroidManifest() {
    return AndroidManifest.create(
        getAndroidManifestProto(), BundleToolVersion.getVersionFromBundleConfig(getBundleConfig()));
  }

  public abstract Optional<ResourceTable> getResourceTable();

  public abstract Optional<Assets> getAssetsConfig();

  public abstract Optional<NativeLibraries> getNativeConfig();

  public abstract Optional<ApexImages> getApexConfig();

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

  public ModuleDeliveryType getDeliveryType() {
    if (getAndroidManifest().getManifestDeliveryElement().isPresent()) {
      ManifestDeliveryElement manifestDeliveryElement =
          getAndroidManifest().getManifestDeliveryElement().get();
      if (manifestDeliveryElement.hasInstallTimeElement()) {
        return manifestDeliveryElement.hasModuleConditions()
            ? CONDITIONAL_INITIAL_INSTALL
            : ALWAYS_INITIAL_INSTALL;
      } else {
        return NO_INITIAL_INSTALL;
      }
    }

    // Handling legacy on-demand attribute value.
    if (getAndroidManifest()
        .getOnDemandAttribute()
        .map(XmlProtoAttribute::getValueAsBoolean)
        .orElse(false)) {
      return NO_INITIAL_INSTALL;
    }

    // Legacy onDemand attribute is equal to false or for base module: no delivery information.
    return ALWAYS_INITIAL_INSTALL;
  }

  public boolean isIncludedInFusing() {
    // The following should never throw if the module/bundle has been validated.
    return isBaseModule() || getAndroidManifest().getIsModuleIncludedInFusing().get();
  }

  public boolean isInstantModule() {
    Optional<Boolean> isInstantModule = getAndroidManifest().isInstantModule();
    return isInstantModule.orElse(false);
  }

  @Memoized
  public boolean hasRenderscript32Bitcode() {
    return findEntries(zipPath -> zipPath.toString().endsWith(".bc")).findFirst().isPresent();
  }

  public ImmutableList<String> getDependencies() {
    return getAndroidManifest().getUsesSplits();
  }

  private ModuleTargeting getModuleTargeting() {
    return getAndroidManifest()
        .getManifestDeliveryElement()
        .map(ManifestDeliveryElement::getModuleConditions)
        .map(ModuleConditions::toTargeting)
        .orElse(ModuleTargeting.getDefaultInstance());
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
        .setOnDemand(getDeliveryType().equals(NO_INITIAL_INSTALL))
        .setIsInstant(isInstantModule())
        .addAllDependencies(getDependencies())
        .setTargeting(getModuleTargeting())
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

    public abstract Builder setBundleConfig(BundleConfig value);

    abstract BundleConfig getBundleConfig();

    abstract ImmutableMap.Builder<ZipPath, ModuleEntry> entryMapBuilder();

    abstract Builder setEntryMap(ImmutableMap<ZipPath, ModuleEntry> entryMap);

    abstract Builder setAndroidManifestProto(XmlNode manifestProto);

    abstract Builder setResourceTable(ResourceTable resourceTable);

    abstract Builder setAssetsConfig(Assets assetsConfig);

    abstract Builder setNativeConfig(NativeLibraries nativeConfig);

    abstract Builder setApexConfig(ApexImages apexConfig);

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
          setAndroidManifestProto(XmlNode.parseFrom(inputStream));
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
      } else if (moduleEntry.getPath().equals(APEX_PROTO_PATH)) {
        try (InputStream inputStream = moduleEntry.getContent()) {
          setApexConfig(ApexImages.parseFrom(inputStream));
        }
      } else if (!moduleEntry.isDirectory()) {
        entryMapBuilder().put(moduleEntry.getPath(), moduleEntry);
      }

      return this;
    }

    public abstract BundleModule build();
  }
}
