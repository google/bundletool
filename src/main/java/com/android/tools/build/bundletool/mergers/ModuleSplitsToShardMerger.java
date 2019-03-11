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

import static com.android.tools.build.bundletool.model.BundleMetadata.BUNDLETOOL_NAMESPACE;
import static com.android.tools.build.bundletool.model.BundleMetadata.MAIN_DEX_LIST_FILE_NAME;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.FileSystemModuleEntry;
import com.android.tools.build.bundletool.model.InputStreamSupplier;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ShardedSystemSplits;
import com.android.tools.build.bundletool.model.SuffixManager;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Merges given module splits into standalone APKs.
 *
 * <p>Outputs of dex merging are stored as files in the `globalTempDir` and referenced by {@link
 * ModuleEntry} instances that are contained in the produced {@link ModuleSplit} instances.
 */
public class ModuleSplitsToShardMerger {

  private static final BundleModuleName BASE_MODULE_NAME =
      BundleModuleName.create(BundleModuleName.BASE_MODULE_NAME);

  private final DexMerger dexMerger;
  private final Path globalTempDir;

  public ModuleSplitsToShardMerger(DexMerger dexMerger, Path globalTempDir) {
    this.dexMerger = dexMerger;
    this.globalTempDir = globalTempDir;
  }

  /**
   * Gets a list of collections of splits, and merges each collection into a single standalone APK
   * (aka shard) and additional unmatched language splits.
   *
   * @param unfusedShards a list of lists - each inner list is a collection of splits
   * @param bundleMetadata the App Bundle metadata
   * @return a list of shards, each one made of the corresponding collection of splits
   */
  public ImmutableList<ModuleSplit> merge(
      ImmutableList<ImmutableList<ModuleSplit>> unfusedShards, BundleMetadata bundleMetadata) {
    // Results of the dex merging are cached. Due to the nature of the cache keys and values, the
    // cache is deliberately not part of the object state, so that it is dropped after the method
    // call finishes.
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> mergedDexCache = new HashMap<>();

    ImmutableList.Builder<ModuleSplit> shards = ImmutableList.builder();
    for (ImmutableList<ModuleSplit> unfusedShard : unfusedShards) {
      shards.add(mergeSingleShard(unfusedShard, bundleMetadata, mergedDexCache));
    }
    return shards.build();
  }

  /**
   * Gets a collections of splits and merges them into a single system APK (aka shard).
   *
   * @param splits a collection of splits
   * @param bundleMetadata the App Bundle metadata
   * @param deviceSpec the device specification
   * @return {@link ShardedSystemSplits} containing a single fused system APK and additional
   *     language splits
   */
  public ShardedSystemSplits mergeSystemShard(
      ImmutableCollection<ModuleSplit> splits,
      BundleMetadata bundleMetadata,
      DeviceSpec deviceSpec) {

    ApkMatcher deviceSpecMatcher = new ApkMatcher(deviceSpec);
    ImmutableSet<ModuleSplit> nonMatchedLanguageSplits =
        splits.stream()
            .filter(split -> split.getApkTargeting().hasLanguageTargeting())
            .filter(split -> !deviceSpecMatcher.matchesModuleSplitByTargeting(split))
            .collect(toImmutableSet());

    ModuleSplit fusedSplit =
        mergeSingleShard(
            Sets.difference(ImmutableSet.copyOf(splits), nonMatchedLanguageSplits).immutableCopy(),
            bundleMetadata,
            new HashMap<>());

    ImmutableMultimap<BundleModuleName, ModuleSplit> langSplitsByModuleMap =
        Multimaps.index(nonMatchedLanguageSplits, ModuleSplit::getModuleName);

    ImmutableList<ModuleSplit> langSplitsWithSplitId =
        langSplitsByModuleMap.keySet().stream()
            .flatMap(
                key ->
                    writeSplitIdInManifestHavingSameModule(langSplitsByModuleMap.get(key)).stream())
            .collect(toImmutableList());

    return ShardedSystemSplits.create(fusedSplit, langSplitsWithSplitId);
  }

