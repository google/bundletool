/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.mergers.AndroidManifestMerger.fusingMergerApplicationElements;
import static com.android.tools.build.bundletool.mergers.AndroidManifestMerger.fusingMergerOnlyReplaceActivities;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.FUSE_APPLICATION_ELEMENTS_FROM_FEATURE_MANIFESTS;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.MERGE_INSTALL_TIME_MODULES_INTO_BASE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.flatteningToImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Multimaps.toMultimap;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Utilities for merging BundleModules. */
public class BundleModuleMerger {

  /**
   * Merge install-time modules into base module, unless they have {@code <dist:removable
   * dist:value="true" />} in install-time attribute.
   */
  public static AppBundle mergeNonRemovableInstallTimeModules(
      AppBundle appBundle, boolean overrideBundleToolVersion) throws IOException {
    ImmutableSet<BundleModule> bundleModulesToFuse =
        Stream.concat(
                Stream.of(appBundle.getBaseModule()),
                appBundle.getFeatureModules().values().stream()
                    .filter(
                        module ->
                            shouldMerge(
                                module, appBundle.getBundleConfig(), overrideBundleToolVersion)))
            .collect(toImmutableSet());

    // If only base module should be fused there is nothing to do.
    if (bundleModulesToFuse.size() == 1) {
      return appBundle;
    }

    BundleModule.Builder mergedBaseModule =
        BundleModule.builder()
            .setName(BundleModuleName.BASE_MODULE_NAME)
            .setBundleType(appBundle.getBundleConfig().getType())
            .setBundletoolVersion(appBundle.getVersion());
    if (appBundle.getBundleConfig().hasApexConfig()) {
      mergedBaseModule.setBundleApexConfig(appBundle.getBundleConfig().getApexConfig());
    }

    mergeApexTable(bundleModulesToFuse).ifPresent(mergedBaseModule::setApexConfig);
    mergeAssetsTable(bundleModulesToFuse).ifPresent(mergedBaseModule::setAssetsConfig);
    mergeNativeLibraryTable(bundleModulesToFuse).ifPresent(mergedBaseModule::setNativeConfig);
    mergeResourceTable(bundleModulesToFuse).ifPresent(mergedBaseModule::setResourceTable);
    mergeAndroidManifest(appBundle.getVersion(), bundleModulesToFuse, mergedBaseModule);

    ImmutableList<ModuleEntry> renamedDexEntries =
        ModuleSplitsToShardMerger.renameDexFromAllModulesToSingleShard(
            getDexEntries(bundleModulesToFuse));

    mergedBaseModule
        .addEntries(getAllEntriesExceptDexAndSpecial(bundleModulesToFuse))
        .addEntries(renamedDexEntries);

    return appBundle.toBuilder()
        .setRawModules(
            Stream.concat(
                    Stream.of(mergedBaseModule.build()),
                    appBundle.getModules().values().stream()
                        .filter(module -> !bundleModulesToFuse.contains(module)))
                .collect(toImmutableSet()))
        .build();
  }

  private static boolean shouldMerge(
      BundleModule module, BundleConfig bundleConfig, boolean overrideBundleToolVersion) {
    if (module.getModuleType() != ModuleType.FEATURE_MODULE) {
      return false;
    }

    return module
        .getAndroidManifest()
        .getManifestDeliveryElement()
        .map(
            manifestDeliveryElement -> {
              Version bundleToolVersion =
                  BundleToolVersion.getVersionFromBundleConfig(bundleConfig);
              // Only override for bundletool version < 1.0.0
              if (overrideBundleToolVersion
                  && !MERGE_INSTALL_TIME_MODULES_INTO_BASE.enabledForVersion(bundleToolVersion)) {
                return manifestDeliveryElement.hasInstallTimeElement()
                    && !manifestDeliveryElement.hasModuleConditions();
              }
              return !manifestDeliveryElement.isInstallTimeRemovable(bundleToolVersion);
            })
        .orElse(false);
  }

