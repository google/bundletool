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
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withNativeActivity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NativeLibsCompressionVariantGeneratorTest {
  @Test
  public void variantsWithNativeLibs_withoutExternalStorage() throws Exception {
    NativeLibsCompressionVariantGenerator nativeLibsCompressionVariantGenerator =
        new NativeLibsCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .build());
    ImmutableCollection<VariantTargeting> splits =
        nativeLibsCompressionVariantGenerator
            .generate(createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"))
            .collect(toImmutableList());
    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_M_API_VERSION));
  }

  @Test
  public void variantsWithNativeLibs_withExternalStorage() throws Exception {
    NativeLibsCompressionVariantGenerator nativeLibsCompressionVariantGenerator =
        new NativeLibsCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setInstallableOnExternalStorage(true)
                .build());
    ImmutableCollection<VariantTargeting> splits =
        nativeLibsCompressionVariantGenerator
            .generate(createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"))
            .collect(toImmutableList());
    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_P_API_VERSION));
  }

  @Test
  public void variantsWithNativeLibs_withNativeActivities() throws Exception {
    NativeLibsCompressionVariantGenerator nativeLibsCompressionVariantGenerator =
        new NativeLibsCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .build());
    ImmutableCollection<VariantTargeting> splits =
        nativeLibsCompressionVariantGenerator
            .generate(
                createSingleLibraryModule(
                    "testModule", "x86", "lib/x86/libnoname.so", withNativeActivity("noname")))
            .collect(toImmutableList());
    assertThat(splits).containsExactly(variantMinSdkTargeting(ANDROID_N_API_VERSION));
  }

  @Test
  public void variantsWithNativeLibs_nativeLibsOptimizationDisabled() throws Exception {
    NativeLibsCompressionVariantGenerator nativeLibsCompressionVariantGenerator =
        new NativeLibsCompressionVariantGenerator(ApkGenerationConfiguration.getDefaultInstance());
    ImmutableCollection<VariantTargeting> splits =
        nativeLibsCompressionVariantGenerator
            .generate(createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"))
            .collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  @Test
  public void variantsWithoutNativeLibs() throws Exception {
    NativeLibsCompressionVariantGenerator nativeLibsCompressionVariantGenerator =
        new NativeLibsCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .build());

    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/leftover.txt")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<VariantTargeting> splits =
        nativeLibsCompressionVariantGenerator.generate(bundleModule).collect(toImmutableList());
    assertThat(splits).isEmpty();
  }

  @Test
  public void variantsWithNativeLibs_instantModule() throws Exception {
    NativeLibsCompressionVariantGenerator nativeLibsCompressionVariantGenerator =
        new NativeLibsCompressionVariantGenerator(
            ApkGenerationConfiguration.builder()
                .setForInstantAppVariants(true)
                .setEnableUncompressedNativeLibraries(true)
                .build());

    ImmutableCollection<VariantTargeting> splits =
        nativeLibsCompressionVariantGenerator
            .generate(createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"))
            .collect(toImmutableList());

    assertThat(splits).isEmpty();
  }

  /** Creates a minimal module with one native library targeted at the given cpu architecture. */
  private static BundleModule createSingleLibraryModule(
      String moduleName,
      String architecture,
      String relativeFilePath,
      ManifestMutator... manifestMutators)
      throws Exception {
    NativeLibraries nativeConfig =
        nativeLibraries(
            targetedNativeDirectory("lib/" + architecture, nativeDirectoryTargeting(architecture)));

    return new BundleModuleBuilder(moduleName)
        .addFile(relativeFilePath)
        .setNativeConfig(nativeConfig)
        .setManifest(androidManifest("com.test.app", manifestMutators))
        .build();
  }
}