  @VisibleForTesting
  ModuleSplit mergeSingleShard(
      ImmutableCollection<ModuleSplit> splitsOfShard,
      BundleMetadata bundleMetadata,
      Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> mergedDexCache) {

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
    ApkTargeting mergedSplitTargeting = ApkTargeting.getDefaultInstance();

    for (ModuleSplit split : splitsOfShard) {
      // Resource tables and Split targetings can be merged for each split individually as we go.
      mergedResourceTable = mergeResourceTables(mergedResourceTable, split);
      mergedSplitTargeting = mergeSplitTargetings(mergedSplitTargeting, split);

      // Android manifests need to be merged later, globally for all splits.
      androidManifestsToMergeByModule.put(split.getModuleName(), split.getAndroidManifest());

      for (ModuleEntry entry : split.getEntries()) {
        if (entry.getPath().startsWith(DEX_DIRECTORY) && !entry.isDirectory()) {
          // Dex files need to be merged later, globally for all splits.
          dexFilesToMergeByModule.put(split.getModuleName(), entry);
        } else {
          mergeEntries(mergedEntriesByPath, split, entry);
        }
      }
    }

    AndroidManifest mergedAndroidManifest = mergeAndroidManifests(androidManifestsToMergeByModule);

    Collection<ModuleEntry> mergedDexFiles =
        mergeDexFilesAndCache(
            dexFilesToMergeByModule, bundleMetadata, mergedAndroidManifest, mergedDexCache);

    // Record names of the modules this shard was fused from.
    ImmutableList<String> fusedModuleNames = getUniqueModuleNames(splitsOfShard);
    AndroidManifest finalAndroidManifest =
        mergedAndroidManifest.toEditor().setFusedModuleNames(fusedModuleNames).save();

    // Construct the final shard.
    return buildShard(
        mergedEntriesByPath.values(),
        mergedDexFiles,
        mergedSplitTargeting,
        finalAndroidManifest,
        mergedResourceTable);
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
            Optional.empty());

