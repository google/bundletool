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

import static com.android.bundle.Config.StandaloneConfig.DexMergingStrategy.NEVER_MERGE;
import static com.android.bundle.Targeting.Abi.AbiAlias.MIPS;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ALIAS_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.META_DATA_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.PROVIDER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.RECEIVER_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.version.BundleToolVersion.getCurrentVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withCustomThemeActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDebuggableAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataValue;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivityAlias;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameProvider;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameReceiver;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.android.tools.build.bundletool.testing.ModuleSplitUtils.createAssetsDirectoryLanguageTargeting;
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
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.createModuleEntryForFile;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.Type;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
import com.android.tools.build.bundletool.testing.TestModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import dagger.Component;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class ModuleSplitsToShardMergerTest {

  private static final byte[] TEST_CONTENT = new byte[1];
  private static final String FEATURE_MODULE_NAME = "feature";

  private static final AndroidManifest DEFAULT_MANIFEST =
      AndroidManifest.create(androidManifest("com.test.app"));
  private static final AndroidManifest L_PLUS_MANIFEST =
      AndroidManifest.create(
          androidManifest("com.test.app", withMinSdkVersion(Versions.ANDROID_L_API_VERSION)));

  private static final byte[] CLASSES_DEX_CONTENT = TestData.readBytes("testdata/dex/classes.dex");
  private static final byte[] CLASSES_OTHER_DEX_CONTENT =
      TestData.readBytes("testdata/dex/classes-other.dex");

  private static final AppBundle BUNDLE_WITH_ONE_FEATURE_NO_MAIN_DEX_LIST =
      new AppBundleBuilder()
          .addModule(
              "base", moduleBuilder -> moduleBuilder.setManifest(androidManifest("com.test")))
          .addModule(
              FEATURE_MODULE_NAME,
              moduleBuilder ->
                  moduleBuilder.setManifest(androidManifestForFeature("com.test.feature")))
          .build();
  private static final AppBundle BUNDLE_WITH_ONE_FEATURE_DISABLED_MERGING =
      new AppBundleBuilder()
          .setBundleConfig(BundleConfigBuilder.create().setDexMergingStrategy(NEVER_MERGE).build())
          .addModule(
              "base", moduleBuilder -> moduleBuilder.setManifest(androidManifest("com.test")))
          .addModule(
              FEATURE_MODULE_NAME,
              moduleBuilder ->
                  moduleBuilder.setManifest(androidManifestForFeature("com.test.feature")))
          .build();

  private static final AppBundle BUNDLE_WITH_BASE_ONLY_NO_MAIN_DEX_LIST =
      new AppBundleBuilder()
          .addModule(
              "base", moduleBuilder -> moduleBuilder.setManifest(androidManifest("com.test")))
          .build();

  @Inject TempDirectory tmpDir;
  @Inject DexMerger d8DexMerger;

  @Inject ModuleSplitsToShardMerger splitsToShardMerger;

  @Before
  public void setUp() {
    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(BUNDLE_WITH_ONE_FEATURE_NO_MAIN_DEX_LIST).build());
  }

  @Test
  public void mergeSingleShard_twoModulesTwoSplits() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(createModuleEntryForFile("assets/some_asset.txt", TEST_CONTENT)))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_other_asset.txt", TEST_CONTENT)))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("assets/some_asset.txt", "assets/some_other_asset.txt");
    assertThat(merged.getApkTargeting()).isEqualToDefaultInstance();
    assertThat(merged.getVariantTargeting()).isEqualToDefaultInstance();
    assertThat(merged.getSplitType()).isEqualTo(SplitType.STANDALONE);
    assertThat(merged.getResourceTable()).isEmpty();
  }

  @Test
  public void mergeSingleShard_mergeDisjointAssets() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_assets/file.txt", TEST_CONTENT)))
            .setAssetsConfig(
                Assets.newBuilder()
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder().setPath("assets/some_assets").build())
                    .build())
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_other_assets/file.txt", TEST_CONTENT)))
            .setAssetsConfig(
                Assets.newBuilder()
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder()
                            .setPath("assets/some_other_assets")
                            .build())
                    .build())
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("assets/some_assets/file.txt", "assets/some_other_assets/file.txt");
    assertThat(merged.getAssetsConfig()).isPresent();
    assertThat(
            merged.getAssetsConfig().get().getDirectoryList().stream()
                .map(TargetedAssetsDirectory::getPath))
        .containsExactly("assets/some_assets", "assets/some_other_assets");
  }

  @Test
  public void mergeSingleShard_mergeAssetsWithIntersection() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_assets/file.txt", TEST_CONTENT)))
            .setAssetsConfig(
                Assets.newBuilder()
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder()
                            .setPath("assets/some_assets")
                            .setTargeting(createAssetsDirectoryLanguageTargeting("de"))
                            .build())
                    .build())
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_assets/file.txt", TEST_CONTENT),
                    createModuleEntryForFile("assets/some_other_assets/file.txt", TEST_CONTENT)))
            .setAssetsConfig(
                Assets.newBuilder()
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder()
                            .setPath("assets/some_assets")
                            .setTargeting(createAssetsDirectoryLanguageTargeting("de"))
                            .build())
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder()
                            .setPath("assets/some_other_assets")
                            .build())
                    .build())
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("assets/some_assets/file.txt", "assets/some_other_assets/file.txt");
    assertThat(merged.getAssetsConfig()).isPresent();
    assertThat(
            merged.getAssetsConfig().get().getDirectoryList().stream()
                .map(TargetedAssetsDirectory::getPath))
        .containsExactly("assets/some_assets", "assets/some_other_assets");
  }

  @Test
  public void mergeSingleShard_throwsIfConflictingAssets() throws Exception {
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_assets/file.txt", TEST_CONTENT)))
            .setAssetsConfig(
                Assets.newBuilder()
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder()
                            .setPath("assets/some_assets")
                            .setTargeting(createAssetsDirectoryLanguageTargeting("en"))
                            .build())
                    .build())
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("assets/some_assets/file.txt", TEST_CONTENT)))
            .setAssetsConfig(
                Assets.newBuilder()
                    .addDirectory(
                        TargetedAssetsDirectory.newBuilder()
                            .setPath("assets/some_assets")
                            .setTargeting(createAssetsDirectoryLanguageTargeting("de"))
                            .build())
                    .build())
            .build();

    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                splitsToShardMerger.mergeSingleShard(
                    ImmutableList.of(split1, split2), createCache()));

    assertThat(exception)
        .hasMessageThat()
        .contains("conflicting targeting values while merging assets config");
  }

  @Test
  public void dexFiles_onlyBaseModule_areUnchanged() throws Exception {
    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(BUNDLE_WITH_BASE_ONLY_NO_MAIN_DEX_LIST).build());

    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT),
                    createModuleEntryForFile("dex/classes2.dex", CLASSES_OTHER_DEX_CONTENT)))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(ImmutableList.of(baseSplit), dexMergingCache);

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("dex/classes.dex", "dex/classes2.dex");
    assertThat(dexData(merged, "dex/classes.dex")).isEqualTo(CLASSES_DEX_CONTENT);
    assertThat(dexData(merged, "dex/classes2.dex")).isEqualTo(CLASSES_OTHER_DEX_CONTENT);

    // No merging means no items in cache.
    assertThat(dexMergingCache).isEmpty();
  }

  @Test
  public void dexFiles_inMultipleModules_areMerged() throws Exception {
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();
    ModuleEntry dexEntry1 = createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT);
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of(dexEntry1))
            .build();
    ModuleEntry dexEntry2 = createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT);
    ModuleSplit featureSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setEntries(ImmutableList.of(dexEntry2))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseSplit, featureSplit), dexMergingCache);

    assertThat(extractPaths(merged.getEntries())).containsExactly("dex/classes.dex");

    byte[] mergedDexData = dexData(merged, "dex/classes.dex");
    assertThat(mergedDexData.length).isGreaterThan(0);
    assertThat(mergedDexData).isNotEqualTo(CLASSES_DEX_CONTENT);
    assertThat(mergedDexData).isNotEqualTo(CLASSES_OTHER_DEX_CONTENT);
    // The merged result should be cached.
    assertThat(dexMergingCache).hasSize(1);
    ImmutableSet<ModuleEntry> cacheKey = getOnlyElement(dexMergingCache.keySet());
    assertThat(cacheKey).containsExactly(dexEntry1, dexEntry2);
    ImmutableList<Path> cacheValue = getOnlyElement(dexMergingCache.values());
    assertThat(cacheValue.stream().allMatch(cachedFile -> cachedFile.startsWith(tmpDir.getPath())))
        .isTrue();
  }

  @Test
  public void dexFiles_allInOneModule_areMerged() throws Exception {
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT),
                    createModuleEntryForFile("dex/classes2.dex", CLASSES_OTHER_DEX_CONTENT)))
            .build();
    ModuleSplit featureSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseSplit, featureSplit), dexMergingCache);

    assertThat(extractPaths(merged.getEntries())).containsExactly("dex/classes.dex");
    byte[] dexData = dexData(merged, "dex/classes.dex");
    assertThat(dexData).isNotEqualTo(CLASSES_DEX_CONTENT);
    assertThat(dexData).isNotEqualTo(CLASSES_OTHER_DEX_CONTENT);
  }

  @Test
  public void dexFiles_inMultipleModules_areRenamedForLPlus() throws Exception {
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();

    ModuleEntry dexEntry1 = createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT);
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of(dexEntry1))
            .setAndroidManifest(L_PLUS_MANIFEST)
            .build();

    ModuleEntry dexEntry2 = createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT);
    ModuleEntry dexEntry3 = createModuleEntryForFile("dex/classes2.dex", CLASSES_DEX_CONTENT);
    ModuleSplit featureSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setEntries(ImmutableList.of(dexEntry3, dexEntry2))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseSplit, featureSplit), dexMergingCache);

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("dex/classes.dex", "dex/classes2.dex", "dex/classes3.dex")
        .inOrder();
    assertThat(dexData(merged, "dex/classes.dex")).isEqualTo(CLASSES_DEX_CONTENT);
    assertThat(dexData(merged, "dex/classes2.dex")).isEqualTo(CLASSES_OTHER_DEX_CONTENT);
    assertThat(dexData(merged, "dex/classes3.dex")).isEqualTo(CLASSES_DEX_CONTENT);

    // The merged result with rename should not be cached.
    assertThat(dexMergingCache).isEmpty();
  }

  @Test
  public void dexFiles_inMultipleModules_areRenamedForLPlusNoBaseModuleDex() throws Exception {
    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();

    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of())
            .setAndroidManifest(L_PLUS_MANIFEST)
            .build();

    ModuleEntry dexEntry1 = createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT);
    ModuleEntry dexEntry2 = createModuleEntryForFile("dex/classes2.dex", CLASSES_DEX_CONTENT);
    ModuleSplit featureSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setEntries(ImmutableList.of(dexEntry1, dexEntry2))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseSplit, featureSplit), dexMergingCache);

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("dex/classes.dex", "dex/classes2.dex");
    assertThat(dexData(merged, "dex/classes.dex")).isEqualTo(CLASSES_OTHER_DEX_CONTENT);
    assertThat(dexData(merged, "dex/classes2.dex")).isEqualTo(CLASSES_DEX_CONTENT);

    // The merged result with rename should not be cached.
    assertThat(dexMergingCache).isEmpty();
  }

  @Test
  public void dexFiles_inMultipleModules_areRenamedWhenMergeIsDisabled() throws Exception {
    TestComponent.useTestModule(
        this, TestModule.builder().withAppBundle(BUNDLE_WITH_ONE_FEATURE_DISABLED_MERGING).build());

    Map<ImmutableSet<ModuleEntry>, ImmutableList<Path>> dexMergingCache = createCache();

    ModuleEntry dexEntry1 = createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT);
    ModuleSplit baseSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("base"))
            .setEntries(ImmutableList.of(dexEntry1))
            .build();

    ModuleEntry dexEntry2 = createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT);
    ModuleSplit featureSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create("feature"))
            .setEntries(ImmutableList.of(dexEntry2))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseSplit, featureSplit), dexMergingCache);

    assertThat(extractPaths(merged.getEntries()))
        .containsExactly("dex/classes.dex", "dex/classes2.dex");
    assertThat(dexData(merged, "dex/classes.dex")).isEqualTo(CLASSES_DEX_CONTENT);
    assertThat(dexData(merged, "dex/classes2.dex")).isEqualTo(CLASSES_OTHER_DEX_CONTENT);

    // The merged result with rename should not be cached.
    assertThat(dexMergingCache).isEmpty();
  }

  @Test
  public void splitTargetings_areMerged() throws Exception {
    // Note: Master splits have restrictions on targeting dimensions, so use non-master splits in
    // the test.
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setApkTargeting(apkAbiTargeting(X86))
            .setMasterSplit(false)
            .build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setApkTargeting(apkDensityTargeting(DensityAlias.HDPI))
            .setMasterSplit(false)
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(ImmutableList.of(split1, split2), createCache());

    assertThat(merged.getApkTargeting())
        .isEqualTo(mergeApkTargeting(apkAbiTargeting(X86), apkDensityTargeting(DensityAlias.HDPI)));
  }

  @Test
  public void systemShards_splitTargetingWithLanguages_areMerged() {
    // Note: Master splits have restrictions on targeting dimensions, so use non-master splits in
    // the test.
    ModuleSplit split1 =
        createModuleSplitBuilder()
            .setApkTargeting(apkAbiTargeting(X86))
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
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(split1, split2, split3), createCache());

    assertThat(merged.getApkTargeting())
        .isEqualTo(
            mergeApkTargeting(
                apkAbiTargeting(X86),
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
                ImmutableList.of(createModuleEntryForFile("res/drawable/image.jpg", TEST_CONTENT)))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(splitStrings, splitDrawables), createCache());

    assertThat(merged.getResourceTable().get())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(pkg(USER_PACKAGE_OFFSET, "com.test.app", stringsType, drawablesType)));
  }

  @Test
  public void manifests_valid_fuseFeatureApplicationElementsIntoBaseManifest() throws Exception {
    AndroidManifest manifestBase =
        AndroidManifest.create(
            androidManifest(
                "com.test.app1",
                withCustomThemeActivity("activity1", 1),
                withCustomThemeActivity("activity2", 2)));
    AndroidManifest manifestFeat =
        AndroidManifest.create(
            androidManifest(
                "com.test.app2",
                withCustomThemeActivity("activity1", 3),
                withMetadataValue("moduleMeta", "module"),
                withSplitNameActivityAlias("moduleActivityAlias", "module"),
                withSplitNameProvider("moduleProvider", "module"),
                withSplitNameReceiver("moduleReceiver", "module"),
                withSplitNameService("moduleService", "module")));

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
        splitsToShardMerger.mergeSingleShard(ImmutableList.of(split1, split2), createCache());

    assertThat(shard.getAndroidManifest().getPackageName()).isEqualTo("com.test.app1");
    assertThat(extractActivityThemeRefIds(shard.getAndroidManifest()))
        .containsExactly("activity1", 3, "activity2", 2);
    assertThat(extractApplicationElements(shard.getAndroidManifest()))
        .containsExactly(
            "activity1",
            ACTIVITY_ELEMENT_NAME,
            "activity2",
            ACTIVITY_ELEMENT_NAME,
            "moduleMeta",
            META_DATA_ELEMENT_NAME,
            "moduleActivityAlias",
            ACTIVITY_ALIAS_ELEMENT_NAME,
            "moduleProvider",
            PROVIDER_ELEMENT_NAME,
            "moduleReceiver",
            RECEIVER_ELEMENT_NAME,
            "moduleService",
            SERVICE_ELEMENT_NAME,
            "com.android.dynamic.apk.fused.modules",
            META_DATA_ELEMENT_NAME);
  }

  @Test
  public void manifests_valid_mergeFeatureActivitiesIntoBaseManifest_versionBefore_1_8_0()
      throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().withBundletoolVersion("1.7.0").build());
    AndroidManifest manifestBase =
        AndroidManifest.create(
            androidManifest(
                "com.test.app1",
                withCustomThemeActivity("activity1", 1),
                withCustomThemeActivity("activity2", 2)));
    AndroidManifest manifestFeat =
        AndroidManifest.create(
            androidManifest(
                "com.test.app2",
                withCustomThemeActivity("activity1", 3),
                withMetadataValue("moduleMeta", "module"),
                withSplitNameActivityAlias("moduleActivityAlias", "module"),
                withSplitNameProvider("moduleProvider", "module"),
                withSplitNameReceiver("moduleReceived", "module"),
                withSplitNameService("moduleService", "module")));

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
        splitsToShardMerger.mergeSingleShard(ImmutableList.of(split1, split2), createCache());

    assertThat(shard.getAndroidManifest().getPackageName()).isEqualTo("com.test.app1");
    assertThat(extractActivityThemeRefIds(shard.getAndroidManifest()))
        .containsExactly("activity1", 3, "activity2", 2);
    assertThat(extractApplicationElements(shard.getAndroidManifest()))
        .containsExactly(
            "activity1",
            ACTIVITY_ELEMENT_NAME,
            "activity2",
            ACTIVITY_ELEMENT_NAME,
            "com.android.dynamic.apk.fused.modules",
            META_DATA_ELEMENT_NAME);
  }

  @Test
  public void manifests_valid_takeManifestOfTheBase_versionBefore_0_13_4() throws Exception {
    TestComponent.useTestModule(this, TestModule.builder().withBundletoolVersion("0.13.3").build());

    AndroidManifest manifestBase =
        AndroidManifest.create(
            androidManifest(
                "com.test.app1",
                withCustomThemeActivity("activity1", 1),
                withCustomThemeActivity("activity2", 2)));
    AndroidManifest manifestFeat =
        AndroidManifest.create(
            androidManifest("com.test.app2", withCustomThemeActivity("activity1", 3)));

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
        splitsToShardMerger.mergeSingleShard(ImmutableList.of(split1, split2), createCache());

    assertThat(shard.getAndroidManifest().getPackageName()).isEqualTo("com.test.app1");
    assertThat(extractActivityThemeRefIds(shard.getAndroidManifest()))
        .containsExactly("activity1", 1, "activity2", 2);
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
                splitsToShardMerger.mergeSingleShard(
                    ImmutableList.of(split1, split2), createCache()));

    assertThat(exception).hasMessageThat().contains("Expected to have base module");
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
                splitsToShardMerger.mergeSingleShard(
                    ImmutableList.of(split1, split2), createCache()));

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
                    targetedNativeDirectory("lib/mips", nativeDirectoryTargeting(MIPS))))
            .build();
    ModuleSplit splitDefault =
        createModuleSplitBuilder().setNativeConfig(NativeLibraries.getDefaultInstance()).build();

    // We don't care about the merged result, just that the merging succeeds.
    splitsToShardMerger.mergeSingleShard(
        ImmutableList.of(splitNonDefault, splitDefault), createCache());
    splitsToShardMerger.mergeSingleShard(
        ImmutableList.of(splitDefault, splitNonDefault), createCache());
  }

  @Test
  public void nonEqualModuleNames_ok() throws Exception {
    ModuleSplit split1 =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("base")).build();
    ModuleSplit split2 =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .build();

    splitsToShardMerger.mergeSingleShard(ImmutableList.of(split1, split2), createCache());
  }

  @Test
  public void nonEqualIsMasterSplitValues_ok() throws Exception {
    ModuleSplit split1 = createModuleSplitBuilder().setMasterSplit(true).build();
    ModuleSplit split2 = createModuleSplitBuilder().setMasterSplit(false).build();

    splitsToShardMerger.mergeSingleShard(ImmutableList.of(split1, split2), createCache());
  }

  @Test
  public void writesMergedModuleNames() throws Exception {
    ModuleSplit baseModuleSplit =
        createModuleSplitBuilder().setModuleName(BundleModuleName.create("base")).build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .build();

    ModuleSplit merged =
        splitsToShardMerger.mergeSingleShard(
            ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

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
                ImmutableList.of(createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT)))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT)))
            .build();
    DexMerger spyDexMerger = Mockito.spy(d8DexMerger);

    new ModuleSplitsToShardMerger(
            getCurrentVersion(), tmpDir, spyDexMerger, BUNDLE_WITH_ONE_FEATURE_NO_MAIN_DEX_LIST)
        .mergeSingleShard(ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

    verify(spyDexMerger).merge(any(), any(), any(), any(), /* isDebuggable= */ eq(false), anyInt());
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
                ImmutableList.of(createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT)))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT)))
            .build();
    DexMerger spyDexMerger = Mockito.spy(d8DexMerger);

    new ModuleSplitsToShardMerger(
            getCurrentVersion(), tmpDir, spyDexMerger, BUNDLE_WITH_ONE_FEATURE_NO_MAIN_DEX_LIST)
        .mergeSingleShard(ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

    verify(spyDexMerger)
        .merge(any(), any(), any(), any(), anyBoolean(), /* minSdkVersion= */ eq(20));
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
                ImmutableList.of(createModuleEntryForFile("dex/classes.dex", CLASSES_DEX_CONTENT)))
            .build();
    ModuleSplit featureModuleSplit =
        createModuleSplitBuilder()
            .setModuleName(BundleModuleName.create(FEATURE_MODULE_NAME))
            .setAndroidManifest(AndroidManifest.create(androidManifest("com.app")))
            .setEntries(
                ImmutableList.of(
                    createModuleEntryForFile("dex/classes.dex", CLASSES_OTHER_DEX_CONTENT)))
            .build();
    DexMerger spyDexMerger = Mockito.spy(d8DexMerger);

    new ModuleSplitsToShardMerger(
            getCurrentVersion(), tmpDir, spyDexMerger, BUNDLE_WITH_ONE_FEATURE_NO_MAIN_DEX_LIST)
        .mergeSingleShard(ImmutableList.of(baseModuleSplit, featureModuleSplit), createCache());

    verify(spyDexMerger).merge(any(), any(), any(), any(), /* isDebuggable= */ eq(true), anyInt());
    verifyNoMoreInteractions(spyDexMerger);
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

  private static byte[] dexData(ModuleSplit module, String entryPath) throws Exception {
    ModuleEntry mergedDexEntry = module.findEntry(entryPath).get();
    return mergedDexEntry.getContent().read();
  }

  private static ListMultimap<String, Integer> extractActivityThemeRefIds(
      AndroidManifest manifest) {
    return Multimaps.transformValues(
        manifest.getActivitiesByName(),
        el -> el.getAndroidAttribute(THEME_RESOURCE_ID).get().getValueAsRefId());
  }

  private static ImmutableMap<String, String> extractApplicationElements(AndroidManifest manifest) {
    return manifest
        .getManifestRoot()
        .getElement()
        .getChildElement(AndroidManifest.APPLICATION_ELEMENT_NAME)
        .getChildrenElements()
        .filter(
            element -> element.getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID).isPresent())
        .collect(
            toImmutableMap(
                element ->
                    element
                        .getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID)
                        .get()
                        .getValueAsString(),
                element -> element.getName()));
  }

  @CommandScoped
  @Component(modules = {BuildApksModule.class, TestModule.class})
  interface TestComponent {
    void inject(ModuleSplitsToShardMergerTest test);

    static void useTestModule(ModuleSplitsToShardMergerTest testInstance, TestModule testModule) {
      DaggerModuleSplitsToShardMergerTest_TestComponent.builder()
          .testModule(testModule)
          .build()
          .inject(testInstance);
    }
  }
}
