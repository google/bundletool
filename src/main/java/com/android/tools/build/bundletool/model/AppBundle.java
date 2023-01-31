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

import static com.android.tools.build.bundletool.model.utils.BundleParser.extractModules;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readBundleConfig;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readBundleMetadata;
import static com.android.tools.build.bundletool.model.utils.BundleParser.sanitize;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static java.util.function.Function.identity;

import com.android.bundle.Config.ApexConfig;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.Config.StandaloneConfig.DexMergingStrategy;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Represents an app bundle.
 *
 * <p>When AppBundle is read, the optional but valid ZIP directories entries are skipped so any code
 * using the model objects can assume any ZIP entry is referring to a regular file.
 */
@Immutable
@AutoValue
public abstract class AppBundle implements Bundle {

  public static final ZipPath METADATA_DIRECTORY = ZipPath.create("BUNDLE-METADATA");

  public static final String BUNDLE_CONFIG_FILE_NAME = "BundleConfig.pb";

  /** Top-level directory names that are not recognized as modules. */
  public static final ImmutableSet<ZipPath> NON_MODULE_DIRECTORIES =
      ImmutableSet.of(METADATA_DIRECTORY, ZipPath.create("META-INF"));

  /**
   * File path for opting out from hibernation. To opt out this file should be included within base
   * module of the {@link AppBundle}. More information can be found in the <a
   * href="https://android-developers.googleblog.com/2022/03/freeing-up-60-of-storage-for-apps.html">blog
   * post</a>.
   */
  public static final String ARCHIVE_OPT_OUT_XML_PATH =
      "res/xml/com_android_vending_archive_opt_out.xml";

  /** Builds an {@link AppBundle} from an App Bundle on disk. */
  public static AppBundle buildFromZip(ZipFile bundleFile) {
    BundleConfig bundleConfig = readBundleConfig(bundleFile);
    Optional<ApexConfig> apexConfig =
        bundleConfig.hasApexConfig() ? Optional.of(bundleConfig.getApexConfig()) : Optional.empty();
    return buildFromModules(
        sanitize(
            extractModules(
                bundleFile,
                bundleConfig.getType(),
                Version.of(bundleConfig.getBundletool().getVersion()),
                apexConfig,
                NON_MODULE_DIRECTORIES)),
        bundleConfig,
        readBundleMetadata(bundleFile));
  }

  public static AppBundle buildFromModules(
      ImmutableList<BundleModule> modules,
      BundleConfig bundleConfig,
      BundleMetadata bundleMetadata) {
    ImmutableSet<ResourceId> pinnedResourceIds =
        bundleConfig.getMasterResources().getResourceIdsList().stream()
            .map(ResourceId::create)
            .collect(toImmutableSet());

    ImmutableSet<String> pinnedResourceNames =
        ImmutableSet.copyOf(bundleConfig.getMasterResources().getResourceNamesList());

    ImmutableListMultimap<String, RuntimeEnabledSdk> runtimeEnabledSdkDependencies =
        modules.stream()
            .filter(module -> module.getRuntimeEnabledSdkConfig().isPresent())
            .map(module -> module.getRuntimeEnabledSdkConfig().get())
            .flatMap(
                runtimeEnabledSdkConfig ->
                    runtimeEnabledSdkConfig.getRuntimeEnabledSdkList().stream())
            .collect(toImmutableListMultimap(RuntimeEnabledSdk::getPackageName, identity()));
    validateUniqueSdkDependencies(runtimeEnabledSdkDependencies);

    return builder()
        .setModules(Maps.uniqueIndex(modules, BundleModule::getName))
        .setMasterPinnedResourceIds(pinnedResourceIds)
        .setMasterPinnedResourceNames(pinnedResourceNames)
        .setBundleConfig(bundleConfig)
        .setBundleMetadata(bundleMetadata)
        .setRuntimeEnabledSdkDependencies(
            runtimeEnabledSdkDependencies.values().stream()
                .collect(toImmutableMap(RuntimeEnabledSdk::getPackageName, identity())))
        .build();
  }

