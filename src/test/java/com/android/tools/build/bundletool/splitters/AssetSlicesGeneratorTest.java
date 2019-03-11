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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_ASSET_VALUE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTypeAttribute;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AssetSlicesGeneratorTest {

  @Test
  public void singleAssetModule() throws Exception {
    ImmutableList<BundleModule> modules =
        ImmutableList.of(
            new BundleModuleBuilder("asset_module")
                .addFile("assets/some_asset.txt")
                .setManifest(
                    androidManifest("com.test.app", withTypeAttribute(MODULE_TYPE_ASSET_VALUE)))
                .build());

    ImmutableList<ModuleSplit> assetSlices =
        new AssetSlicesGenerator(modules, ApkGenerationConfiguration.getDefaultInstance())
            .generateAssetSlices();

    assertThat(assetSlices).hasSize(1);

    ModuleSplit assetSlice = assetSlices.get(0);

    assertThat(assetSlice.getModuleName().getName()).isEqualTo("asset_module");
    assertThat(assetSlice.getSplitType()).isEqualTo(SplitType.ASSET_SLICE);
    assertThat(assetSlice.isMasterSplit()).isTrue();
    assertThat(assetSlice.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(extractPaths(assetSlice.getEntries())).containsExactly("assets/some_asset.txt");
  }
}
