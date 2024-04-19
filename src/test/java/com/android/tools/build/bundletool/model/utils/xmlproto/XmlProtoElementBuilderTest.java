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

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.SourcePosition;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNamespace;
import com.android.aapt.Resources.XmlNode;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlProtoElementBuilderTest {

  @Test
  public void getAttributeIgnoringNamespace_duplicate() {
    XmlAttribute attribute = XmlAttribute.newBuilder().setName("attribute").build();
    XmlElement protoElement =
        XmlElement.newBuilder().addAttribute(attribute).addAttribute(attribute).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getAttributeIgnoringNamespace("attribute").get();
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getAttribute_duplicate() {
    XmlAttribute attribute =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();
    XmlElement protoElement =
        XmlElement.newBuilder().addAttribute(attribute).addAttribute(attribute).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getAttribute("namespace", "attribute").get();
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getAndroidAttribute_duplicate() {
    XmlAttribute attribute =
        XmlAttribute.newBuilder()
            .setName("attribute")
            .setNamespaceUri("namespace")
            .setResourceId(42)
            .build();
    XmlElement protoElement =
        XmlElement.newBuilder().addAttribute(attribute).addAttribute(attribute).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoAttributeBuilder fetchedAttribute = element.getAndroidAttribute(42).get();
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void addAttribute() {
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(XmlElement.newBuilder());

    element.addAttribute(XmlProtoAttributeBuilder.create("attribute1"));
    element.addAttribute(XmlProtoAttributeBuilder.create("attribute2"));

    assertThat(element.getProto().build())
        .isEqualTo(
            XmlElement.newBuilder()
                .addAttribute(XmlAttribute.newBuilder().setName("attribute1"))
                .addAttribute(XmlAttribute.newBuilder().setName("attribute2"))
                .build());
  }

  @Test
  public void getOrCreateAttribute_attributeAlreadyExists() {
    XmlAttribute attribute = XmlAttribute.newBuilder().setName("attribute").build();
    XmlElement protoElement = XmlElement.newBuilder().addAttribute(attribute).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoAttributeBuilder fetchedAttribute = element.getOrCreateAttribute("attribute");
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getOrCreateAttribute_attributeDoesNotExist() {
    XmlAttribute attribute = XmlAttribute.newBuilder().setName("attribute").build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(XmlElement.newBuilder());

    XmlProtoAttributeBuilder fetchedAttribute = element.getOrCreateAttribute("attribute");
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build())
        .isEqualTo(XmlElement.newBuilder().addAttribute(attribute).build());
  }

  @Test
  public void getOrCreateAttribute_attributeDiffersByNamespace() {
    XmlAttribute attrWithoutNamespace = XmlAttribute.newBuilder().setName("attribute").build();
    XmlAttribute attrWithNamespace =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();

    XmlProtoElementBuilder element =
        new XmlProtoElementBuilder(XmlElement.newBuilder().addAttribute(attrWithNamespace));

    XmlProtoAttributeBuilder fetchedAttribute = element.getOrCreateAttribute("attribute");
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attrWithoutNamespace);
    assertThat(element.getProto().build())
        .isEqualTo(
            XmlElement.newBuilder()
                .addAttribute(attrWithNamespace)
                .addAttribute(attrWithoutNamespace)
                .build());
  }

  @Test
  public void getOrCreateAttribute_withNamespace_attributeAlreadyExists() {
    XmlAttribute attribute =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();
    XmlElement protoElement = XmlElement.newBuilder().addAttribute(attribute).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getOrCreateAttribute("namespace", "attribute");
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getOrCreateAttribute_withNamespace_attributeDoesNotExist() {
    XmlAttribute attribute =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(XmlElement.newBuilder());

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getOrCreateAttribute("namespace", "attribute");
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attribute);
    assertThat(element.getProto().build())
        .isEqualTo(XmlElement.newBuilder().addAttribute(attribute).build());
  }

  @Test
  public void getOrCreateAttribute_withNamespace_attributeDiffersByNamespace() {
    XmlAttribute attributeWithoutNamespace = XmlAttribute.newBuilder().setName("attribute").build();
    XmlAttribute attributeWithNamespace =
        XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace").build();
    XmlProtoElementBuilder element =
        new XmlProtoElementBuilder(XmlElement.newBuilder().addAttribute(attributeWithoutNamespace));

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getOrCreateAttribute("namespace", "attribute");
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attributeWithNamespace);
    assertThat(element.getProto().build())
        .isEqualTo(
            XmlElement.newBuilder()
                .addAttribute(attributeWithoutNamespace)
                .addAttribute(attributeWithNamespace)
                .build());
  }

  @Test
  public void getOrCreateAndroidAttribute_attributeAlreadyExists() {
    XmlAttribute attributeNotAndroid =
        XmlAttribute.newBuilder()
            .setName("attribute")
            .setNamespaceUri("namespace")
            .setResourceId(0x123)
            .build();
    XmlAttribute attributeAndroid =
        XmlAttribute.newBuilder()
            .setName("attribute")
            .setNamespaceUri(ANDROID_NAMESPACE_URI)
            .setResourceId(0x123)
            .build();
    XmlAttribute attributeNoNamespace =
        XmlAttribute.newBuilder().setName("attribute").setResourceId(0x123).build();

    XmlElement protoElement =
        XmlElement.newBuilder()
            .addAttribute(attributeNotAndroid)
            .addAttribute(attributeAndroid)
            .addAttribute(attributeNoNamespace)
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getOrCreateAndroidAttribute("attribute", 0x123);
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attributeAndroid);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getOrCreateAndroidAttribute_attributeDoesNotExist() {
    XmlAttribute attributeNotAndroid =
        XmlAttribute.newBuilder()
            .setName("attribute")
            .setNamespaceUri("namespace")
            .setResourceId(0x123)
            .build();
    XmlAttribute attributeAndroid =
        XmlAttribute.newBuilder()
            .setName("attribute")
            .setNamespaceUri(ANDROID_NAMESPACE_URI)
            .setResourceId(0x123)
            .build();
    XmlAttribute attributeNoNamespace =
        XmlAttribute.newBuilder().setName("attribute").setResourceId(0x123).build();

    XmlProtoElementBuilder element =
        new XmlProtoElementBuilder(
            XmlElement.newBuilder()
                .addAttribute(attributeNotAndroid)
                .addAttribute(attributeNoNamespace));

    XmlProtoAttributeBuilder fetchedAttribute =
        element.getOrCreateAndroidAttribute("attribute", 0x123);
    assertThat(fetchedAttribute.getProto().build()).isEqualTo(attributeAndroid);
    assertThat(element.getProto().build())
        .isEqualTo(
            XmlElement.newBuilder()
                .addAttribute(attributeNotAndroid)
                .addAttribute(attributeNoNamespace)
                .addAttribute(attributeAndroid)
                .build());
  }

  @Test
  public void removeAttribute_attributeExists() {
    XmlElement.Builder protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setName("attribute")
                    .setNamespaceUri("namespace")
                    .build());
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement);

    element.removeAttribute("namespace", "attribute");

    assertThat(element.getProto().build()).isEqualToDefaultInstance();
  }

  @Test
  public void removeAttribute_namespaceNotMatching() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace"))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    element.removeAttribute("otherNamespace", "attribute");

    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void removeAttribute_nameNotMatching() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder().setName("attribute").setNamespaceUri("namespace"))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    element.removeAttribute("namespace", "otherName");

    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void removeAndroidAttribute_attributeExists() {
    XmlElement.Builder protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setName("attribute")
                    .setNamespaceUri(ANDROID_NAMESPACE_URI)
                    .setResourceId(0x123)
                    .build());
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement);

    element.removeAndroidAttribute(0x123);

    assertThat(element.getProto().build()).isEqualToDefaultInstance();
  }

  @Test
  public void removeAndroidAttribute_noName() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setResourceId(0x123)
                    .setNamespaceUri(ANDROID_NAMESPACE_URI))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    element.removeAndroidAttribute(0x123);

    assertThat(element.getProto().build()).isEqualToDefaultInstance();
  }

  @Test
  public void removeAndroidAttribute_namespaceNotMatching() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setName("attribute")
                    .setNamespaceUri("namespace")
                    .setResourceId(0x123))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    element.removeAndroidAttribute(0x123);

    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void removeAndroidAttribute_resourceNotMatching() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setName("attribute")
                    .setNamespaceUri(ANDROID_NAMESPACE_URI)
                    .setResourceId(0x1234))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    element.removeAndroidAttribute(0x123);

    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getChildrenElement_matchingPredicate() {
    XmlElement childElement = XmlElement.newBuilder().setName("hello").build();
    XmlElement matchingElement = XmlElement.newBuilder().setName("foo").build();
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addChild(XmlNode.newBuilder().setElement(childElement))
            .addChild(XmlNode.newBuilder().setElement(matchingElement))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    ImmutableList<XmlProtoElementBuilder> fetchedElements =
        element.getChildrenElements(el -> el.getName().equals("foo")).collect(toImmutableList());
    assertThat(fetchedElements).hasSize(1);
    XmlProtoElementBuilder fetchedElement = fetchedElements.get(0);
    assertThat(fetchedElement.getProto().build()).isEqualTo(matchingElement);
  }

  @Test
  public void getChildrenElement_noMatchingPredicate() {
    XmlElement childElement = XmlElement.newBuilder().setName("hello").build();
    XmlElement matchingElement = XmlElement.newBuilder().setName("foo").build();
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addChild(XmlNode.newBuilder().setElement(childElement))
            .addChild(XmlNode.newBuilder().setElement(matchingElement))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    ImmutableList<XmlProtoElementBuilder> fetchedElements =
        element.getChildrenElements(el -> el.getName().equals("bye")).collect(toImmutableList());
    assertThat(fetchedElements).isEmpty();
  }

  @Test
  public void getOrCreateChildElement_childExists() {
    XmlElement childElement = XmlElement.newBuilder().setName("hello").build();
    XmlElement protoElement =
        XmlElement.newBuilder().addChild(XmlNode.newBuilder().setElement(childElement)).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoElementBuilder fetchedElement = element.getOrCreateChildElement("hello");
    assertThat(fetchedElement.getProto().build()).isEqualTo(childElement);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getOrCreateChildElement_childDoesNotExist() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addChild(
                XmlNode.newBuilder()
                    .setElement(
                        XmlElement.newBuilder().setName("hello").setNamespaceUri("namespace")))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoElementBuilder fetchedElement = element.getOrCreateChildElement("hello");
    assertThat(fetchedElement.getProto().build())
        .isEqualTo(XmlElement.newBuilder().setName("hello").build());
    assertThat(element.getProto().build())
        .isEqualTo(
            protoElement.toBuilder()
                .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("hello")))
                .build());
  }

  @Test
  public void getOrCreateChildElement_withNamespace_childExists() {
    XmlElement childElement =
        XmlElement.newBuilder().setName("hello").setNamespaceUri("namespace").build();
    XmlElement protoElement =
        XmlElement.newBuilder().addChild(XmlNode.newBuilder().setElement(childElement)).build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoElementBuilder fetchedElement = element.getOrCreateChildElement("namespace", "hello");
    assertThat(fetchedElement.getProto().build()).isEqualTo(childElement);
    assertThat(element.getProto().build()).isEqualTo(protoElement);
  }

  @Test
  public void getOrCreateChildElement_withNamespace_childDoesNotExist() {
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addChild(XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName("hello")))
            .build();
    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());

    XmlProtoElementBuilder fetchedElement = element.getOrCreateChildElement("namespace", "hello");
    assertThat(fetchedElement.getProto().build())
        .isEqualTo(XmlElement.newBuilder().setName("hello").setNamespaceUri("namespace").build());
    assertThat(element.getProto().build())
        .isEqualTo(
            protoElement.toBuilder()
                .addChild(
                    XmlNode.newBuilder()
                        .setElement(
                            XmlElement.newBuilder().setName("hello").setNamespaceUri("namespace")))
                .build());
  }

  @Test
  public void addChildElement() {
    XmlElement protoElement = XmlElement.getDefaultInstance();
    XmlElement childElement =
        XmlElement.newBuilder()
            .addAttribute(XmlAttribute.newBuilder().setName("attribute"))
            .build();

    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());
    element.addChildElement(new XmlProtoElementBuilder(childElement.toBuilder()));

    assertThat(element.getProto().build())
        .isEqualTo(
            protoElement.toBuilder()
                .addChild(XmlNode.newBuilder().setElement(childElement))
                .build());
  }

  @Test
  public void addChildText() {
    XmlElement protoElement = XmlElement.getDefaultInstance();

    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());
    element.addChildText("hello");

    assertThat(element.getProto().build())
        .isEqualTo(
            protoElement.toBuilder().addChild(XmlNode.newBuilder().setText("hello")).build());
  }

  @Test
  public void addNamespaceDeclaration() {
    XmlElement protoElement = XmlElement.getDefaultInstance();

    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());
    element.addNamespaceDeclaration("ns", "http://namespace");

    assertThat(element.getProto().build())
        .isEqualTo(
            protoElement.toBuilder()
                .addNamespaceDeclaration(
                    XmlNamespace.newBuilder().setPrefix("ns").setUri("http://namespace"))
                .build());
  }

  @Test
  public void modifyingProtoModifiesAlsoWrapper() {
    XmlElement.Builder protoElement = XmlElement.newBuilder().setName("hello");
    XmlProtoElementBuilder wrapperElement = new XmlProtoElementBuilder(protoElement);

    protoElement.setName("world");

    assertThat(wrapperElement.getName()).isEqualTo("world");
  }

  @Test
  public void getNamespaceDeclarations() {
    XmlElement protoElement = XmlElement.getDefaultInstance();

    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());
    element.addNamespaceDeclaration("ns", "http://namespace");
    element.addNamespaceDeclaration("ns2", "http://namespace2");

    assertThat(element.getNamespaceDeclarations())
        .containsExactly(
            XmlProtoNamespace.create("ns", "http://namespace"),
            XmlProtoNamespace.create("ns2", "http://namespace2"));
  }

  @Test
  public void removeChildrenElementsIf() {
    XmlElement childElement = XmlElement.newBuilder().setName("hello").build();
    XmlElement matchingElement = XmlElement.newBuilder().setName("foo").build();
    XmlElement protoElement =
        XmlElement.newBuilder()
            .addChild(XmlNode.newBuilder().setElement(childElement))
            .addChild(XmlNode.newBuilder().setElement(matchingElement))
            .build();

    XmlProtoElementBuilder element = new XmlProtoElementBuilder(protoElement.toBuilder());
    element.removeChildrenElementsIf(
        node -> node.isElement() && node.getElement().getName().equals("foo"));

    ImmutableList<XmlProtoElementBuilder> fetchedElements =
        element.getChildrenElements().collect(toImmutableList());
    assertThat(fetchedElements).hasSize(1);
    XmlProtoElementBuilder fetchedElement = fetchedElements.get(0);
    assertThat(fetchedElement.getProto().build()).isEqualTo(childElement);
  }

  @Test
  public void removeSourceDataRecursive() {
    SourcePosition sourcePosition =
        SourcePosition.newBuilder().setLineNumber(12).setColumnNumber(32).build();
    XmlElement element =
        XmlElement.newBuilder()
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setSource(sourcePosition)
                    .setName("name1")
                    .setValue("value"))
            .addAttribute(
                XmlAttribute.newBuilder()
                    .setSource(sourcePosition)
                    .setName("name2")
                    .setValue("value"))
            .addNamespaceDeclaration(
                XmlNamespace.newBuilder()
                    .setSource(sourcePosition)
                    .setPrefix("pref")
                    .setUri("http://uri"))
            .addChild(
                XmlNode.newBuilder()
                    .setElement(
                        XmlElement.newBuilder()
                            .addAttribute(
                                XmlAttribute.newBuilder()
                                    .setSource(sourcePosition)
                                    .setName("nested")
                                    .setValue("another"))))
            .addChild(XmlNode.newBuilder().setSource(sourcePosition).setText("Text1"))
            .addChild(XmlNode.newBuilder().setSource(sourcePosition).setText("Text2"))
            .build();

    XmlElement elementWithoutSource =
        new XmlProtoElementBuilder(element.toBuilder())
            .removeSourceDataRecursive()
            .build()
            .getProto();

    XmlElement expected =
        XmlElement.newBuilder()
            .addAttribute(XmlAttribute.newBuilder().setName("name1").setValue("value"))
            .addAttribute(XmlAttribute.newBuilder().setName("name2").setValue("value"))
            .addNamespaceDeclaration(
                XmlNamespace.newBuilder().setPrefix("pref").setUri("http://uri"))
            .addChild(
                XmlNode.newBuilder()
                    .setElement(
                        XmlElement.newBuilder()
                            .addAttribute(
                                XmlAttribute.newBuilder().setName("nested").setValue("another"))))
            .addChild(XmlNode.newBuilder().setText("Text1"))
            .addChild(XmlNode.newBuilder().setText("Text2"))
            .build();
    assertThat(elementWithoutSource).isEqualTo(expected);
  }
}
