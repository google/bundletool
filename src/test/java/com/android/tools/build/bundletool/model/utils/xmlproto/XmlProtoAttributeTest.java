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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.XmlAttribute;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlProtoAttributeTest {

  @Test
  public void createAttribute_withName() {
    XmlProtoAttribute attribute = XmlProtoAttribute.create("name");
    assertThat(attribute.getProto()).isEqualTo(XmlAttribute.newBuilder().setName("name").build());
  }

  @Test
  public void createAttribute_withNameAndNamespace() {
    XmlProtoAttribute attribute = XmlProtoAttribute.create("namespace", "name");
    assertThat(attribute.getProto())
        .isEqualTo(XmlAttribute.newBuilder().setName("name").setNamespaceUri("namespace").build());
  }

  @Test
  public void createAndroidAttribute() {
    XmlProtoAttribute attribute = XmlProtoAttribute.createAndroidAttribute("name", 0x123);
    assertThat(attribute.getProto())
        .isEqualTo(
            XmlAttribute.newBuilder()
                .setName("name")
                .setNamespaceUri(ANDROID_NAMESPACE_URI)
                .setResourceId(0x123)
                .build());
  }

  @Test
  public void toBuilderAndBack_unchanged() {
    XmlProtoAttribute attribute =
        XmlProtoAttributeBuilder.create("namespace", "name").setValueAsString("hello").build();

    assertThat(attribute.toBuilder().build()).isEqualTo(attribute);
  }

  @Test
  public void getName() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(XmlAttribute.newBuilder().setName("name").build());
    assertThat(attribute.getName()).isEqualTo("name");
  }

  @Test
  public void getNamespace() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(XmlAttribute.newBuilder().setNamespaceUri("namespace").build());
    assertThat(attribute.getNamespaceUri()).isEqualTo("namespace");
  }

  @Test
  public void getResourceId() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(XmlAttribute.newBuilder().setResourceId(0x123).build());
    assertThat(attribute.getResourceId()).isEqualTo(0x123);
  }

  @Test
  public void getValueAsString_valueAsCompiledString() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setStr(Resources.String.newBuilder().setValue("Text")))
                .build());

    assertThat(attribute.getValueAsString()).isEqualTo("Text");
  }

  @Test
  public void getValueAsString_valueAsRawString() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(XmlAttribute.newBuilder().setValue("Text").build());

    assertThat(attribute.getValueAsString()).isEqualTo("Text");
  }

  @Test
  public void getValueAsString_compiledValueTakesPrecedenceOverRawValue() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setValue("RawValue")
                .setCompiledItem(
                    Item.newBuilder()
                        .setStr(Resources.String.newBuilder().setValue("CompiledValue")))
                .build());

    assertThat(attribute.getValueAsString()).isEqualTo("CompiledValue");
  }

  @Test
  public void getValueAsString_valueNotAString_returnsRawValue() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setValue("Text")
                .setCompiledItem(
                    Item.newBuilder().setPrim(Primitive.newBuilder().setBooleanValue(true)))
                .build());

    assertThat(attribute.getValueAsString()).isEqualTo("Text");
  }

  @Test
  public void getValueAsBoolean_booleanValue() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setPrim(Primitive.newBuilder().setBooleanValue(true)))
                .build());

    assertThat(attribute.getValueAsBoolean()).isTrue();
  }

  @Test
  public void getValueAsBoolean_notABooleanValue() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setStr(Resources.String.newBuilder().setValue("true")))
                .build());

    assertThrows(UnexpectedAttributeTypeException.class, () -> attribute.getValueAsBoolean());
  }

  @Test
  public void getValueAsRefId_refIdValue() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(Item.newBuilder().setRef(Reference.newBuilder().setId(0x123)))
                .build());

    assertThat(attribute.getValueAsRefId()).isEqualTo(0x123);
  }

  @Test
  public void getValueAsRefId_notARefId() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setStr(Resources.String.newBuilder().setValue("0x123")))
                .build());

    assertThrows(UnexpectedAttributeTypeException.class, () -> attribute.getValueAsRefId());
  }

  @Test
  public void getValueAsDecimalInteger_decimalIntegerValue() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setPrim(Primitive.newBuilder().setIntDecimalValue(123)))
                .build());

    assertThat(attribute.getValueAsDecimalInteger()).isEqualTo(123);
  }

  @Test
  public void getValueAsDecimalInteger_notADecimalInteger() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setStr(Resources.String.newBuilder().setValue("123")))
                .build());

    assertThrows(
        UnexpectedAttributeTypeException.class, () -> attribute.getValueAsDecimalInteger());
  }

  @Test
  public void getValueAsInteger_decimalInteger() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setPrim(Primitive.newBuilder().setIntDecimalValue(123)))
                .build());

    assertThat(attribute.getValueAsInteger()).isEqualTo(123);
  }

  @Test
  public void getValueAsInteger_hexInteger() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setPrim(Primitive.newBuilder().setIntHexadecimalValue(0x123)))
                .build());

    assertThat(attribute.getValueAsInteger()).isEqualTo(0x123);
  }

  @Test
  public void getValueAsInteger_notInteger() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setStr(Resources.String.newBuilder().setValue("123")))
                .build());

    assertThrows(UnexpectedAttributeTypeException.class, () -> attribute.getValueAsInteger());
  }

  @Test
  public void testEquals_equal() {
    XmlProtoAttribute attribute1 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsString("hello").build();
    XmlProtoAttribute attribute2 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsString("hello").build();
    assertThat(attribute1.equals(attribute2)).isTrue();
  }

  @Test
  public void testEquals_notEqual() {
    XmlProtoAttribute attribute1 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsDecimalInteger(123).build();
    XmlProtoAttribute attribute2 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsRefId(123).build();
    assertThat(attribute1.equals(attribute2)).isFalse();
  }

  @Test
  public void testEquals_nullObject() {
    XmlProtoAttribute attribute = XmlProtoAttributeBuilder.create("namespace", "hello").build();
    assertThat(attribute.equals(null)).isFalse();
  }

  @Test
  public void testHashCode_equal() {
    XmlProtoAttribute attribute1 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsString("hello").build();
    XmlProtoAttribute attribute2 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsString("hello").build();
    assertThat(attribute1.hashCode()).isEqualTo(attribute2.hashCode());
  }

  @Test
  public void testHashCode_notEqual() {
    XmlProtoAttribute attribute1 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsDecimalInteger(123).build();
    XmlProtoAttribute attribute2 =
        XmlProtoAttributeBuilder.create("namespace", "hello").setValueAsRefId(123).build();
    assertThat(attribute1.hashCode()).isNotEqualTo(attribute2.hashCode());
  }

  @Test
  public void hasStringValue_generalValue_true() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(XmlAttribute.newBuilder().setValue("Text").build());

    assertThat(attribute.hasStringValue()).isTrue();
  }

  @Test
  public void hasStringValue_compiledValue_true() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setStr(Resources.String.newBuilder().setValue("Text")))
                .build());

    assertThat(attribute.hasStringValue()).isTrue();
  }

  @Test
  public void hasStringValue_false() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(
                    Item.newBuilder().setPrim(Primitive.newBuilder().setBooleanValue(true)))
                .build());

    assertThat(attribute.hasStringValue()).isFalse();
  }

  @Test
  public void hasRefIdVale_true() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(
            XmlAttribute.newBuilder()
                .setCompiledItem(Item.newBuilder().setRef(Reference.newBuilder().setId(0x123)))
                .build());

    assertThat(attribute.hasRefIdValue()).isTrue();
  }

  @Test
  public void hasRefIdVale_false() {
    XmlProtoAttribute attribute =
        new XmlProtoAttribute(XmlAttribute.newBuilder().setValue("text").build());

    assertThat(attribute.hasRefIdValue()).isFalse();
  }
}
