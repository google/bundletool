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

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the {@link SplitsProtoXmlBuilder} class. */
@RunWith(JUnit4.class)
public final class SplitsProtoXmlBuilderTest {

  @Test
  public void buildEmpty() {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    XmlNode rootNode = splitsProtoXmlBuilder.build();
    // Answer:
    // <splits>
    // </splits>
    XmlProtoElementBuilder actual = XmlProtoElementBuilder.create("splits");
    assertThat(rootNode).isEqualTo(getProto(actual));
  }

  @Test
  public void buildOneModule() {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    splitsProtoXmlBuilder.addLanguageMapping(
        BundleModuleName.create("module"),
        /* language = */ "en",
        /* splitId = */ "module.config.en");
    XmlNode rootNode = splitsProtoXmlBuilder.build();
    // Answer:
    // <splits>
    //   <module name="module">
    //     <language>
    //       <entry key="en" split="module.config.en">
    //     </language>
    //   </module>
    // </splits>

    XmlProtoElementBuilder actual =
        XmlProtoElementBuilder.create("splits")
            .addChildElement(
                XmlProtoElementBuilder.create("module")
                    .addAttribute(createAttribute("name", "module"))
                    .addChildElement(
                        XmlProtoElementBuilder.create("language")
                            .addChildElement(
                                XmlProtoElementBuilder.create("entry")
                                    .addAttribute(createAttribute("key", "en"))
                                    .addAttribute(createAttribute("split", "module.config.en")))));
    assertThat(rootNode).isEqualTo(getProto(actual));
  }

  @Test
  public void buildManyModules() {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    splitsProtoXmlBuilder.addLanguageMapping(
        BASE_MODULE_NAME, /* language = */ "ru", /* splitId = */ "config.ru");
    splitsProtoXmlBuilder.addLanguageMapping(
        BundleModuleName.create("module"),
        /* language = */ "en",
        /* splitId = */ "module.config.en");
    XmlNode rootNode = splitsProtoXmlBuilder.build();
    // Answer:
    // <splits>
    //   <module name="">
    //     <language>
    //       <entry key="ru" split="config.ru">
    //     </language>
    //   </module>
    //   <module name="module">
    //     <language>
    //       <entry key="en" split="module.config.en">
    //     </language>
    //   </module>
    // </splits>

    assertThat(rootNode.getElement().getName()).isEqualTo("splits");
    assertThat(rootNode.getElement().getChildList())
        .containsExactly(
            getProto(
                XmlProtoElementBuilder.create("module")
                    .addAttribute(createAttribute("name", "module"))
                    .addChildElement(
                        XmlProtoElementBuilder.create("language")
                            .addChildElement(
                                XmlProtoElementBuilder.create("entry")
                                    .addAttribute(createAttribute("key", "en"))
                                    .addAttribute(createAttribute("split", "module.config.en"))))),
            getProto(
                XmlProtoElementBuilder.create("module")
                    .addAttribute(createAttribute("name", ""))
                    .addChildElement(
                        XmlProtoElementBuilder.create("language")
                            .addChildElement(
                                XmlProtoElementBuilder.create("entry")
                                    .addAttribute(createAttribute("key", "ru"))
                                    .addAttribute(createAttribute("split", "config.ru"))))));
  }

  @Test
  public void buildManyEntries() {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    splitsProtoXmlBuilder.addLanguageMapping(
        BASE_MODULE_NAME, /* language = */ "ru", /* splitId = */ "config.ru");

    splitsProtoXmlBuilder.addLanguageMapping(
        BASE_MODULE_NAME, /* language = */ "en", /* splitId = */ "");

    splitsProtoXmlBuilder.addLanguageMapping(
        BASE_MODULE_NAME, /* language = */ "fr", /* splitId = */ "config.fr");

    XmlNode rootNode = splitsProtoXmlBuilder.build();
    // Answer:
    // <splits>
    //   <module name="">
    //     <language>
    //       <entry key="ru" split="config.ru">
    //       <entry key="en" split="">
    //       <entry key="fr" split="config.fr">
    //     </language>
    //   </module>
    // </splits>

    // Checking base structure.
    assertThat(rootNode.getElement().getName()).isEqualTo("splits");
    assertThat(rootNode.getElement().getChildCount()).isEqualTo(1);
    assertThat(rootNode.getElement().getChild(0).getElement().getName()).isEqualTo("module");
    assertThat(rootNode.getElement().getChild(0).getElement().getAttributeList())
        .containsExactly(createAttribute("name", "").getProto().build());

    // Checking entries of splits[0][0] (splits -> module -> language).
    assertThat(
            rootNode.getElement().getChild(0).getElement().getChild(0).getElement().getChildList())
        .containsExactly(
            getProto(
                XmlProtoElementBuilder.create("entry")
                    .addAttribute(createAttribute("key", "ru"))
                    .addAttribute(createAttribute("split", "config.ru"))),
            getProto(
                XmlProtoElementBuilder.create("entry")
                    .addAttribute(createAttribute("key", "en"))
                    .addAttribute(createAttribute("split", ""))),
            getProto(
                XmlProtoElementBuilder.create("entry")
                    .addAttribute(createAttribute("key", "fr"))
                    .addAttribute(createAttribute("split", "config.fr"))));
  }

  @Test
  public void buildsMultipleModuleEntries_stableOrder() {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();

    splitsProtoXmlBuilder.addLanguageMapping(BundleModuleName.create("a"), "en", "a.en");
    splitsProtoXmlBuilder.addLanguageMapping(BundleModuleName.create("a"), "fr", "a.fr");
    splitsProtoXmlBuilder.addLanguageMapping(BundleModuleName.create("b"), "en", "a.en");
    splitsProtoXmlBuilder.addLanguageMapping(BundleModuleName.create("b"), "fr", "a.fr");

    SplitsProtoXmlBuilder splitsProtoXmlBuilderDifferentOrder = new SplitsProtoXmlBuilder();
    splitsProtoXmlBuilderDifferentOrder.addLanguageMapping(
        BundleModuleName.create("b"), "fr", "a.fr");
    splitsProtoXmlBuilderDifferentOrder.addLanguageMapping(
        BundleModuleName.create("b"), "en", "a.en");
    splitsProtoXmlBuilderDifferentOrder.addLanguageMapping(
        BundleModuleName.create("a"), "en", "a.en");
    splitsProtoXmlBuilderDifferentOrder.addLanguageMapping(
        BundleModuleName.create("a"), "fr", "a.fr");

    assertThat(splitsProtoXmlBuilder.build())
        .isEqualTo(splitsProtoXmlBuilderDifferentOrder.build());
  }

  @Test
  public void buildMultipleAssign_throws() {
    SplitsProtoXmlBuilder splitsProtoXmlBuilder = new SplitsProtoXmlBuilder();
    splitsProtoXmlBuilder.addLanguageMapping(
        BASE_MODULE_NAME, /* language = */ "ru", /* splitId = */ "config.ru");

    splitsProtoXmlBuilder.addLanguageMapping(
        BASE_MODULE_NAME, /* language = */ "en", /* splitId = */ "");

    assertThrows(
        CommandExecutionException.class,
        () ->
            splitsProtoXmlBuilder.addLanguageMapping(
                BASE_MODULE_NAME, /* language = */ "ru", /* splitId = */ "config.ru"));
  }

  private static XmlProtoAttributeBuilder createAttribute(String key, String value) {
    return XmlProtoAttributeBuilder.create(key).setValueAsString(value);
  }

  private static XmlNode getProto(XmlProtoElementBuilder element) {
    return XmlProtoNode.createElementNode(element.build()).getProto();
  }
}
