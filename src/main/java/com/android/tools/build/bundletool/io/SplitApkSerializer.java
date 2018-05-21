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
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/** Serializes split APKs on disk. */
public class SplitApkSerializer {

  public static final String SPLIT_APKS_SUB_DIR = "splits";

  private final ApkSerializerHelper apkSerializerHelper;

  public SplitApkSerializer(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfig,
      Compression compression) {
    this.apkSerializerHelper = new ApkSerializerHelper(aapt2Command, signingConfig, compression);
  }

  public ApkDescription writeSplitToDisk(ModuleSplit split, Path outputDirectory) {
    checkState(isDirectory(outputDirectory));

    String apkFileName = getApkFileName(split, split.getModuleName());
    // Using ZipPath to ensure '/' path delimiter in the ApkDescription proto.
    String apkFileRelPath = ZipPath.create(SPLIT_APKS_SUB_DIR).resolve(apkFileName).toString();

    apkSerializerHelper.writeToZipFile(split, outputDirectory.resolve(apkFileRelPath));

    return ApkDescription.newBuilder()
        .setPath(apkFileRelPath)
        .setTargeting(split.getApkTargeting())
        .setSplitApkMetadata(
            SplitApkMetadata.newBuilder()
                .setSplitId(split.getAndroidManifest().get().getSplitId().orElse(""))
                .setIsMasterSplit(split.isMasterSplit()))
        .build();
  }

  private static String getApkFileName(ModuleSplit apk, BundleModuleName moduleName) {
    String splitId = apk.getAndroidManifest().get().getSplitId().orElse("");
    String fileSuffix =
        apk.isMasterSplit() ? "master" : Iterables.getLast(Arrays.asList(splitId.split("\\.")));
    return String.format("%s-%s.apk", moduleName.getName(), fileSuffix);
  }
}
