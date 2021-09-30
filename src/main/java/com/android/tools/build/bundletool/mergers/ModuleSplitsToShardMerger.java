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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.mergers.AndroidManifestMerger.fusingMergerApplicationElements;
import static com.android.tools.build.bundletool.mergers.AndroidManifestMerger.fusingMergerOnlyReplaceActivities;
import static com.android.tools.build.bundletool.mergers.AndroidManifestMerger.useBaseModuleManifestMerger;
import static com.android.tools.build.bundletool.mergers.MergingUtils.mergeTargetedAssetsDirectories;
import static com.android.tools.build.bundletool.model.BundleMetadata.BUNDLETOOL_NAMESPACE;
import static com.android.tools.build.bundletool.model.BundleMetadata.MAIN_DEX_LIST_FILE_NAME;
import static com.android.tools.build.bundletool.model.BundleMetadata.OBFUSCATION_NAMESPACE;
import static com.android.tools.build.bundletool.model.BundleMetadata.PROGUARD_MAP_FILE_NAME;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.FUSE_ACTIVITIES_FROM_FEATURE_MANIFESTS;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.FUSE_APPLICATION_ELEMENTS_FROM_FEATURE_MANIFESTS;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Config.StandaloneConfig.DexMergingStrategy;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Merges given module splits into standalone APKs.
 *
 * <p>Outputs of dex merging are stored as files in the `globalTempDir` and referenced by {@link
 * ModuleEntry} instances that are contained in the produced {@link ModuleSplit} instances.
 */
public class ModuleSplitsToShardMerger {

  private final Version bundletoolVersion;
  private final TempDirectory globalTempDir;
  private final DexMerger dexMerger;
  private final AppBundle appBundle;

  @Inject
  public ModuleSplitsToShardMerger(
      Version bundletoolVersion,
      TempDirectory globalTempDir,
      DexMerger dexMerger,
      AppBundle appBundle) {
    this.bundletoolVersion = bundletoolVersion;
    this.globalTempDir = globalTempDir;
    this.dexMerger = dexMerger;
    this.appBundle = appBundle;
  }

  /** Gets a list of splits, and merges them into a single standalone APK (aka shard). */
  public ModuleSplit mergeSingleShard(
      ImmutableCollection<ModuleSplit> splitsOfShard,
      Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> mergedDexCache) {
    return mergeSingleShard(
        splitsOfShard,
        mergedDexCache,
        /* mergedSplitType= */ SplitType.STANDALONE,
        createManifestMerger());
  }

  /**
   * Gets a list of splits, and merges them into a single standalone APK (aka shard).
   *
   * <p>Allows to customize split type {@code mergedSplitType} of merged shard and Android manifest
   * merger {@code manifestMerger}.
   */
  public ModuleSplit mergeSingleShard(
      ImmutableCollection<ModuleSplit> splitsOfShard,
      Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> mergedDexCache,
      SplitType mergedSplitType,
      AndroidManifestMerger manifestMerger) {

    ListMultimap<BundleModuleName, ModuleEntry> dexFilesToMergeByModule =
        ArrayListMultimap.create();
    // If multiple splits were generated from one module, we'll see the same manifest multiple
    // times. The multimap filters out identical (module name, manifest) pairs by contract.
    // All splits of a module should have the same manifest, so the following multimap should
    // associate just one value with each key. This is checked explicitly for the base module
    // because the manifest merger requires *single* base manifest.
    SetMultimap<BundleModuleName, AndroidManifest> androidManifestsToMergeByModule =
        HashMultimap.create();

    Map<ZipPath, ModuleEntry> mergedEntriesByPath = new HashMap<>();
    Optional<ResourceTable> mergedResourceTable = Optional.empty();
    Map<String, TargetedAssetsDirectory> mergedAssetsConfig = new HashMap<>();
    ApkTargeting mergedSplitTargeting = ApkTargeting.getDefaultInstance();

    for (ModuleSplit split : splitsOfShard) {
      // Resource tables and Split targetings can be merged for each split individually as we go.
      mergedResourceTable = mergeResourceTables(mergedResourceTable, split);
      mergedSplitTargeting = mergeSplitTargetings(mergedSplitTargeting, split);

      // Android manifests need to be merged later, globally for all splits.
      androidManifestsToMergeByModule.put(split.getModuleName(), split.getAndroidManifest());

      for (ModuleEntry entry : split.getEntries()) {
        if (entry.getPath().startsWith(DEX_DIRECTORY)) {
          // Dex files need to be merged later, globally for all splits.
          dexFilesToMergeByModule.put(split.getModuleName(), entry);
        } else {
          mergeEntries(mergedEntriesByPath, split, entry);
        }
      }

      split
          .getAssetsConfig()
          .ifPresent(
              assetsConfig -> {
                mergeTargetedAssetsDirectories(mergedAssetsConfig, assetsConfig.getDirectoryList());
              });
    }

    AndroidManifest mergedAndroidManifest = manifestMerger.merge(androidManifestsToMergeByModule);

    Collection<ModuleEntry> mergedDexFiles =
        mergeDexFilesAndCache(dexFilesToMergeByModule, mergedAndroidManifest, mergedDexCache);

    // Record names of the modules this shard was fused from.
    ImmutableList<String> fusedModuleNames = getUniqueModuleNames(splitsOfShard);
    if (mergedSplitType.equals(SplitType.STANDALONE)) {
      mergedAndroidManifest =
          mergedAndroidManifest.toEditor().setFusedModuleNames(fusedModuleNames).save();
    }

    // Construct the final shard.
    return buildShard(
        mergedEntriesByPath.values(),
        mergedDexFiles,
        mergedSplitTargeting,
        mergedAndroidManifest,
        mergedResourceTable,
        mergedAssetsConfig,
        mergedSplitType);
  }

