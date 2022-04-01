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

import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

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
import com.android.tools.build.bundletool.model.VariantKey;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;

/**
 * Adds a localeConfig attribute within <Application> in Android Manifest and injects a
 * locales_config.xml resource to the master base module when splitting APKs by language.
 *
 * <p>The format of the localeConfig attribute is as the following: <application
 * android:localeConfig="@xml/locales_config>
 *
 * <p>The format of the locales_config xml is as the following: <locale-config> <locale
 * android:name="en-us"/> <locale android:name="pt"/> ... </locale-config>
 *
 * <p>It is done only for split and system APKs, not standalone and instant APKs.
 */
public class LocaleConfigXmlInjector {
  private static final String XML_TYPE_NAME = "xml";
  private static final String RESOURCE_PATH = "res/xml/locales_config.xml";
  private static final String RESOURCE_FILE_NAME = "locales_config";
  private static final String LOCALE_CONFIG_ELEMENT = "locale-config";
  private static final String LOCALE_ELEMENT = "locale";

  public ImmutableList<ModuleSplit> process(
      VariantKey variantKey, ImmutableList<ModuleSplit> splits) {
    switch (variantKey.getSplitType()) {
      case SYSTEM:
        // Injection for system APK variant is same as split APK variant as both
        // contain single base-master split which is always installed and additional
        // splits. In case of system APK variant base-master split is the fused system
        // split and splits are unmatched language splits.
      case SPLIT:
        return processSplitApkVariant(splits);
        // No need to inject a locales_config.xml if resources with all languages are
        // available inside the one APK.
      case STANDALONE:
      case INSTANT:
      case ARCHIVE:
        return splits;
      case ASSET_SLICE:
        throw new IllegalStateException("Unexpected Asset Slice inside variant.");
    }
    throw new IllegalStateException(
        String.format("Unknown split type %s", variantKey.getSplitType()));
  }

  private static ImmutableList<ModuleSplit> processSplitApkVariant(
      ImmutableList<ModuleSplit> splits) {
    // Only inject a locales_config.xml to the base module splits
    boolean hasLanguageSplits =
        splits.stream()
            .anyMatch(
                split ->
                    split.isBaseModuleSplit() && split.getApkTargeting().hasLanguageTargeting());

    XmlNode localesXmlContent = createLocalesXmlNode(splits);

    ImmutableList.Builder<ModuleSplit> result = new ImmutableList.Builder<>();

    for (ModuleSplit split : splits) {
      // Inject a locales_config.xml when all of the following conditions are met:
      // 1. It's a master split
      // 2. It's a base module split
      // 3. It has the resource table
      // 4. The localeConfig key was not added in the APP’s Manifest.
      // 5. The language split configuration was enabled.
      // 6. The locales_config.xml doesn't exist in the resources(res/xml/).
      if (split.isMasterSplit()
          && split.isBaseModuleSplit()
          && split.getResourceTable().isPresent()
          && hasLanguageSplits
          && !split.getAndroidManifest().hasLocaleConfig()
          && !split.findEntry(ZipPath.create(RESOURCE_PATH)).isPresent()) {
        result.add(injectLocaleConfigXml(split, localesXmlContent));
      } else {
        result.add(split);
      }
    }
    return result.build();
  }

  private static ModuleSplit injectLocaleConfigXml(ModuleSplit split, XmlNode xmlNode) {

    ResourceInjector resourceInjector = ResourceInjector.fromModuleSplit(split);
    ResourceId resourceId = resourceInjector.addResource(XML_TYPE_NAME, createXmlEntry());

    ModuleEntry localesConfigEntry = addLocalesConfigEntry(xmlNode);

    return split.toBuilder()
        .setResourceTable(resourceInjector.build())
        // Inject a locales_config.xml
        .setEntries(
            ImmutableList.<ModuleEntry>builder()
                .addAll(split.getEntries())
                .add(localesConfigEntry)
                .build())
        // Add a localeConfig key to the Manifest pointing to an XML file where the locales
        // configuration were stored.
        .setAndroidManifest(
            split
                .getAndroidManifest()
                .toEditor()
                .setLocaleConfig(resourceId.getFullResourceId())
                .save())
        .build();
  }

  private static ModuleEntry addLocalesConfigEntry(XmlNode xmlNode) {
    return ModuleEntry.builder()
        .setPath(ZipPath.create(RESOURCE_PATH))
        .setContent(ByteSource.wrap(xmlNode.toByteArray()))
        .build();
  }

  public static XmlNode createLocalesXmlNode(ImmutableList<ModuleSplit> splits) {
    // Get all locales from the base module splits
    ImmutableSet<String> allLocales = getLocalesFromBaseModuleSplits(splits);

    XmlProtoElementBuilder localesConfigXml = XmlProtoElementBuilder.create(LOCALE_CONFIG_ELEMENT);

    allLocales.stream()
        .filter(locale -> !locale.isEmpty())
        .forEach(locale -> localesConfigXml.addChildElement(createAttributes(locale)));

    return XmlProtoNode.createElementNode(localesConfigXml.build()).getProto();
  }

  private static ImmutableSet<String> getLocalesFromBaseModuleSplits(
      ImmutableList<ModuleSplit> splits) {
    // TODO("b/213543969"): Bundletool is infeasible to detect the set of languages a developer
    // explicitly supports if the app has dependencies which themselves provide extra resources
    // from libraries such as Android X or Jetpack in many languages. The bug is created for the
    // gradle to add a separate file with the resource source information, rather than including it
    // in the ResourceTable.
    return splits.stream()
        // Only inject a locales_config.xml to the base module splits
        .filter(ModuleSplit::isBaseModuleSplit)
        .filter(split -> split.getResourceTable().isPresent())
        .flatMap(split -> ResourcesUtils.getAllLocales(split.getResourceTable().get()).stream())
        .filter(locale -> !locale.isEmpty())
        .collect(toImmutableSet());
  }

  private static XmlProtoElementBuilder createAttributes(String locale) {
    return XmlProtoElementBuilder.create(LOCALE_ELEMENT)
        .addAttribute(
            XmlProtoAttributeBuilder.createAndroidAttribute(
                NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                .setValueAsString(locale));
  }

  private static Entry createXmlEntry() {
    return Entry.newBuilder()
        // Set filename without ".xml" as resource name.
        .setName(RESOURCE_FILE_NAME)
        .addConfigValue(
            ConfigValue.newBuilder()
                .setValue(
                    Value.newBuilder()
                        .setItem(
                            Item.newBuilder()
                                .setFile(
                                    FileReference.newBuilder()
                                        .setPath(RESOURCE_PATH)
                                        .setType(FileReference.Type.PROTO_XML)))))
        .build();
  }
}
