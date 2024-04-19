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
package com.android.tools.build.bundletool.model.utils.xmlproto;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.TestData;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlProtoNodeBuilderTest {

  @Test
  public void setElement_overridesElement() {
    XmlProtoNodeBuilder node =
        XmlProtoNode.createElementNode(XmlProtoElement.create("hello")).toBuilder();
    node.setElement(XmlProtoElementBuilder.create("bye"));

    assertThat(node.getProto().build())
        .isEqualTo(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("bye")).build());
  }

  @Test
  public void setElement_overridesText() {
    XmlProtoNodeBuilder node = XmlProtoNode.createTextNode("hello").toBuilder();
    node.setElement(XmlProtoElementBuilder.create("bye"));

    assertThat(node.getProto().build())
        .isEqualTo(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("bye")).build());
  }

  @Test
  public void setText_overridesElement() {
    XmlProtoNodeBuilder node =
        XmlProtoNode.createElementNode(XmlProtoElement.create("hello")).toBuilder();
    node.setText("bye");

    assertThat(node.getProto().build()).isEqualTo(XmlNode.newBuilder().setText("bye").build());
  }

  @Test
  public void setText_overridesText() {
    XmlProtoNodeBuilder node = XmlProtoNode.createTextNode("hello").toBuilder();
    node.setText("bye");

    assertThat(node.getProto().build()).isEqualTo(XmlNode.newBuilder().setText("bye").build());
  }

  @Test
  public void modifyingProtoModifiesAlsoWrapper() {
    XmlNode.Builder protoNode =
        XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("hello"));
    XmlProtoNodeBuilder wrapperNode = new XmlProtoNodeBuilder(protoNode);

    protoNode.getElementBuilder().addChild(XmlNode.newBuilder().setText("text"));

    assertThat(wrapperNode.getElement().getChildText().map(XmlProtoNodeBuilder::build))
        .hasValue(XmlProtoNode.createTextNode("text"));
  }

  @Test
  public void modifyingDeepElementReflectsChangeInParentsToo() throws Exception {
    XmlNode.Builder xmlNode = XmlNode.newBuilder();
    TextFormat.merge(TestData.openReader("testdata/manifest/manifest1.textpb"), xmlNode);

    XmlProtoNodeBuilder node = new XmlProtoNode(xmlNode.build()).toBuilder();
    node.getElement()
        .getChildElement("uses-sdk")
        .getOrCreateAndroidAttribute("minSdkVersion", 0x0101020c)
        .setValueAsDecimalInteger(999);

    assertThat(
            node.build()
                .getProto()
                .getElement()
                .getChildList()
                .stream()
                .filter(childNode -> childNode.getElement().getName().equals("uses-sdk"))
                .collect(onlyElement())
                .getElement()
                .getAttributeList()
                .stream()
                .filter(attribute -> attribute.getName().equals("minSdkVersion"))
                .collect(onlyElement())
                .getCompiledItem()
                .getPrim()
                .getIntDecimalValue())
        .isEqualTo(999);
  }
}
