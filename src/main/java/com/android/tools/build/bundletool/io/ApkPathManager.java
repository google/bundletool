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
package com.android.tools.build.bundletool.io;

import static java.util.stream.Collectors.toList;

import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.base.Joiner;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/**
 * Associates {@link ModuleSplit} with a file name ensuring there will be no path conflicts in the
 * APK Set.
 *
 * <ul>
 *   <li>Standalone APKs will have the format: "standalones/standalone-x86-hdpi.apk"
 *   <li>Standalone APEXs will have the format: "standalones/standalone-x86.apex"
 *   <li>Split APKs will have the format: "splits/moduleName-master.apk",
 *       "splits/moduleName-x86.apk", etc.
 *   <li>Instant APKs will have the format: "instant/instant-moduleName-master.apk",
 *       "instant/instant-moduleName-x86.apk", etc.
 *   <li>Asset Slices will have the format: "asset-slices/moduleName-master.apk",
 *       "asset-slices/moduleName-x86.apk", etc.
 * </ul>
 *
 * <p>If the name of an APK conflicts with a previously generated APK, a unique number is appended
 * to its name, e.g. "splits/moduleName-x86_2.apk".
 *
 * <p>The class is thread-safe.
 */
public class ApkPathManager {

  private static final Joiner NAME_PARTS_JOINER = Joiner.on('-');

  private final ApkBuildMode apkBuildMode;

  /** Paths of APKs that have already been allocated. */
  @GuardedBy("this")
  private final Set<ZipPath> usedPaths = new HashSet<>();

  @Inject
  ApkPathManager(ApkBuildMode apkBuildMode) {
    this.apkBuildMode = apkBuildMode;
  }

  /**
   * Returns a unique file path for the given ModuleSplit.
   *
   * <p>Note that calling this method twice for the same object will return a different result since
   * each returned value is unique.
   */
  public ZipPath getApkPath(ModuleSplit moduleSplit) {
    if (apkBuildMode.equals(ApkBuildMode.UNIVERSAL)) {
      return ZipPath.create("universal.apk");
    }
    String moduleName = moduleSplit.getModuleName().getName();
    String targetingSuffix = getTargetingSuffix(moduleSplit);

    ZipPath directory;
    String apkFileName;
    switch (moduleSplit.getSplitType()) {
      case SPLIT:
        directory = ZipPath.create("splits");
        apkFileName = buildName(moduleName, targetingSuffix);
        break;
      case INSTANT:
        directory = ZipPath.create("instant");
        apkFileName = buildName("instant", moduleName, targetingSuffix);
        break;
      case STANDALONE:
        directory = ZipPath.create("standalones");
        apkFileName = buildName("standalone", targetingSuffix);
        break;
      case SYSTEM:
        if (moduleSplit.isBaseModuleSplit() && moduleSplit.isMasterSplit()) {
          directory = ZipPath.create("system");
          apkFileName = buildName("system");
        } else {
          directory = ZipPath.create("splits");
          apkFileName = buildName(moduleName, targetingSuffix);
        }
        break;
      case ASSET_SLICE:
        directory = ZipPath.create("asset-slices");
        apkFileName = buildName(moduleName, targetingSuffix);
        break;
      case ARCHIVE:
        directory = ZipPath.create("archive");
        apkFileName = buildName("archive");
        break;
      default:
        throw new IllegalStateException("Unrecognized split type: " + moduleSplit.getSplitType());
    }

    return findAndClaimUnusedPath(directory, apkFileName, fileExtension(moduleSplit));
  }

  /**
   * Iterates over the given {@code proposedName} by suffixing the name with an increasing integer
   * until an unused path is found, then returns this path.
   */
  private synchronized ZipPath findAndClaimUnusedPath(
      ZipPath directory, String proposedName, String fileExtension) {
    ZipPath apkPath = directory.resolve(proposedName + fileExtension);

    int serialNumber = 1;
    while (usedPaths.contains(apkPath)) {
      serialNumber++;
      String fileName = String.format("%s_%d", proposedName, serialNumber);
      apkPath = directory.resolve(fileName + fileExtension);
    }
    usedPaths.add(apkPath);

    return apkPath;
  }

  private static String fileExtension(ModuleSplit moduleSplit) {
    return moduleSplit.isApex() ? ".apex" : ".apk";
  }

  private static String getTargetingSuffix(ModuleSplit moduleSplit) {
    return moduleSplit.isMasterSplit() && !moduleSplit.getSplitType().equals(SplitType.STANDALONE)
        ? "master"
        : moduleSplit.getSuffix();
  }

  private static String buildName(String... nameParts) {
    return NAME_PARTS_JOINER.join(
        Arrays.stream(nameParts).filter(s -> !s.isEmpty()).collect(toList()));
  }
}