  private AndroidManifestMerger createManifestMerger() {
    if (FUSE_APPLICATION_ELEMENTS_FROM_FEATURE_MANIFESTS.enabledForVersion(bundletoolVersion)) {
      return fusingMergerApplicationElements();
    }
    if (FUSE_ACTIVITIES_FROM_FEATURE_MANIFESTS.enabledForVersion(bundletoolVersion)) {
      return fusingMergerOnlyReplaceActivities();
    }
    return useBaseModuleManifestMerger();
  }

  /**
   * Gets a list of collections of splits, and merges each collection into a single standalone APK
   * (aka shard).
   *
   * @param unfusedShards a list of lists - each inner list is a collection of splits
   * @return a list of shards, each one made of the corresponding collection of splits
   */
  public ImmutableList<ModuleSplit> mergeApex(
      ImmutableList<ImmutableList<ModuleSplit>> unfusedShards) {
    return unfusedShards.stream().map(this::mergeSingleApexShard).collect(toImmutableList());
  }

  @VisibleForTesting
  ModuleSplit mergeSingleApexShard(ImmutableList<ModuleSplit> splitsOfShard) {
    checkState(!splitsOfShard.isEmpty(), "A shard is made of at least one split.");

    Map<ZipPath, ModuleEntry> mergedEntriesByPath = new HashMap<>();
    ApkTargeting splitTargeting = ApkTargeting.getDefaultInstance();

    for (ModuleSplit split : splitsOfShard) {
      // An APEX shard is made of one master split and one multi-Abi split, so we use the latter.
      splitTargeting =
          splitTargeting.hasMultiAbiTargeting() ? splitTargeting : split.getApkTargeting();

      for (ModuleEntry entry : split.getEntries()) {
        mergeEntries(mergedEntriesByPath, split, entry);
      }
    }

    ModuleSplit shard =
        buildShard(
            mergedEntriesByPath.values(),
            ImmutableList.of(),
            splitTargeting,
            // An APEX module is made of one module, so any manifest works.
            splitsOfShard.get(0).getAndroidManifest(),
            /* mergedResourceTable= */ Optional.empty(),
            /* mergedAssetsConfig= */ new HashMap<>(),
            /* mergedSplitType= */ SplitType.STANDALONE);

    // Add the APEX config as it's used to identify APEX APKs.
    return shard.toBuilder()
        .setApexConfig(splitsOfShard.get(0).getApexConfig().get())
        .setApexEmbeddedApkConfigs(splitsOfShard.get(0).getApexEmbeddedApkConfigs())
        .build();
  }

  private ModuleSplit buildShard(
      Collection<ModuleEntry> entriesByPath,
      Collection<ModuleEntry> mergedDexFiles,
      ApkTargeting splitTargeting,
      AndroidManifest androidManifest,
      Optional<ResourceTable> mergedResourceTable,
      Map<String, TargetedAssetsDirectory> mergedAssetsConfig,
      SplitType mergedSplitType) {
    ImmutableList<ModuleEntry> entries =
        ImmutableList.<ModuleEntry>builder().addAll(entriesByPath).addAll(mergedDexFiles).build();
    ModuleSplit.Builder shard =
        ModuleSplit.builder()
            .setAndroidManifest(androidManifest)
            .setEntries(entries)
            .setApkTargeting(splitTargeting)
            .setSplitType(mergedSplitType)
            // We don't care about the following properties for shards. The values are set just to
            // satisfy contract of @AutoValue.Builder.
            // `nativeConfig` is optional and therefore not being set.
            .setMasterSplit(false)
            .setModuleName(BASE_MODULE_NAME)
            .setVariantTargeting(VariantTargeting.getDefaultInstance());
    mergedResourceTable.ifPresent(shard::setResourceTable);
    if (!mergedAssetsConfig.isEmpty()) {
      shard.setAssetsConfig(
          Assets.newBuilder().addAllDirectory(mergedAssetsConfig.values()).build());
    }
    return shard.build();
  }

