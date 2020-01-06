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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.Resources.XmlAttribute;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlProtoAttributeBuilderTest {

  @Test
  public void createWithName() {
    XmlProtoAttributeBuilder attribute = XmlProtoAttributeBuilder.create("hello");
    assertThat(attribute.getProto().build())
        .isEqualTo(XmlAttribute.newBuilder().setName("hello").build());
  }

  @Test
  public void createWithNameAndNamespace() {
    XmlProtoAttributeBuilder attribute = XmlProtoAttributeBuilder.create("namespace", "hello");
    assertThat(attribute.getProto().build())
        .isEqualTo(XmlAttribute.newBuilder().setName("hello").setNamespaceUri("namespace").build());
  }

  @Test
  public void createAndroidAttribute() {
    XmlProtoAttributeBuilder attribute =
        XmlProtoAttributeBuilder.createAndroidAttribute("hello", 0x123);
    assertThat(attribute.getProto().build())
        .isEqualTo(
            XmlAttribute.newBuilder()
                .setName("hello")
                .setNamespaceUri(ANDROID_NAMESPACE_URI)
                .setResourceId(0x123)
                .build());
  }

  @Test
  public void setValueAsBoolean() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsBoolean(true);
    assertThat(attribute.getValueAsBoolean()).isTrue();

    attribute.setValueAsBoolean(false);
    assertThat(attribute.getValueAsBoolean()).isFalse();
  }

  @Test
  public void setValueAsBoolean_overridesPreviousType() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    attribute.setValueAsBoolean(true);

    assertThat(attribute.getValueAsBoolean()).isTrue();
  }

  @Test
  public void setValueAsBoolean_clearsRawValue() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    checkState(!attribute.getProto().build().getValue().isEmpty());

    attribute.setValueAsBoolean(true);
    assertThat(attribute.getProto().build().getValue()).isEmpty();
  }

  @Test
  public void setValueAsRefId() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsRefId(0x123);
    assertThat(attribute.getValueAsRefId()).isEqualTo(0x123);

    attribute.setValueAsRefId(0x456);
    assertThat(attribute.getValueAsRefId()).isEqualTo(0x456);
  }

  @Test
  public void setValueAsRefIdWithName() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsRefId(0x123, "string/title");
    assertThat(attribute.getValueAsRefId()).isEqualTo(0x123);
    assertThat(attribute.getProto().getCompiledItem().getRef().getName()).isEqualTo("string/title");

    attribute.setValueAsRefId(0x456);
    assertThat(attribute.getProto().getCompiledItem().getRef().getName()).isEmpty();
  }

  @Test
  public void setValueAsRefId_overridesPreviousType() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    attribute.setValueAsRefId(0x123);

    assertThat(attribute.getValueAsRefId()).isEqualTo(0x123);
  }

  @Test
  public void setValueAsRefId_clearsRawValue() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    checkState(!attribute.getProto().build().getValue().isEmpty());

    attribute.setValueAsRefId(0x123);
    assertThat(attribute.getProto().build().getValue()).isEmpty();
  }

  @Test
  public void setValueAsDecimalInteger() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsDecimalInteger(123);
    assertThat(attribute.getValueAsDecimalInteger()).isEqualTo(123);

    attribute.setValueAsDecimalInteger(456);
    assertThat(attribute.getValueAsDecimalInteger()).isEqualTo(456);
  }

  @Test
  public void setValueAsDecimalInteger_overridesPreviousType() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    attribute.setValueAsDecimalInteger(123);

    assertThat(attribute.getValueAsDecimalInteger()).isEqualTo(123);
  }

  @Test
  public void setValueAsDecimalInteger_alsoSetsRawValue() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    attribute.setValueAsDecimalInteger(123);
    assertThat(attribute.getProto().build().getValue()).isEqualTo("123");
  }

  @Test
  public void setValueAsHexInteger() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsHexInteger(0x123);
    assertThat(attribute.getValueAsInteger()).isEqualTo(0x123);

    attribute.setValueAsHexInteger(0x456);
    assertThat(attribute.getValueAsHexInteger()).isEqualTo(0x456);
  }

  @Test
  public void setValueAsHexInteger_overridesPreviousType() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    attribute.setValueAsHexInteger(0x123);

    assertThat(attribute.getValueAsHexInteger()).isEqualTo(0x123);
  }

  @Test
  public void setValueAsHexInteger_alsoSetsRawValue() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    attribute.setValueAsHexInteger(0x123);
    assertThat(attribute.getProto().build().getValue()).isEqualTo("0x00000123");
  }

  @Test
  public void setValueAsString() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    assertThat(attribute.getValueAsString()).isEqualTo("hello");

    attribute.setValueAsString("world");
    assertThat(attribute.getValueAsString()).isEqualTo("world");
  }

  @Test
  public void setValueAsString_alsoSetsTheRawValue() {
    XmlProtoAttributeBuilder attribute = createAttribute().setValueAsString("hello");
    assertThat(attribute.getProto().build().getValue()).isEqualTo("hello");

    attribute.setValueAsString("world");
    assertThat(attribute.getProto().build().getValue()).isEqualTo("world");
  }

  @Test
  public void setResourceId() {
    XmlProtoAttributeBuilder attribute = createAttribute().setResourceId(123);
    assertThat(attribute.getProto().build().getResourceId()).isEqualTo(123);

    attribute.setResourceId(456);
    assertThat(attribute.getProto().build().getResourceId()).isEqualTo(456);
  }

  @Test
  public void modifyingProtoModifiesAlsoWrapper() {
    XmlAttribute.Builder protoAttribute = XmlAttribute.newBuilder().setName("hello");
    XmlProtoAttributeBuilder wrapperAttribute = new XmlProtoAttributeBuilder(protoAttribute);

    protoAttribute.setName("world");

    assertThat(wrapperAttribute.getName()).isEqualTo("world");
  }

  @Test
  public void debugString_refIdWithName() {
    XmlProtoAttribute attribute =
        XmlProtoAttributeBuilder.create("title")
            .setValueAsRefId(0x7f030000, "string/title")
            .build();
    assertThat(attribute.getDebugString()).isEqualTo("@string/title");
  }

  @Test
  public void debugString_refIdWithoutName() {
    XmlProtoAttribute attribute =
        XmlProtoAttributeBuilder.create("title").setValueAsRefId(0x7f030000).build();
    assertThat(attribute.getDebugString()).isEqualTo("0x7f030000");
  }

  private static XmlProtoAttributeBuilder createAttribute() {
    return XmlProtoAttributeBuilder.create("hello");
  }
}
