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
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
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
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
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
  }

  /** Describes the content type of the module. */
  public enum ModuleType {
    FEATURE_MODULE,
    ASSET_MODULE
  }

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

  public ModuleType getModuleType() {
    // If the module type is not defined in the manifest, default to feature module for backwards
    // compatibility.
    return getAndroidManifest().getModuleType();
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
        .map(this::applyMinSdkVersion)
        .orElse(ModuleTargeting.getDefaultInstance());
  }

  private ModuleTargeting applyMinSdkVersion(ModuleTargeting moduleTargeting) {
    // MinSdkVersion of the module is applied to module targeting if:
    // (1) module is conditional, (2) it doesn't have min-sdk condition already
    // and (3) minSdkVersion of the module is explicitly defined in the manifest.
    Optional<Integer> minSdkVersion = getAndroidManifest().getMinSdkVersion();
    if (moduleTargeting.equals(ModuleTargeting.getDefaultInstance())
        || moduleTargeting.hasSdkVersionTargeting()
        || !minSdkVersion.isPresent()) {
      return moduleTargeting;
    }

    return moduleTargeting
        .toBuilder()
        .setSdkVersionTargeting(sdkVersionTargeting(sdkVersionFrom(minSdkVersion.get())))
        .build();
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

  public abstract Builder toBuilder();

  /** Builder for BundleModule. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(BundleModuleName value);

    public abstract Builder setBundleConfig(BundleConfig value);

    public abstract Builder setResourceTable(ResourceTable resourceTable);

    public abstract Builder setAndroidManifestProto(XmlNode manifestProto);

    public abstract Builder setAssetsConfig(Assets assetsConfig);

    public abstract Builder setNativeConfig(NativeLibraries nativeConfig);

    public abstract Builder setApexConfig(ApexImages apexConfig);

    abstract ImmutableMap.Builder<ZipPath, ModuleEntry> entryMapBuilder();

    abstract Builder setEntryMap(ImmutableMap<ZipPath, ModuleEntry> entryMap);

    /**
     * Convenience method to set all entries at once.
     *
     * <p>Note: this method does not accept special entries such as manifest or resource table.
     * Thus, prefer using the {@link #addEntry(ModuleEntry)} method when possible, since it also
     * accepts the special files.
     */
    public Builder setRawEntries(Collection<ModuleEntry> entries) {
      entries.forEach(
          entry ->
              checkArgument(
                  !SpecialModuleEntry.getSpecialEntry(entry.getPath()).isPresent(),
                  "Cannot add special entry '%s' using method setRawEntries.",
                  entry.getPath()));
      setEntryMap(entries.stream().collect(toImmutableMap(ModuleEntry::getPath, identity())));
      return this;
    }

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
      Optional<SpecialModuleEntry> specialEntry =
          SpecialModuleEntry.getSpecialEntry(moduleEntry.getPath());
      if (specialEntry.isPresent()) {
        try (InputStream inputStream = moduleEntry.getContent()) {
          specialEntry.get().addToModule(this, inputStream);
        }
      } else if (!moduleEntry.isDirectory()) {
        entryMapBuilder().put(moduleEntry.getPath(), moduleEntry);
      }

      return this;
    }

    public abstract BundleModule build();
  }

  /**
   * A special entry in a module of the Android App Bundle.
   *
   * <p>An entry is considered special when it's read by bundletool.
   */
  public enum SpecialModuleEntry {
    ANDROID_MANIFEST("manifest/AndroidManifest.xml") {
      @Override
      void addToModule(BundleModule.Builder module, InputStream inputStream) throws IOException {
        module.setAndroidManifestProto(XmlNode.parseFrom(inputStream));
      }
    },
    RESOURCE_TABLE("resources.pb") {
      @Override
      void addToModule(BundleModule.Builder module, InputStream inputStream) throws IOException {
        module.setResourceTable(ResourceTable.parseFrom(inputStream));
      }
    },
    ASSETS_TABLE("assets.pb") {
      @Override
      void addToModule(BundleModule.Builder module, InputStream inputStream) throws IOException {
        module.setAssetsConfig(Assets.parseFrom(inputStream));
      }
    },
    NATIVE_LIBS_TABLE("native.pb") {
      @Override
      void addToModule(BundleModule.Builder module, InputStream inputStream) throws IOException {
        module.setNativeConfig(NativeLibraries.parseFrom(inputStream));
      }
    },
    APEX_TABLE("apex.pb") {
      @Override
      void addToModule(BundleModule.Builder module, InputStream inputStream) throws IOException {
        module.setApexConfig(ApexImages.parseFrom(inputStream));
      }
    };

    private static final ImmutableMap<ZipPath, SpecialModuleEntry> SPECIAL_ENTRY_BY_PATH =
        Arrays.stream(SpecialModuleEntry.values())
            .collect(toImmutableMap(SpecialModuleEntry::getPath, identity()));

    abstract void addToModule(BundleModule.Builder module, InputStream inputStream)
        throws IOException;

    /**
     * Returns the {@link SpecialModuleEntry} instance associated with the given path, or an empty
     * Optional if the path is not a special entry.
     */
    public static Optional<SpecialModuleEntry> getSpecialEntry(ZipPath entryPath) {
      return Optional.ofNullable(SPECIAL_ENTRY_BY_PATH.get(entryPath));
    }

    private final ZipPath entryPath;

    private SpecialModuleEntry(String entryPath) {
      this.entryPath = ZipPath.create(entryPath);
    }

    public ZipPath getPath() {
      return entryPath;
    }
  }
}