  private Collection<ModuleEntry> mergeDexFilesAndCache(
      ListMultimap<BundleModuleName, ModuleEntry> dexFilesToMergeByModule,
      AndroidManifest androidManifest,
      Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> mergedDexCache) {
    if (dexFilesToMergeByModule.size() <= 1 || appBundle.getFeatureModules().size() <= 1) {
      // Don't merge if there is only one dex file or an application doesn't have feature modules.
      // If base module contains multiple dex files, it should have been built with multi-dex
      // support.
      return dexFilesToMergeByModule.values();
    } else if (getDexMergingStrategy().equals(DexMergingStrategy.NEVER_MERGE)
        || androidManifest.getEffectiveMinSdkVersion() >= Versions.ANDROID_L_API_VERSION) {
      // When APK targets L+ devices we know the devices already have multi-dex support.
      // In this case or when dex merging is explicitly disabled we skip merging dexes and just
      // rename original ones to be in order.
      return renameDexFromAllModulesToSingleShard(dexFilesToMergeByModule);
    } else {
      ImmutableList<ModuleEntry> dexEntries =
          ImmutableList.copyOf(dexFilesToMergeByModule.values());

      ImmutableList<Path> mergedDexFiles =
          mergedDexCache.computeIfAbsent(
              ImmutableSet.copyOf(dexEntries), key -> mergeDexFiles(dexEntries, androidManifest));

      // Names of the merged dex files need to be preserved ("classes.dex", "classes2.dex" etc.).
      return mergedDexFiles.stream()
          .map(
              filePath ->
                  ModuleEntry.builder()
                      .setPath(DEX_DIRECTORY.resolve(filePath.getFileName().toString()))
                      .setContent(filePath)
                      .build())
          .collect(toImmutableList());
    }
  }

  static ImmutableList<ModuleEntry> renameDexFromAllModulesToSingleShard(
      Multimap<BundleModuleName, ModuleEntry> dexFilesToMergeByModule) {
    // We don't need to rename classes*.dex in base module.
    Stream<ModuleEntry> dexFilesFromBase = dexFilesToMergeByModule.get(BASE_MODULE_NAME).stream();

    // Take Dex files from all other modules and rename them to be after base module dexes.
    int dexFilesCountInBase = dexFilesToMergeByModule.get(BASE_MODULE_NAME).size();
    Stream<ModuleEntry> dexFilesFromNotBase =
        dexFilesToMergeByModule.keys().stream()
            .distinct()
            .filter(moduleName -> !BASE_MODULE_NAME.equals(moduleName))
            .flatMap(moduleName -> dexFilesToMergeByModule.get(moduleName).stream());

    Stream<ModuleEntry> renamedDexFiles =
        Streams.mapWithIndex(
            dexFilesFromNotBase,
            (entry, index) ->
                entry.toBuilder()
                    .setPath(
                        DEX_DIRECTORY.resolve(
                            dexFilesCountInBase + index == 0
                                ? "classes.dex"
                                : String.format("classes%d.dex", dexFilesCountInBase + index + 1)))
                    .build());

    return Stream.concat(dexFilesFromBase, renamedDexFiles).collect(toImmutableList());
  }

  private ImmutableList<Path> mergeDexFiles(
      List<ModuleEntry> dexEntries, AndroidManifest androidManifest) {
    try {
      Path dexOriginalDir = Files.createTempDirectory(globalTempDir.getPath(), "dex-merging-in");
      // The merged dex files will be written to a sub-directory of the global temp directory
      // that exists throughout execution of a bundletool command.
      Path dexMergedDir = Files.createTempDirectory(globalTempDir.getPath(), "dex-merging-out");

      // The dex merger requires the main dex list represented as a file.
      Optional<Path> mainDexListFile = writeMainDexListFileIfPresent();

      // The dex merger requires the proguard map represented as a file.
      Optional<Path> proguardMapFile = writeProguardMapFileIfPresent();

      // Write input dex data to temporary files "0.dex", "1.dex" etc. The names/order is not
      // important. The filenames just need to be unique and have the ".dex" extension.
      ImmutableList<Path> dexFiles =
          writeModuleEntriesToIndexedFiles(dexEntries, dexOriginalDir, /* fileSuffix= */ ".dex");

      ImmutableList<Path> mergedDexFiles =
          dexMerger.merge(
              dexFiles,
              dexMergedDir,
              mainDexListFile,
              proguardMapFile,
              androidManifest.getEffectiveApplicationDebuggable(),
              androidManifest.getEffectiveMinSdkVersion());

      return mergedDexFiles;

    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("I/O error while merging dex files.")
          .build();
    }
  }

