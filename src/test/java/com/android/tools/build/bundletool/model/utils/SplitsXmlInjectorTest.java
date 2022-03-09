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
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.ARCHIVE;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.INSTANT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SPLIT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.STANDALONE;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SYSTEM;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TestUtils.createModuleEntryForFile;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
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
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SplitsProtoXmlBuilder;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ExtensionRegistry;
import java.util.Collection;
import java.util.Optional;
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
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "",
                /* masterSplit= */ true,
                INSTANT,
                /* languageTargeting= */ null),
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "",
                /* masterSplit= */ true,
                STANDALONE,
                /* languageTargeting= */ null),
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "",
                /* masterSplit= */ false,
                SPLIT,
                /* languageTargeting= */ null),
            createModuleSplit(
                "module",
                /* splitId= */ "",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null),
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "",
                /* masterSplit= */ true,
                ARCHIVE,
                /* languageTargeting= */ null));

    assertThat(xmlInjectorProcess(GeneratedApks.fromModuleSplits(modules)).stream())
        .containsExactlyElementsIn(modules);
  }

  @Test
  public void process() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            BASE_MODULE_NAME.getName(),
            /* splitId= */ "",
            /* masterSplit= */ true,
            SPLIT,
            /* languageTargeting= */ null);
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "config.ru",
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("ru")),
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
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
    GeneratedApks generatedApks =
        GeneratedApks.fromModuleSplits(
            ImmutableList.<ModuleSplit>builder().add(baseMasterSplit).addAll(otherSplits).build());

    assertThat(generatedApks.getAllApksStream()).containsAtLeastElementsIn(otherSplits);
    ModuleSplit processedBaseMasterSplit =
        xmlInjectorProcess(generatedApks).stream()
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
            .addLanguageMapping(BASE_MODULE_NAME, "ru", "config.ru")
            .addLanguageMapping(BASE_MODULE_NAME, "fr", "config.fr")
            .build();
    assertThat(XmlNode.parseFrom(processedBaseMasterSplit.getEntries().get(0).getContent().read()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_noLanguageTargeting() throws Exception {
    ResourceTable baseResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.example.app")
            .addStringResourceForMultipleLocales(
                "title", ImmutableMap.of("ru", "title ru-RU", "fr", "title fr", "es", "title es"))
            .build();
    ModuleSplit baseModule =
        createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null)
            .toBuilder()
            .setResourceTable(baseResourceTable)
            .build();

    ResourceTable featureResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.example.app.module")
            .addStringResourceForMultipleLocales(
                "module_str", ImmutableMap.of("ru", "module ru-RU"))
            .build();
    ModuleSplit featureModule =
        createModuleSplit(
                "module",
                /* splitId= */ "module",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null)
            .toBuilder()
            .setResourceTable(featureResourceTable)
            .build();

    GeneratedApks generatedApks =
        GeneratedApks.fromModuleSplits(ImmutableList.of(baseModule, featureModule));

    ModuleSplit processedBaseMasterSplit =
        xmlInjectorProcess(generatedApks).stream()
            .filter(module -> module.isMasterSplit() && module.isBaseModuleSplit())
            .collect(onlyElement());

    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .containsResource("com.example.app:xml/splits0")
        .withFileReference("res/xml/splits0.xml");

    XmlNode expectedSplitsProtoXml =
        new SplitsProtoXmlBuilder()
            .addLanguageMapping(BundleModuleName.create("module"), "ru", "module")
            .addLanguageMapping(BASE_MODULE_NAME, "ru", "")
            .addLanguageMapping(BASE_MODULE_NAME, "fr", "")
            .addLanguageMapping(BASE_MODULE_NAME, "es", "")
            .build();
    Optional<ModuleEntry> splitsXml = processedBaseMasterSplit.findEntry("res/xml/splits0.xml");
    assertThat(splitsXml).isPresent();

    assertThat(
            XmlNode.parseFrom(
                splitsXml.get().getContent().read(), ExtensionRegistry.getEmptyRegistry()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_systemSplits() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            BASE_MODULE_NAME.getName(),
            /* splitId= */ "",
            /* masterSplit= */ true,
            SYSTEM,
            languageTargeting("es"));
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "config.ru",
                /* masterSplit= */ false,
                SYSTEM,
                languageTargeting("ru")),
            createModuleSplit(
                BASE_MODULE_NAME.getName(),
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
    GeneratedApks generatedApks =
        GeneratedApks.fromModuleSplits(
            ImmutableList.<ModuleSplit>builder().add(baseMasterSplit).addAll(otherSplits).build());

    assertThat(generatedApks.getAllApksStream()).containsAtLeastElementsIn(otherSplits);
    ModuleSplit processedBaseMasterSplit =
        xmlInjectorProcess(generatedApks).stream()
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
            .addLanguageMapping(BASE_MODULE_NAME, "ru", "config.ru")
            .addLanguageMapping(BASE_MODULE_NAME, "fr", "config.fr")
            .addLanguageMapping(BASE_MODULE_NAME, "es", "")
            .build();
    assertThat(XmlNode.parseFrom(processedBaseMasterSplit.getEntries().get(0).getContent().read()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_standaloneSplitTypes() throws Exception {
    ModuleSplit standalone =
        createModuleSplit(
            BASE_MODULE_NAME.getName(),
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

    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(ImmutableList.of(standalone));

    ModuleSplit processedStandalone =
        xmlInjectorProcess(generatedApks).stream().collect(onlyElement());

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
            .addLanguageMapping(BASE_MODULE_NAME, "ru", "")
            .addLanguageMapping(BASE_MODULE_NAME, "fr", "")
            .build();
    assertThat(XmlNode.parseFrom(processedStandalone.getEntries().get(0).getContent().read()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedSplitsProtoXml);
  }

  @Test
  public void process_archiveSplitTypes() throws Exception {
    ModuleSplit archived =
        createModuleSplit(
            BASE_MODULE_NAME.getName(),
            /* splitId= */ "",
            /* masterSplit= */ true,
            SplitType.ARCHIVE,
            /* languageTargeting= */ null);
    ResourceTable archivedResourceTable =
        new ResourceTableBuilder()
            .addPackage("com.example.app")
            .addStringResourceForMultipleLocales(
                "title", ImmutableMap.of("ru-RU", "title ru-RU", "fr", "title fr"))
            .build();
    archived = archived.toBuilder().setResourceTable(archivedResourceTable).build();

    GeneratedApks generatedApks = GeneratedApks.fromModuleSplits(ImmutableList.of(archived));

    ModuleSplit processedArchivedApk =
        xmlInjectorProcess(generatedApks).stream().collect(onlyElement());

    assertThat(
            processedArchivedApk
                .getAndroidManifest()
                .getMetadataResourceId("com.android.vending.splits"))
        .isEmpty();
  }

  @Test
  public void process_fileExists() {
    ModuleEntry existingModuleEntry =
        createModuleEntryForFile("res/xml/splits0.xml", "123".getBytes(UTF_8));

    ModuleSplit baseMasterSplit =
        createModuleSplit(
                BASE_MODULE_NAME.getName(),
                /* splitId= */ "",
                /* masterSplit= */ true,
                SPLIT,
                /* languageTargeting= */ null)
            .toBuilder()
            .setEntries(ImmutableList.of(existingModuleEntry))
            .build();

    ModuleSplit processedBaseMasterSplit =
        xmlInjectorProcess(GeneratedApks.fromModuleSplits(ImmutableList.of(baseMasterSplit)))
            .stream()
            .collect(onlyElement());

    assertThat(processedBaseMasterSplit.getEntries()).hasSize(2);
    assertThat(processedBaseMasterSplit.getEntries()).contains(existingModuleEntry);
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

  private ImmutableList<ModuleSplit> xmlInjectorProcess(GeneratedApks generatedApks) {
    return generatedApks.getAllApksGroupedByOrderedVariants().asMap().entrySet().stream()
        .map(keySplit -> splitsXmlInjector.process(keySplit.getKey(), keySplit.getValue()))
        .flatMap(Collection::stream)
        .collect(toImmutableList());
  }
}
