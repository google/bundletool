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

import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.compareManifestMutators;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkAlternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.getSplitsWithTargetingEqualTo;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AssetsLanguageSplitterTest {

  @Test
  public void singleAndEmptyDefaultSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/i18n#lang_jp/strings.pak")
            .addFile("assets/i18n#lang_jp/strings2.pak")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/i18n#lang_jp", assetsDirectoryTargeting(languageTargeting("jp")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = LanguageAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(2);
    ModuleSplit split = assetsSplits.iterator().next();
    verifySplitFor(assetsSplits, ApkTargeting.getDefaultInstance());
    assertThat(split.getApkTargeting()).isEqualTo(apkLanguageTargeting(languageTargeting("jp")));
    assertThat(extractPaths(split.getEntries()))
        .containsExactly("assets/i18n#lang_jp/strings.pak", "assets/i18n#lang_jp/strings2.pak");
  }

  @Test
  public void singleAndDefaultSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/i18n#lang_jp/strings.pak")
            .addFile("assets/i18n#lang_jp/strings2.pak")
            .addFile("assets/other/strings.pak")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/i18n#lang_jp", assetsDirectoryTargeting(languageTargeting("jp"))),
                    targetedAssetsDirectory(
                        "assets/other", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = LanguageAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(2);
    verifySplitFor(
        assetsSplits,
        apkLanguageTargeting("jp"),
        "assets/i18n#lang_jp/strings.pak",
        "assets/i18n#lang_jp/strings2.pak");
    verifySplitFor(assetsSplits, ApkTargeting.getDefaultInstance(), "assets/other/strings.pak");
  }

  @Test
  public void languageAlternativesSplit() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/i18n#lang_jp/strings.pak")
            .addFile("assets/i18n#lang_jp/strings2.pak")
            .addFile("assets/i18n/strings.pak")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/i18n#lang_jp", assetsDirectoryTargeting(languageTargeting("jp"))),
                    targetedAssetsDirectory(
                        "assets/i18n",
                        assetsDirectoryTargeting(alternativeLanguageTargeting("jp")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = LanguageAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(3);
    verifySplitFor(assetsSplits, ApkTargeting.getDefaultInstance());
    verifySplitFor(
        assetsSplits,
        apkLanguageTargeting("jp"),
        "assets/i18n#lang_jp/strings.pak",
        "assets/i18n#lang_jp/strings2.pak");
    verifySplitFor(assetsSplits, apkAlternativeLanguageTargeting("jp"), "assets/i18n/strings.pak");
  }

  @Test
  public void languageAlternativesSplit_multipleDistinctGroups() throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/i18n#lang_jp/strings.pak")
            .addFile("assets/i18n#lang_jp/strings2.pak")
            .addFile("assets/i18n/strings.pak")
            .addFile("assets/different#lang_en/strings.pak")
            .addFile("assets/different#lang_jp/strings.pak")
            .addFile("assets/different/strings.pak")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/i18n#lang_jp", assetsDirectoryTargeting(languageTargeting("jp"))),
                    targetedAssetsDirectory(
                        "assets/i18n",
                        assetsDirectoryTargeting(alternativeLanguageTargeting("jp"))),
                    targetedAssetsDirectory(
                        "assets/different#lang_en",
                        assetsDirectoryTargeting(languageTargeting("en"))),
                    targetedAssetsDirectory(
                        "assets/different#lang_jp",
                        assetsDirectoryTargeting(languageTargeting("jp"))),
                    targetedAssetsDirectory(
                        "assets/different",
                        assetsDirectoryTargeting(alternativeLanguageTargeting("en", "jp")))))
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);
    Collection<ModuleSplit> assetsSplits = LanguageAssetsSplitter.create().split(baseSplit);
    assertThat(assetsSplits).hasSize(5);
    verifySplitFor(assetsSplits, ApkTargeting.getDefaultInstance());
    verifySplitFor(
        assetsSplits,
        apkLanguageTargeting("jp"),
        "assets/i18n#lang_jp/strings.pak",
        "assets/i18n#lang_jp/strings2.pak",
        "assets/different#lang_jp/strings.pak");
    verifySplitFor(assetsSplits, apkAlternativeLanguageTargeting("jp"), "assets/i18n/strings.pak");
    verifySplitFor(
        assetsSplits, apkLanguageTargeting("en"), "assets/different#lang_en/strings.pak");
    verifySplitFor(
        assetsSplits, apkAlternativeLanguageTargeting("en", "jp"), "assets/different/strings.pak");
  }

  @Test
  public void manifestMutatorToRequireSplits_notRegistered_whenNoLanguageSpecificAssets()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/other/strings.pak")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/other", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);

    ImmutableCollection<ModuleSplit> assetsSplits =
        LanguageAssetsSplitter.create().split(baseSplit);

    assertThat(assetsSplits).hasSize(1);
    assertThat(assetsSplits.asList().get(0).getMasterManifestMutators()).isEmpty();
  }

  @Test
  public void manifestMutatorToRequireSplits_registered_whenLanguageSpecificAssetsPresent()
      throws Exception {
    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("assets/i18n#lang_jp/strings.pak")
            .addFile("assets/other/strings.pak")
            .setAssetsConfig(
                assets(
                    targetedAssetsDirectory(
                        "assets/i18n#lang_jp", assetsDirectoryTargeting(languageTargeting("jp"))),
                    targetedAssetsDirectory(
                        "assets/other", AssetsDirectoryTargeting.getDefaultInstance())))
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forAssets(testModule);

    Collection<ModuleSplit> assetsSplits = LanguageAssetsSplitter.create().split(baseSplit);

    ImmutableList<ModuleSplit> configSplits =
        assetsSplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());

    assertThat(configSplits).isNotEmpty();
    for (ModuleSplit configSplit : configSplits) {
      assertThat(
              compareManifestMutators(
                  configSplit.getMasterManifestMutators(), withSplitsRequired(/* value= */ true)))
          .isTrue();
    }
  }

  private void verifySplitFor(
      Collection<ModuleSplit> splits, ApkTargeting apkTargeting, String... expectedEntries) {
    ModuleSplit split =
        Iterables.getOnlyElement(getSplitsWithTargetingEqualTo(splits, apkTargeting));
    assertThat(extractPaths(split.getEntries())).containsExactlyElementsIn(expectedEntries);
  }
}