  @Override
  public abstract ImmutableMap<BundleModuleName, BundleModule> getModules();

  /**
   * Resource IDs that must remain in the master split regardless of their targeting configuration.
   */
  public abstract ImmutableSet<ResourceId> getMasterPinnedResourceIds();

  /**
   * Resource names that must remain in the master split regardless of their targeting
   * configuration.
   */
  public abstract ImmutableSet<String> getMasterPinnedResourceNames();

  public abstract BundleConfig getBundleConfig();

  @Override
  public abstract BundleMetadata getBundleMetadata();

  abstract Optional<String> getPackageNameOptional();

  /**
   * Returns runtime-enabled SDK dependencies of this bundle, keyed by SDK package name.
   *
   * <p>Note, that this method flattens dependencies across all modules, forgetting the association
   * of the runtime-enabled SDK dependencies with specific app bundle modules.
   */
  public abstract ImmutableMap<String, RuntimeEnabledSdk> getRuntimeEnabledSdkDependencies();

  /**
   * Returns all feature modules for this bundle, including the base module.
   *
   * <p>ML modules are treated as feature modules across bundletool and app delivery, so they are
   * returned by this method.
   */
  public ImmutableMap<BundleModuleName, BundleModule> getFeatureModules() {
    return getModules().values().stream()
        .filter(module -> module.getModuleType().isFeatureModule())
        .collect(toImmutableMap(BundleModule::getName, identity()));
  }

  public ImmutableMap<BundleModuleName, BundleModule> getAssetModules() {
    return getModules().values().stream()
        .filter(module -> module.getModuleType().equals(ModuleType.ASSET_MODULE))
        .collect(toImmutableMap(BundleModule::getName, identity()));
  }

  public BundleModule getBaseModule() {
    return getModule(BundleModuleName.BASE_MODULE_NAME);
  }

  public boolean hasBaseModule() {
    return getModules().containsKey(BundleModuleName.BASE_MODULE_NAME);
  }

  @Override
  public String getPackageName() {
    if (getPackageNameOptional().isPresent()) {
      return getPackageNameOptional().get();
    }
    if (isAssetOnly()) {
      return getModules().values().stream()
          .map(module -> module.getAndroidManifest().getPackageName())
          .distinct()
          .collect(onlyElement());
    }
    return getBaseModule().getAndroidManifest().getPackageName();
  }

  @Override
  public BundleModule getModule(BundleModuleName moduleName) {
    BundleModule module = getModules().get(moduleName);
    checkState(module != null, "Module '%s' not found.", moduleName);
    return module;
  }

  public Version getVersion() {
    return Version.of(getBundleConfig().getBundletool().getVersion());
  }

  /**
   * Returns a set of ABIs that this App Bundle targets.
   *
   * <p>Note that the each module of the App Bundle must target the same set of ABIs or have no
   * native code.
   *
   * <p>Returns empty set if the App Bundle has no native code at all.
   */
  public ImmutableSet<Abi> getTargetedAbis() {
    return getFeatureModules().values().stream()
        .map(BundleModule::getNativeConfig)
        .flatMap(
            nativeConfig -> {
              if (nativeConfig.isPresent()) {
                return nativeConfig.get().getDirectoryList().stream()
                    .map(TargetedNativeDirectory::getTargeting)
                    .map(NativeDirectoryTargeting::getAbi);
              } else {
                return Stream.empty();
              }
            })
        .distinct()
        .collect(toImmutableSet());
  }

  public boolean isApex() {
    return !isAssetOnly() && getBaseModule().getApexConfig().isPresent();
  }

  public boolean isAssetOnly() {
    return getBundleConfig().getType().equals(BundleType.ASSET_ONLY);
  }

  /** Returns whether the base module has `sharedUserId` attribute in the manifest. */
  public boolean hasSharedUserId() {
    return getBaseModule().getAndroidManifest().hasSharedUserId();
  }

