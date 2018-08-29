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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.android.tools.build.bundletool.utils.Versions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NativeLibrariesCompressionSplitterTest {

  SdkVersion sdkVersionMPlus = sdkVersionFrom(Versions.ANDROID_M_API_VERSION);
  SdkVersion sdkVersionLPlus = sdkVersionFrom(Versions.ANDROID_L_API_VERSION);

  private static final AndroidManifest DEFAULT_MANIFEST =
      AndroidManifest.create(androidManifest("com.test.app"));

  @Test
  public void splittingByCompression() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule("testModule", "x86", "lib/x86/libnoname.so")));

    assertThat(splits).hasSize(2);

    ImmutableMap<SdkVersionTargeting, ModuleSplit> splitByTargeting =
        Maps.uniqueIndex(splits, module -> module.getVariantTargeting().getSdkVersionTargeting());

    assertThat(splitByTargeting)
        .containsKey(sdkVersionTargeting(sdkVersionMPlus, ImmutableSet.of(sdkVersionLPlus)));
    assertThat(splitByTargeting)
        .containsKey(sdkVersionTargeting(sdkVersionLPlus, ImmutableSet.of(sdkVersionMPlus)));

    ModuleSplit moduleSplitMPlus =
        splitByTargeting.get(
            sdkVersionTargeting(sdkVersionMPlus, ImmutableSet.of(sdkVersionLPlus)));
    assertThat(extractPaths(moduleSplitMPlus.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplitMPlus.isMasterSplit()).isTrue();
    ImmutableList<Boolean> compressionMPlus = getShouldCompress(moduleSplitMPlus);
    assertThat(compressionMPlus).containsExactly(false);
    assertThat(moduleSplitMPlus.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplitMPlus.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();

    ModuleSplit moduleSplitLPlus =
        splitByTargeting.get(
            sdkVersionTargeting(sdkVersionLPlus, ImmutableSet.of(sdkVersionMPlus)));
    assertThat(extractPaths(moduleSplitMPlus.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplitLPlus.isMasterSplit()).isTrue();
    ImmutableList<Boolean> compressionLPlus = getShouldCompress(moduleSplitLPlus);
    assertThat(compressionLPlus).containsExactly(true);
    assertThat(moduleSplitLPlus.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplitLPlus.getMasterManifestMutators(), withExtractNativeLibs(true)))
        .isTrue();
  }

  @Test
  public void splittingByCompressionTargetsMPlus() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter();
    ImmutableCollection<ModuleSplit> splits =
        nativeLibrariesCompressionSplitter.split(
            ModuleSplit.forNativeLibraries(
                createSingleLibraryModule(
                    "testModule",
                    "x86",
                    "lib/x86/libnoname.so",
                    withMinSdkVersion(Versions.ANDROID_M_API_VERSION))));

    assertThat(splits).hasSize(1);
    ModuleSplit moduleSplitMPlus = splits.asList().get(0);
    assertThat(extractPaths(moduleSplitMPlus.getEntries())).containsExactly("lib/x86/libnoname.so");
    assertThat(moduleSplitMPlus.isMasterSplit()).isTrue();
    assertThat(moduleSplitMPlus.getVariantTargeting().getSdkVersionTargeting())
        .isEqualTo(sdkVersionTargeting(sdkVersionMPlus));
    assertThat(moduleSplitMPlus.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplitMPlus.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();

    ImmutableList<Boolean> compressionMPlus = getShouldCompress(moduleSplitMPlus);
    assertThat(compressionMPlus).containsExactly(false);
  }

  @Test
  public void otherEntriesCompressionUnchanged() throws Exception {
    NativeLibrariesCompressionSplitter nativeLibrariesCompressionSplitter =
        new NativeLibrariesCompressionSplitter();

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
        nativeLibrariesCompressionSplitter.split(ModuleSplit.forModule(bundleModule));

    assertThat(splits).hasSize(2);

    Map<SdkVersionTargeting, ModuleSplit> splitByTargeting =
        Maps.uniqueIndex(splits, module -> module.getVariantTargeting().getSdkVersionTargeting());

    ModuleSplit moduleSplitMPlus =
        splitByTargeting.get(
            sdkVersionTargeting(sdkVersionMPlus, ImmutableSet.of(sdkVersionLPlus)));
    assertThat(extractPaths(moduleSplitMPlus.getEntries()))
        .containsExactly("lib/x86_64/libsome.so", "assets/leftover.txt", "dex/classes.dex");
    assertThat(moduleSplitMPlus.isMasterSplit()).isTrue();

    assertThat(getEntry(moduleSplitMPlus.getEntries(), "lib/x86_64/libsome.so").shouldCompress())
        .isFalse();
    assertThat(getEntry(moduleSplitMPlus.getEntries(), "assets/leftover.txt").shouldCompress())
        .isTrue();
    assertThat(getEntry(moduleSplitMPlus.getEntries(), "dex/classes.dex").shouldCompress())
        .isTrue();

    assertThat(moduleSplitMPlus.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplitMPlus.getMasterManifestMutators(), withExtractNativeLibs(false)))
        .isTrue();

    ModuleSplit moduleSplitLPlus =
        splitByTargeting.get(
            sdkVersionTargeting(sdkVersionLPlus, ImmutableSet.of(sdkVersionMPlus)));
    assertThat(extractPaths(moduleSplitLPlus.getEntries()))
        .containsExactly("lib/x86_64/libsome.so", "assets/leftover.txt", "dex/classes.dex");
    assertThat(moduleSplitLPlus.isMasterSplit()).isTrue();

    assertThat(getEntry(moduleSplitLPlus.getEntries(), "lib/x86_64/libsome.so").shouldCompress())
        .isTrue();
    assertThat(getEntry(moduleSplitLPlus.getEntries(), "assets/leftover.txt").shouldCompress())
        .isTrue();
    assertThat(getEntry(moduleSplitLPlus.getEntries(), "dex/classes.dex").shouldCompress())
        .isTrue();
    assertThat(moduleSplitLPlus.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(
            compareManifestMutators(
                moduleSplitLPlus.getMasterManifestMutators(), withExtractNativeLibs(true)))
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

  private ImmutableList<Boolean> getShouldCompress(ImmutableCollection<ModuleEntry> entryList) {
    return entryList
        .stream()
        .map(entry -> entry.shouldCompress())
        .distinct()
        .collect(toImmutableList());
  }

  private ImmutableList<Boolean> getShouldCompress(ModuleSplit moduleSplit) {
    return getShouldCompress(moduleSplit.getEntries());
  }

  /**
   * Compares manifest mutators by applying the mutators against same manifests and comparing the
   * edited manifest, as we can't compare two mutators(lambda expressions) directly.
   */
  private boolean compareManifestMutators(
      ImmutableList<com.android.tools.build.bundletool.model.ManifestMutator> manifestMutators,
      com.android.tools.build.bundletool.model.ManifestMutator otherManifestMutator) {

    return DEFAULT_MANIFEST
        .applyMutators(manifestMutators)
        .equals(DEFAULT_MANIFEST.applyMutators(ImmutableList.of(otherManifestMutator)));
  }
}
