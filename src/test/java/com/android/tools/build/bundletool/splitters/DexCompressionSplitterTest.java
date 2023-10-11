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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_O_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DexCompressionSplitterTest {

  @Test
  public void dexCompressionSplitter_withP_withDexFiles() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    ImmutableCollection<ModuleSplit> splits =
        dexCompressionSplitter.split(
            ModuleSplit.forDex(
                createModuleWithDexFile(), variantSdkTargeting(ANDROID_P_API_VERSION)));

    assertThat(splits).hasSize(1);

    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_P_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("dex/classes.dex");
    assertThat(moduleSplit.isMasterSplit()).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "dex/classes.dex")).isTrue();
    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void dexCompressionSplitter_withP_noDexFiles() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    ModuleSplit moduleSplit =
        ModuleSplit.forModule(
            new BundleModuleBuilder("testModule")
                .addFile("lib/x86_64/libsome.so")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build(),
            variantSdkTargeting(ANDROID_P_API_VERSION));

    ImmutableCollection<ModuleSplit> splits = dexCompressionSplitter.split(moduleSplit);

    assertThat(splits).hasSize(1);
    assertThat(splits.asList().get(0)).isEqualTo(moduleSplit);
  }

  @Test
  public void dexCompressionSplitter_withP_otherEntriesCompressionUnchanged() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    BundleModule bundleModule =
        createModuleBuilderWithDexFile()
            .addFile("lib/x86_64/libsome.so")
            .addFile("assets/leftover.txt")
            .build();

    ImmutableCollection<ModuleSplit> splits =
        dexCompressionSplitter.split(
            ModuleSplit.forModule(bundleModule, variantSdkTargeting(ANDROID_P_API_VERSION)));

    assertThat(splits).hasSize(1);

    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_P_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries()))
        .containsExactly("lib/x86_64/libsome.so", "assets/leftover.txt", "dex/classes.dex");
    assertThat(moduleSplit.isMasterSplit()).isTrue();

    assertThat(getForceUncompressed(moduleSplit, "dex/classes.dex")).isTrue();
    assertThat(getForceUncompressed(moduleSplit, "lib/x86_64/libsome.so")).isFalse();
    assertThat(getForceUncompressed(moduleSplit, "assets/leftover.txt")).isFalse();

    assertThat(moduleSplit.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void dexCompressionSplitter_preP_withDexFiles() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    ImmutableCollection<ModuleSplit> splits =
        dexCompressionSplitter.split(
            ModuleSplit.forDex(
                createModuleWithDexFile(), variantSdkTargeting(ANDROID_O_API_VERSION)));

    assertThat(splits).hasSize(1);

    ModuleSplit moduleSplit = Iterables.getOnlyElement(splits);

    assertThat(moduleSplit.getVariantTargeting())
        .isEqualTo(variantSdkTargeting(ANDROID_O_API_VERSION));

    assertThat(extractPaths(moduleSplit.getEntries())).containsExactly("dex/classes.dex");
    assertThat(getForceUncompressed(moduleSplit, "dex/classes.dex")).isFalse();
    assertThat(splits.asList().get(0)).isEqualTo(moduleSplit);
  }

  private static boolean getForceUncompressed(ModuleSplit moduleSplit, String path) {
    return moduleSplit.findEntry(path).get().getForceUncompressed();
  }

  /** Creates a minimal module with single dex file. */
  private static BundleModule createModuleWithDexFile(ManifestMutator... manifestMutators)
      throws Exception {
    return createModuleBuilderWithDexFile(manifestMutators).build();
  }

  private static BundleModuleBuilder createModuleBuilderWithDexFile(
      ManifestMutator... manifestMutators) {
    return new BundleModuleBuilder("testModule")
        .addFile("dex/classes.dex")
        .setManifest(androidManifest("com.test.app", manifestMutators));
  }
}
