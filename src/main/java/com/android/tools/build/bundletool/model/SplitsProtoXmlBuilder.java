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

package com.android.tools.build.bundletool.model;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.TreeBasedTable;

/**
 * Builder of the splits.xml file in proto format.
 *
 * <p>Example:
 *
 * <pre>{@code
 * <splits>
 *   <module name=""> <!-- base module -->
 *     <language>
 *       <entry key="en" split="config.en" />
 *       <entry key="en_AU" split="config.en_AU" />
 *       <entry key="ru" split="config.ru" />
 *       <entry key="fr" split="" /> <!-- included in the master -->
 *     </language>
 *   </module>
 *   <module name="dynamic_feature_1">
 *     <language>
 *       <entry key="en" split="dynamic_feature_1.config.en" />
 *       <entry key="fr" split="dynamic_feature_1" /> <!-- included in the master split -->
 *     </language>
 *   </module>
 * </splits>
 * }</pre>
 */
public final class SplitsProtoXmlBuilder {

  // Stores mapping of <module, language, splitID>.
  private final TreeBasedTable<String, String, String> splitsByModuleAndLanguage =
      TreeBasedTable.create();

  public SplitsProtoXmlBuilder addLanguageMapping(
      BundleModuleName module, String language, String splitId) {
    if (splitsByModuleAndLanguage.contains(module.getNameForSplitId(), language)) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Multiple modules are assigned to the '%s' language.", language)
          .build();
    }
    splitsByModuleAndLanguage.put(module.getNameForSplitId(), language, splitId);
    return this;
  }

  public XmlNode build() {
    XmlProtoElementBuilder splitsXml = XmlProtoElementBuilder.create("splits");

    for (String moduleName : splitsByModuleAndLanguage.rowKeySet()) {
      XmlProtoElementBuilder languageXml = XmlProtoElementBuilder.create("language");
      splitsByModuleAndLanguage
          .row(moduleName)
          .forEach(
              (language, splitId) -> languageXml.addChildElement(createEntry(language, splitId)));

      splitsXml.addChildElement(
          XmlProtoElementBuilder.create("module")
              .addAttribute(XmlProtoAttributeBuilder.create("name").setValueAsString(moduleName))
              .addChildElement(languageXml));
    }
    return XmlProtoNode.createElementNode(splitsXml.build()).getProto();
  }

  private static XmlProtoElementBuilder createEntry(String key, String splitId) {
    return XmlProtoElementBuilder.create("entry")
        .addAttribute(XmlProtoAttributeBuilder.create("key").setValueAsString(key))
        .addAttribute(XmlProtoAttributeBuilder.create("split").setValueAsString(splitId));
  }
}
