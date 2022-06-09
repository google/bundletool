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

import static com.android.tools.build.bundletool.mergers.MergingUtils.getSameValueOrNonNull;
import static com.android.tools.build.bundletool.mergers.MergingUtils.mergeTargetedAssetsDirectories;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Config.ApexEmbeddedApkConfig;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import java.util.HashMap;
import java.util.Map;

/**
 * Merges module splits together that have the same targeting.
 *
 * <p>The current implementation assumes all splits belong to the same variant.
 */
public class SameTargetingMerger implements ModuleSplitMerger {

  @Override
  public ImmutableList<ModuleSplit> merge(ImmutableCollection<ModuleSplit> moduleSplits) {
    checkArgument(
        moduleSplits.stream().map(ModuleSplit::getVariantTargeting).distinct().count() == 1,
        "SameTargetingMerger doesn't support merging splits from different variants.");
    ImmutableList.Builder<ModuleSplit> result = ImmutableList.builder();
    ImmutableListMultimap<ApkTargeting, ModuleSplit> splitsByTargeting =
        Multimaps.index(moduleSplits, ModuleSplit::getApkTargeting);
    for (ApkTargeting targeting : splitsByTargeting.keySet()) {
      result.add(mergeSplits(splitsByTargeting.get(targeting)));
    }
    return result.build();
  }

  private ModuleSplit mergeSplits(ImmutableCollection<ModuleSplit> splits) {
    ModuleSplit.Builder builder = ModuleSplit.builder();
    ImmutableList.Builder<ModuleEntry> entries = ImmutableList.builder();
    AndroidManifest mergedManifest = null;
    ResourceTable mergedResourceTable = null;
    NativeLibraries mergedNativeConfig = null;
    Map<String, TargetedAssetsDirectory> mergedAssetsConfig = new HashMap<>();
    ApexImages mergedApexConfig = null;
    ImmutableList<ApexEmbeddedApkConfig> mergedApexEmbeddedApkConfigs = null;
    BundleModuleName mergedModuleName = null;
    Boolean mergedIsMasterSplit = null;
    VariantTargeting mergedVariantTargeting = null;
    Boolean sparseEncoding = null;

    for (ModuleSplit split : splits) {
      mergedManifest =
          getSameValueOrNonNull(mergedManifest, split.getAndroidManifest())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Encountered two distinct manifests while merging."));
      if (split.getResourceTable().isPresent()) {
        mergedResourceTable =
            getSameValueOrNonNull(mergedResourceTable, split.getResourceTable().get())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Unsupported case: encountered two distinct resource tables while "
                                + "merging."));
      }
      if (split.getNativeConfig().isPresent()) {
        mergedNativeConfig =
            getSameValueOrNonNull(mergedNativeConfig, split.getNativeConfig().get())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Encountered two distinct native configs while merging."));
      }
      if (split.getApexConfig().isPresent()) {
        mergedApexConfig =
            getSameValueOrNonNull(mergedApexConfig, split.getApexConfig().get())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Encountered two distinct apex configs while merging."));
      }
      mergedApexEmbeddedApkConfigs =
          getSameValueOrNonNull(mergedApexEmbeddedApkConfigs, split.getApexEmbeddedApkConfigs())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Encountered two distinct apex embedded apk configs while merging."));
      mergedModuleName =
          getSameValueOrNonNull(mergedModuleName, split.getModuleName())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Encountered two distinct module names while merging."));
      mergedIsMasterSplit =
          getSameValueOrNonNull(mergedIsMasterSplit, Boolean.valueOf(split.isMasterSplit()))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Encountered conflicting isMasterSplit flag values while merging."));
      mergedVariantTargeting =
          getSameValueOrNonNull(mergedVariantTargeting, split.getVariantTargeting())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Encountered conflicting variant targeting values while merging."));
      entries.addAll(split.getEntries());
      builder.setApkTargeting(split.getApkTargeting());

      split
          .getAssetsConfig()
          .ifPresent(
              assetsConfig ->
                  mergeTargetedAssetsDirectories(
                      mergedAssetsConfig, assetsConfig.getDirectoryList()));
      sparseEncoding =
          getSameValueOrNonNull(sparseEncoding, split.getSparseEncoding())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Encountered different sparse encoding values while merging."));
    }
    builder.setSparseEncoding(Boolean.valueOf(sparseEncoding));

    if (mergedManifest != null) {
      builder.setAndroidManifest(mergedManifest);
    }
    if (mergedResourceTable != null) {
      builder.setResourceTable(mergedResourceTable);
    }
    if (mergedNativeConfig != null) {
      builder.setNativeConfig(mergedNativeConfig);
    }
    if (!mergedAssetsConfig.isEmpty()) {
      builder.setAssetsConfig(
          Assets.newBuilder().addAllDirectory(mergedAssetsConfig.values()).build());
    }
    if (mergedApexConfig != null) {
      builder.setApexConfig(mergedApexConfig);
    }
    if (mergedApexEmbeddedApkConfigs != null) {
      builder.setApexEmbeddedApkConfigs(mergedApexEmbeddedApkConfigs);
    }
    if (mergedModuleName != null) {
      builder.setModuleName(mergedModuleName);
    }
    if (mergedIsMasterSplit != null) {
      builder.setMasterSplit(mergedIsMasterSplit);
    }
    builder.setVariantTargeting(mergedVariantTargeting);
    builder.setEntries(entries.build());
    return builder.build();
  }
}