  private static void mergeEntries(
      Map<ZipPath, ModuleEntry> mergedEntriesByPath, ModuleSplit split, ModuleEntry entry) {
    ModuleEntry existingEntry = mergedEntriesByPath.putIfAbsent(entry.getPath(), entry);
    // Any conflicts of plain entries should be caught by bundle validations.
    checkState(
        existingEntry == null || existingEntry.equals(entry),
        "Module '%s' and some other module(s) contain entry '%s' with different contents.",
        split.getModuleName(),
        entry.getPath());
  }

  private Optional<ResourceTable> mergeResourceTables(
      Optional<ResourceTable> merged, ModuleSplit split) {
    if (!merged.isPresent()) {
      return split.getResourceTable();
    }
    if (split.getResourceTable().isPresent()) {
      try {
        return Optional.of(
            new ResourceTableMerger().merge(merged.get(), split.getResourceTable().get()));
      } catch (CommandExecutionException | IllegalStateException e) {
        throw CommandExecutionException.builder()
            .withCause(e)
            .withInternalMessage(
                "Failed to merge with resource table of module '%s'.", split.getModuleName())
            .build();
      }
    }
    return merged;
  }

  private ApkTargeting mergeSplitTargetings(ApkTargeting merged, ModuleSplit split) {
    try {
      return MergingUtils.mergeShardTargetings(merged, split.getApkTargeting());
    } catch (CommandExecutionException | IllegalStateException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage(
              "Failed to merge with targeting of module '%s'.", split.getModuleName())
          .build();
    }
  }

  private Optional<Path> writeMainDexListFileIfPresent() throws IOException {

    Optional<ByteSource> mainDexListFile =
        appBundle
            .getBundleMetadata()
            .getFileAsByteSource(BUNDLETOOL_NAMESPACE, MAIN_DEX_LIST_FILE_NAME);

    if (!mainDexListFile.isPresent()) {
      return Optional.empty();
    }

    Path mainDexListFilePath = Files.createTempFile(globalTempDir.getPath(), "mainDexList", ".txt");
    try (InputStream inputStream = mainDexListFile.get().openStream()) {
      Files.copy(inputStream, mainDexListFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
    return Optional.of(mainDexListFilePath);
  }

  private Optional<Path> writeProguardMapFileIfPresent() throws IOException {

    Optional<ByteSource> proguardFile =
        appBundle
            .getBundleMetadata()
            .getFileAsByteSource(OBFUSCATION_NAMESPACE, PROGUARD_MAP_FILE_NAME);

    if (!proguardFile.isPresent()) {
      return Optional.empty();
    }

    Path proguardMapFilePath = Files.createTempFile(globalTempDir.getPath(), "proguard", ".map");
    try (InputStream inputStream = proguardFile.get().openStream()) {
      Files.copy(inputStream, proguardMapFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
    return Optional.of(proguardMapFilePath);
  }

  private DexMergingStrategy getDexMergingStrategy() {
    return appBundle
        .getBundleConfig()
        .getOptimizations()
        .getStandaloneConfig()
        .getDexMergingStrategy();
  }

  private static ImmutableList<String> getUniqueModuleNames(
      ImmutableCollection<ModuleSplit> splits) {
    return splits.stream()
        .map(ModuleSplit::getModuleName)
        .map(BundleModuleName::getName)
        .distinct()
        .collect(toImmutableList());
  }

  /**
   * Writes given entries to files named {@code "0<fileSuffix>", "1<fileSuffix>", ...} inside the
   * specified directory.
   */
  private static ImmutableList<Path> writeModuleEntriesToIndexedFiles(
      List<ModuleEntry> moduleEntries, Path toDirectory, String fileSuffix) throws IOException {
    ImmutableList.Builder<Path> files = ImmutableList.builder();
    for (int i = 0; i < moduleEntries.size(); i++) {
      Path fileName = toDirectory.resolve(i + fileSuffix);
      writeModuleEntryToFile(moduleEntries.get(i), fileName);
      files.add(fileName);
    }
    return files.build();
  }

  private static void writeModuleEntryToFile(ModuleEntry entry, Path toFile) throws IOException {
    try (InputStream is = entry.getContent().openStream()) {
      Files.copy(is, toFile);
    }
  }
}