  /**
   * Returns {@code true} if bundletool will merge dex files when generating standalone APKs. This
   * happens for applications with dynamic feature modules that have min sdk below 21 and specified
   * DexMergingStrategy is MERGE_IF_NEEDED.
   */
  public boolean dexMergingEnabled() {
    return getDexMergingStrategy().equals(DexMergingStrategy.MERGE_IF_NEEDED)
        && getBaseModule().getAndroidManifest().getEffectiveMinSdkVersion() < 21
        && getFeatureModules().size() > 1;
  }

  private DexMergingStrategy getDexMergingStrategy() {
    return getBundleConfig().getOptimizations().getStandaloneConfig().getDexMergingStrategy();
  }

  /** Returns value of the store archive setting. */
  public Optional<Boolean> getStoreArchive() {
    if (getBundleConfig().getOptimizations().hasStoreArchive()) {
      return Optional.of(getBundleConfig().getOptimizations().getStoreArchive().getEnabled());
    }
    if (hasBaseModule()
        && getBaseModule()
            .findEntriesUnderPath(BundleModule.RESOURCES_DIRECTORY)
            .anyMatch(entry -> entry.getPath().equals(ZipPath.create(ARCHIVE_OPT_OUT_XML_PATH)))) {
      return Optional.of(false);
    }
    return Optional.empty();
  }

  /** Returns {@code true} if bundletool has to generate a LocaleConfig file. */
  public boolean injectLocaleConfig() {
    return getBundleConfig().getLocales().getInjectLocaleConfig();
  }

  public abstract Builder toBuilder();

  static Builder builder() {
    return new AutoValue_AppBundle.Builder().setRuntimeEnabledSdkDependencies(ImmutableMap.of());
  }

  /** Builder for App Bundle object */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setModules(ImmutableMap<BundleModuleName, BundleModule> modules);

    /** Convenience method to extract module names and set module map. */
    public Builder setRawModules(Collection<BundleModule> bundleModules) {
      setModules(bundleModules.stream().collect(toImmutableMap(BundleModule::getName, identity())));
      return this;
    }

    /** Convenience method to add extra modules to the builder. */
    public Builder addRawModules(Collection<BundleModule> bundleModules) {
      modulesBuilder()
          .putAll(
              bundleModules.stream().collect(toImmutableMap(BundleModule::getName, identity())));
      return this;
    }

    public Builder addRawModule(BundleModule bundleModule) {
      modulesBuilder().put(bundleModule.getName(), bundleModule);
      return this;
    }

    public abstract Builder setMasterPinnedResourceIds(ImmutableSet<ResourceId> pinnedResourceIds);

    public abstract Builder setMasterPinnedResourceNames(ImmutableSet<String> pinnedResourceNames);

    public abstract Builder setBundleConfig(BundleConfig bundleConfig);

    public abstract Builder setBundleMetadata(BundleMetadata bundleMetadata);

    public abstract Builder setRuntimeEnabledSdkDependencies(
        ImmutableMap<String, RuntimeEnabledSdk> runtimeEnabledSdkDependencies);

    /**
     * Package name is explicitly set in BuidSdkApksForAppCommand, since it does not take app bundle
     * as input.
     */
    public abstract Builder setPackageNameOptional(String packageName);

    public abstract AppBundle build();

    abstract ImmutableMap.Builder<BundleModuleName, BundleModule> modulesBuilder();
  }

  private static void validateUniqueSdkDependencies(
      ImmutableListMultimap<String, RuntimeEnabledSdk> runtimeEnabledSdkDependencies) {
    runtimeEnabledSdkDependencies
        .keySet()
        .forEach(
            sdkPackageName -> {
              if (runtimeEnabledSdkDependencies.get(sdkPackageName).size() > 1) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Found multiple dependencies on the same runtime-enabled SDK '%s'.",
                        sdkPackageName)
                    .build();
              }
            });
  }
}
