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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.INSTANT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SPLIT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.STANDALONE;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SYSTEM;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.InMemoryModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SplitsProtoXmlBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.android.tools.build.bundletool.testing.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class SplitsXmlInjectorTest {

  private static final String PACKAGE_NAME = "com.example.app";
  private SplitsXmlInjector splitsXmlInjector;

  @Before
  public void setUp() {
    splitsXmlInjector = new SplitsXmlInjector();
  }

  @Test
  public void process_ignoresWrongSplits() {
    ImmutableList<ModuleSplit> modules =
        ImmutableList.of(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "",
                /* masterSplit= */ true,
                INSTANT,
                /* languageTargeting= */ null),
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "",
                /* masterSplit= */ true,
                STANDALONE,
                /* languageTargeting= */ null),
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "",
                /* masterSplit= */ false,
                SPLIT,
                /* languageTargeting= */ null),
            createModuleSplit(
                "module",
                /* splitId= */ "",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null));
    assertThat(
            splitsXmlInjector.process(GeneratedApks.fromModuleSplits(modules)).getAllApksStream())
        .containsExactlyElementsIn(modules);
  }

  @Test
  public void process() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "",
            /* masterSplit= */ true,
            SPLIT,
            /* languageTargeting= */ null);
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "config.ru",
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("ru")),
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "config.fr",
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("fr")),
            createModuleSplit(
                "module",
                /* splitId= */ "module",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null),
            createModuleSplit(
                "module",
                /* splitId= */ "module.config.ru",
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("ru")));
    GeneratedApks result =
        splitsXmlInjector.process(
            GeneratedApks.fromModuleSplits(
                ImmutableList.<ModuleSplit>builder()
                    .add(baseMasterSplit)
                    .addAll(otherSplits)
                    .build()));

    assertThat(result.getAllApksStream()).containsAllIn(otherSplits);
    ModuleSplit processedBaseMasterSplit =
        result
            .getAllApksStream()
            .filter(module -> module.isMasterSplit() && module.isBaseModuleSplit())
            .collect(onlyElement());

    assertThat(
            processedBaseMasterSplit
                .getAndroidManifest()
                .getMetadataResourceId("com.android.vending.splits"))
        .hasValue(0x7f010000);
    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .containsResource("com.example.app:xml/splits0")
        .withFileReference("res/xml/splits0.xml");

    XmlNode expectedSplitsProtoXml =
        new SplitsProtoXmlBuilder()
            .addLanguageMapping(BundleModuleName.create("module"), "ru", "module.config.ru")
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "ru", "config.ru")
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "fr", "config.fr")
            .build();
    assertThat(
            XmlNode.parseFrom(
                TestUtils.getEntryContent(processedBaseMasterSplit.getEntries().get(0))))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_systemSplits() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "",
            /* masterSplit= */ true,
            SYSTEM,
            languageTargeting("es"));
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "config.ru",
                /* masterSplit= */ false,
                SYSTEM,
                languageTargeting("ru")),
            createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "config.fr",
                /* masterSplit= */ false,
                SYSTEM,
                languageTargeting("fr")),
            createModuleSplit(
                "module",
                /* splitId= */ "module.config.ru",
                /* masterSplit= */ false,
                SYSTEM,
                languageTargeting("ru")));
    GeneratedApks result =
        splitsXmlInjector.process(
            GeneratedApks.fromModuleSplits(
                ImmutableList.<ModuleSplit>builder()
                    .add(baseMasterSplit)
                    .addAll(otherSplits)
                    .build()));

    assertThat(result.getAllApksStream()).containsAllIn(otherSplits);
    ModuleSplit processedBaseMasterSplit =
        result
            .getAllApksStream()
            .filter(module -> module.isMasterSplit() && module.isBaseModuleSplit())
            .collect(onlyElement());

    assertThat(
            processedBaseMasterSplit
                .getAndroidManifest()
                .getMetadataResourceId("com.android.vending.splits"))
        .hasValue(0x7f010000);
    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .containsResource("com.example.app:xml/splits0")
        .withFileReference("res/xml/splits0.xml");

    XmlNode expectedSplitsProtoXml =
        new SplitsProtoXmlBuilder()
            .addLanguageMapping(BundleModuleName.create("module"), "ru", "module.config.ru")
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "ru", "config.ru")
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "fr", "config.fr")
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "es", "")
            .build();
    assertThat(
            XmlNode.parseFrom(
                TestUtils.getEntryContent(processedBaseMasterSplit.getEntries().get(0))))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_standaloneSplitTypes() throws Exception {
    ModuleSplit standalone =
        createModuleSplit(
            BASE_MODULE_NAME,
            /* splitId= */ "",
            /* masterSplit= */ true,
            STANDALONE,
            /* languageTargeting= */ null);
    ResourceTable standaloneResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.example.app")
            .addStringResourceForMultipleLocales(
                "title", ImmutableMap.of("ru-RU", "title ru-RU", "fr", "title fr"))
            .build();
    standalone = standalone.toBuilder().setResourceTable(standaloneResourceTable).build();

    GeneratedApks result =
        splitsXmlInjector.process(GeneratedApks.fromModuleSplits(ImmutableList.of(standalone)));

    ModuleSplit processedStandalone = result.getAllApksStream().collect(onlyElement());

    assertThat(
            processedStandalone
                .getAndroidManifest()
                .getMetadataResourceId("com.android.vending.splits"))
        .hasValue(0x7f020000);
    assertThat(processedStandalone.getResourceTable().get())
        .containsResource("com.example.app:xml/splits0")
        .withFileReference("res/xml/splits0.xml");

    XmlNode expectedSplitsProtoXml =
        new SplitsProtoXmlBuilder()
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "ru", "")
            .addLanguageMapping(BundleModuleName.create(BASE_MODULE_NAME), "fr", "")
            .build();
    assertThat(
            XmlNode.parseFrom(TestUtils.getEntryContent(processedStandalone.getEntries().get(0))))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_fileExists() {
    InMemoryModuleEntry existingInMemoryModuleEntry =
        InMemoryModuleEntry.ofFile("res/xml/splits0.xml", "123".getBytes(UTF_8));

    ModuleSplit baseMasterSplit =
        createModuleSplit(
                BASE_MODULE_NAME,
                /* splitId= */ "",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null)
            .toBuilder()
            .setEntries(ImmutableList.of(existingInMemoryModuleEntry))
            .build();

    ModuleSplit processedBaseMasterSplit =
        splitsXmlInjector
            .process(GeneratedApks.fromModuleSplits(ImmutableList.of(baseMasterSplit)))
            .getAllApksStream()
            .collect(onlyElement());

    assertThat(processedBaseMasterSplit.getEntries()).hasSize(2);
    assertThat(processedBaseMasterSplit.getEntries()).contains(existingInMemoryModuleEntry);
    assertThat(processedBaseMasterSplit.getEntries().get(1).getPath().toString())
        .isEqualTo("res/xml/splits1.xml");
    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .containsResource("com.example.app:xml/splits1")
        .withFileReference("res/xml/splits1.xml");
  }

  private static ModuleSplit createModuleSplit(
      String moduleName,
      String splitId,
      boolean masterSplit,
      SplitType splitType,
      @Nullable LanguageTargeting languageTargeting) {
    return ModuleSplit.builder()
        .setAndroidManifest(
            AndroidManifest.create(androidManifest(PACKAGE_NAME))
                .toEditor()
                .setSplitId(splitId)
                .save())
        .setEntries(ImmutableList.of())
        .setMasterSplit(masterSplit)
        .setSplitType(splitType)
        .setModuleName(BundleModuleName.create(moduleName))
        .setApkTargeting(
            languageTargeting == null
                ? ApkTargeting.getDefaultInstance()
                : ApkTargeting.newBuilder().setLanguageTargeting(languageTargeting).build())
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .build();
  }
}
