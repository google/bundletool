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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceInjector;
import com.android.tools.build.bundletool.model.SplitsProtoXmlBuilder;
import com.android.tools.build.bundletool.model.VariantKey;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Injects a splits{N}.xml resource in master base module with language -> split ID mapping.
 *
 * <p>It is done only for split APK and standalone APKs, not instant APKs.
 */
public class SplitsXmlInjector {

  private static final String XML_TYPE_NAME = "xml";
  private static final String XML_PATH_PATTERN = "res/xml/splits%s.xml";
  private static final String METADATA_KEY = "com.android.vending.splits";

  public ImmutableList<ModuleSplit> process(VariantKey variantKey, Collection<ModuleSplit> splits) {
    switch (variantKey.getSplitType()) {
      case SYSTEM:
        // Injection for system APK variant is same as split APK variant as both
        // contain single base-master split which is always installed and additional
        // splits. In case of system APK variant base-master split is the fused system
        // split and splits are unmatched language splits.
      case SPLIT:
        return processSplitApkVariant(splits);
      case STANDALONE:
        return splits.stream()
            .map(SplitsXmlInjector::processStandaloneVariant)
            .collect(toImmutableList());
      case INSTANT:
      case ARCHIVE:
        return ImmutableList.copyOf(splits);
      case ASSET_SLICE:
        throw new IllegalStateException("Unexpected Asset Slice inside variant.");
    }
    throw new IllegalStateException(
        String.format("Unknown split type %s", variantKey.getSplitType()));
  }

  private static ModuleSplit processStandaloneVariant(ModuleSplit split) {
    if (!split.getResourceTable().isPresent()) {
      return split;
    }

    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    addLanguageSplitsFromResourceTable(splitsProtoXmlBuilder, split);
    return injectSplitsXml(split, splitsProtoXmlBuilder.build());
  }

  private static ImmutableList<ModuleSplit> processSplitApkVariant(Collection<ModuleSplit> splits) {
    boolean hasLanguageSplits =
        splits.stream().anyMatch(split -> split.getApkTargeting().hasLanguageTargeting());

    // If language splits are available we gather language mappings from these splits otherwise this
    // means that language splits are not generated and we need to gather all languages from
    // resource table of master splits.
    // We need to check splits targeting and can not rely on disabled language dimension because in
    // case of system variant it is possible to generate splits that don't have language targeting
    // for bundles with enabled language dimension.
    XmlNode splitsXmlContent =
        hasLanguageSplits
            ? getSplitsXmlContentFromLanguageTargeting(splits)
            : getSplitsXmlContentFromResourceTables(splits);

    ImmutableList.Builder<ModuleSplit> result = new ImmutableList.Builder<>();

    for (ModuleSplit split : splits) {
      if (split.isMasterSplit() && split.isBaseModuleSplit()) {
        result.add(injectSplitsXml(split, splitsXmlContent));
      } else {
        result.add(split);
      }
    }
    return result.build();
  }

  private static XmlNode getSplitsXmlContentFromLanguageTargeting(Collection<ModuleSplit> splits) {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    for (ModuleSplit split : splits) {
      String splitId = split.getAndroidManifest().getSplitId().orElse("");
      for (String language : split.getApkTargeting().getLanguageTargeting().getValueList()) {
        splitsProtoXmlBuilder.addLanguageMapping(split.getModuleName(), language, splitId);
      }
    }
    return splitsProtoXmlBuilder.build();
  }

  private static XmlNode getSplitsXmlContentFromResourceTables(Collection<ModuleSplit> splits) {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    splits.stream()
        .filter(ModuleSplit::isMasterSplit)
        .forEach(split -> addLanguageSplitsFromResourceTable(splitsProtoXmlBuilder, split));
    return splitsProtoXmlBuilder.build();
  }

  private static void addLanguageSplitsFromResourceTable(
      SplitsProtoXmlBuilder splitsProtoXmlBuilder, ModuleSplit split) {
    if (!split.getResourceTable().isPresent()) {
      return;
    }
    String splitId = split.getAndroidManifest().getSplitId().orElse("");
    ResourcesUtils.getAllLanguages(split.getResourceTable().get()).stream()
        .filter(language -> !language.isEmpty())
        .forEach(
            language ->
                splitsProtoXmlBuilder.addLanguageMapping(split.getModuleName(), language, splitId));
  }

  private static ModuleSplit injectSplitsXml(ModuleSplit split, XmlNode xmlNode) {
    ZipPath resourcePath = getUniqueResourcePath(split);

    ResourceInjector resourceInjector = ResourceInjector.fromModuleSplit(split);
    ResourceId resourceId =
        resourceInjector.addResource(XML_TYPE_NAME, createXmlEntry(resourcePath));

    return split.toBuilder()
        .setResourceTable(resourceInjector.build())
        .setEntries(
            ImmutableList.<ModuleEntry>builder()
                .addAll(split.getEntries())
                .add(
                    ModuleEntry.builder()
                        .setPath(resourcePath)
                        .setContent(ByteSource.wrap(xmlNode.toByteArray()))
                        .build())
                .build())
        .setAndroidManifest(
            split
                .getAndroidManifest()
                .toEditor()
                .addMetaDataResourceId(METADATA_KEY, resourceId.getFullResourceId())
                .save())
        .build();
  }

  private static Entry createXmlEntry(ZipPath resourcePath) {
    return Entry.newBuilder()
        // Set filename without ".xml" as resource name.
        .setName(resourcePath.getFileName().toString().split("\\.", 2)[0])
        .addConfigValue(
            ConfigValue.newBuilder()
                .setValue(
                    Value.newBuilder()
                        .setItem(
                            Item.newBuilder()
                                .setFile(
                                    FileReference.newBuilder()
                                        .setPath(resourcePath.toString())
                                        .setType(FileReference.Type.PROTO_XML)))))
        .build();
  }

  private static ZipPath getUniqueResourcePath(ModuleSplit split) {
    return Stream.iterate(0, i -> i + 1)
        .map(number -> ZipPath.create(String.format(XML_PATH_PATTERN, number)))
        .filter(path -> !split.findEntry(path).isPresent())
        .findFirst()
        .get();
  }
}
