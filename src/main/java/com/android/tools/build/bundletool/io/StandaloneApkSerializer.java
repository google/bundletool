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


import com.android.bundle.Commands.ApexApkMetadata;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import javax.inject.Inject;

/** Serializes standalone APKs to disk. */
public class StandaloneApkSerializer {

  private final ApkSerializerHelper apkSerializerHelper;

  @Inject
  public StandaloneApkSerializer(ApkSerializerHelper apkSerializerHelper) {
    this.apkSerializerHelper = apkSerializerHelper;
  }

  public ApkDescription writeToDisk(
      ModuleSplit standaloneSplit, Path outputDirectory, ZipPath apkPath) {
    return writeToDiskInternal(standaloneSplit, outputDirectory, apkPath);
  }

  public ApkDescription writeToDiskAsUniversal(ModuleSplit standaloneSplit, Path outputDirectory) {
    return writeToDiskInternal(standaloneSplit, outputDirectory, ZipPath.create("universal.apk"));
  }

  public ApkDescription writeSystemApkToDisk(
      ModuleSplit systemSplit, Path outputDirectory, ZipPath apkPath) {
    apkSerializerHelper.writeToZipFile(systemSplit, outputDirectory.resolve(apkPath.toString()));

    ApkDescription.Builder apkDescription =
        ApkDescription.newBuilder()
            .setPath(apkPath.toString())
            .setTargeting(systemSplit.getApkTargeting());

    if (systemSplit.isBaseModuleSplit() && systemSplit.isMasterSplit()) {
      apkDescription.setSystemApkMetadata(
          SystemApkMetadata.newBuilder()
              .addAllFusedModuleName(systemSplit.getAndroidManifest().getFusedModuleNames()));
    } else {
      apkDescription.setSplitApkMetadata(
          SplitApkMetadata.newBuilder()
              // Only the base master split doesn't have a split id.
              .setSplitId(systemSplit.getAndroidManifest().getSplitId().get())
              .setIsMasterSplit(systemSplit.isMasterSplit()));
    }
    return apkDescription.build();
  }

  @VisibleForTesting
  ApkDescription writeToDiskInternal(
      ModuleSplit standaloneSplit, Path outputDirectory, ZipPath apkPath) {
    apkSerializerHelper.writeToZipFile(
        standaloneSplit, outputDirectory.resolve(apkPath.toString()));

    ApkDescription.Builder apkDescription =
        ApkDescription.newBuilder()
            .setPath(apkPath.toString())
            .setTargeting(standaloneSplit.getApkTargeting());

    if (standaloneSplit.isApex()) {
      apkDescription.setApexApkMetadata(
          ApexApkMetadata.newBuilder()
              .addAllApexEmbeddedApkConfig(standaloneSplit.getApexEmbeddedApkConfigs())
              .build());
    } else {
      apkDescription.setStandaloneApkMetadata(
          StandaloneApkMetadata.newBuilder()
              .addAllFusedModuleName(standaloneSplit.getAndroidManifest().getFusedModuleNames()));
    }

    return apkDescription.build();
  }
}