  private static ImmutableSet<ModuleEntry> getAllEntriesExceptDexAndSpecial(
      Set<BundleModule> bundleModulesToFuse) {
    Map<ZipPath, ModuleEntry> mergedEntriesByPath = new HashMap<>();
    bundleModulesToFuse.stream()
        .flatMap(module -> module.getEntries().stream())
        .filter(
            moduleEntry ->
                !moduleEntry.getPath().startsWith(DEX_DIRECTORY) && !moduleEntry.isSpecialEntry())
        .forEach(
            moduleEntry -> {
              ModuleEntry existingModuleEntry =
                  mergedEntriesByPath.putIfAbsent(moduleEntry.getPath(), moduleEntry);
              if (existingModuleEntry != null && !existingModuleEntry.equals(moduleEntry)) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Existing module entry '%s' with different contents.",
                        moduleEntry.getPath())
                    .build();
              }
            });
    return ImmutableSet.copyOf(mergedEntriesByPath.values());
  }

  private static ImmutableListMultimap<BundleModuleName, ModuleEntry> getDexEntries(
      Set<BundleModule> bundleModulesToFuse) {
    return bundleModulesToFuse.stream()
        .collect(
            flatteningToImmutableListMultimap(
                BundleModule::getName,
                module ->
                    module.getEntries().stream()
                        .filter(moduleEntry -> moduleEntry.getPath().startsWith(DEX_DIRECTORY))));
  }

  private static void mergeAndroidManifest(
      Version bundletoolVersion,
      ImmutableSet<BundleModule> bundleModulesToFuse,
      BundleModule.Builder mergedBaseModule) {
    HashMultimap<BundleModuleName, AndroidManifest> manifests =
        bundleModulesToFuse.stream()
            .collect(
                toMultimap(
                    BundleModule::getName, BundleModule::getAndroidManifest, HashMultimap::create));
    AndroidManifestMerger manifestMerger =
        FUSE_APPLICATION_ELEMENTS_FROM_FEATURE_MANIFESTS.enabledForVersion(bundletoolVersion)
            ? fusingMergerApplicationElements()
            : fusingMergerOnlyReplaceActivities();

    AndroidManifest mergedManifest = manifestMerger.merge(manifests);
    mergedManifest =
        mergedManifest
            .toEditor()
            .setFusedModuleNames(
                bundleModulesToFuse.stream()
                    .map(module -> module.getName().getName())
                    .collect(toImmutableList()))
            .save();
    mergedBaseModule.setAndroidManifest(mergedManifest);
  }

  private static Optional<ResourceTable> mergeResourceTable(
      ImmutableSet<BundleModule> bundleModulesToFuse) {
    if (bundleModulesToFuse.stream().noneMatch(module -> module.getResourceTable().isPresent())) {
      return Optional.empty();
    }
    ResourceTable.Builder resourceTableMerged = ResourceTable.newBuilder();
    for (BundleModule bundleModule : bundleModulesToFuse) {
      bundleModule.getResourceTable().ifPresent(resourceTableMerged::mergeFrom);
    }
    return Optional.of(resourceTableMerged.build());
  }

  private static Optional<NativeLibraries> mergeNativeLibraryTable(
      ImmutableSet<BundleModule> bundleModulesToFuse) {
    if (bundleModulesToFuse.stream().noneMatch(module -> module.getNativeConfig().isPresent())) {
      return Optional.empty();
    }
    NativeLibraries.Builder nativeLibrariesMerged = NativeLibraries.newBuilder();
    for (BundleModule bundleModule : bundleModulesToFuse) {
      bundleModule.getNativeConfig().ifPresent(nativeLibrariesMerged::mergeFrom);
    }
    return Optional.of(nativeLibrariesMerged.build());
  }

  private static Optional<Assets> mergeAssetsTable(ImmutableSet<BundleModule> bundleModulesToFuse) {
    if (bundleModulesToFuse.stream().noneMatch(module -> module.getAssetsConfig().isPresent())) {
      return Optional.empty();
    }
    Assets.Builder assetsMerged = Assets.newBuilder();
    for (BundleModule bundleModule : bundleModulesToFuse) {
      bundleModule.getAssetsConfig().ifPresent(assetsMerged::mergeFrom);
    }
    return Optional.of(assetsMerged.build());
  }

  private static Optional<ApexImages> mergeApexTable(
      ImmutableSet<BundleModule> bundleModulesToFuse) {
    if (bundleModulesToFuse.stream().noneMatch(module -> module.getApexConfig().isPresent())) {
      return Optional.empty();
    }
    ApexImages.Builder apexImagesMerged = ApexImages.newBuilder();
    for (BundleModule bundleModule : bundleModulesToFuse) {
      bundleModule.getApexConfig().ifPresent(apexImagesMerged::mergeFrom);
    }
    return Optional.of(apexImagesMerged.build());
  }

  private BundleModuleMerger() {}
}
