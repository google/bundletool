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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.HDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.locale;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.mergeConfigs;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.value;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.apkLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeApkTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.extractPaths;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static junit.framework.TestCase.fail;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.StringPool;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourcesTableFactory;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LanguageResourcesSplitter}. */
@RunWith(JUnit4.class)
public class LanguageResourcesSplitterTest {

  @Test
  public void languageResources_split() throws Exception {
    ResourceTable resourceTable =
        getStringResourceTable(
            locale("de"),
            "Willkommen",
            locale("fr"),
            "Bienvenue",
            Configuration.getDefaultInstance(),
            "Welcome");

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forResources(module);

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    assertThat(languageSplits).hasSize(3);
    boolean hasDeSplit = false;
    boolean hasFrSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        hasDefaultSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(Configuration.getDefaultInstance(), "Welcome"));
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("de"))) {
        hasDeSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(locale("de"), "Willkommen"));
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("fr"))) {
        hasFrSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(locale("fr"), "Bienvenue"));
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasDeSplit).isTrue();
    assertThat(hasFrSplit).isTrue();
  }

  @Test
  public void languageResources_doesntSplitIfAlreadyTargeted() throws Exception {
    ResourceTable resourceTable =
        getDrawableResourceTable(
            mergeConfigs(HDPI, locale("de")),
            "res/drawable-de-hdpi/welcome.png",
            mergeConfigs(MDPI, locale("fr")),
            "res/drawable-fr-mdpi/welcome.png",
            HDPI,
            "res/drawable-hdpi/welcome.png");

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ApkTargeting initialTargeting = apkDensityTargeting(DensityAlias.HDPI);
    ModuleSplit densitySplit =
        ModuleSplit.forResources(module)
            .toBuilder()
            .setApkTargeting(initialTargeting)
            .setMasterSplit(false)
            .build();

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(densitySplit);
    assertThat(languageSplits).hasSize(1);
    ModuleSplit languageSplit = languageSplits.iterator().next();
    assertThat(languageSplit).isEqualTo(densitySplit);
  }

  @Ignore("Re-enable after we can generate multi-dimensional splits.")
  @Test
  public void languageResources_addsToTargeting() throws Exception {
    ResourceTable resourceTable =
        getDrawableResourceTable(
            mergeConfigs(HDPI, locale("de")),
            "res/drawable-de-hdpi/welcome.png",
            mergeConfigs(MDPI, locale("fr")),
            "res/drawable-fr-mdpi/welcome.png",
            HDPI,
            "res/drawable-hdpi/welcome.png");

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ApkTargeting initialTargeting = apkDensityTargeting(DensityAlias.HDPI);
    ModuleSplit densitySplit =
        ModuleSplit.forResources(module)
            .toBuilder()
            .setApkTargeting(initialTargeting)
            .setMasterSplit(false)
            .build();

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(densitySplit);
    assertThat(languageSplits).hasSize(3);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    boolean hasDeHdpiSplit = false;
    boolean hasFrHdpiSplit = false;
    boolean hasHdpiSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(initialTargeting)) {
        hasHdpiSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getDrawableResourceTable(HDPI, "res/drawable-hdpi/welcome.png"));
      } else if (split
          .getApkTargeting()
          .equals(mergeApkTargeting(apkLanguageTargeting("de"), initialTargeting))) {
        hasDeHdpiSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(
                getDrawableResourceTable(
                    mergeConfigs(HDPI, locale("de")), "res/drawable-de-hdpi/welcome.png"));
      } else if (split
          .getApkTargeting()
          .equals(mergeApkTargeting(apkLanguageTargeting("fr"), initialTargeting))) {
        hasFrHdpiSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(
                getDrawableResourceTable(
                    mergeConfigs(MDPI, locale("fr")), "res/drawable-fr-mdpi/welcome.png"));
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasHdpiSplit).isTrue();
    assertThat(hasDeHdpiSplit).isTrue();
    assertThat(hasFrHdpiSplit).isTrue();
  }

  @Test
  public void languageResources_withRegions() throws Exception {
    ResourceTable resourceTable =
        getStringResourceTable(
            locale("zh-cn"),
            "欢迎",
            locale("zh-tw"),
            "歡迎",
            Configuration.getDefaultInstance(),
            "Welcome");

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forResources(module);

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);
    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    boolean hasZhSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        hasDefaultSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(Configuration.getDefaultInstance(), "Welcome"));
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("zh"))) {
        hasZhSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(locale("zh-cn"), "欢迎", locale("zh-tw"), "歡迎"));
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasZhSplit).isTrue();
  }

  @Test
  public void languageSplits_skipNonResourceEntries() throws Exception {
    ResourceTable resourceTable =
        getStringResourceTable(
            locale("fr"), "Bienvenue", Configuration.getDefaultInstance(), "Welcome");

    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .addFile("assets/$fr/other.txt")
            .addFile("assets/$en/other.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forModule(module);

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);
    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    boolean hasFrSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        hasDefaultSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(Configuration.getDefaultInstance(), "Welcome"));
        assertThat(extractPaths(split.getEntries()))
            .containsExactly("assets/$fr/other.txt", "assets/$en/other.txt");
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("fr"))) {
        hasFrSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(locale("fr"), "Bienvenue"));
        assertThat(split.getEntries()).isEmpty();
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasFrSplit).isTrue();
  }

  @Test
  public void languageResources_sourcePoolPreserved() throws Exception {
    StringPool sourcePool =
        StringPool.newBuilder().setData(ByteString.copyFrom(new byte[] {'x'})).build();
    ResourceTable resourceTable =
        getStringResourceTable(
                locale("de"), "Willkommen", Configuration.getDefaultInstance(), "Welcome")
            .toBuilder()
            .setSourcePool(sourcePool)
            .build();
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forResources(module);

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);
    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());

    for (ModuleSplit split : languageSplits) {
      assertThat(split.getResourceTable()).isPresent();
      assertThat(split.getResourceTable().get().getSourcePool()).isEqualTo(sourcePool);
    }
  }

  @Test
  public void languageSplits_defaultLanguageGenerated_noDefaultLanguagePresent() throws Exception {
    ResourceTable resourceTable = getStringResourceTable(locale("fr"), "Bienvenue");
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .addFile("assets/some_important_asset.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forModule(module);

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);
    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
    boolean hasFrSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        hasDefaultSplit = true;
        assertThat(split.getResourceTable().get()).isEqualTo(ResourceTable.getDefaultInstance());
        assertThat(extractPaths(split.getEntries()))
            .containsExactly("assets/some_important_asset.txt");
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("fr"))) {
        hasFrSplit = true;
        assertThat(split.getResourceTable().get())
            .isEqualTo(getStringResourceTable(locale("fr"), "Bienvenue"));
        assertThat(split.getEntries()).isEmpty();
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasFrSplit).isTrue();
  }

  @Test
  public void masterSplitFlag_outputMasterSplitInheritsTrue() throws Exception {
    ResourceTable resourceTable =
        getStringResourceTable(
            locale("de"), "Willkommen", Configuration.getDefaultInstance(), "Welcome");
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forResources(module);
    assertThat(baseSplit.isMasterSplit()).isTrue();

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);

    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
    boolean hasDeSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        hasDefaultSplit = true;
        assertThat(split.isMasterSplit()).isTrue();
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("de"))) {
        hasDeSplit = true;
        assertThat(split.isMasterSplit()).isFalse();
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasDeSplit).isTrue();
  }

  @Test
  public void masterSplitFlag_outputMasterSplitInheritsFalse() throws Exception {
    ResourceTable resourceTable =
        getStringResourceTable(
            locale("de"), "Willkommen", Configuration.getDefaultInstance(), "Welcome");
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit =
        ModuleSplit.forResources(module)
            .toBuilder()
            // Pretend that this is not a master split to begin with => it cannot become master
            // split by running the splitter.
            .setMasterSplit(false)
            .build();

    Collection<ModuleSplit> languageSplits = new LanguageResourcesSplitter().split(baseSplit);

    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
    boolean hasDeSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit split : languageSplits) {
      if (split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        hasDefaultSplit = true;
        assertThat(split.isMasterSplit()).isFalse();
      } else if (split.getApkTargeting().equals(apkLanguageTargeting("de"))) {
        hasDeSplit = true;
        assertThat(split.isMasterSplit()).isFalse();
      } else {
        fail(String.format("Unexpected targeting: %s", split.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasDeSplit).isTrue();
  }

  @Test
  public void removingResourceTableFileEntry_removesReferencedFilesFromSplit() throws Exception {
    ResourceTable resourceTable =
        ResourcesTableFactory.createResourceTable(
            "image",
            fileReference("res/drawable-hdpi/image.jpg", HDPI),
            fileReference("res/drawable-fr-hdpi/image.jpg", mergeConfigs(locale("fr"), HDPI)));

    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-hdpi/image.jpg")
            .addFile("res/drawable-fr-hdpi/image.jpg")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();
    ModuleSplit baseSplit = ModuleSplit.forModule(testModule);

    LanguageResourcesSplitter languageSplitter = new LanguageResourcesSplitter();
    Collection<ModuleSplit> languageSplits = languageSplitter.split(baseSplit);
    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
    boolean hasFrSplit = false;
    boolean hasDefaultSplit = false;
    for (ModuleSplit languageSplit : languageSplits) {
      if (languageSplit.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
        assertThat(extractPaths(languageSplit.getEntries()))
            .containsExactly("res/drawable-hdpi/image.jpg");
        hasDefaultSplit = true;
      } else if (languageSplit.getApkTargeting().equals(apkLanguageTargeting("fr"))) {
        assertThat(extractPaths(languageSplit.getEntries()))
            .containsExactly("res/drawable-fr-hdpi/image.jpg");
        hasFrSplit = true;
      } else {
        fail(String.format("Unexpected split targeting: %s", languageSplit.getApkTargeting()));
      }
    }
    assertThat(hasDefaultSplit).isTrue();
    assertThat(hasFrSplit).isTrue();
  }

  @Test
  public void noEmptySplits_whenNoResourcesLeft() throws Exception {
    ResourceTable resourceTable =
        ResourcesTableFactory.createResourceTable(
            "image",
            fileReference("res/drawable-en-hdpi/image.jpg", mergeConfigs(locale("en-GB"), HDPI)),
            fileReference("res/drawable-fr-hdpi/image.jpg", mergeConfigs(locale("fr"), HDPI)));

    BundleModule testModule =
        new BundleModuleBuilder("testModule")
            .addFile("res/drawable-en-hdpi/image.jpg")
            .addFile("res/drawable-fr-hdpi/image.jpg")
            .setResourceTable(resourceTable)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ModuleSplit resourcesSplit = ModuleSplit.forModule(testModule);
    LanguageResourcesSplitter languageSplitter = new LanguageResourcesSplitter();
    Collection<ModuleSplit> languageSplits = languageSplitter.split(resourcesSplit);

    assertThat(languageSplits).hasSize(2);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getSplitType)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(SplitType.SPLIT);
    assertThat(
            languageSplits
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .distinct()
                .collect(toImmutableSet()))
        .containsExactly(lPlusVariantTargeting());
    Map<ApkTargeting, ModuleSplit> targetingMap =
        Maps.uniqueIndex(languageSplits, ModuleSplit::getApkTargeting);

    ApkTargeting enTargeting = apkLanguageTargeting("en");
    assertThat(targetingMap).containsKey(enTargeting);
    ModuleSplit enSplit = targetingMap.get(enTargeting);
    assertThat(extractPaths(enSplit.getEntries()))
        .containsExactly("res/drawable-en-hdpi/image.jpg");

    ApkTargeting frTargeting = apkLanguageTargeting("fr");
    assertThat(targetingMap).containsKey(frTargeting);
    ModuleSplit frSplit = targetingMap.get(frTargeting);
    assertThat(extractPaths(frSplit.getEntries()))
        .containsExactly("res/drawable-fr-hdpi/image.jpg");
  }

  private static ResourceTable getStringResourceTable(Configuration config1, String value1) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(0x01, "strings", entry(0x01, "welcome_label", value(value1, config1)))));
  }

  private static ResourceTable getStringResourceTable(
      Configuration config1, String value1, Configuration config2, String value2) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(
                0x01,
                "strings",
                entry(0x01, "welcome_label", value(value1, config1), value(value2, config2)))));
  }

  private static ResourceTable getStringResourceTable(
      Configuration config1,
      String value1,
      Configuration config2,
      String value2,
      Configuration config3,
      String value3) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(
                0x01,
                "strings",
                entry(
                    0x01,
                    "welcome_label",
                    value(value1, config1),
                    value(value2, config2),
                    value(value3, config3)))));
  }

  private static ResourceTable getDrawableResourceTable(Configuration config1, String filePath) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(
                0x01, "drawable", entry(0x01, "welcome_image", fileReference(filePath, config1)))));
  }

  private static ResourceTable getDrawableResourceTable(
      Configuration config1,
      String filePath,
      Configuration config2,
      String filePath2,
      Configuration config3,
      String filePath3) {
    return resourceTable(
        pkg(
            USER_PACKAGE_OFFSET,
            "com.test.app",
            type(
                0x01,
                "drawable",
                entry(
                    0x01,
                    "welcome_image",
                    fileReference(filePath, config1),
                    fileReference(filePath2, config2),
                    fileReference(filePath3, config3)))));
  }
}
