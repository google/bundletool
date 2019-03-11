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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.bundle.Commands.ApexApkMetadata;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata.SystemApkType;
import com.android.bundle.Config.Compression;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

/** Serializes standalone APKs to disk. */
public class StandaloneApkSerializer {

  private final ApkPathManager apkPathManager;
  private final ApkSerializerHelper apkSerializerHelper;

  public StandaloneApkSerializer(
      ApkPathManager apkPathManager,
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfig,
      Version bundleVersion,
      Compression compression) {
    this.apkPathManager = apkPathManager;
    this.apkSerializerHelper =
        new ApkSerializerHelper(aapt2Command, signingConfig, bundleVersion, compression);
  }

  public ApkDescription writeToDisk(ModuleSplit standaloneSplit, Path outputDirectory) {
    ZipPath apkPath = apkPathManager.getApkPath(standaloneSplit);
    return writeToDiskInternal(standaloneSplit, outputDirectory, apkPath);
  }

  public ApkDescription writeToDiskAsUniversal(ModuleSplit standaloneSplit, Path outputDirectory) {
    return writeToDiskInternal(standaloneSplit, outputDirectory, ZipPath.create("universal.apk"));
  }

  public ApkDescription writeSystemApkToDisk(ModuleSplit systemSplit, Path outputDirectory) {
    return writeSystemApkToDiskInternal(systemSplit, outputDirectory, SystemApkType.SYSTEM);
  }
  /**
   * Writes an compressed system APK and stub system APK containing just android manifest to disk.
   */
  public ImmutableList<ApkDescription> writeCompressedSystemApksToDisk(
      ModuleSplit systemSplit, Path outputDirectory) {
    ApkDescription stubApkDescription =
        writeSystemApkToDiskInternal(
            splitWithOnlyManifest(systemSplit), outputDirectory, SystemApkType.SYSTEM_STUB);
    ZipPath compressedApkPath =
        ZipPath.create(getCompressedApkPathFromStubApkPath(stubApkDescription.getPath()));
    apkSerializerHelper.writeCompressedApkToZipFile(
        systemSplit, outputDirectory.resolve(compressedApkPath.toString()));
    return ImmutableList.of(
        stubApkDescription,
        createSystemApkDescription(
            systemSplit, compressedApkPath, SystemApkType.SYSTEM_COMPRESSED));
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
      apkDescription.setApexApkMetadata(ApexApkMetadata.getDefaultInstance());
    } else {
      apkDescription.setStandaloneApkMetadata(
          StandaloneApkMetadata.newBuilder()
              .addAllFusedModuleName(standaloneSplit.getAndroidManifest().getFusedModuleNames()));
    }

    return apkDescription.build();
  }

  private ApkDescription writeSystemApkToDiskInternal(
      ModuleSplit systemSplit, Path outputDirectory, SystemApkMetadata.SystemApkType apkType) {
    ZipPath apkPath = apkPathManager.getApkPath(systemSplit);
    apkSerializerHelper.writeToZipFile(systemSplit, outputDirectory.resolve(apkPath.toString()));
    return createSystemApkDescription(systemSplit, apkPath, apkType);
  }

  /**
   * The compressed system APK should have the same file name as stub system APK (".apk" file
   * extension) but end with ".apk.gz" file extension.
   */
  private static String getCompressedApkPathFromStubApkPath(String stubApkPath) {
    checkArgument(stubApkPath.endsWith(".apk"));
    return stubApkPath + ".gz";
  }

  private static ApkDescription createSystemApkDescription(
      ModuleSplit systemSplit, ZipPath apkPath, SystemApkMetadata.SystemApkType apkType) {
    return ApkDescription.newBuilder()
        .setPath(apkPath.toString())
        .setSystemApkMetadata(
            SystemApkMetadata.newBuilder()
                .addAllFusedModuleName(systemSplit.getAndroidManifest().getFusedModuleNames())
                .setSystemApkType(apkType))
        .setTargeting(systemSplit.getApkTargeting())
        .build();
  }

  private static ModuleSplit splitWithOnlyManifest(ModuleSplit split) {
    return ModuleSplit.builder()
        .setModuleName(split.getModuleName())
        .setSplitType(split.getSplitType())
        .setVariantTargeting(split.getVariantTargeting())
        .setApkTargeting(split.getApkTargeting())
        .setAndroidManifest(split.getAndroidManifest())
        .setMasterSplit(split.isMasterSplit())
        .build();
  }
}
