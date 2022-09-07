/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.tools.build.bundletool.io.ApkSerializerManager;
import com.android.tools.build.bundletool.io.ApkSetWriter;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.shards.ModuleSplitterForShards;
import com.android.tools.build.bundletool.shards.StandaloneApksGenerator;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;

/** Executes the "build-sdk-apks" command. */
public class BuildSdkApksManager {
  private final ApkSerializerManager apkSerializerManager;
  private final BuildSdkApksCommand command;
  private final TempDirectory tempDirectory;
  private final SdkBundle sdkBundle;
  private final ApkOptimizations apkOptimizations;
  private final ModuleSplitterForShards moduleSplitterForShards;

  @Inject
  BuildSdkApksManager(
      ApkSerializerManager apkSerializerManager,
      BuildSdkApksCommand command,
      TempDirectory tempDirectory,
      SdkBundle sdkBundle,
      ApkOptimizations apkOptimizations,
      ModuleSplitterForShards moduleSplitterForShards) {
    this.apkSerializerManager = apkSerializerManager;
    this.command = command;
    this.tempDirectory = tempDirectory;
    this.sdkBundle = sdkBundle;
    this.apkOptimizations = apkOptimizations;
    this.moduleSplitterForShards = moduleSplitterForShards;
  }

  void execute() throws IOException {
    ImmutableList<ModuleSplit> sdkApks = generateSdkApks();
    GeneratedApks generatedApks = GeneratedApks.builder().setStandaloneApks(sdkApks).build();

    if (command.getOverwriteOutput() && Files.exists(command.getOutputFile())) {
      MoreFiles.deleteRecursively(command.getOutputFile(), RecursiveDeleteOption.ALLOW_INSECURE);
    }

    // Create variants and serialize APKs.
    apkSerializerManager.serializeSdkApkSet(createApkSetWriter(), generatedApks);
  }

  private ApkSetWriter createApkSetWriter() {
    switch (command.getOutputFormat()) {
      case APK_SET:
        return ApkSetWriter.zip(tempDirectory.getPath(), command.getOutputFile());
      case DIRECTORY:
        return ApkSetWriter.directory(command.getOutputFile());
    }
    throw InvalidCommandException.builder()
        .withInternalMessage("Unsupported output format '%s'.", command.getOutputFormat())
        .build();
  }

  private ImmutableList<ModuleSplit> generateSdkApks() {
    checkState(sdkBundle.getVersionCode().isPresent(), "Missing version code for SDK Bundle.");
    return moduleSplitterForShards
        .generateSplits(sdkBundle.getModule(), apkOptimizations.getStandaloneDimensions())
        .stream()
        .map(moduleSplit -> moduleSplit.writeSdkVersionName(sdkBundle.getVersionName()))
        .map(moduleSplit -> moduleSplit.writeSdkVersionCode(sdkBundle.getVersionCode().get()))
        .map(moduleSplit -> moduleSplit.writeManifestPackage(sdkBundle.getManifestPackageName()))
        .map(
            moduleSplit ->
                moduleSplit.writeSdkLibraryElement(
                    sdkBundle.getPackageName(), sdkBundle.getSdkAndroidVersionMajor()))
        .map(ModuleSplit::overrideMinSdkVersionForSdkSandbox)
        .map(moduleSplit -> moduleSplit.writePatchVersion(sdkBundle.getPatchVersion()))
        .map(moduleSplit -> moduleSplit.writeSdkProviderClassName(sdkBundle.getProviderClassName()))
        .map(this::writeCompatSdkProviderClassNameIfPresent)
        .map(StandaloneApksGenerator::setVariantTargetingAndSplitType)
        .map(this::addSdkRuntimeTargeting)
        .collect(toImmutableList());
  }

  private ModuleSplit writeCompatSdkProviderClassNameIfPresent(ModuleSplit moduleSplit) {
    if (sdkBundle.getCompatProviderClassName().isPresent()) {
      return moduleSplit.writeCompatSdkProviderClassName(
          sdkBundle.getCompatProviderClassName().get());
    }
    return moduleSplit;
  }

  private ModuleSplit addSdkRuntimeTargeting(ModuleSplit moduleSplit) {
    return moduleSplit.toBuilder()
        .setVariantTargeting(
            moduleSplit.getVariantTargeting().toBuilder()
                .setSdkRuntimeTargeting(
                    SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true))
                .build())
        .build();
  }
}
