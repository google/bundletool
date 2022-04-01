/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SPLIT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SYSTEM;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.truth.resources.TruthResourceTable.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.VariantKey;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ExtensionRegistry;
import java.util.Arrays;
import java.util.HashSet;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class LocaleConfigXmlInjectorTest {
  private static final String PACKAGE_NAME = "com.example.app.module";
  private LocaleConfigXmlInjector localeConfigXmlInjector;

  @Before
  public void setUp() {
    localeConfigXmlInjector = new LocaleConfigXmlInjector();
  }

  @Test
  public void process() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            new ResourceTableBuilder().addPackage("com.example.app.module").build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ true,
            SPLIT,
            /* languageTargeting= */ null);
    ModuleSplit otherSplitA =
        createModuleSplit(
            new ResourceTableBuilder()
                .addPackage("com.example.app.module")
                .addStringResourceForMultipleLocales(
                    "module", ImmutableMap.of("ru-RU", "module ru-RU"))
                .build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ false,
            SPLIT,
            languageTargeting("ru"));
    ModuleSplit otherSplitB =
        createModuleSplit(
            new ResourceTableBuilder()
                .addPackage("com.example.app.module")
                .addStringResourceForMultipleLocales("module", ImmutableMap.of("fr", "module fr"))
                .build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ false,
            SPLIT,
            languageTargeting("fr"));
    ModuleSplit otherSplitC =
        createModuleSplit(
            new ResourceTableBuilder()
                .addPackage("com.example.app.module")
                .addStringResourceForMultipleLocales(
                    "module", ImmutableMap.of("en-AU", "module en-AU"))
                .build(),
            "module",
            /* masterSplit= */ false,
            SPLIT,
            languageTargeting("en"));
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(otherSplitA, otherSplitB, otherSplitC);

    ImmutableList<ModuleSplit> processedSplits =
        localeConfigXmlInjector.process(
            VariantKey.create(baseMasterSplit),
            ImmutableList.<ModuleSplit>builder().add(baseMasterSplit).addAll(otherSplits).build());

    ModuleSplit processedBaseMasterSplit =
        processedSplits.stream().filter(ModuleSplit::isMasterSplit).collect(onlyElement());

    assertThat(processedBaseMasterSplit.getAndroidManifest().hasLocaleConfig()).isTrue();

    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .containsResource("com.example.app.module:xml/locales_config")
        .withFileReference("res/xml/locales_config.xml");

    XmlNode expectedLocaleConfigProtoXml =
        createLocalesXmlNode(new HashSet<>(Arrays.asList("ru-RU", "fr")));

    assertThat(
            XmlNode.parseFrom(
                processedBaseMasterSplit.getEntries().get(0).getContent().read(),
                ExtensionRegistry.getEmptyRegistry()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedLocaleConfigProtoXml);

    ImmutableList<ModuleSplit> processedOtherSplits =
        processedSplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(processedOtherSplits).containsExactly(otherSplitA, otherSplitB, otherSplitC);
  }

  @Test
  public void process_noLanguageTargeting() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            new ResourceTableBuilder().addPackage("com.example.app.module").build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ true,
            SPLIT,
            /* languageTargeting= */ null);
    ModuleSplit otherSplit =
        createModuleSplit(
            new ResourceTableBuilder().addPackage("com.example.app.module").build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ false,
            SPLIT,
            /* languageTargeting= */ null);
    ModuleSplit processedBaseMasterSplit =
        localeConfigXmlInjector
            .process(
                VariantKey.create(baseMasterSplit), ImmutableList.of(baseMasterSplit, otherSplit))
            .stream()
            .filter(split -> split.isMasterSplit() && split.isBaseModuleSplit())
            .collect(onlyElement());

    assertThat(processedBaseMasterSplit.getAndroidManifest().hasLocaleConfig()).isFalse();

    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .doesNotContainResource("com.example.app.module:xml/locales_config");
  }

  @Test
  public void process_duplicateLocales() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            new ResourceTableBuilder().addPackage("com.example.app.module").build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ true,
            SPLIT,
            /* languageTargeting= */ null);
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(
            createModuleSplit(
                new ResourceTableBuilder()
                    .addPackage("com.example.app.module")
                    .addStringResourceForMultipleLocales(
                        "module", ImmutableMap.of("ru-RU", "module ru-RU"))
                    .build(),
                BASE_MODULE_NAME.getName(),
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("ru")),
            createModuleSplit(
                new ResourceTableBuilder()
                    .addPackage("com.example.app.module")
                    .addStringResourceForMultipleLocales(
                        "module", ImmutableMap.of("ru-RU", "module ru-RU"))
                    .build(),
                BASE_MODULE_NAME.getName(),
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("ru")),
            createModuleSplit(
                new ResourceTableBuilder()
                    .addPackage("com.example.app.module")
                    .addStringResourceForMultipleLocales(
                        "module", ImmutableMap.of("fr", "module fr"))
                    .build(),
                BASE_MODULE_NAME.getName(),
                /* masterSplit= */ false,
                SPLIT,
                languageTargeting("fr")));

    ModuleSplit processedBaseMasterSplit =
        localeConfigXmlInjector
            .process(
                VariantKey.create(baseMasterSplit),
                ImmutableList.<ModuleSplit>builder()
                    .add(baseMasterSplit)
                    .addAll(otherSplits)
                    .build())
            .stream()
            .filter(split -> split.isMasterSplit() && split.isBaseModuleSplit())
            .collect(onlyElement());
    XmlNode expectedLocaleConfigProtoXml =
        createLocalesXmlNode(new HashSet<>(Arrays.asList("ru-RU", "fr")));

    assertThat(
            XmlNode.parseFrom(
                processedBaseMasterSplit.getEntries().get(0).getContent().read(),
                ExtensionRegistry.getEmptyRegistry()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedLocaleConfigProtoXml);
  }

  @Test
  public void process_systemSplits() throws Exception {
    ModuleSplit baseMasterSplit =
        createModuleSplit(
            new ResourceTableBuilder().addPackage("com.example.app.module").build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ true,
            SYSTEM,
            /* languageTargeting= */ null);
    ModuleSplit otherSplitA =
        createModuleSplit(
            new ResourceTableBuilder()
                .addPackage("com.example.app.module")
                .addStringResourceForMultipleLocales(
                    "module", ImmutableMap.of("ru-RU", "module ru-RU"))
                .build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ false,
            SYSTEM,
            languageTargeting("ru"));
    ModuleSplit otherSplitB =
        createModuleSplit(
            new ResourceTableBuilder()
                .addPackage("com.example.app.module")
                .addStringResourceForMultipleLocales("module", ImmutableMap.of("fr", "module fr"))
                .build(),
            BASE_MODULE_NAME.getName(),
            /* masterSplit= */ false,
            SYSTEM,
            languageTargeting("fr"));
    ModuleSplit otherSplitC =
        createModuleSplit(
            new ResourceTableBuilder()
                .addPackage("com.example.app.module")
                .addStringResourceForMultipleLocales(
                    "module", ImmutableMap.of("en-AU", "module en-AU"))
                .build(),
            "module",
            /* masterSplit= */ false,
            SYSTEM,
            languageTargeting("en"));
    ImmutableList<ModuleSplit> otherSplits =
        ImmutableList.of(otherSplitA, otherSplitB, otherSplitC);

    ImmutableList<ModuleSplit> processedSplits =
        localeConfigXmlInjector.process(
            VariantKey.create(baseMasterSplit),
            ImmutableList.<ModuleSplit>builder().add(baseMasterSplit).addAll(otherSplits).build());

    ModuleSplit processedBaseMasterSplit =
        processedSplits.stream().filter(ModuleSplit::isMasterSplit).collect(onlyElement());

    assertThat(processedBaseMasterSplit.getAndroidManifest().hasLocaleConfig()).isTrue();

    assertThat(processedBaseMasterSplit.getResourceTable().get())
        .containsResource("com.example.app.module:xml/locales_config")
        .withFileReference("res/xml/locales_config.xml");

    XmlNode expectedLocaleConfigProtoXml =
        createLocalesXmlNode(new HashSet<>(Arrays.asList("ru-RU", "fr")));

    assertThat(
            XmlNode.parseFrom(
                processedBaseMasterSplit.getEntries().get(0).getContent().read(),
                ExtensionRegistry.getEmptyRegistry()))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(expectedLocaleConfigProtoXml);

    ImmutableList<ModuleSplit> processedOtherSplits =
        processedSplits.stream().filter(split -> !split.isMasterSplit()).collect(toImmutableList());
    assertThat(processedOtherSplits).containsExactly(otherSplitA, otherSplitB, otherSplitC);
  }

  private static ModuleSplit createModuleSplit(
      ResourceTable resourceTable,
      String moduleName,
      boolean masterSplit,
      SplitType splitType,
      @Nullable LanguageTargeting languageTargeting) {
    return ModuleSplit.builder()
        .setAndroidManifest(AndroidManifest.create(androidManifest(PACKAGE_NAME)))
        .setResourceTable(resourceTable)
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

  private static XmlNode createLocalesXmlNode(HashSet<String> locales) {
    XmlProtoElementBuilder localesConfigXml = XmlProtoElementBuilder.create("locale-config");

    locales.forEach(locale -> localesConfigXml.addChildElement(createAttributes(locale)));

    return XmlProtoNode.createElementNode(localesConfigXml.build()).getProto();
  }

  private static XmlProtoElementBuilder createAttributes(String locale) {
    return XmlProtoElementBuilder.create("locale")
        .addAttribute(
            XmlProtoAttributeBuilder.createAndroidAttribute("name", 0x01010003)
                .setValueAsString(locale));
  }
}
