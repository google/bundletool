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

package com.android.tools.build.bundletool.utils;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.InMemoryModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceInjector;
import com.android.tools.build.bundletool.model.SplitsProtoXmlBuilder;
import com.android.tools.build.bundletool.model.VariantKey;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Injects a splits{N}.xml resource in master base module with language -> split ID mapping.
 *
 * <p>It is done only for split APKs, not instant APKs nor standalone APKs.
 */
public class SplitsXmlInjector {

  private static final String XML_TYPE_NAME = "xml";
  private static final String XML_PATH_PATTERN = "res/xml/splits%s.xml";
  private static final String METADATA_KEY = "com.android.vending.splits";

  public GeneratedApks process(GeneratedApks generatedApks) {
    // A variant is a set of APKs. One device is guaranteed to receive only APKs from the same. This
    // is why we are processing split.xml for each variant separately.
    return GeneratedApks.fromModuleSplits(
        generatedApks.getAllApksGroupedByOrderedVariants().asMap().entrySet().stream()
            .map(
                keySplit ->
                    isSplitApkVariant(keySplit.getKey())
                        ? processVariantModuleList(keySplit.getValue())
                        : keySplit.getValue())
            .flatMap(Collection::stream)
            .collect(toImmutableList()));
  }

  private ImmutableList<ModuleSplit> processVariantModuleList(Collection<ModuleSplit> splits) {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    for (ModuleSplit split : splits) {
      String splitId = split.getAndroidManifest().getSplitId().orElse("");
      for (String language : split.getApkTargeting().getLanguageTargeting().getValueList()) {
        splitsProtoXmlBuilder.addLanguageMapping(split.getModuleName(), language, splitId);
      }
    }

    XmlNode splitsXmlContent = splitsProtoXmlBuilder.build();
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

  private ModuleSplit injectSplitsXml(ModuleSplit split, XmlNode xmlNode) {
    ZipPath resourcePath = getUniqueResourcePath(split);

    ResourceInjector resourceInjector = ResourceInjector.fromModuleSplit(split);
    ResourceId resourceId =
        resourceInjector.addResource(XML_TYPE_NAME, createXmlEntry(resourcePath));

    return split
        .toBuilder()
        .setResourceTable(resourceInjector.build())
        .setEntries(
            ImmutableList.<ModuleEntry>builder()
                .addAll(split.getEntries())
                .add(InMemoryModuleEntry.ofFile(resourcePath, xmlNode.toByteArray()))
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

  private static boolean isSplitApkVariant(VariantKey split) {
    return split.getSplitType() == SplitType.SPLIT;
  }
}
