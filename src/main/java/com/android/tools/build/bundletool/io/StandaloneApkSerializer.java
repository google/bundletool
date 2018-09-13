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

package com.android.tools.build.bundletool.io;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Config.Compression;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.util.Optional;

/** Serializes standalone APKs to disk. */
public class StandaloneApkSerializer {

  public static final String STANDALONE_APKS_SUB_DIR = "standalones";

  private final ApkPathManager apkPathManager;
  private final ApkSerializerHelper apkSerializerHelper;

  public StandaloneApkSerializer(
      ApkPathManager apkPathManager,
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfig,
      Compression compression) {
    this.apkPathManager = apkPathManager;
    this.apkSerializerHelper = new ApkSerializerHelper(aapt2Command, signingConfig, compression);
  }

  public ApkDescription writeToDisk(ModuleSplit standaloneSplit, Path outputDirectory) {
    ZipPath apkPath = apkPathManager.getApkPath(standaloneSplit);
    return writeToDiskInternal(standaloneSplit, outputDirectory, apkPath);
  }

  public ApkDescription writeToDiskAsUniversal(ModuleSplit standaloneSplit, Path outputDirectory) {
    return writeToDiskInternal(standaloneSplit, outputDirectory, ZipPath.create("universal.apk"));
  }

  @VisibleForTesting
  ApkDescription writeToDiskInternal(
      ModuleSplit standaloneSplit, Path outputDirectory, ZipPath apkPath) {
    apkSerializerHelper.writeToZipFile(
        standaloneSplit, outputDirectory.resolve(apkPath.toString()));

    return ApkDescription.newBuilder()
        .setPath(apkPath.toString())
        .setStandaloneApkMetadata(
            StandaloneApkMetadata.newBuilder()
                .addAllFusedModuleName(standaloneSplit.getAndroidManifest().getFusedModuleNames()))
        .setTargeting(standaloneSplit.getApkTargeting())
        .build();
  }
}
