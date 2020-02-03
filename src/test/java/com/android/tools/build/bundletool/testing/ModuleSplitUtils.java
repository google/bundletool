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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ManifestMutator;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;

/** Utility to create and manipulate the {@link ModuleSplit}. */
public class ModuleSplitUtils {

  private static final Version CURRENT_VERSION = BundleToolVersion.getCurrentVersion();

  private static final AndroidManifest DEFAULT_MANIFEST =
      AndroidManifest.create(androidManifest("com.test.app"), CURRENT_VERSION);

  /** Creates {@link ModuleSplit.Builder} with fields pre-populated to default values. */
  public static ModuleSplit.Builder createModuleSplitBuilder() {
    return ModuleSplit.builder()
        .setAndroidManifest(DEFAULT_MANIFEST)
        .setEntries(ImmutableList.of())
        .setMasterSplit(true)
        .setModuleName(BundleModuleName.create("module"))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(lPlusVariantTargeting());
  }

  public static ModuleSplit applyManifestMutators(
      ModuleSplit moduleSplit, ImmutableList<ManifestMutator> manifestMutators) {

    return moduleSplit
        .toBuilder()
        .setAndroidManifest(moduleSplit.getAndroidManifest().applyMutators(manifestMutators))
        .build();
  }

  public static AssetsDirectoryTargeting createAssetsDirectoryLanguageTargeting(String lang) {
    return AssetsDirectoryTargeting.newBuilder()
        .setLanguage(LanguageTargeting.newBuilder().addValue(lang).build())
        .build();
  }
}
