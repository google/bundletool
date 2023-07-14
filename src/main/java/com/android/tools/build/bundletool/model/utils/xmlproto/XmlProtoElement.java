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

import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.google.errorprone.annotations.Immutable;
import java.util.List;

/** Wrapper around the {@link XmlElement} proto, providing a fluent API. */
@Immutable
public final class XmlProtoElement
    extends XmlProtoElementOrBuilder<
        XmlNode, XmlProtoNode, XmlElement, XmlProtoElement, XmlAttribute, XmlProtoAttribute>
    implements ToXmlNode {

  private final XmlElement element;

  public static XmlProtoElement create(String namespaceUri, String name) {
    return new XmlProtoElement(
        XmlElement.newBuilder().setNamespaceUri(namespaceUri).setName(name).build());
  }

  public static XmlProtoElement create(String name) {
    return create(NO_NAMESPACE_URI, name);
  }

  public XmlProtoElement(XmlElement element) {
    this.element = checkNotNull(element);
  }

  public XmlProtoElementBuilder toBuilder() {
    return new XmlProtoElementBuilder(element.toBuilder());
  }

  @Override
  public XmlNode toXmlNode() {
    return XmlNode.newBuilder().setElement(element).build();
  }

  @Override
  public XmlElement getProto() {
    return element;
  }

  @Override
  protected List<XmlAttribute> getProtoAttributesList() {
    return element.getAttributeList();
  }

  @Override
  protected List<XmlNode> getProtoChildrenList() {
    return element.getChildList();
  }

  @Override
  protected XmlProtoNode newNode(XmlNode node) {
    return new XmlProtoNode(node);
  }

  @Override
  protected XmlProtoAttribute newAttribute(XmlAttribute attribute) {
    return new XmlProtoAttribute(attribute);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof XmlProtoElement)) {
      return false;
    }
    return element.equals(((XmlProtoElement) o).getProto());
  }

  @Override
  public int hashCode() {
    return element.hashCode();
  }
}
