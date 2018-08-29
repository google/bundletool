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
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.concurrent.GuardedBy;

/** Serializes split APKs on disk. */
public class SplitApkSerializer {

  private static final ZipPath SPLIT_APKS_SUB_DIR = ZipPath.create("splits");
  private static final ZipPath INSTANT_APKS_SUB_DIR = ZipPath.create("instant");

  private final SuffixManager suffixManager = new SuffixManager();

  private final ApkSerializerHelper apkSerializerHelper;

  public SplitApkSerializer(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfig,
      Compression compression) {
    this.apkSerializerHelper = new ApkSerializerHelper(aapt2Command, signingConfig, compression);
  }

  /** Writes the installable split to disk. */
  public ApkDescription writeSplitToDisk(ModuleSplit split, Path outputDirectory) {
    return writeToDisk(
        split, outputDirectory, SPLIT_APKS_SUB_DIR, ApkDescription.Builder::setSplitApkMetadata);
  }

  /** Writes the instant split to disk. */
  public ApkDescription writeInstantSplitToDisk(ModuleSplit split, Path outputDirectory) {
    return writeToDisk(
        split,
        outputDirectory,
        INSTANT_APKS_SUB_DIR,
        ApkDescription.Builder::setInstantApkMetadata);
  }

  /** Writes the given split to the path subdirectory in the zip file. */
  private ApkDescription writeToDisk(
      ModuleSplit split,
      Path outputDirectory,
      ZipPath subDirectoryInApkSet,
      BiFunction<ApkDescription.Builder, SplitApkMetadata, ApkDescription.Builder> setApkMetadata) {
    checkState(isDirectory(outputDirectory));

    String apkFileName = getApkFileName(split, split.getModuleName());
    // Using ZipPath to ensure '/' path delimiter in the ApkDescription proto.
    String apkFileRelPath = subDirectoryInApkSet.resolve(apkFileName).toString();

    apkSerializerHelper.writeToZipFile(split, outputDirectory.resolve(apkFileRelPath));
    ApkDescription.Builder builder =
        ApkDescription.newBuilder().setPath(apkFileRelPath).setTargeting(split.getApkTargeting());
    return setApkMetadata
        .apply(
            builder,
            SplitApkMetadata.newBuilder()
                .setSplitId(split.getAndroidManifest().getSplitId().orElse(""))
                .setIsMasterSplit(split.isMasterSplit())
                .build())
        .build();
  }

  private String getApkFileName(ModuleSplit apk, BundleModuleName moduleName) {
    String splitId = apk.getAndroidManifest().getSplitId().orElse("");
    String fileSuffix =
        apk.isMasterSplit() ? "master" : Iterables.getLast(Arrays.asList(splitId.split("\\.")));

    String prefix = apk.getSplitType() == SplitType.INSTANT ? "instant-" : "";
    return String.format(
        "%s%s.apk",
        prefix,
        suffixManager.resolveSuffix(String.format("%s-%s", moduleName.getName(), fileSuffix)));
  }

  private static class SuffixManager {
    @GuardedBy("this")
    private final Set<String> usedSuffixes = new HashSet<>();

    synchronized String resolveSuffix(String proposedSuffix) {
      String currentProposal = proposedSuffix;
      int serialNumber = 1;
      while (usedSuffixes.contains(currentProposal)) {
        serialNumber++;
        currentProposal = String.format("%s_%d", proposedSuffix, serialNumber);
      }
      usedSuffixes.add(currentProposal);
      return currentProposal;
    }
  }
}
