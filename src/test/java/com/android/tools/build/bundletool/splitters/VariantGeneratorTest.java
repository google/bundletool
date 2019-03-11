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
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMaxSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VariantGeneratorTest {
  @Test
  public void targetsPreLOnlyInManifest_throws() throws Exception {
    int preL = 20;
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .setManifest(androidManifest("com.test.app", withMaxSdkVersion(preL)))
            .build();

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> new VariantGenerator(bundleModule).generateVariants());

    assertThat(exception)
        .hasMessageThat()
        .contains("does not target devices on Android L or above");
  }

  @Test
  public void variantsWithNativeLibsDexFiles_allOptimizationsDisabled() throws Exception {
    VariantGenerator variantGenerator = new VariantGenerator(createSingleLibraryDexModule());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits).containsExactly(lPlusVariantTargeting());
  }

  @Test
  public void variantsWithNativeLibsDexFiles_withNativeLibsOptimization() throws Exception {

    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModule(),
            ApkGenerationConfiguration.builder()
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(lPlusVariantTargeting(), variantMinSdkTargeting(ANDROID_M_API_VERSION));
  }

  @Test
  public void variantsWithNativeLibsDexFiles_withDexOptimization() throws Exception {
    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModule(),
            ApkGenerationConfiguration.builder().setEnableDexCompressionSplitter(true).build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(lPlusVariantTargeting(), variantMinSdkTargeting(ANDROID_P_API_VERSION));
  }

  @Test
  public void variantsWithNativeLibsDexFiles_withNativeLibsAndDexOptimization() throws Exception {

    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModule(),
            ApkGenerationConfiguration.builder()
                .setEnableDexCompressionSplitter(true)
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(
            lPlusVariantTargeting(),
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            variantMinSdkTargeting(ANDROID_P_API_VERSION));
  }

  @Test
  public void variantsWithAllOptimizations_minSdkAffectsLPlusVariant() throws Exception {

    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModuleMinSdk(22),
            ApkGenerationConfiguration.builder()
                .setEnableDexCompressionSplitter(true)
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(
            variantMinSdkTargeting(22),
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            variantMinSdkTargeting(ANDROID_P_API_VERSION));
  }

  @Test
  public void variantsWithAllOptimizations_minSdkRemovesLPlusVariant() throws Exception {

    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModuleMinSdk(ANDROID_M_API_VERSION),
            ApkGenerationConfiguration.builder()
                .setEnableDexCompressionSplitter(true)
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(
            variantMinSdkTargeting(ANDROID_M_API_VERSION),
            variantMinSdkTargeting(ANDROID_P_API_VERSION));
  }

  @Test
  public void variantsWithAllOptimizations_maxSdkRemovesDexVariant() throws Exception {
    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModuleMaxSdk(ANDROID_P_API_VERSION - 1),
            ApkGenerationConfiguration.builder()
                .setEnableDexCompressionSplitter(true)
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(lPlusVariantTargeting(), variantMinSdkTargeting(ANDROID_M_API_VERSION));
  }

  @Test
  public void variantsWithAllOptimizations_sdkRangeCropsVariants() throws Exception {
    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModuleSdkRange(
                ANDROID_M_API_VERSION - 1, ANDROID_M_API_VERSION + 1),
            ApkGenerationConfiguration.builder()
                .setEnableDexCompressionSplitter(true)
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits)
        .containsExactly(
            variantMinSdkTargeting(ANDROID_M_API_VERSION - 1),
            variantMinSdkTargeting(ANDROID_M_API_VERSION));
  }

  @Test
  public void variantsWithNativeLibsDexFiles_withInstantModule_allOptimizationsDisabled()
      throws Exception {

    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModule(),
            ApkGenerationConfiguration.builder().setForInstantAppVariants(true).build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits).containsExactly(lPlusVariantTargeting());
  }

  @Test
  public void variantsWithNativeLibsDexFiles_withInstantModule_withNativeLibsAndDexOptimization()
      throws Exception {

    VariantGenerator variantGenerator =
        new VariantGenerator(
            createSingleLibraryDexModule(),
            ApkGenerationConfiguration.builder()
                .setForInstantAppVariants(true)
                .setEnableDexCompressionSplitter(true)
                .setEnableNativeLibraryCompressionSplitter(true)
                .build());

    ImmutableCollection<VariantTargeting> splits = variantGenerator.generateVariants();
    assertThat(splits).containsExactly(lPlusVariantTargeting());
  }

  /** Creates a minimal module with one native library and dex files. */
  private static BundleModule createSingleLibraryDexModule() throws Exception {
    return createSingleLibraryDexModule(androidManifest("com.test.app"));
  }

  private static BundleModule createSingleLibraryDexModule(XmlNode androidManifest)
      throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting("x86")));
    return new BundleModuleBuilder("testModule")
        .setManifest(androidManifest)
        .setNativeConfig(nativeConfig)
        .addFile("lib/x86/liba.so")
        .addFile("dex/classes.dex")
        .build();
  }

  private static BundleModule createSingleLibraryDexModuleMinSdk(int minSdkVersion)
      throws Exception {
    return createSingleLibraryDexModule(
        androidManifest("com.test.app", withMinSdkVersion(minSdkVersion)));
  }

  private static BundleModule createSingleLibraryDexModuleMaxSdk(int maxSdkVersion)
      throws Exception {
    return createSingleLibraryDexModule(
        androidManifest("com.test.app", withMaxSdkVersion(maxSdkVersion)));
  }

  private static BundleModule createSingleLibraryDexModuleSdkRange(
      int minSdkVersion, int maxSdkVersion) throws Exception {
    return createSingleLibraryDexModule(
        androidManifest(
            "com.test.app", withMinSdkVersion(minSdkVersion), withMaxSdkVersion(maxSdkVersion)));
  }
}
