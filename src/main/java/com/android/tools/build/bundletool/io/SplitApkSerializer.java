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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.file.Files.isDirectory;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Config.Compression;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.version.Version;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;

/** Serializes split APKs on disk. */
public class SplitApkSerializer {

  private final ApkPathManager apkPathManager;
  private final ApkSerializerHelper apkSerializerHelper;

  public SplitApkSerializer(
      ApkPathManager apkPathManager,
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfig,
      Version bundleVersion,
      Compression compression) {
    this.apkPathManager = apkPathManager;
    this.apkSerializerHelper =
        new ApkSerializerHelper(aapt2Command, signingConfig, bundleVersion, compression);
  }

  /** Writes the installable split to disk. */
  public ApkDescription writeSplitToDisk(ModuleSplit split, Path outputDirectory) {
    return writeToDisk(split, outputDirectory, ApkDescription.Builder::setSplitApkMetadata);
  }

  /** Writes the instant split to disk. */
  public ApkDescription writeInstantSplitToDisk(ModuleSplit split, Path outputDirectory) {
    return writeToDisk(split, outputDirectory, ApkDescription.Builder::setInstantApkMetadata);
  }

  /** Writes the asset slice to disk. */
  public ApkDescription writeAssetSliceToDisk(ModuleSplit split, Path outputDirectory) {
    return writeToDisk(split, outputDirectory, ApkDescription.Builder::setAssetSliceMetadata);
  }

  /** Writes the given split to the path subdirectory in the APK Set. */
  private ApkDescription writeToDisk(
      ModuleSplit split,
      Path outputDirectory,
      BiFunction<ApkDescription.Builder, SplitApkMetadata, ApkDescription.Builder> setApkMetadata) {
    checkState(isDirectory(outputDirectory), "Output directory does not exist.");

    ZipPath apkPath = apkPathManager.getApkPath(split);

    apkSerializerHelper.writeToZipFile(split, outputDirectory.resolve(apkPath.toString()));
    ApkDescription.Builder builder =
        ApkDescription.newBuilder()
            .setPath(apkPath.toString())
            .setTargeting(split.getApkTargeting());
    return setApkMetadata
        .apply(
            builder,
            SplitApkMetadata.newBuilder()
                .setSplitId(split.getAndroidManifest().getSplitId().orElse(""))
                .setIsMasterSplit(split.isMasterSplit())
                .build())
        .build();
  }
}
