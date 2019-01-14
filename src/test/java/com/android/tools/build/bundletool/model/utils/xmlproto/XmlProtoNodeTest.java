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

import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlProtoNodeTest {

  @Test
  public void createElementNode_withName() {
    XmlProtoNode node = XmlProtoNode.createElementNode(XmlProtoElement.create("hello"));
    assertThat(node.getProto())
        .isEqualTo(
            XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("hello")).build());
  }

  @Test
  public void createElementNode_withNameAndNamespace() {
    XmlProtoNode node =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    assertThat(node.getProto())
        .isEqualTo(
            XmlNode.newBuilder()
                .setElement(XmlElement.newBuilder().setName("hello").setNamespaceUri("namespace"))
                .build());
  }

  @Test
  public void createElementNodeIsElement() {
    XmlProtoNode node = XmlProtoNode.createElementNode(XmlProtoElement.create("hello"));
    assertThat(node.isElement()).isTrue();
    assertThat(node.isText()).isFalse();
  }

  @Test
  public void createTextNodeIsText() {
    XmlProtoNode node = XmlProtoNode.createTextNode("hello");
    assertThat(node.isElement()).isFalse();
    assertThat(node.isText()).isTrue();
  }

  @Test
  public void getElementOnElementNode() {
    XmlProtoElement element = XmlProtoElement.create("hello");
    XmlProtoNode node = XmlProtoNode.createElementNode(element);

    assertThat(node.getElement()).isEqualTo(element);
  }

  @Test
  public void getElementOnTextNode_throws() {
    XmlProtoNode node = XmlProtoNode.createTextNode("hello");
    XmlProtoException e = assertThrows(XmlProtoException.class, () -> node.getElement());
    assertThat(e).hasMessageThat().contains("Expected node of type 'element' but found:");
  }

  @Test
  public void getTextOnTextNode() {
    XmlProtoNode node = XmlProtoNode.createTextNode("hello");
    assertThat(node.getText()).isEqualTo("hello");
  }

  @Test
  public void getTextOnElementNode_throws() {
    XmlProtoNode node = XmlProtoNode.createElementNode(XmlProtoElement.create("hello"));
    XmlProtoException e = assertThrows(XmlProtoException.class, () -> node.getText());
    assertThat(e).hasMessageThat().contains("Expected node of type 'text' but found:");
  }

  @Test
  public void toBuilderAndBack_unchanged() {
    XmlProtoNode node = XmlProtoNode.createElementNode(XmlProtoElement.create("hello"));
    assertThat(node.toBuilder().build()).isEqualTo(node);
  }

  @Test
  public void testEquals_equal() {
    XmlProtoNode node1 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    XmlProtoNode node2 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    assertThat(node1.equals(node2)).isTrue();
  }

  @Test
  public void testEquals_notEqual() {
    XmlProtoNode node1 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    XmlProtoNode node2 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "world"));
    assertThat(node1.equals(node2)).isFalse();
  }

  @Test
  public void testEquals_nullObject() {
    XmlProtoNode node =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    assertThat(node.equals(null)).isFalse();
  }

  @Test
  public void testHashCode_equal() {
    XmlProtoNode node1 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    XmlProtoNode node2 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
  }

  @Test
  public void testHashCode_notEqual() {
    XmlProtoNode node1 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "hello"));
    XmlProtoNode node2 =
        XmlProtoNode.createElementNode(XmlProtoElement.create("namespace", "world"));
    assertThat(node1.hashCode()).isNotEqualTo(node2.hashCode());
  }
}
