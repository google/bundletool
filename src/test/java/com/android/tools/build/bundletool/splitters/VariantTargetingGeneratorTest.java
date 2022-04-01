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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VariantTargetingGeneratorTest {

  @Test
  public void generateVariantTargetings_combinesVariantTargetingsFromMultipleModules() {
    VariantTargetingGenerator variantTargetingGenerator =
        new VariantTargetingGenerator(new PerModuleVariantTargetingGenerator());
    XmlNode androidManifest = androidManifest("com.test.app");
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    BundleModule singleLibraryAndDexModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest)
            .setNativeConfig(nativeConfig)
            .addFile("lib/x86/lib.so")
            .build();
    BundleModule singleDexModule =
        new BundleModuleBuilder("testModule2")
            .setManifest(androidManifest)
            .addFile("dex/classes.dex")
            .build();
    ApkGenerationConfiguration apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setEnableDexCompressionSplitter(true)
            .setEnableUncompressedNativeLibraries(true)
            .build();

    ImmutableSet<VariantTargeting> splits =
        variantTargetingGenerator.generateVariantTargetings(
            ImmutableList.of(singleLibraryAndDexModule, singleDexModule),
            apkGenerationConfiguration);

    assertThat(splits)
        .comparingExpectedFieldsOnly()
        .containsExactly(
            // L+ variant is always generated.
            lPlusVariantTargeting(),
            // M+ variant generated for the module with the native library.
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            // Q+ variant generated for the module with the dex file.
            variantMinSdkTargeting(ANDROID_Q_API_VERSION));
  }
}
