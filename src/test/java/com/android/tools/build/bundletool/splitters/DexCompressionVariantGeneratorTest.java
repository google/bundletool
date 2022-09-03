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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DexCompressionVariantGeneratorTest {
  @Test
  public void variantsGeneration_withDexFile_generatesQVariant() throws Exception {
    DexCompressionVariantGenerator dexCompressionVariantGenerator =
        new DexCompressionVariantGenerator(
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build());
    ImmutableCollection<VariantTargeting> splits =
        dexCompressionVariantGenerator
            .generate(createModuleWithDexFile())
            .collect(toImmutableList());
    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_Q_API_VERSION));
  }

  @Test
  public void variantsGeneration_withDexFile_generatesSVariant() throws Exception {
    DexCompressionVariantGenerator dexCompressionVariantGenerator =
        new DexCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setEnableDexCompressionSplitter(true)
                .setDexCompressionSplitterForTargetSdk(UncompressedDexTargetSdk.SDK_31)
                .build());
    ImmutableList<VariantTargeting> splits =
        dexCompressionVariantGenerator
            .generate(createModuleWithDexFile())
            .collect(toImmutableList());
    // If UncompressedDexTargetSdk and EnableDexCompressionSplitter are both set, we create an S+
    // variant.
    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_S_API_VERSION));
  }

  @Test
  public void variantsGeneration_withDexFile_doesNotGenerateSVariant() throws Exception {
    DexCompressionVariantGenerator dexCompressionVariantGenerator =
        new DexCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setDexCompressionSplitterForTargetSdk(UncompressedDexTargetSdk.SDK_31)
                .build());
    ImmutableList<VariantTargeting> splits =
        dexCompressionVariantGenerator
            .generate(createModuleWithDexFile())
            .collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  @Test
  public void variantsGeneration_withDexFileAnddexFileOptimizationDisabled_generatesNoVariant()
      throws Exception {
    DexCompressionVariantGenerator dexCompressionVariantGenerator =
        new DexCompressionVariantGenerator(ApkGenerationConfiguration.getDefaultInstance());

    ImmutableCollection<VariantTargeting> splits =
        dexCompressionVariantGenerator
            .generate(createModuleWithDexFile())
            .collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  @Test
  public void variantsGeneration_withoutDexFile_generatesNoVariant() throws Exception {
    DexCompressionVariantGenerator dexCompressionVariantGenerator =
        new DexCompressionVariantGenerator(
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build());

    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/leftover.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<VariantTargeting> splits =
        dexCompressionVariantGenerator.generate(bundleModule).collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  @Test
  public void variantsGeneration_withInstantModule_generatesNoVariant() throws Exception {
    DexCompressionVariantGenerator dexCompressionVariantGenerator =
        new DexCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setForInstantAppVariants(true)
                .setEnableDexCompressionSplitter(true)
                .build());
    ImmutableCollection<VariantTargeting> splits =
        dexCompressionVariantGenerator
            .generate(createModuleWithDexFile())
            .collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  /** Creates a minimal module with single dex file. */
  private static BundleModule createModuleWithDexFile(ManifestMutator... manifestMutators)
      throws IOException {
    return new BundleModuleBuilder("testModule")
        .addFile("dex/classes.dex")
        .setManifest(androidManifest("com.test.app", manifestMutators))
        .build();
  }
}
