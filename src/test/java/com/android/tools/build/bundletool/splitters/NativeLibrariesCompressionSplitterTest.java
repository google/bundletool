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

import static com.android.tools.build.bundletool.model.ManifestMutator.withExtractNativeLibs;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withNativeActivity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NativeLibrariesCompressionSplitterTest {

  private static final AndroidManifest DEFAULT_MANIFEST =
      AndroidManifest.create(androidManifest("com.test.app"));

  @Test
  public void nativeCompressionSplitter_withM_withLibsWithoutExternalStorage() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"),
                variantSdkTargeting(ANDROID_M_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_M_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isTrue();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();
  }

  @Test
  public void nativeCompressionSplitter_withM_withNativeActivities() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule(
                    "testModule", "x86", "lib/x86/libnoname.so", withNativeActivity("noname")),
                variantSdkTargeting(ANDROID_M_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_M_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isFalse();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void nativeCompressionSplitter_withM_withMainLibrary() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libmain.so"),
                variantSdkTargeting(ANDROID_M_API_VERSION)));

    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libmain.so")).isFalse();
  }

  @Test
  public void nativeCompressionSplitter_withN_withNativeActivities() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule(
                    "testModule", "x86", "lib/x86/libnoname.so", withNativeActivity("noname")),
                variantSdkTargeting(ANDROID_N_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_N_API_VERSION));
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isTrue();
  }

  @Test
  public void nativeCompressionSplitter_withP_withLibsWithExternalStorage() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setInstallableOnExternalStorage(true)
                .build());
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"),
                variantSdkTargeting(ANDROID_P_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_P_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isTrue();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();
  }

  @Test
  public void nativeCompressionSplitter_withM_withoutLibs() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();
    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("dex/classes.dex")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forModule(bundleModule, variantSdkTargeting(ANDROID_M_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_M_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("dex/classes.dex");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "dex/classes.dex")).isFalse();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void otherEntriesCompressionUnchanged() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();

    BundleModule bundleModule =
        new BundleModuleBuilder("testModule")
            .addFile("lib/x86_64/libsome.so")
            .addFile("assets/leftover.txt")
            .addFile("dex/classes.dex")
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory(
                        "lib/x86_64", nativeDirectoryTargeting(AbiAlias.X86_64))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forModule(bundleModule, variantSdkTargeting(ANDROID_M_API_VERSION)));

    assertThat(splits).hasSize(1);

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_M_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries()))
        .containsExactly("lib/x86_64/libsome.so", "assets/leftover.txt", "dex/classes.dex");
    assertThat(moduleSplit.isMasterSplit()).isTrue();

    assertThat(getEntry(moduleSplit.getEntries(), "lib/x86_64/libsome.so").getForceUncompressed())
        .isTrue();
    assertThat(getEntry(moduleSplit.getEntries(), "assets/leftover.txt").getForceUncompressed())
        .isFalse();
    assertThat(getEntry(moduleSplit.getEntries(), "dex/classes.dex").getForceUncompressed())
        .isFalse();

    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();
  }

  @Test
  public void nativeCompressionSplitter_preM_withLibsWithoutExternalStorage() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        createSplitterWithEnabledUncompressedLibraries();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"),
                lPlusVariantTargeting()));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isFalse();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(true)))
        .isTrue();
  }

  @Test
  public void nativeCompressionSplitter_preP_withLibsWithoutExternalStorage() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setInstallableOnExternalStorage(true)
                .build());
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"),
                variantMinSdkTargeting(Versions.ANDROID_O_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantMinSdkTargeting(Versions.ANDROID_O_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isFalse();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(true)))
        .isTrue();
  }

  @Test
  public void splittingByCompression_preM_instantModule() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(true)
                .setForInstantAppVariants(true)
                .build());

    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"),
                lPlusVariantTargeting()));

    assertThat(splits).hasSize(1);

    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting()).isEqualTo(lPlusVariantTargeting());
    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isTrue();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();
  }

  @Test
  public void nativeCompressionSplitter_withP_disabledUncompressedNativeLibs() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter(
            ApkGenerationConfiguration.builder()
                .setEnableUncompressedNativeLibraries(false)
                .build());
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so"),
                variantSdkTargeting(ANDROID_P_API_VERSION)));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_P_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86/libnoname.so")).isFalse();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplit.getMasterManifestMutators(), withExtractNativeLibs(true)))
        .isTrue();
  }

  private static ModuleEntry getEntry(ImmutableList<ModuleEntry> moduleEntries, String path) {
    return moduleEntries.get(extractPaths(moduleEntries).indexOf(path));
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

  private static boolean getForceUncompressed(ModuleSplit moduleSplit, String path) {
    return moduleSplit.findEntry(path).get().getForceUncompressed();
  }

  /**
   * Compares manifest mutators by applying the mutators against same manifests and comparing the
   * edited manifest, as we can't compare two mutators(lambda expressions) directly.
   */
  private static boolean compareManifestMutators(
      ImmutableList<com.android.tools.build.bundletool.model.ManifestMutator> manifestMutators,
      com.android.tools.build.bundletool.model.ManifestMutator otherManifestMutator) {

    return DEFAULT_MANIFEST
        .applyMutators(manifestMutators)
        .equals(DEFAULT_MANIFEST.applyMutators(ImmutableList.of(otherManifestMutator)));
  }

  private static NativeLibrariesCompressionSplitter
      createSplitterWithEnabledUncompressedLibraries() {
    return new NativeLibrariesCompressionSplitter(
        ApkGenerationConfiguration.builder().setEnableUncompressedNativeLibraries(true).build());
  }
}
