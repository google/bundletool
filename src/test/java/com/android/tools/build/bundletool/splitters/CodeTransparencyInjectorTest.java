/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.io.ByteSource;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CodeTransparencyInjectorTest {

  @Test
  public void mainSplitOfTheBaseModule_transparencyFileInjected() {
    CodeTransparencyInjector injector =
        new CodeTransparencyInjector(getAppBundleBuilder(/* minSdkVersion= */ 28).build());
    ModuleSplit moduleSplit =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .build();

    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getTransparencyFile(result)).isPresent();
  }

  @Test
  public void notMainSplit_transparencyFileNotInjected() {
    CodeTransparencyInjector injector =
        new CodeTransparencyInjector(getAppBundleBuilder(/* minSdkVersion= */ 28).build());
    ModuleSplit moduleSplit =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setMasterSplit(false)
            .setSplitType(SplitType.SPLIT)
            .build();

    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getTransparencyFile(result)).isEmpty();
  }

  @Test
  public void notBaseModule_transparencyFileNotInjected() {
    CodeTransparencyInjector injector =
        new CodeTransparencyInjector(getAppBundleBuilder(/* minSdkVersion= */ 28).build());
    ModuleSplit moduleSplit =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("config_split"))
            .setMasterSplit(true)
            .setSplitType(SplitType.SPLIT)
            .build();

    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getTransparencyFile(result)).isEmpty();
  }

  @Test
  public void standalone_withDfms_minSdkGreatedThan21_transparencyFileInjected() {
    CodeTransparencyInjector injector =
        new CodeTransparencyInjector(
            getAppBundleBuilder(/* minSdkVersion= */ 28)
                .addModule(
                    new BundleModuleBuilder("DFM")
                        .setManifest(androidManifestForFeature("com.test.app"))
                        .build())
                .build());
    ModuleSplit moduleSplit =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setSplitType(SplitType.STANDALONE)
            .setMasterSplit(false)
            .build();

    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getTransparencyFile(result)).isPresent();
  }

  @Test
  public void standalone_noDfms_minSdk20_transparencyFileInjected() {
    // Setting min SDK version < 21 would prevent transparency file propagation if
    // the bundle had any DFMs.
    CodeTransparencyInjector injector =
        new CodeTransparencyInjector(getAppBundleBuilder(/* minSdkVersion= */ 20).build());
    ModuleSplit moduleSplit =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setSplitType(SplitType.STANDALONE)
            .setMasterSplit(false)
            .build();

    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getTransparencyFile(result)).isPresent();
  }

  @Test
  public void standalone_withDfms_minSdk20_transparencyFileNotInjected() {
    // Setting min SDK version < 21 should prevent transparency file propagation for
    // this bundle, since it has DFM and specifies default
    // DexMergingStrategy.MERGE_IF_NEEDED.
    CodeTransparencyInjector injector =
        new CodeTransparencyInjector(
            getAppBundleBuilder(/* minSdkVersion= */ 20)
                .addModule(
                    new BundleModuleBuilder("DFM")
                        .setManifest(androidManifestForFeature("com.test.app"))
                        .build())
                .build());
    ModuleSplit moduleSplit =
        getModuleSplitBuilder()
            .setModuleName(BundleModuleName.BASE_MODULE_NAME)
            .setSplitType(SplitType.STANDALONE)
            .setMasterSplit(false)
            .build();

    ModuleSplit result = injector.inject(moduleSplit);

    assertThat(getTransparencyFile(result)).isEmpty();
  }

  private static Optional<ModuleEntry> getTransparencyFile(ModuleSplit moduleSplit) {
    return moduleSplit.getEntries().stream()
        .filter(
            moduleEntry ->
                moduleEntry
                    .getPath()
                    .getFileName()
                    .toString()
                    .equals(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
        .findFirst();
  }

  private static ModuleSplit.Builder getModuleSplitBuilder() {
    return ModuleSplit.builder()
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setAndroidManifest(
            AndroidManifest.create(androidManifest("com.test.app"))
                .toEditor()
                .setMinSdkVersion(28)
                .save())
        .setVariantTargeting(VariantTargeting.getDefaultInstance());
  }

  private static AppBundleBuilder getAppBundleBuilder(int minSdkVersion) {
    return new AppBundleBuilder()
        .addMetadataFile(
            BundleMetadata.BUNDLETOOL_NAMESPACE,
            BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
            ByteSource.empty())
        .addModule(
            new BundleModuleBuilder("base")
                .setManifest(
                    AndroidManifest.create(androidManifest("com.test.app"))
                        .toEditor()
                        .setMinSdkVersion(minSdkVersion)
                        .save()
                        .getManifestRoot()
                        .getProto())
                .build());
  }
}
