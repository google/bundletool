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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_P_API_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DexCompressionSplitterTest {

  private static final SdkVersion P_PLUS = sdkVersionFrom(ANDROID_P_API_VERSION);
  private static final SdkVersion L_PLUS = sdkVersionFrom(ANDROID_L_API_VERSION);

  @Test
  public void dexCompressionSplitter_noDexFiles() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    ModuleSplit moduleSplit =
        ModuleSplit.forModule(
            new BundleModuleBuilder("testModule")
                .addFile("lib/x86_64/libsome.so")
                .addFile("assets/leftover.txt")
                .setManifest(androidManifest("com.test.app"))
                .build());

    ImmutableCollection<ModuleSplit> splits = dexCompressionSplitter.split(moduleSplit);

    assertThat(splits).hasSize(1);
    assertThat(splits.asList().get(0)).isEqualTo(moduleSplit);
  }

  @Test
  public void dexCompressionSplitter_withDexFiles() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    ImmutableCollection<ModuleSplit> splits =
        dexCompressionSplitter.split(ModuleSplit.forDex(createModuleWithDexFile()));

    assertThat(splits).hasSize(2);

    ImmutableMap<SdkVersionTargeting, ModuleSplit> splitByTargeting =
        Maps.uniqueIndex(splits, module -> module.getVariantTargeting().getSdkVersionTargeting());

    assertThat(splitByTargeting).containsKey(sdkVersionTargeting(P_PLUS, ImmutableSet.of(L_PLUS)));
    assertThat(splitByTargeting).containsKey(sdkVersionTargeting(L_PLUS, ImmutableSet.of(P_PLUS)));

    ModuleSplit moduleSplitPPlus =
        splitByTargeting.get(sdkVersionTargeting(P_PLUS, ImmutableSet.of(L_PLUS)));
    assertThat(extractPaths(moduleSplitPPlus.getEntries())).containsExactly("dex/classes.dex");
    assertThat(moduleSplitPPlus.isMasterSplit()).isTrue();
    assertThat(getCompression(moduleSplitPPlus, "dex/classes.dex")).isFalse();
    assertThat(moduleSplitPPlus.getApkTargeting()).isEqualToDefaultInstance();

    ModuleSplit moduleSplitLPlus =
        splitByTargeting.get(sdkVersionTargeting(L_PLUS, ImmutableSet.of(P_PLUS)));
    assertThat(extractPaths(moduleSplitLPlus.getEntries())).containsExactly("dex/classes.dex");
    assertThat(moduleSplitLPlus.isMasterSplit()).isTrue();
    assertThat(getCompression(moduleSplitLPlus, "dex/classes.dex")).isTrue();
    assertThat(moduleSplitLPlus.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void dexCompressionSplitter_targetsPPlus() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    ImmutableCollection<ModuleSplit> splits =
        dexCompressionSplitter.split(
            ModuleSplit.forDex(createModuleWithDexFile(withMinSdkVersion(ANDROID_P_API_VERSION))));

    assertThat(splits).hasSize(1);

    ModuleSplit moduleSplitPPlus = splits.asList().get(0);
    assertThat(extractPaths(moduleSplitPPlus.getEntries())).containsExactly("dex/classes.dex");
    assertThat(moduleSplitPPlus.isMasterSplit()).isTrue();
    assertThat(getCompression(moduleSplitPPlus, "dex/classes.dex")).isFalse();
    assertThat(moduleSplitPPlus.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(moduleSplitPPlus.isMasterSplit()).isTrue();
    assertThat(moduleSplitPPlus.getVariantTargeting().getSdkVersionTargeting())
        .isEqualTo(sdkVersionTargeting(P_PLUS));
    assertThat(moduleSplitPPlus.getApkTargeting()).isEqualToDefaultInstance();
  }

  @Test
  public void dexCompressionSplitter_otherEntriesCompressionUnchanged() throws Exception {
    DexCompressionSplitter dexCompressionSplitter = new DexCompressionSplitter();
    BundleModule bundleModule =
        createModuleBuilderWithDexFile()
            .addFile("lib/x86_64/libsome.so")
            .addFile("assets/leftover.txt")
            .build();

    ImmutableCollection<ModuleSplit> splits =
        dexCompressionSplitter.split(ModuleSplit.forModule(bundleModule));

    assertThat(splits).hasSize(2);

    Map<SdkVersionTargeting, ModuleSplit> splitByTargeting =
        Maps.uniqueIndex(splits, module -> module.getVariantTargeting().getSdkVersionTargeting());

    ModuleSplit moduleSplitPPlus =
        splitByTargeting.get(sdkVersionTargeting(P_PLUS, ImmutableSet.of(L_PLUS)));
    assertThat(extractPaths(moduleSplitPPlus.getEntries()))
        .containsExactly("lib/x86_64/libsome.so", "assets/leftover.txt", "dex/classes.dex");
    assertThat(moduleSplitPPlus.isMasterSplit()).isTrue();

    assertThat(getCompression(moduleSplitPPlus, "dex/classes.dex")).isFalse();
    assertThat(getCompression(moduleSplitPPlus, "lib/x86_64/libsome.so")).isTrue();
    assertThat(getCompression(moduleSplitPPlus, "assets/leftover.txt")).isTrue();

    assertThat(moduleSplitPPlus.getApkTargeting()).isEqualToDefaultInstance();

    ModuleSplit moduleSplitLPlus =
        splitByTargeting.get(sdkVersionTargeting(L_PLUS, ImmutableSet.of(P_PLUS)));
    assertThat(extractPaths(moduleSplitLPlus.getEntries()))
        .containsExactly("lib/x86_64/libsome.so", "assets/leftover.txt", "dex/classes.dex");
    assertThat(moduleSplitLPlus.isMasterSplit()).isTrue();

    assertThat(getCompression(moduleSplitLPlus, "dex/classes.dex")).isTrue();
    assertThat(getCompression(moduleSplitLPlus, "lib/x86_64/libsome.so")).isTrue();
    assertThat(getCompression(moduleSplitLPlus, "assets/leftover.txt")).isTrue();

    assertThat(moduleSplitLPlus.getApkTargeting()).isEqualToDefaultInstance();
  }

  private static boolean getCompression(ModuleSplit moduleSplit, String path) {
    return moduleSplit.findEntry(path).get().shouldCompress();
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
