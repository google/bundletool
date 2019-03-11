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

import com.android.aapt.Resources;
import com.android.aapt.Resources.Array;
import com.android.aapt.Resources.Array.Element;
import com.android.aapt.Resources.Attribute;
import com.android.aapt.Resources.Attribute.Symbol;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Plural;
import com.android.aapt.Resources.Plural.Arity;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Primitive.EmptyType;
import com.android.aapt.Resources.Primitive.NullType;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.Reference.Type;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Styleable;
import com.android.aapt.Resources.StyledString;
import com.android.aapt.Resources.StyledString.Span;
import com.android.aapt.Resources.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class XmlProtoPrintUtilsTest {

  @Test
  public void processStyledString_emptyString() {
    StyledString styledString = StyledString.newBuilder().setValue("").build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEmpty();
  }

  @Test
  public void processStyledString_startAndEnd() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(Span.newBuilder().setTag("a").setFirstChar(0).setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<a>hello</a>");
  }

  @Test
  public void processStyledString_multipleTagsAtSamePosition() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(Span.newBuilder().setTag("a").setFirstChar(0).setLastChar(4))
            .addSpan(Span.newBuilder().setTag("c").setFirstChar(0).setLastChar(4))
            .addSpan(Span.newBuilder().setTag("b").setFirstChar(0).setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<a><b><c>hello</c></b></a>");
  }

  @Test
  public void processStyledString_nestedTags() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(Span.newBuilder().setTag("a").setFirstChar(0).setLastChar(4))
            .addSpan(Span.newBuilder().setTag("b").setFirstChar(1).setLastChar(3))
            .addSpan(Span.newBuilder().setTag("c").setFirstChar(2).setLastChar(2))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<a>h<b>e<c>l</c>l</b>o</a>");
  }

  @Test
  public void processStyledString_differentTagsStartAndEndAtSamePosition() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(Span.newBuilder().setTag("a").setFirstChar(0).setLastChar(2))
            .addSpan(Span.newBuilder().setTag("b").setFirstChar(3).setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<a>hel</a><b>lo</b>");
  }

  @Test
  public void processStyledString_startAtSamePositionEndAtDifferentPosition() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(Span.newBuilder().setTag("a").setFirstChar(0).setLastChar(2))
            .addSpan(Span.newBuilder().setTag("b").setFirstChar(0).setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<b><a>hel</a>lo</b>");
  }

  @Test
  public void processStyledString_startAtDifferentPositionEndAtSamePosition() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(Span.newBuilder().setTag("a").setFirstChar(0).setLastChar(4))
            .addSpan(Span.newBuilder().setTag("b").setFirstChar(3).setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<a>hel<b>lo</b></a>");
  }

  @Test
  public void processStyledString_withAttributes() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(
                Span.newBuilder()
                    .setTag("a;attr1=value1;attr2=value2")
                    .setFirstChar(0)
                    .setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString).isEqualTo("<a attr1=\"value1\" attr2=\"value2\">hello</a>");
  }

  @Test
  public void processStyledString_withAttributeValueContainingEqualSign() {
    StyledString styledString =
        StyledString.newBuilder()
            .setValue("hello")
            .addSpan(
                Span.newBuilder()
                    .setTag("a;href=https://support.google.com?topic=123")
                    .setFirstChar(0)
                    .setLastChar(4))
            .build();
    String processedString = XmlProtoPrintUtils.processStyledString(styledString);
    assertThat(processedString)
        .isEqualTo("<a href=\"https://support.google.com?topic=123\">hello</a>");
  }

  @Test
  public void getRefAsString_reference() {
    Reference ref =
        Reference.newBuilder().setId(0x00000123).setName("name").setType(Type.REFERENCE).build();
    assertThat(XmlProtoPrintUtils.getRefAsString(ref)).isEqualTo("@name");
  }

  @Test
  public void getRefAsString_attribute() {
    Reference ref =
        Reference.newBuilder().setId(0x00000123).setName("name").setType(Type.ATTRIBUTE).build();
    assertThat(XmlProtoPrintUtils.getRefAsString(ref)).isEqualTo("?name");
  }

  @Test
  public void getRefAsString_noName() {
    Reference ref = Reference.newBuilder().setId(0x00000123).setType(Type.REFERENCE).build();
    assertThat(XmlProtoPrintUtils.getRefAsString(ref)).isEqualTo("0x00000123");
  }

  @Test
  public void getPrimitiveValueAsString_null() {
    Primitive primitive =
        Primitive.newBuilder().setNullValue(NullType.getDefaultInstance()).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEmpty();
  }

  @Test
  public void getPrimitiveValueAsString_empty() {
    Primitive primitive =
        Primitive.newBuilder().setEmptyValue(EmptyType.getDefaultInstance()).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEmpty();
  }

  @Test
  public void getPrimitiveValueAsString_intHexadecimal() {
    Primitive primitive = Primitive.newBuilder().setIntHexadecimalValue(0x12345678).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEqualTo("0x12345678");
  }


  @Test
  public void getPrimitiveValueAsString_colorArgb4() {
    Primitive primitive = Primitive.newBuilder().setColorArgb4Value(0x11223344).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEqualTo("#11223344");
  }

  @Test
  public void getPrimitiveValueAsString_colorArgb8() {
    Primitive primitive = Primitive.newBuilder().setColorArgb4Value(0x12345678).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEqualTo("#12345678");
  }

  @Test
  public void getPrimitiveValueAsString_colorRgb4() {
    Primitive primitive = Primitive.newBuilder().setColorRgb4Value(0xFF112233).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEqualTo("#112233");
  }

  @Test
  public void getPrimitiveValueAsString_colorRgb8() {
    Primitive primitive = Primitive.newBuilder().setColorRgb8Value(0xFF123456).build();
    assertThat(XmlProtoPrintUtils.getPrimitiveValueAsString(primitive)).isEqualTo("#123456");
  }

  @Test
  public void getCompoundValueAsString_attr() {
    CompoundValue compoundValue =
        CompoundValue.newBuilder()
            .setAttr(
                Attribute.newBuilder()
                    .addSymbol(Symbol.newBuilder().setName(createReference("name1")).setValue(123))
                    .addSymbol(Symbol.newBuilder().setName(createReference("name2")).setValue(456)))
            .build();

    assertThat(XmlProtoPrintUtils.getCompoundValueAsString(compoundValue))
        .isEqualTo("{name1=123, name2=456}");
  }

  @Test
  public void getCompoundValueAsString_array() {
    CompoundValue compoundValue =
        CompoundValue.newBuilder()
            .setArray(
                Array.newBuilder()
                    .addElement(Element.newBuilder().setItem(createItem("name1")))
                    .addElement(Element.newBuilder().setItem(createItem("name2"))))
            .build();

    assertThat(XmlProtoPrintUtils.getCompoundValueAsString(compoundValue))
        .isEqualTo("[\"name1\", \"name2\"]");
  }

  @Test
  public void getCompoundValueAsString_plural() {
    CompoundValue compoundValue =
        CompoundValue.newBuilder()
            .setPlural(
                Plural.newBuilder()
                    .addEntry(
                        Plural.Entry.newBuilder().setArity(Arity.ONE).setItem(createItem("one")))
                    .addEntry(
                        Plural.Entry.newBuilder().setArity(Arity.MANY).setItem(createItem("many"))))
            .build();

    assertThat(XmlProtoPrintUtils.getCompoundValueAsString(compoundValue))
        .isEqualTo("{ONE=\"one\", MANY=\"many\"}");
  }

  @Test
  public void getCompoundValueAsString_style() {
    CompoundValue compoundValue =
        CompoundValue.newBuilder()
            .setStyle(
                Style.newBuilder()
                    .addEntry(Style.Entry.newBuilder().setItem(createItem("name1")))
                    .addEntry(Style.Entry.newBuilder().setItem(createItem("name2"))))
            .build();

    assertThat(XmlProtoPrintUtils.getCompoundValueAsString(compoundValue))
        .isEqualTo("[\"name1\", \"name2\"]");
  }

  @Test
  public void getCompoundValueAsString_styleable() {
    CompoundValue compoundValue =
        CompoundValue.newBuilder()
            .setStyleable(
                Styleable.newBuilder()
                    .addEntry(Styleable.Entry.newBuilder().setAttr(createReference("name1")))
                    .addEntry(Styleable.Entry.newBuilder().setAttr(createReference("name2"))))
            .build();

    assertThat(XmlProtoPrintUtils.getCompoundValueAsString(compoundValue))
        .isEqualTo("[@name1, @name2]");
  }

  @Test
  public void getValueTypeAsString_file() {
    Value fileValue =
        Value.newBuilder()
            .setItem(Item.newBuilder().setFile(FileReference.getDefaultInstance()))
            .build();
    assertThat(XmlProtoPrintUtils.getValueTypeAsString(fileValue)).isEqualTo("FILE");
  }

  @Test
  public void getValueTypeAsString_boolean() {
    Value booleanValue =
        Value.newBuilder()
            .setItem(Item.newBuilder().setPrim(Primitive.newBuilder().setBooleanValue(false)))
            .build();
    assertThat(XmlProtoPrintUtils.getValueTypeAsString(booleanValue)).isEqualTo("BOOLEAN");
  }

  @Test
  public void getValueTypeAsString_color() {
    Value colorValue =
        Value.newBuilder()
            .setItem(Item.newBuilder().setPrim(Primitive.newBuilder().setColorArgb4Value(0)))
            .build();
    assertThat(XmlProtoPrintUtils.getValueTypeAsString(colorValue)).isEqualTo("COLOR_ARGB4");
  }

  @Test
  public void getValueTypeAsString_compoundValue() {
    Value colorValue =
        Value.newBuilder()
            .setCompoundValue(CompoundValue.newBuilder().setArray(Array.getDefaultInstance()))
            .build();
    assertThat(XmlProtoPrintUtils.getValueTypeAsString(colorValue)).isEqualTo("ARRAY");
  }

  private static Reference createReference(String name) {
    return Reference.newBuilder().setId(0x00000123).setName(name).setType(Type.REFERENCE).build();
  }

  private static Item createItem(String name) {
    return Item.newBuilder().setStr(Resources.String.newBuilder().setValue(name)).build();
  }
}
