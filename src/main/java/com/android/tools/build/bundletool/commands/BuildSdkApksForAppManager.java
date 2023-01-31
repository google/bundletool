/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.build.bundletool.commands;

import com.android.bundle.Commands.LocalTestingInfo;
import com.android.tools.build.bundletool.io.ApkSerializerManager;
import com.android.tools.build.bundletool.io.ApkSetWriter;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.GeneratedAssetSlices;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.shards.ModuleSplitterForShards;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.inject.Inject;

/** Executes build-sdk-apks-for-app command. */
public class BuildSdkApksForAppManager {

  private final BuildSdkApksForAppCommand command;
  private final BundleModule module;
  private final ModuleSplitterForShards moduleSplitterForShards;
  private final TempDirectory tempDirectory;
  private final ApkSerializerManager apkSerializerManager;

  @Inject
  BuildSdkApksForAppManager(
      BuildSdkApksForAppCommand command,
      BundleModule module,
      ModuleSplitterForShards moduleSplitterForShards,
      TempDirectory tempDirectory,
      ApkSerializerManager apkSerializerManager) {
    this.command = command;
    this.module = module;
    this.moduleSplitterForShards = moduleSplitterForShards;
    this.tempDirectory = tempDirectory;
    this.apkSerializerManager = apkSerializerManager;
  }

  void execute() {
    // No sharding dimensions are passed to module splitter, so the resulting list will only have 1
    // element.
    ImmutableList<ModuleSplit> splits =
        moduleSplitterForShards.generateSplits(module, /* shardingDimensions= */ ImmutableSet.of());

    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(splits);
    ApkSetWriter apkSetWriter = ApkSetWriter.zip(tempDirectory.getPath(), command.getOutputFile());

    apkSerializerManager.serializeApkSetWithoutToc(
        apkSetWriter,
        generatedApks,
        GeneratedAssetSlices.builder().build(),
        /* deviceSpec= */ Optional.empty(),
        LocalTestingInfo.getDefaultInstance(),
        /* permanentlyFusedModules= */ ImmutableSet.of());
  }
}
