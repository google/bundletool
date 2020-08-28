/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.shards;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javax.inject.Inject;

/**
 * Generates APKs sharded by ABI and screen density.
 *
 * <p>Supports generation of both standalone and system APKs.
 */
public final class ShardedApksFacade {

  private final StandaloneApksGenerator standaloneApksGenerator;
  private final StandaloneApexApksGenerator standaloneApexApksGenerator;
  private final SystemApksGenerator systemApksGenerator;
  private final BundleModule64BitNativeLibrariesRemover bundleModule64BitNativeLibrariesRemover;
  private final BundleConfig bundleConfig;
  private final ApkBuildMode apkBuildMode;

  @Inject
  public ShardedApksFacade(
      StandaloneApksGenerator standaloneApksGenerator,
      StandaloneApexApksGenerator standaloneApexApksGenerator,
      SystemApksGenerator systemApksGenerator,
      BundleModule64BitNativeLibrariesRemover bundleModule64BitNativeLibrariesRemover,
      BundleConfig bundleConfig,
      ApkBuildMode apkBuildMode) {
    this.standaloneApksGenerator = standaloneApksGenerator;
    this.standaloneApexApksGenerator = standaloneApexApksGenerator;
    this.systemApksGenerator = systemApksGenerator;
    this.bundleModule64BitNativeLibrariesRemover = bundleModule64BitNativeLibrariesRemover;
    this.bundleConfig = bundleConfig;
    this.apkBuildMode = apkBuildMode;
  }

  public ImmutableList<ModuleSplit> generateSplits(
      ImmutableList<BundleModule> modules, ApkOptimizations apkOptimizations) {
    return standaloneApksGenerator.generateStandaloneApks(
        maybeRemove64BitLibraries(modules), apkOptimizations);
  }

  public ImmutableList<ModuleSplit> generateSystemSplits(
      ImmutableList<BundleModule> modules,
      ImmutableSet<BundleModuleName> modulesToFuse,
      ApkOptimizations apkOptimizations) {
    return systemApksGenerator.generateSystemApks(
        maybeRemove64BitLibraries(modules), modulesToFuse, apkOptimizations);
  }

  public ImmutableList<ModuleSplit> generateApexSplits(ImmutableList<BundleModule> modules) {
    return standaloneApexApksGenerator.generateStandaloneApks(maybeRemove64BitLibraries(modules));
  }

  private ImmutableList<BundleModule> maybeRemove64BitLibraries(
      ImmutableList<BundleModule> modules) {
    boolean shouldStrip64BitLibraries =
        bundleConfig.getOptimizations().getStandaloneConfig().getStrip64BitLibraries()
            && !apkBuildMode.equals(ApkBuildMode.UNIVERSAL);
    if (!shouldStrip64BitLibraries) {
      return modules;
    }
    return modules.stream()
        .map(bundleModule64BitNativeLibrariesRemover::strip64BitLibraries)
        .collect(toImmutableList());
  }
}
