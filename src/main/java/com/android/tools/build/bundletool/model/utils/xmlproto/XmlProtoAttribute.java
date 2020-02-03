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

import com.android.aapt.Resources.XmlAttribute;
import com.google.errorprone.annotations.Immutable;

/** Wrapper around the {@link XmlAttribute} proto, providing a fluent API. */
@Immutable
public final class XmlProtoAttribute extends XmlProtoAttributeOrBuilder<XmlAttribute> {

  private final XmlAttribute attribute;

  XmlProtoAttribute(XmlAttribute attribute) {
    this.attribute = checkNotNull(attribute);
  }

  /** Creates a new {@link XmlProtoAttribute} without value. */
  public static XmlProtoAttribute create(String namespaceUri, String name) {
    return new XmlProtoAttribute(
        XmlAttribute.newBuilder().setName(name).setNamespaceUri(namespaceUri).build());
  }

  /** Same as {@link #create(String, String)} without namespace. */
  public static XmlProtoAttribute create(String name) {
    return create(NO_NAMESPACE_URI, name);
  }

  public static XmlProtoAttribute createAndroidAttribute(String name, int attributeResId) {
    return new XmlProtoAttribute(
        XmlAttribute.newBuilder()
            .setName(name)
            .setNamespaceUri(ANDROID_NAMESPACE_URI)
            .setResourceId(attributeResId)
            .build());
  }

  @Override
  public XmlAttribute getProto() {
    return attribute;
  }

  public XmlProtoAttributeBuilder toBuilder() {
    return new XmlProtoAttributeBuilder(attribute.toBuilder());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof XmlProtoAttribute)) {
      return false;
    }
    return attribute.equals(((XmlProtoAttribute) o).getProto());
  }

  @Override
  public int hashCode() {
    return attribute.hashCode();
  }
}
