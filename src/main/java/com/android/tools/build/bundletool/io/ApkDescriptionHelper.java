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
package com.android.tools.build.bundletool.io;

import com.android.bundle.Commands.ApexApkMetadata;
import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ArchivedApkMetadata;
import com.android.bundle.Commands.SigningDescription;
import com.android.bundle.Commands.SplitApkMetadata;
import com.android.bundle.Commands.StandaloneApkMetadata;
import com.android.bundle.Commands.SystemApkMetadata;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import java.util.Optional;

/** Helper class which creates ApkDescription for module splits. */
class ApkDescriptionHelper {

  static ApkDescription createApkDescription(ZipPath relativePath, ModuleSplit split) {
    return createApkDescription(relativePath, split, /* signingDescription= */ Optional.empty());
  }

  static ApkDescription createApkDescription(
      ZipPath relativePath, ModuleSplit split, Optional<SigningDescription> signingDescription) {
    ApkDescription.Builder resultBuilder =
        ApkDescription.newBuilder()
            .setPath(relativePath.toString())
            .setTargeting(split.getApkTargeting());
    switch (split.getSplitType()) {
      case INSTANT:
        resultBuilder.setInstantApkMetadata(createSplitApkMetadata(split));
        break;
      case SPLIT:
        resultBuilder.setSplitApkMetadata(createSplitApkMetadata(split));
        break;
      case SYSTEM:
        if (split.isBaseModuleSplit() && split.isMasterSplit()) {
          resultBuilder.setSystemApkMetadata(createSystemApkMetadata(split));
        } else {
          resultBuilder.setSplitApkMetadata(createSplitApkMetadata(split));
        }
        break;
      case STANDALONE:
        if (split.isApex()) {
          resultBuilder.setApexApkMetadata(createApexApkMetadata(split));
        } else {
          resultBuilder.setStandaloneApkMetadata(createStandaloneApkMetadata(split));
        }
        break;
      case ASSET_SLICE:
        resultBuilder.setAssetSliceMetadata(createSplitApkMetadata(split));
        break;
      case ARCHIVE:
        resultBuilder.setArchivedApkMetadata(ArchivedApkMetadata.getDefaultInstance());
        break;
    }
    signingDescription.ifPresent(resultBuilder::setSigningDescription);
    return resultBuilder.build();
  }

  private static SplitApkMetadata createSplitApkMetadata(ModuleSplit split) {
    return SplitApkMetadata.newBuilder()
        .setSplitId(split.getAndroidManifest().getSplitId().orElse(""))
        .setIsMasterSplit(split.isMasterSplit())
        .build();
  }

  private static SystemApkMetadata createSystemApkMetadata(ModuleSplit split) {
    return SystemApkMetadata.newBuilder()
        .addAllFusedModuleName(split.getAndroidManifest().getFusedModuleNames())
        .build();
  }

  private static StandaloneApkMetadata createStandaloneApkMetadata(ModuleSplit split) {
    return StandaloneApkMetadata.newBuilder()
        .addAllFusedModuleName(split.getAndroidManifest().getFusedModuleNames())
        .build();
  }

  private static ApexApkMetadata createApexApkMetadata(ModuleSplit split) {
    return ApexApkMetadata.newBuilder()
        .addAllApexEmbeddedApkConfig(split.getApexEmbeddedApkConfigs())
        .build();
  }

  private ApkDescriptionHelper() {}
}
