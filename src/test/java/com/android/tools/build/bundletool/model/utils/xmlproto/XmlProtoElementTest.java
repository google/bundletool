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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlProtoElementTest {

  @Test
  public void createElement_withName() {
    XmlProtoElement element = XmlProtoElement.create("hello");
    assertThat(element.getProto()).isEqualTo(XmlElement.newBuilder().setName("hello").build());
  }

  @Test
  public void createElement_withNameAndNamespace() {
    XmlProtoElement element = XmlProtoElement.create("namespace", "hello");
    assertThat(element.getProto())
        .isEqualTo(XmlElement.newBuilder().setName("hello").setNamespaceUri("namespace").build());
  }

  @Test
  public void toBuilderAndBack_unchanged() {
    XmlProtoElement element = XmlProtoElement.create("namespace", "hello");
    assertThat(element.toBuilder().build()).isEqualTo(element);
  }

  @Test
  public void getName() {
    XmlProtoElement element = XmlProtoElement.create("namespace", "hello");
    assertThat(element.getName()).isEqualTo("hello");
  }

  @Test
  public void getNamespace() {
    XmlProtoElement element = XmlProtoElement.create("namespace", "hello");
    assertThat(element.getNamespaceUri()).isEqualTo("namespace");
  }

  @Test
  public void getAttributes() {
    XmlAttribute attribute1 = XmlAttribute.newBuilder().setName("test").build();
    XmlAttribute attribute2 = XmlAttribute.newBuilder().setName("test2").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addAttribute(attribute1)
                .addAttribute(attribute2)
                .build());

    assertThat(element.getAttributes())
        .containsExactly(new XmlProtoAttribute(attribute1), new XmlProtoAttribute(attribute2));
  }

  @Test
  public void getAttributes_noAttributes() {
    XmlProtoElement element = XmlProtoElement.create("hello");
    assertThat(element.getAttributes()).isEmpty();
  }

  @Test
  public void testGetChildren() {
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(
                    XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("child1")))
                .addChild(XmlNode.newBuilder().setText("child2"))
                .build());

    assertThat(element.getChildren())
        .containsExactly(
            XmlProtoNode.createElementNode(XmlProtoElement.create("child1")),
            XmlProtoNode.createTextNode("child2"));
  }

  @Test
  public void getChildren_noChildren() {
    XmlProtoElement element = XmlProtoElement.create("hello");
    assertThat(element.getChildren()).isEmpty();
  }

  @Test
  public void getChildText() {
    XmlNode child = XmlNode.newBuilder().setText("child").build();
    XmlProtoElement element =
        new XmlProtoElement(XmlElement.newBuilder().setName("hello").addChild(child).build());

    assertThat(element.getChildText()).hasValue(new XmlProtoNode(child));
  }

  @Test
  public void getChildText_noTextChild() {
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("child")))
                .build());

    assertThat(element.getChildText()).isEmpty();
  }

  @Test
  public void getChildrenText() {
    XmlNode childText1 = XmlNode.newBuilder().setText("child1").build();
    XmlNode childText2 = XmlNode.newBuilder().setText("child2").build();
    XmlNode childElement3 =
        XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("element")).build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(childText1)
                .addChild(childText2)
                .addChild(childElement3)
                .build());

    assertThat(element.getChildrenText())
        .containsExactly(new XmlProtoNode(childText1), new XmlProtoNode(childText2));
  }

  @Test
  public void getChildrenText_noTextChildren() {
    XmlNode childElement =
        XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("element")).build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder().setName("hello").addChild(childElement).build());

    assertThat(element.getChildrenText()).isEmpty();
  }

  @Test
  public void getAttribute_withName() {
    XmlAttribute attribute1 = XmlAttribute.newBuilder().setName("attribute1").build();
    XmlAttribute attribute2 =
        XmlAttribute.newBuilder().setName("attribute1").setNamespaceUri("namespace").build();
    XmlAttribute attribute3 = XmlAttribute.newBuilder().setName("attribute2").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addAttribute(attribute1)
                .addAttribute(attribute2)
                .addAttribute(attribute3)
                .build());

    assertThat(element.getAttribute("attribute1")).hasValue(new XmlProtoAttribute(attribute1));
  }

  @Test
  public void getAttribute_withNameAndNamespace() {
    XmlAttribute attribute1 = XmlAttribute.newBuilder().setName("attribute").build();
    XmlAttribute attribute2 =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addAttribute(attribute1)
                .addAttribute(attribute2)
                .build());

    assertThat(element.getAttribute("namespace", "attribute"))
        .hasValue(new XmlProtoAttribute(attribute2));
  }

  @Test
  public void getAttribute_attributeDoesNotExist() {
    XmlAttribute attribute = XmlAttribute.newBuilder().setName("attribute1").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder().setName("hello").addAttribute(attribute).build());

    assertThat(element.getAttribute("attribute2")).isEmpty();
    assertThat(element.getAttribute("namespace", "attribute1")).isEmpty();
  }

  @Test
  public void getAttributeIgnoringNamespace_withNamespace() {
    XmlAttribute attribute =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder().setName("hello").addAttribute(attribute).build());

    assertThat(element.getAttributeIgnoringNamespace("attribute"))
        .hasValue(new XmlProtoAttribute(attribute));
  }

  @Test
  public void getAttributeIgnoringNamespace_withoutNamespace() {
    XmlAttribute attribute = XmlAttribute.newBuilder().setName("attribute").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder().setName("hello").addAttribute(attribute).build());

    assertThat(element.getAttributeIgnoringNamespace("attribute"))
        .hasValue(new XmlProtoAttribute(attribute));
  }

  @Test
  public void getAttributeWihtResourceId() {
    XmlAttribute attr1 = XmlAttribute.newBuilder().setName("attr1").setResourceId(0X123).build();
    XmlAttribute attr2 = XmlAttribute.newBuilder().setName("attr2").setResourceId(0X456).build();
    XmlAttribute attribute3 = XmlAttribute.newBuilder().setName("attribute3").build();
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .addAttribute(attr1)
                .addAttribute(attr2)
                .addAttribute(attribute3)
                .build());

    assertThat(element.getAndroidAttribute(0x123)).hasValue(new XmlProtoAttribute(attr1));
    assertThat(element.getAndroidAttribute(0x456)).hasValue(new XmlProtoAttribute(attr2));
  }

  @Test
  public void getChildrenElements_withName() {
    XmlElement childElement1 = XmlElement.newBuilder().setName("child").build();
    XmlElement childElement2 =
        XmlElement.newBuilder()
            .setName("child")
            .addAttribute(XmlAttribute.newBuilder().setName("test"))
            .build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .addChild(XmlNode.newBuilder().setElement(childElement1))
                .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("other")))
                .addChild(XmlNode.newBuilder().setElement(childElement2))
                .addChild(
                    XmlNode.newBuilder()
                        .setElement(childElement1.toBuilder().setNamespaceUri("namespace")))
                .build());

    assertThat(element.getChildrenElements("child"))
        .containsExactly(new XmlProtoElement(childElement1), new XmlProtoElement(childElement2));
  }

  @Test
  public void getChildrenElements_withNameAndNamespace() {
    XmlElement childElement1 =
        XmlElement.newBuilder().setName("child").setNamespaceUri("namespace").build();
    XmlElement childElement2 =
        XmlElement.newBuilder()
            .setName("child")
            .setNamespaceUri("namespace")
            .addAttribute(XmlAttribute.newBuilder().setName("test").build())
            .build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .addChild(XmlNode.newBuilder().setElement(childElement1))
                .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("other")))
                .addChild(
                    XmlNode.newBuilder().setElement(childElement1.toBuilder().clearNamespaceUri()))
                .addChild(XmlNode.newBuilder().setElement(childElement2))
                .build());

    assertThat(element.getChildrenElements("namespace", "child"))
        .containsExactly(new XmlProtoElement(childElement1), new XmlProtoElement(childElement2));
  }

  @Test
  public void getChildrenElement_noChildren() {
    XmlProtoElement element = new XmlProtoElement(XmlElement.newBuilder().setName("hello").build());
    assertThat(element.getChildrenElements("test")).isEmpty();
  }

  @Test
  public void getOptionalChildElement_zeroElement() {
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(
                    XmlNode.newBuilder()
                        .setElement(
                            XmlElement.newBuilder().setName("test").setNamespaceUri("namespace")))
                .build());

    assertThat(element.getOptionalChildElement("test")).isEmpty();
  }

  @Test
  public void getOptionalChildElement_oneElement() {
    XmlElement childElement = XmlElement.newBuilder().setName("child").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThat(element.getOptionalChildElement("child"))
        .hasValue(new XmlProtoElement(childElement));
  }

  @Test
  public void getOptionalChildElement_manyElements_throws() {
    XmlElement childElement = XmlElement.newBuilder().setName("child").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThrows(XmlProtoException.class, () -> element.getOptionalChildElement("child"));
  }

  @Test
  public void getOptionalChildElement_withNamespace_zeroElement() {
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("test")))
                .build());

    assertThat(element.getOptionalChildElement("namespace", "test")).isEmpty();
  }

  @Test
  public void getOptionalChildElement_withNamespace_oneElement() {
    XmlElement childElement =
        XmlElement.newBuilder().setName("child").setNamespaceUri("namespace").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThat(element.getOptionalChildElement("namespace", "child"))
        .hasValue(new XmlProtoElement(childElement));
  }

  @Test
  public void getOptionalChildElement_withNamespace_manyElements_throws() {
    XmlElement childElement =
        XmlElement.newBuilder().setName("child").setNamespaceUri("namespace").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThrows(
        XmlProtoException.class, () -> element.getOptionalChildElement("namespace", "child"));
  }

  @Test
  public void getChildElement_zeroElement_throws() {
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(
                    XmlNode.newBuilder()
                        .setElement(
                            XmlElement.newBuilder().setName("test").setNamespaceUri("namespace")))
                .build());

    assertThrows(XmlProtoException.class, () -> element.getChildElement("test"));
  }

  @Test
  public void getChildElement_oneElement() {
    XmlElement childElement = XmlElement.newBuilder().setName("child").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThat(element.getChildElement("child")).isEqualTo(new XmlProtoElement(childElement));
  }

  @Test
  public void getChildElement_manyElements_throws() {
    XmlElement childElement = XmlElement.newBuilder().setName("child").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThrows(XmlProtoException.class, () -> element.getChildElement("child"));
  }

  @Test
  public void getChildElement_withNamespace_zeroElement_throws() {
    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("test")))
                .build());

    assertThrows(XmlProtoException.class, () -> element.getChildElement("namespace", "test"));
  }

  @Test
  public void getChildElement_withNamespace_oneElement() {
    XmlElement childElement =
        XmlElement.newBuilder().setName("child").setNamespaceUri("namespace").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThat(element.getChildElement("namespace", "child"))
        .isEqualTo(new XmlProtoElement(childElement));
  }

  @Test
  public void getChildElement_withNamespace_manyElements_throws() {
    XmlElement childElement =
        XmlElement.newBuilder().setName("child").setNamespaceUri("namespace").build();

    XmlProtoElement element =
        new XmlProtoElement(
            XmlElement.newBuilder()
                .setName("hello")
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());

    assertThrows(XmlProtoException.class, () -> element.getChildElement("namespace", "child"));
  }

  @Test
  public void testEquals_equal() {
    XmlProtoElement element1 = XmlProtoElement.create("namespace", "hello");
    XmlProtoElement element2 = XmlProtoElement.create("namespace", "hello");
    assertThat(element1.equals(element2)).isTrue();
  }

  @Test
  public void testEquals_notEqual() {
    XmlProtoElement element1 = XmlProtoElement.create("namespace", "hello");
    XmlProtoElement element2 = XmlProtoElement.create("namespace", "world");
    assertThat(element1.equals(element2)).isFalse();
  }

  @Test
  public void testEquals_nullObject() {
    XmlProtoElement element = XmlProtoElement.create("namespace", "hello");
    assertThat(element.equals(null)).isFalse();
  }

  @Test
  public void testHashCode_equal() {
    XmlProtoElement element1 = XmlProtoElement.create("namespace", "hello");
    XmlProtoElement element2 = XmlProtoElement.create("namespace", "hello");
    assertThat(element1.hashCode()).isEqualTo(element2.hashCode());
  }

  @Test
  public void testHashCode_notEqual() {
    XmlProtoElement element1 = XmlProtoElement.create("namespace", "hello");
    XmlProtoElement element2 = XmlProtoElement.create("namespace", "world");
    assertThat(element1.hashCode()).isNotEqualTo(element2.hashCode());
  }
}
