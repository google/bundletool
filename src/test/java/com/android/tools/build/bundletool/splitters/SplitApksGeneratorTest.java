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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SplitApksGeneratorTest {

  private static final Version BUNDLETOOL_VERSION = BundleToolVersion.getCurrentVersion();

  @Test
  public void simpleMultipleModules() throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        new SplitApksGenerator(
                bundleModule, BUNDLETOOL_VERSION, ApkGenerationConfiguration.getDefaultInstance())
            .generateSplits();

    assertThat(moduleSplits).hasSize(2);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getModuleName().getName());

    ModuleSplit baseModule = moduleSplitMap.get("base");
    assertThat(baseModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseModule.getEntries())).containsExactly("assets/leftover.txt");
    assertThat(baseModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(testModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testModule.getEntries())).containsExactly("assets/test.txt");
    assertThat(testModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
  }

  @Test
  public void multipleModules_withOnlyBaseModuleWithNativeLibraries() throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        new SplitApksGenerator(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder()
                    .setEnableNativeLibraryCompressionSplitter(true)
                    .build())
            .generateSplits();

    VariantTargeting lVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_L_API_VERSION,
            /* alternativeSdkVersions= */ ANDROID_M_API_VERSION);
    VariantTargeting mVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_M_API_VERSION,
            /* alternativeSdkVersions= */ ANDROID_L_API_VERSION);

    // 2 splits for L and M variants.
    assertThat(moduleSplits).hasSize(4);
    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(lVariantTargeting, mVariantTargeting);

    ModuleSplit baseLModule = getModuleSplit(moduleSplits, lVariantTargeting, "base");
    assertThat(baseLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseLModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(isCompressed(baseLModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testLModule = getModuleSplit(moduleSplits, lVariantTargeting, "test");
    assertThat(testLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testLModule.getEntries())).containsExactly("assets/test.txt");

    ModuleSplit baseMModule = getModuleSplit(moduleSplits, mVariantTargeting, "base");
    assertThat(baseMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseMModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(isCompressed(baseMModule, "lib/x86_64/libsome.so")).isFalse();

    ModuleSplit testMModule = getModuleSplit(moduleSplits, mVariantTargeting, "test");
    assertThat(testMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testMModule.getEntries())).containsExactly("assets/test.txt");
  }

  @Test
  public void multipleModules_withOnlyBaseModuleWithNativeLibrariesOtherModuleWithDexFiles()
      throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .addFile("dex/classes.dex")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        new SplitApksGenerator(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder()
                    .setEnableNativeLibraryCompressionSplitter(true)
                    .setEnableDexCompressionSplitter(true)
                    .build())
            .generateSplits();

    VariantTargeting lVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_L_API_VERSION,
            /* alternativeSdkVersions= */ ANDROID_M_API_VERSION,
            ANDROID_P_API_VERSION);
    VariantTargeting mVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_M_API_VERSION,
            /* alternativeSdkVersions= */ ANDROID_L_API_VERSION,
            ANDROID_P_API_VERSION);
    VariantTargeting pVariantTargeting =
        variantMinSdkTargeting(
            /* minSdkVersion= */ ANDROID_P_API_VERSION,
            /* alternativeSdkVersions= */ ANDROID_L_API_VERSION,
            ANDROID_M_API_VERSION);

    // 2 splits for L, M, P variants.
    assertThat(moduleSplits).hasSize(6);
    assertThat(
            moduleSplits.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableSet()))
        .containsExactly(lVariantTargeting, mVariantTargeting, pVariantTargeting);

    ModuleSplit baseLModule = getModuleSplit(moduleSplits, lVariantTargeting, "base");
    assertThat(baseLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseLModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(isCompressed(baseLModule, "lib/x86_64/libsome.so")).isTrue();

    ModuleSplit testLModule = getModuleSplit(moduleSplits, lVariantTargeting, "test");
    assertThat(testLModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testLModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(isCompressed(testLModule, "dex/classes.dex")).isTrue();

    ModuleSplit baseMModule = getModuleSplit(moduleSplits, mVariantTargeting, "base");
    assertThat(baseMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(baseMModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(isCompressed(baseMModule, "lib/x86_64/libsome.so")).isFalse();

    ModuleSplit testMModule = getModuleSplit(moduleSplits, mVariantTargeting, "test");
    assertThat(testMModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testMModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(isCompressed(testMModule, "dex/classes.dex")).isTrue();

    ModuleSplit basePModule = getModuleSplit(moduleSplits, pVariantTargeting, "base");
    assertThat(basePModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(basePModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(isCompressed(basePModule, "lib/x86_64/libsome.so")).isFalse();

    ModuleSplit testPModule = getModuleSplit(moduleSplits, pVariantTargeting, "test");
    assertThat(testPModule.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(extractPaths(testPModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(isCompressed(testPModule, "dex/classes.dex")).isFalse();
  }

  @Test
  public void
      multipleModules_withinstantAppOnlyBaseModuleWithNativeLibrariesOtherModuleWithDexFiles()
          throws Exception {

    ImmutableList<BundleModule> bundleModule =
        ImmutableList.of(
            new BundleModuleBuilder("base")
                .addFile("assets/leftover.txt")
                .addFile("lib/x86_64/libsome.so")
                .setManifest(androidManifest("com.test.app"))
                .setNativeConfig(
                    nativeLibraries(
                        targetedNativeDirectory(
                            "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
                .build(),
            new BundleModuleBuilder("test")
                .addFile("assets/test.txt")
                .addFile("dex/classes.dex")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableList<ModuleSplit> moduleSplits =
        new SplitApksGenerator(
                bundleModule,
                BUNDLETOOL_VERSION,
                ApkGenerationConfiguration.builder()
                    .setEnableNativeLibraryCompressionSplitter(true)
                    .setEnableDexCompressionSplitter(true)
                    .setForInstantAppVariants(true)
                    .build())
            .generateSplits();

    // 2 splits for L variant
    assertThat(moduleSplits).hasSize(2);
    ImmutableMap<String, ModuleSplit> moduleSplitMap =
        Maps.uniqueIndex(moduleSplits, split -> split.getModuleName().getName());

    ModuleSplit baseModule = moduleSplitMap.get("base");
    assertThat(baseModule.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(extractPaths(baseModule.getEntries()))
        .containsExactly("assets/leftover.txt", "lib/x86_64/libsome.so");
    assertThat(baseModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(isCompressed(baseModule, "lib/x86_64/libsome.so")).isFalse();

    ModuleSplit testModule = moduleSplitMap.get("test");
    assertThat(testModule.getSplitType()).isEqualTo(SplitType.INSTANT);
    assertThat(extractPaths(testModule.getEntries()))
        .containsExactly("assets/test.txt", "dex/classes.dex");
    assertThat(testModule.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(isCompressed(testModule, "dex/classes.dex")).isTrue();
  }

  private static ModuleSplit getModuleSplit(
      ImmutableList<ModuleSplit> moduleSplits,
      VariantTargeting variantTargeting,
      String moduleName) {
    return moduleSplits.stream()
        .filter(moduleSplit -> moduleSplit.getVariantTargeting().equals(variantTargeting))
        .filter(moduleSplit -> moduleSplit.getModuleName().getName().equals(moduleName))
        .findFirst()
        .get();
  }

  private static boolean isCompressed(ModuleSplit moduleSplit, String path) {
    return moduleSplit.findEntry(path).get().shouldCompress();
  }
}
