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

package com.android.tools.build.bundletool.testing;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;

/** Helpers related to creating APKs archives in tests. */
public final class ApksArchiveHelpers {

  private static final byte[] DUMMY_BYTES = new byte[100];

  public static Path createApksArchiveFile(BuildApksResult result, Path location) throws Exception {
    ZipBuilder archiveBuilder = new ZipBuilder();

    result
        .getVariantList()
        .stream()
        .flatMap(variant -> variant.getApkSetList().stream())
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .forEach(
            apkDesc ->
                archiveBuilder.addFileWithContent(ZipPath.create(apkDesc.getPath()), DUMMY_BYTES));
    archiveBuilder.addFileWithProtoContent(ZipPath.create("toc.pb"), result);

    return archiveBuilder.writeTo(location);
  }

  public static Variant createVariant(VariantTargeting variantTargeting, ApkSet... apkSets) {
    return Variant.newBuilder()
        .setTargeting(variantTargeting)
        .addAllApkSet(Arrays.asList(apkSets))
        .build();
  }

  public static Variant createVariantForSingleSplitApk(
      VariantTargeting variantTargeting, ApkTargeting apkTargeting, Path apkPath) {
    return createVariant(
        variantTargeting,
        createSplitApkSet("base", createMasterApkDescription(apkTargeting, apkPath)));
  }

  public static ApkSet createSplitApkSet(String moduleName, ApkDescription... apkDescription) {
    return createSplitApkSet(
        moduleName,
        /* onDemand= */ false,
        /* moduleDependencies= */ ImmutableList.of(),
        apkDescription);
  }

  public static ApkSet createSplitApkSet(
      String moduleName,
      boolean onDemand,
      ImmutableList<String> moduleDependencies,
      ApkDescription... apkDescription) {
    return ApkSet.newBuilder()
        .setModuleMetadata(
            ModuleMetadata.newBuilder()
                .setName(moduleName)
                .setOnDemand(onDemand)
                .addAllDependencies(moduleDependencies))
        .addAllApkDescription(Arrays.asList(apkDescription))
        .build();
  }

  public static ApkDescription createMasterApkDescription(ApkTargeting apkTargeting, Path apkPath) {
    return createApkDescription(apkTargeting, apkPath, /* isMasterSplit= */ true);
  }

  public static ApkDescription createApkDescription(
      ApkTargeting apkTargeting, Path apkPath, boolean isMasterSplit) {
    return ApkDescription.newBuilder()
        .setPath(apkPath.toString())
        .setTargeting(apkTargeting)
        .setSplitApkMetadata(SplitApkMetadata.newBuilder().setIsMasterSplit(isMasterSplit))
        .build();
  }

  /** Creates an instant apk set with the given module name, ApkTargeting, and path for the apk. */
  public static ApkSet createInstantApkSet(
      String moduleName, ApkTargeting apkTargeting, Path apkPath) {
    return ApkSet.newBuilder()
        .setModuleMetadata(ModuleMetadata.newBuilder().setName(moduleName).setIsInstant(true))
        .addApkDescription(
            ApkDescription.newBuilder()
                .setPath(apkPath.toString())
                .setTargeting(apkTargeting)
                .setInstantApkMetadata(SplitApkMetadata.newBuilder().setIsMasterSplit(true)))
        .build();
  }

  public static ApkSet createStandaloneApkSet(ApkTargeting apkTargeting, Path apkPath) {
    // Note: Standalone APK is represented as a module named "base".
    return ApkSet.newBuilder()
        .setModuleMetadata(ModuleMetadata.newBuilder().setName("base"))
        .addApkDescription(
            ApkDescription.newBuilder()
                .setPath(apkPath.toString())
                .setTargeting(apkTargeting)
                .setStandaloneApkMetadata(
                    StandaloneApkMetadata.newBuilder().addFusedModuleName("base")))
        .build();
  }
}
