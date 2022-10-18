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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.FeatureModuleType;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.RuntimeEnabledSdkDependency;
import com.android.bundle.Config.ApexConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
  public static final ZipPath DRAWABLE_RESOURCE_DIRECTORY = ZipPath.create("res/drawable");

  /** The top-level directory of an App Bundle module that contains APEX image files. */
  public static final ZipPath APEX_DIRECTORY = ZipPath.create("apex");

  public static final String APEX_IMAGE_SUFFIX = "img";
  public static final String BUILD_INFO_SUFFIX = "build_info.pb";

  /** The file of an App Bundle module that contains the APEX manifest. */
  public static final ZipPath APEX_MANIFEST_PATH = ZipPath.create("root/apex_manifest.pb");

  public static final ZipPath APEX_MANIFEST_JSON_PATH = ZipPath.create("root/apex_manifest.json");

  /** The public key used to sign the apex */
  public static final ZipPath APEX_PUBKEY_PATH = ZipPath.create("root/apex_pubkey");

  /** The NOTICE file of an APEX Bundle module. */
  public static final ZipPath APEX_NOTICE_PATH = ZipPath.create("assets/NOTICE.html.gz");

  /** Used to parse file names in the apex/ directory, for multi-Abi targeting. */
  public static final Splitter ABI_SPLITTER = Splitter.on(".").omitEmptyStrings();

  public abstract BundleModuleName getName();

  /** Describes the content type of the module. */
  public enum ModuleType {
    UNKNOWN_MODULE_TYPE(false),
    FEATURE_MODULE(true),
    ASSET_MODULE(false),
    ML_MODULE(true),
    SDK_DEPENDENCY_MODULE(true);

    private final boolean isFeatureModule;

    ModuleType(boolean isFeatureModule) {
      this.isFeatureModule = isFeatureModule;
    }

    /**
     * Returns whether the module is a feature, including ML modules and AAB modules generated from
     * runtime-enabled SDK dependencies.
     */
    public boolean isFeatureModule() {
      return isFeatureModule;
    }
  }

  /** BundleType of the bundle that this module belongs to. */
  public abstract BundleType getBundleType();

  /** Version of Bundeltool used to build the bundle that this module belongs to. */
  public abstract Version getBundletoolVersion();

  public abstract XmlNode getAndroidManifestProto();

  @Memoized
  public AndroidManifest getAndroidManifest() {
    return AndroidManifest.create(getAndroidManifestProto(), getBundletoolVersion());
  }

  /** ApexConfig of the bundle that this module belongs to. */
  public abstract Optional<ApexConfig> getBundleApexConfig();

  public abstract Optional<ResourceTable> getResourceTable();

  public abstract Optional<Assets> getAssetsConfig();

  public abstract Optional<NativeLibraries> getNativeConfig();

  public abstract Optional<ApexImages> getApexConfig();

  public abstract Optional<RuntimeEnabledSdkConfig> getRuntimeEnabledSdkConfig();

  /** Only present for modules of type SDK_DEPENDENCY_MODULE. */
  public abstract Optional<SdkModulesConfig> getSdkModulesConfig();

  /**
   * Returns entries of the module, indexed by their module path.
   *
   * <p>Note that special module files (eg. {@code AndroidManifest.xml} are NOT represented as
   * entries.
   */
  abstract ImmutableMap<ZipPath, ModuleEntry> getEntryMap();

  public abstract ModuleType getModuleType();

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
    return BundleModuleName.BASE_MODULE_NAME.equals(getName());
  }

  public ModuleDeliveryType getDeliveryType() {
    return getAndroidManifest().getModuleDeliveryType();
  }

  public Optional<ModuleDeliveryType> getInstantDeliveryType() {
    if (!isInstantModule()) {
      return Optional.empty();
    }
    return Optional.of(getAndroidManifest().getInstantModuleDeliveryType());
  }

  public boolean isIncludedInFusing() {
    // The following should never throw if the module/bundle has been validated.
    return isBaseModule()
        || getAndroidManifest()
            .getIsModuleIncludedInFusing()
            .orElseThrow(
                () ->
                    InvalidBundleException.createWithUserMessage(
                        "Unable to determine if module should be fused: missing <dist:fusing> tag"
                            + " inside <dist:module> in AndroidManifest.xml"));
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

    return moduleTargeting.toBuilder()
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
    return getModuleMetadata(/* isSdkRuntimeVariant= */ false);
  }

  public ModuleMetadata getModuleMetadata(boolean isSdkRuntimeVariant) {
    ModuleMetadata.Builder moduleMetadata =
        ModuleMetadata.newBuilder()
            .setName(getName().getName())
            .setIsInstant(isInstantModule())
            .addAllDependencies(getDependencies())
            .setTargeting(getModuleTargeting())
            .setDeliveryType(moduleDeliveryTypeToDeliveryType(getDeliveryType()));

    moduleTypeToFeatureModuleType(getModuleType())
        .ifPresent(moduleType -> moduleMetadata.setModuleType(moduleType));
    if (isSdkRuntimeVariant) {
      getRuntimeEnabledSdkConfig()
          .ifPresent(
              runtimeEnabledSdkConfig ->
                  moduleMetadata.addAllRuntimeEnabledSdkDependencies(
                      runtimeEnabledDependenciesFromConfig(runtimeEnabledSdkConfig)));
    }

    return moduleMetadata.build();
  }

  private static ImmutableSet<RuntimeEnabledSdkDependency> runtimeEnabledDependenciesFromConfig(
      RuntimeEnabledSdkConfig runtimeEnabledSdkConfig) {
    return runtimeEnabledSdkConfig.getRuntimeEnabledSdkList().stream()
        .map(
            runtimeEnabledSdk ->
                RuntimeEnabledSdkDependency.newBuilder()
                    .setPackageName(runtimeEnabledSdk.getPackageName())
                    .setMajorVersion(runtimeEnabledSdk.getVersionMajor())
                    .setMinorVersion(runtimeEnabledSdk.getVersionMinor())
                    .build())
        .collect(toImmutableSet());
  }

  private static DeliveryType moduleDeliveryTypeToDeliveryType(
      ModuleDeliveryType moduleDeliveryType) {
    switch (moduleDeliveryType) {
      case ALWAYS_INITIAL_INSTALL:
      case CONDITIONAL_INITIAL_INSTALL:
        return DeliveryType.INSTALL_TIME;
      case NO_INITIAL_INSTALL:
        return DeliveryType.ON_DEMAND;
    }
    throw new IllegalArgumentException("Unknown module delivery type: " + moduleDeliveryType);
  }

  private static Optional<FeatureModuleType> moduleTypeToFeatureModuleType(ModuleType moduleType) {
    switch (moduleType) {
      case FEATURE_MODULE:
      case SDK_DEPENDENCY_MODULE:
        return Optional.of(FeatureModuleType.FEATURE_MODULE);
      case ML_MODULE:
        return Optional.of(FeatureModuleType.ML_MODULE);
      case ASSET_MODULE:
      case UNKNOWN_MODULE_TYPE:
        return Optional.empty();
    }
    throw new IllegalArgumentException("Unknown module type: " + moduleType);
  }

  public static Builder builder() {
    return new AutoValue_BundleModule.Builder().setModuleType(ModuleType.UNKNOWN_MODULE_TYPE);
  }

  public abstract Builder toBuilder();

  /** Builder for BundleModule. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(BundleModuleName value);

    public abstract Builder setBundleType(BundleType bundleType);

    public abstract Builder setBundletoolVersion(Version version);

    public abstract Builder setBundleApexConfig(ApexConfig apexConfig);

    public abstract Builder setResourceTable(ResourceTable resourceTable);

    public Builder setAndroidManifest(AndroidManifest androidManifest) {
      return setAndroidManifestProto(androidManifest.getManifestRoot().getProto());
    }

    public abstract Builder setAndroidManifestProto(XmlNode manifestProto);

    public abstract Builder setAssetsConfig(Assets assetsConfig);

    public abstract Builder setNativeConfig(NativeLibraries nativeConfig);

    public abstract Builder setApexConfig(ApexImages apexConfig);

    public abstract Builder setRuntimeEnabledSdkConfig(
        RuntimeEnabledSdkConfig runtimeEnabledSdkConfig);

    public abstract Builder setSdkModulesConfig(SdkModulesConfig sdkModulesConfig);

    public abstract Builder setModuleType(ModuleType moduleType);

    abstract ImmutableMap.Builder<ZipPath, ModuleEntry> entryMapBuilder();

    abstract Builder setEntryMap(ImmutableMap<ZipPath, ModuleEntry> entryMap);

    /**
     * Convenience method to set all entries at once.
     *
     * <p>Note: this method does not accept special entries such as manifest or resource table.
     * Thus, prefer using the {@link #addEntry(ModuleEntry)} method when possible, since it also
     * accepts the special files.
     */
    @CanIgnoreReturnValue
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

    /**
     * @see #addEntry(ModuleEntry)
     */
    @CanIgnoreReturnValue
    public Builder addEntries(Collection<ModuleEntry> entries) {
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
     */
    @CanIgnoreReturnValue
    public Builder addEntry(ModuleEntry moduleEntry) {
      Optional<SpecialModuleEntry> specialEntry =
          SpecialModuleEntry.getSpecialEntry(moduleEntry.getPath());
      if (specialEntry.isPresent()) {
        try (InputStream inputStream = moduleEntry.getContent().openStream()) {
          specialEntry.get().addToModule(this, inputStream);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      } else {
        entryMapBuilder().put(moduleEntry.getPath(), moduleEntry);
      }

      return this;
    }

    abstract Optional<XmlNode> getAndroidManifestProto();

    public boolean hasAndroidManifest() {
      return getAndroidManifestProto().isPresent();
    }

    public abstract BundleModuleName getName();

    abstract BundleModule autoBuild();

    public final BundleModule build() {
      BundleModule bundleModule = autoBuild();
      // If module type is not explicitly set, we are reading it from the manifest.
      if (bundleModule.getModuleType().equals(ModuleType.UNKNOWN_MODULE_TYPE)) {
        bundleModule =
            bundleModule.toBuilder()
                .setModuleType(bundleModule.getAndroidManifest().getModuleType())
                .build();
      }
      return bundleModule;
    }
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
    },
    RUNTIME_ENABLED_SDK_CONFIG("runtime_enabled_sdk_config.pb") {
      @Override
      void addToModule(BundleModule.Builder module, InputStream inputStream) throws IOException {
        module.setRuntimeEnabledSdkConfig(RuntimeEnabledSdkConfig.parseFrom(inputStream));
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