    // Add the APEX config as it's used to identify APEX APKs.
    return shard.toBuilder().setApexConfig(splitsOfShard.get(0).getApexConfig().get()).build();
  }

  private ModuleSplit buildShard(
      Collection<ModuleEntry> entriesByPath,
      Collection<ModuleEntry> mergedDexFiles,
      ApkTargeting splitTargeting,
      AndroidManifest androidManifest,
      Optional<ResourceTable> mergedResourceTable) {
    ImmutableList<ModuleEntry> entries =
        ImmutableList.<ModuleEntry>builder().addAll(entriesByPath).addAll(mergedDexFiles).build();
    ModuleSplit.Builder shard =
        ModuleSplit.builder()
            .setAndroidManifest(androidManifest)
            .setEntries(entries)
            .setApkTargeting(splitTargeting)
            .setSplitType(SplitType.STANDALONE)
            // We don't care about the following properties for shards. The values are set just to
            // satisfy contract of @AutoValue.Builder.
            // `nativeConfig` is optional and therefore not being set.
            .setMasterSplit(false)
            .setModuleName(BASE_MODULE_NAME)
            .setVariantTargeting(VariantTargeting.getDefaultInstance());
    mergedResourceTable.ifPresent(shard::setResourceTable);
    return shard.build();
  }

  private AndroidManifest mergeAndroidManifests(
      SetMultimap<BundleModuleName, AndroidManifest> manifestsToMergeByModule) {
    // For now assume that all necessary information has been propagated to the base manifest when
    // building the bundle. Actual manifest merging will be implemented later.
    return getOnlyBaseAndroidManifest(manifestsToMergeByModule);
  }

  private Collection<ModuleEntry> mergeDexFilesAndCache(
      ListMultimap<BundleModuleName, ModuleEntry> dexFilesToMergeByModule,
      BundleMetadata bundleMetadata,
      AndroidManifest androidManifest,
      Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> mergedDexCache) {

    if (dexFilesToMergeByModule.keySet().size() <= 1) {
      // Don't merge if all dex files live inside a single module. If that module contains multiple
      // dex files, it should have been built with multi-dex support.
      return dexFilesToMergeByModule.values();

    } else {
      ImmutableList<ModuleEntry> dexEntries =
          ImmutableList.copyOf(dexFilesToMergeByModule.values());

      ImmutableList<Path> mergedDexFiles =
          mergedDexCache.computeIfAbsent(
              ImmutableSet.copyOf(dexEntries),
              key -> mergeDexFiles(dexEntries, bundleMetadata, androidManifest));

      // Names of the merged dex files need to be preserved ("classes.dex", "classes2.dex" etc.).
      return mergedDexFiles
          .stream()
          .map(
              filePath ->
                  FileSystemModuleEntry.ofFile(
                      /* entryPath= */ DEX_DIRECTORY.resolve(filePath.getFileName().toString()),
                      /* fileSystemPath= */ filePath))
          .collect(toImmutableList());
    }
  }

  private ImmutableList<Path> mergeDexFiles(
      List<ModuleEntry> dexEntries,
      BundleMetadata bundleMetadata,
      AndroidManifest androidManifest) {
    try {
      Path dexOriginalDir = Files.createTempDirectory(globalTempDir, "dex-merging-in");
      // The merged dex files will be written to a sub-directory of the global temp directory
      // that exists throughout execution of a bundletool command.
      Path dexMergedDir = Files.createTempDirectory(globalTempDir, "dex-merging-out");

      // The dex merger requires the main dex list represented as a file.
      Optional<Path> mainDexListFile = writeMainDexListFileIfPresent(bundleMetadata);

      // Write input dex data to temporary files "0.dex", "1.dex" etc. The names/order is not
      // important. The filenames just need to be unique and have the ".dex" extension.
      ImmutableList<Path> dexFiles =
          writeModuleEntriesToIndexedFiles(dexEntries, dexOriginalDir, /* fileSuffix= */ ".dex");

      ImmutableList<Path> mergedDexFiles =
          dexMerger.merge(
              dexFiles,
              dexMergedDir,
              mainDexListFile,
              androidManifest.getEffectiveApplicationDebuggable(),
              androidManifest.getEffectiveMinSdkVersion());

      return mergedDexFiles;

    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("I/O error while merging dex files.")
          .build();
    }
  }

  private static void mergeEntries(
      Map<ZipPath, ModuleEntry> mergedEntriesByPath, ModuleSplit split, ModuleEntry entry) {
    ModuleEntry existingEntry = mergedEntriesByPath.putIfAbsent(entry.getPath(), entry);
    // Any conflicts of plain entries should be caught by bundle validations.
    checkState(
        existingEntry == null || ModuleEntry.equal(existingEntry, entry),
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
            .withMessage(
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
          .withMessage("Failed to merge with targeting of module '%s'.", split.getModuleName())
          .build();
    }
  }

  private Optional<Path> writeMainDexListFileIfPresent(BundleMetadata bundleMetadata)
      throws IOException {

    Optional<InputStreamSupplier> mainDexListFileData =
        bundleMetadata.getFileData(BUNDLETOOL_NAMESPACE, MAIN_DEX_LIST_FILE_NAME);

    if (!mainDexListFileData.isPresent()) {
      return Optional.empty();
    }

    Path mainDexListFile = Files.createTempFile(globalTempDir, "mainDexList", ".txt");
    try (InputStream inputStream = mainDexListFileData.get().get()) {
      Files.copy(inputStream, mainDexListFile, StandardCopyOption.REPLACE_EXISTING);
    }
    return Optional.of(mainDexListFile);
  }

  private static AndroidManifest getOnlyBaseAndroidManifest(
      SetMultimap<BundleModuleName, AndroidManifest> manifestsToMergeByModule) {

    Set<AndroidManifest> baseManifests = manifestsToMergeByModule.get(BASE_MODULE_NAME);

    if (baseManifests.size() != 1) {
      throw CommandExecutionException.builder()
          .withMessage(
              "Expected exactly one base module manifest, but found %d.", baseManifests.size())
          .build();
    }

    return Iterables.getOnlyElement(baseManifests);
  }

  private static ImmutableList<String> getUniqueModuleNames(
      ImmutableCollection<ModuleSplit> splits) {
    return splits
        .stream()
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
    try (InputStream is = entry.getContent();
        OutputStream os = BufferedIo.outputStream(toFile)) {
      ByteStreams.copy(is, os);
    }
  }

  private static ImmutableList<ModuleSplit> writeSplitIdInManifestHavingSameModule(
      ImmutableCollection<ModuleSplit> splits) {
    SuffixManager suffixManager = new SuffixManager();
    return splits.stream()
        .map(split -> split.writeSplitIdInManifest(suffixManager.createSuffix(split)))
        .collect(toImmutableList());
  }
}
