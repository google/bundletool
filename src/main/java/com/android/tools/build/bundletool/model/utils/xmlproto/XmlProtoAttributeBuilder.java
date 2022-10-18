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
import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.aapt.Resources;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.XmlAttribute;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Wrapper around the {@link XmlAttribute.Builder} proto, providing a fluent API. */
public final class XmlProtoAttributeBuilder
    extends XmlProtoAttributeOrBuilder<XmlAttribute.Builder> {

  private final XmlAttribute.Builder attribute;

  XmlProtoAttributeBuilder(XmlAttribute.Builder attribute) {
    this.attribute = checkNotNull(attribute);
  }

  /** Creates a new {@link XmlProtoAttributeBuilder} without value. */
  public static XmlProtoAttributeBuilder create(String namespaceUri, String name) {
    return new XmlProtoAttributeBuilder(
        XmlAttribute.newBuilder().setName(name).setNamespaceUri(namespaceUri));
  }

  /** Same as {@link #create(String, String)} without namespace. */
  public static XmlProtoAttributeBuilder create(String name) {
    return create(NO_NAMESPACE_URI, name);
  }

  public static XmlProtoAttributeBuilder createAndroidAttribute(String name, int attributeResId) {
    return new XmlProtoAttributeBuilder(
        XmlAttribute.newBuilder()
            .setName(name)
            .setNamespaceUri(ANDROID_NAMESPACE_URI)
            .setResourceId(attributeResId));
  }

  @Override
  public XmlAttribute.Builder getProto() {
    return attribute;
  }

  public XmlProtoAttribute build() {
    return new XmlProtoAttribute(attribute.build());
  }

  public XmlProtoAttributeBuilder setValueAsBoolean(boolean value) {
    attribute.clearValue();
    attribute.setCompiledItem(
        Item.newBuilder().setPrim(Primitive.newBuilder().setBooleanValue(value)));
    return this;
  }

  public XmlProtoAttributeBuilder setValueAsRefId(int refId) {
    attribute.clearValue();
    attribute.setCompiledItem(Item.newBuilder().setRef(Reference.newBuilder().setId(refId)));
    return this;
  }

  public XmlProtoAttributeBuilder setValueAsRefId(int refId, String name) {
    attribute.clearValue();
    attribute.setCompiledItem(
        Item.newBuilder().setRef(Reference.newBuilder().setId(refId).setName(name)));
    return this;
  }

  public XmlProtoAttributeBuilder setValueAsDecimalInteger(int value) {
    attribute.setValue(String.valueOf(value));
    attribute.setCompiledItem(
        Item.newBuilder().setPrim(Primitive.newBuilder().setIntDecimalValue(value)));
    return this;
  }

  public XmlProtoAttributeBuilder setValueAsHexInteger(int value) {
    attribute.setValue(String.format("0x%08x", value));
    attribute.setCompiledItem(
        Item.newBuilder().setPrim(Primitive.newBuilder().setIntHexadecimalValue(value)));
    return this;
  }

  public XmlProtoAttributeBuilder setValueAsString(String value) {
    attribute.setValue(value);
    attribute.setCompiledItem(
        Item.newBuilder().setStr(Resources.String.newBuilder().setValue(value)));
    return this;
  }

  @CanIgnoreReturnValue
  public XmlProtoAttributeBuilder setValueAsDimension(int value) {
    attribute.setValue(String.valueOf(value));
    attribute.setCompiledItem(
        Item.newBuilder().setPrim(Primitive.newBuilder().setDimensionValue(value)));
    return this;
  }

  public XmlProtoAttributeBuilder setResourceId(int resourceId) {
    attribute.setResourceId(resourceId);
    return this;
  }
}
