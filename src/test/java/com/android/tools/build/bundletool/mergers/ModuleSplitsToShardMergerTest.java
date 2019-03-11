/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDebuggableAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.value;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkMultiAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.Type;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.InMemoryModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ShardedSystemSplits;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class ModuleSplitsToShardMergerTest {

  private static final byte[] DUMMY_CONTENT = new byte[1];
  private static final AndroidManifest DEFAULT_MANIFEST =
      AndroidManifest.create(androidManifest("com.test.app"));

  private static final BundleMetadata NO_MAIN_DEX_LIST = BundleMetadata.builder().build();

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  private final DexMerger d8DexMerger = new D8DexMerger();

  @Before
  public void setUp() {
    tmpDir = tmp.getRoot().toPath();
  }

  @Test
  public void merge_oneSetOfSplits_producesSingleShard() throws Exception {
    ModuleSplit masterSplit = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit x86Split =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("lib/x86/libtest.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .build();

    ImmutableList<ModuleSplit> shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .merge(ImmutableList.of(ImmutableList.of(masterSplit, x86Split)), NO_MAIN_DEX_LIST);

    ModuleSplit shard = getOnlyElement(shards);
    assertThat(shard.getApkTargeting()).isEqualTo(apkAbiTargeting(AbiAlias.X86));
    assertSingleEntryStandaloneShard(shard, "lib/x86/libtest.so");
  }

  @Test
  public void merge_twoSetsOfSplits_producesTwoShards() throws Exception {
    ModuleSplit masterSplit = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit x86Split =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("lib/x86/libtest.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .build();
    ModuleSplit mipsSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("lib/mips/libtest.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.MIPS))
            .build();

    ImmutableList<ModuleSplit> shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .merge(
                ImmutableList.of(
                    ImmutableList.of(masterSplit, x86Split),
                    ImmutableList.of(masterSplit, mipsSplit)),
                NO_MAIN_DEX_LIST);

    assertThat(shards).hasSize(2);
    ImmutableMap<ApkTargeting, ModuleSplit> shardsByTargeting =
        Maps.uniqueIndex(shards, ModuleSplit::getApkTargeting);
    assertSingleEntryStandaloneShard(
        shardsByTargeting.get(apkAbiTargeting(AbiAlias.X86)), "lib/x86/libtest.so");
    assertSingleEntryStandaloneShard(
        shardsByTargeting.get(apkAbiTargeting(AbiAlias.MIPS)), "lib/mips/libtest.so");
  }

  @Test
  public void mergeSystemShard_oneSetsOfSplits_producesOneShard() throws Exception {
    ModuleSplit masterSplit = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit x86Split =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("lib/x86/libtest.so", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .build();
    ModuleSplit esSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/i18n#lang_es/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("es")))
            .setMasterSplit(false)
            .build();
    ModuleSplit frSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/i18n#lang_fr/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("fr")))
            .setMasterSplit(false)
            .build();

    ShardedSystemSplits shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSystemShard(
                ImmutableList.of(masterSplit, x86Split, esSplit, frSplit),
                NO_MAIN_DEX_LIST,
                mergeSpecs(abis("x86"), locales("es")));

    ModuleSplit fusedSplit = shards.getSystemImageSplit();

    assertThat(extractPaths(fusedSplit.getEntries()))
        .containsExactly("lib/x86/libtest.so", "assets/i18n#lang_es/strings.pak");
    assertThat(fusedSplit.getApkTargeting())
        .isEqualTo(mergeApkTargeting(apkAbiTargeting(AbiAlias.X86), apkLanguageTargeting("es")));
    assertThat(fusedSplit.getVariantTargeting()).isEqualToDefaultInstance();
    assertThat(fusedSplit.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(fusedSplit.isBaseModuleSplit()).isTrue();
    assertThat(fusedSplit.getResourceTable()).isEmpty();
    assertThat(fusedSplit.getAndroidManifest().getSplitId()).isEmpty();

    ModuleSplit frLangSplit = Iterables.getOnlyElement(shards.getAdditionalLanguageSplits());

    assertThat(extractPaths(frLangSplit.getEntries()))
        .containsExactly("assets/i18n#lang_fr/strings.pak");
    assertThat(frLangSplit.getApkTargeting()).isEqualTo(apkLanguageTargeting("fr"));
    assertThat(frLangSplit.getSplitType()).isEqualTo(SplitType.SPLIT);
    assertThat(frLangSplit.isMasterSplit()).isFalse();
    assertThat(frLangSplit.getResourceTable()).isEmpty();
    assertThat(frLangSplit.getAndroidManifest().getSplitId()).hasValue("config.fr");
  }

  @Test
  public void mergeSystemShard_multipleModules_producesOneShard() throws Exception {
    ModuleSplit masterSplit = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit esSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/i18n#lang_es/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("es")))
            .setMasterSplit(false)
            .build();
    ModuleSplit frSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/i18n#lang_fr/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("fr")))
            .setMasterSplit(false)
            .build();
    ModuleSplit esVrSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("vr"))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "assets/vr/i18n#lang_es/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("es")))
            .setMasterSplit(false)
            .build();
    ModuleSplit frVrSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("vr"))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "assets/vr/i18n#lang_fr/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("fr")))
            .setMasterSplit(false)
            .build();
    ModuleSplit itVrSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("vr"))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "assets/vr/i18n#lang_it/strings.pak", DUMMY_CONTENT)))
            .setApkTargeting(apkLanguageTargeting(languageTargeting("it")))
            .setMasterSplit(false)
            .build();

    ShardedSystemSplits shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSystemShard(
                ImmutableList.of(masterSplit, esSplit, frSplit, esVrSplit, frVrSplit, itVrSplit),
                NO_MAIN_DEX_LIST,
                mergeSpecs(abis("x86"), locales("fr")));

    ModuleSplit fusedShard = shards.getSystemImageSplit();
    assertThat(fusedShard.isBaseModuleSplit()).isTrue();

    assertThat(extractPaths(fusedShard.getEntries()))
        .containsExactly("assets/i18n#lang_fr/strings.pak", "assets/vr/i18n#lang_fr/strings.pak");

    // es-base, es-vr, it-vr splits.
    ImmutableList<ModuleSplit> langSplits = shards.getAdditionalLanguageSplits();
    assertThat(langSplits).hasSize(3);

    ImmutableMap<String, ModuleSplit> langSplitsNameMap =
        Maps.uniqueIndex(langSplits, split -> split.getAndroidManifest().getSplitId().orElse(""));

    assertThat(langSplitsNameMap.keySet())
        .containsExactly("config.es", "vr.config.es", "vr.config.it");

    assertThat(extractPaths(langSplitsNameMap.get("config.es").getEntries()))
        .containsExactly("assets/i18n#lang_es/strings.pak");
    assertThat(extractPaths(langSplitsNameMap.get("vr.config.es").getEntries()))
        .containsExactly("assets/vr/i18n#lang_es/strings.pak");
    assertThat(extractPaths(langSplitsNameMap.get("vr.config.it").getEntries()))
        .containsExactly("assets/vr/i18n#lang_it/strings.pak");
  }

  @Test
  public void mergeSingleShard_twoModulesTwoSplits() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_asset.txt", DUMMY_CONTENT)))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("assets/some_other_asset.txt", DUMMY_CONTENT)))
            .build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(
                ImmutableList.of(baseModuleSplit, featureModuleSplit),
                NO_MAIN_DEX_LIST,
                createCache());

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("assets/some_asset.txt", "assets/some_other_asset.txt");
    assertThat(merged.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(merged.getVariantTargeting()).isEqualToDefaultInstance();
    assertThat(merged.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(merged.getResourceTable()).isEmpty();
  }

  @Test
  public void dexFiles_allInOneModule_areUnchanged() throws Exception {
    byte[] classesDexData = {'1'};
    byte[] classes2DexData = {'2'};
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("dex/classes.dex", classesDexData),
                    InMemoryModuleEntry.ofFile("dex/classes2.dex", classes2DexData)))
            .build();
    ModuleSplit featureSplit =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("feature")).build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(
                ImmutableList.of(baseSplit, featureSplit), NO_MAIN_DEX_LIST, dexMergingCache);

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("dex/classes.dex", "dex/classes2.dex");
    ModuleEntry classesDexEntry = merged.findEntriesUnderPath("dex/classes.dex").findFirst().get();
    assertThat(TestUtils.getEntryContent(classesDexEntry)).isEqualTo(classesDexData);
    ModuleEntry classes2DexEntry =
        merged.findEntriesUnderPath("dex/classes2.dex").findFirst().get();
    assertThat(TestUtils.getEntryContent(classes2DexEntry)).isEqualTo(classes2DexData);
    // No merging means no items in cache.
    assertThat(dexMergingCache).isEmpty();
  }

  @Test
  public void dexFiles_inMultipleModules_areMerged() throws Exception {
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();
    InMemoryModuleEntry dexEntry1 =
        InMemoryModuleEntry.ofFile(
            "dex/classes.dex", TestData.readBytes("testdata/dex/classes.dex"));
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of(dexEntry1))
            .build();
    InMemoryModuleEntry dexEntry2 =
        InMemoryModuleEntry.ofFile(
            "dex/classes.dex", TestData.readBytes("testdata/dex/classes-other.dex"));
    ModuleSplit featureSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setEntries(ImmutableList.of(dexEntry2))
            .build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(
                ImmutableList.of(baseSplit, featureSplit), NO_MAIN_DEX_LIST, dexMergingCache);

    assertThat(extractPaths(merged.getEntries())).containsExactly("dex/classes.dex");
    ModuleEntry mergedDexEntry = merged.findEntriesUnderPath("dex/classes.dex").findFirst().get();
    byte[] mergedDexData = TestUtils.getEntryContent(mergedDexEntry);
    assertThat(mergedDexData.length).isGreaterThan(0);
    assertThat(mergedDexData).isNotEqualTo(TestData.readBytes("testdata/dex/classes.dex"));
    assertThat(mergedDexData).isNotEqualTo(TestData.readBytes("testdata/dex/classes-other.dex"));
    // The merged result should be cached.
    assertThat(dexMergingCache).hasSize(1);
    ImmutableSet<ModuleEntry> cacheKey = getOnlyElement(dexMergingCache.keySet());
    assertThat(cacheKey).containsExactly(dexEntry1, dexEntry2);
    ImmutableList<Path> cacheValue = getOnlyElement(dexMergingCache.values());
    assertThat(cacheValue.stream().allMatch(cachedFile -> cachedFile.startsWith(tmpDir))).isTrue();
  }

  @Test
  public void splitTargetings_areMerged() throws Exception {
    // Note: Master splits have restrictions on targeting dimensions, so use non-master splits in
    // the test.
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .setMasterSplit(false)
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setApkTargeting(apkDensityTargeting(DensityAlias.HDPI))
            .setMasterSplit(false)
            .build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(ImmutableList.of(split1, split2), NO_MAIN_DEX_LIST, createCache());

    assertThat(merged.getApkTargeting())
        .isEqualTo(
            mergeApkTargeting(
                apkAbiTargeting(AbiAlias.X86), apkDensityTargeting(DensityAlias.HDPI)));
  }

  @Test
  public void systemShards_splitTargetingWithLanguages_areMerged() {
    // Note: Master splits have restrictions on targeting dimensions, so use non-master splits in
    // the test.
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setApkTargeting(apkAbiTargeting(AbiAlias.X86))
            .setMasterSplit(false)
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setApkTargeting(apkDensityTargeting(DensityAlias.HDPI))
            .setMasterSplit(false)
            .build();

    ModuleSplit split3 =
        createModuleSplitBuilder()
            .setApkTargeting(apkLanguageTargeting("en"))
            .setMasterSplit(false)
            .build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(
                ImmutableList.of(split1, split2, split3), NO_MAIN_DEX_LIST, createCache());

    assertThat(merged.getApkTargeting())
        .isEqualTo(
            mergeApkTargeting(
                apkAbiTargeting(AbiAlias.X86),
                apkDensityTargeting(DensityAlias.HDPI),
                apkLanguageTargeting("en")));
  }

  @Test
  public void resourceTables_areMerged() throws Exception {
    Type stringsType = type(0x01, "strings", entry(0x01, "label", value("Welcome", locale("en"))));
    Type drawablesType =
        type(
            0x02,
            "drawable",
            entry(
                0x01,
                "image",
                value("res/drawable/image.jpg", Configuration.getDefaultInstance())));
    ModuleSplit splitStrings =
        createModuleSplitBuilder()
            .setResourceTable(resourceTable(pkg(USER_PACKAGE_OFFSET, "com.test.app", stringsType)))
            .build();
    ModuleSplit splitDrawables =
        createModuleSplitBuilder()
            .setResourceTable(
                resourceTable(pkg(USER_PACKAGE_OFFSET, "com.test.app", drawablesType)))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile("res/drawable/image.jpg", DUMMY_CONTENT)))
            .build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(
                ImmutableList.of(splitStrings, splitDrawables), NO_MAIN_DEX_LIST, createCache());

    assertThat(merged.getResourceTable().get())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(pkg(USER_PACKAGE_OFFSET, "com.test.app", stringsType, drawablesType)));
  }

  @Test
  public void manifests_valid_takeManifestOfTheBase() throws Exception {
    AndroidManifest manifestBase = AndroidManifest.create(androidManifest("com.test.app1"));
    AndroidManifest manifestFeat = AndroidManifest.create(androidManifest("com.test.app2"));

    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setAndroidManifest(manifestBase)
            .setModuleName(BundleModuleName.create("base"))
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setAndroidManifest(manifestFeat)
            .setModuleName(BundleModuleName.create("module"))
            .build();

    ModuleSplit shard =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(ImmutableList.of(split1, split2), NO_MAIN_DEX_LIST, createCache());

    assertThat(shard.getAndroidManifest().getPackageName()).isEqualTo("com.test.app1");
  }

  @Test
  public void manifests_noBaseManifest_throws() throws Exception {
    AndroidManifest manifest1 = AndroidManifest.create(androidManifest("com.test.app1"));
    AndroidManifest manifest2 = AndroidManifest.create(androidManifest("com.test.app2"));

    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setAndroidManifest(manifest1)
            .setModuleName(BundleModuleName.create("module1"))
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setAndroidManifest(manifest2)
            .setModuleName(BundleModuleName.create("module2"))
            .build();

    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
                    .mergeSingleShard(
                        ImmutableList.of(split1, split2), NO_MAIN_DEX_LIST, createCache()));

    assertThat(exception)
        .hasMessageThat()
        .contains("Expected exactly one base module manifest, but found 0");
  }

  @Test
  public void manifests_multipleBaseManifests_throws() throws Exception {
    AndroidManifest manifest1 = AndroidManifest.create(androidManifest("com.test.app1"));
    AndroidManifest manifest2 = AndroidManifest.create(androidManifest("com.test.app2"));

    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setAndroidManifest(manifest1)
            .setModuleName(BundleModuleName.create("base"))
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setAndroidManifest(manifest2)
            .setModuleName(BundleModuleName.create("base"))
            .build();

    Throwable exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
                    .mergeSingleShard(
                        ImmutableList.of(split1, split2), NO_MAIN_DEX_LIST, createCache()));

    assertThat(exception)
        .hasMessageThat()
        .contains("Expected exactly one base module manifest, but found 2");
  }

  @Test
  public void nativeConfigs_defaultAndNonDefault_ok() throws Exception {
    ModuleSplit splitNonDefault =
        createModuleSplitBuilder()
            .setNativeConfig(
                nativeLibraries(
                    targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(AbiAlias.MIPS))))
            .build();
    ModuleSplit splitDefault =
        createModuleSplitBuilder().setNativeConfig(NativeLibraries.getDefaultInstance()).build();

    // We don't care about the merged result, just that the merging succeeds.
    new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
        .mergeSingleShard(
            ImmutableList.of(splitNonDefault, splitDefault), NO_MAIN_DEX_LIST, createCache());
    new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
        .mergeSingleShard(
            ImmutableList.of(splitDefault, splitNonDefault), NO_MAIN_DEX_LIST, createCache());
  }

  @Test
  public void nonEqualModuleNames_ok() throws Exception {
    ModuleSplit split1 =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("base")).build();
    ModuleSplit split2 =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("module2")).build();

    new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
        .mergeSingleShard(ImmutableList.of(split1, split2), NO_MAIN_DEX_LIST, createCache());
  }

  @Test
  public void nonEqualIsMasterSplitValues_ok() throws Exception {
    ModuleSplit split1 = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit split2 = createModuleSplitBuilder().setMasterSplit(false).build();

    new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
        .mergeSingleShard(ImmutableList.of(split1, split2), NO_MAIN_DEX_LIST, createCache());
  }

  @Test
  public void writesMergedModuleNames() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("base")).build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("feature")).build();

    ModuleSplit merged =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeSingleShard(
                ImmutableList.of(baseModuleSplit, featureModuleSplit),
                NO_MAIN_DEX_LIST,
                createCache());

    assertThat(merged.getAndroidManifest().getFusedModuleNames())
        .containsExactly("base", "feature");
  }

  @Test
  public void propagatesDebuggable_falseWhenAbsent() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "dex/classes.dex", TestData.readBytes("testdata/dex/classes.dex"))))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "dex/classes.dex", TestData.readBytes("testdata/dex/classes-other.dex"))))
            .build();
    DexMerger spyDexMerger = Mockito.spy(d8DexMerger);

    new ModuleSplitsToShardMerger(spyDexMerger, tmpDir)
        .mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), NO_MAIN_DEX_LIST, createCache());

    verify(spyDexMerger).merge(any(), any(), any(), /* isDebuggable= */ eq(false), anyInt());
    verifyNoMoreInteractions(spyDexMerger);
  }

  @Test
  public void propagatesMinSdkVersion() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(
                AndroidManifest.create(androidManifest("com.app", withMinSdkVersion(20))))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "dex/classes.dex", TestData.readBytes("testdata/dex/classes.dex"))))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "dex/classes.dex", TestData.readBytes("testdata/dex/classes-other.dex"))))
            .build();
    DexMerger spyDexMerger = Mockito.spy(d8DexMerger);

    new ModuleSplitsToShardMerger(spyDexMerger, tmpDir)
        .mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), NO_MAIN_DEX_LIST, createCache());

    verify(spyDexMerger).merge(any(), any(), any(), anyBoolean(), /* minSdkVersion= */ eq(20));
    verifyNoMoreInteractions(spyDexMerger);
  }

  @Test
  public void propagatesDebuggable_trueWhenSetToTrue() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setAndroidManifest(
                AndroidManifest.create(androidManifest("com.app", withDebuggableAttribute(true))))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "dex/classes.dex", TestData.readBytes("testdata/dex/classes.dex"))))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    InMemoryModuleEntry.ofFile(
                        "dex/classes.dex", TestData.readBytes("testdata/dex/classes-other.dex"))))
            .build();
    DexMerger spyDexMerger = Mockito.spy(d8DexMerger);

    new ModuleSplitsToShardMerger(spyDexMerger, tmpDir)
        .mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), NO_MAIN_DEX_LIST, createCache());

    verify(spyDexMerger).merge(any(), any(), any(), /* isDebuggable= */ eq(true), anyInt());
    verifyNoMoreInteractions(spyDexMerger);
  }

  @Test
  public void mergeApex_oneSetOfSplits_producesOneShard() throws Exception {
    ApexImages apexConfig = ApexImages.getDefaultInstance();
    ModuleSplit masterSplit =
        createModuleSplitBuilder().setMasterSplit(true).setApexConfig(apexConfig).build();
    ModuleSplit x86Split =
        createModuleSplitBuilder()
            .setEntries(ImmutableList.of(InMemoryModuleEntry.ofFile("apex/x86.img", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApexConfig(apexConfig)
            .setApkTargeting(apkMultiAbiTargeting(AbiAlias.X86))
            .build();

    ImmutableList<ModuleSplit> shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeApex(ImmutableList.of(ImmutableList.of(masterSplit, x86Split)));

    ModuleSplit x86Shard = getOnlyElement(shards);
    assertThat(x86Shard.getApkTargeting()).isEqualTo(apkMultiAbiTargeting(AbiAlias.X86));
    assertSingleEntryStandaloneShard(x86Shard, "apex/x86.img");
  }

  @Test
  public void mergeApex_twoSetsOfSplits_producesTwoShards() throws Exception {
    ApexImages apexConfig = ApexImages.getDefaultInstance();
    ModuleSplit masterSplit =
        createModuleSplitBuilder().setMasterSplit(true).setApexConfig(apexConfig).build();
    ModuleSplit x86Split =
        createModuleSplitBuilder()
            .setEntries(ImmutableList.of(InMemoryModuleEntry.ofFile("apex/x86.img", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApexConfig(apexConfig)
            .setApkTargeting(apkMultiAbiTargeting(AbiAlias.X86))
            .build();
    ModuleSplit mipsSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("apex/mips.img", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApexConfig(apexConfig)
            .setApkTargeting(apkMultiAbiTargeting(AbiAlias.MIPS))
            .build();

    ImmutableList<ModuleSplit> shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeApex(
                ImmutableList.of(
                    ImmutableList.of(masterSplit, x86Split),
                    ImmutableList.of(masterSplit, mipsSplit)));

    assertThat(shards).hasSize(2);
    ImmutableMap<ApkTargeting, ModuleSplit> shardsByTargeting =
        Maps.uniqueIndex(shards, ModuleSplit::getApkTargeting);

    assertSingleEntryStandaloneShard(
        shardsByTargeting.get(apkMultiAbiTargeting(AbiAlias.X86)), "apex/x86.img");
    assertSingleEntryStandaloneShard(
        shardsByTargeting.get(apkMultiAbiTargeting(AbiAlias.MIPS)), "apex/mips.img");
  }

  @Test
  public void mergeApex_twoSetsOfSplits_multipleAbi_producesTwoShards() throws Exception {
    ApexImages apexConfig = ApexImages.getDefaultInstance();
    ApkTargeting singleAbiTargeting =
        apkMultiAbiTargeting(
            ImmutableSet.of(ImmutableSet.of(AbiAlias.X86_64)),
            ImmutableSet.of(ImmutableSet.of(AbiAlias.X86_64, AbiAlias.X86)));
    ApkTargeting doubleAbiTargeting =
        apkMultiAbiTargeting(
            ImmutableSet.of(ImmutableSet.of(AbiAlias.X86_64, AbiAlias.X86)),
            ImmutableSet.of(ImmutableSet.of(AbiAlias.X86_64)));
    ModuleSplit masterSplit =
        createModuleSplitBuilder().setMasterSplit(true).setApexConfig(apexConfig).build();
    ModuleSplit singleAbiSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("apex/x86_64.img", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApexConfig(apexConfig)
            .setApkTargeting(singleAbiTargeting)
            .build();
    ModuleSplit doubleAbiSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(InMemoryModuleEntry.ofFile("apex/x86_64.x86.img", DUMMY_CONTENT)))
            .setMasterSplit(false)
            .setApexConfig(apexConfig)
            .setApkTargeting(doubleAbiTargeting)
            .build();

    ImmutableList<ModuleSplit> shards =
        new ModuleSplitsToShardMerger(d8DexMerger, tmpDir)
            .mergeApex(
                ImmutableList.of(
                    ImmutableList.of(masterSplit, doubleAbiSplit),
                    ImmutableList.of(masterSplit, singleAbiSplit)));

    assertThat(shards).hasSize(2);
    ImmutableMap<ApkTargeting, ModuleSplit> shardsByTargeting =
        Maps.uniqueIndex(shards, ModuleSplit::getApkTargeting);
    assertSingleEntryStandaloneShard(shardsByTargeting.get(singleAbiTargeting), "apex/x86_64.img");
    assertSingleEntryStandaloneShard(
        shardsByTargeting.get(doubleAbiTargeting), "apex/x86_64.x86.img");
  }

  /** Creates {@link ModuleSplit.Builder} with fields pre-populated to default values. */
  private ModuleSplit.Builder createModuleSplitBuilder() {
    return ModuleSplit.builder()
        .setAndroidManifest(DEFAULT_MANIFEST)
        .setEntries(ImmutableList.of())
        .setMasterSplit(true)
        .setModuleName(BundleModuleName.create("base"))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(lPlusVariantTargeting());
  }

  private static Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> createCache() {
    return new HashMap<>();
  }

  private static void assertSingleEntryStandaloneShard(ModuleSplit shard, String entry) {
    assertThat(shard.getVariantTargeting()).isEqualToDefaultInstance();
    assertThat(shard.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(extractPaths(shard.getEntries())).containsExactly(entry);
  }
}
