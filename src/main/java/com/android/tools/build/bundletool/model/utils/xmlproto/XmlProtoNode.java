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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.google.errorprone.annotations.Immutable;

/** Wrapper around the {@link XmlNode} proto, providing a fluent API. */
@Immutable
public final class XmlProtoNode extends XmlProtoNodeOrBuilder<XmlElement, XmlProtoElement, XmlNode>
    implements ToXmlNode {

  private final XmlNode node;

  public XmlProtoNode(XmlNode node) {
    this.node = checkNotNull(node);
  }

  public static XmlProtoNode createElementNode(XmlProtoElement element) {
    return new XmlProtoNode(XmlNode.newBuilder().setElement(element.getProto()).build());
  }

  public static XmlProtoNode createTextNode(String text) {
    return new XmlProtoNode(XmlNode.newBuilder().setText(text).build());
  }

  public XmlProtoNodeBuilder toBuilder() {
    return new XmlProtoNodeBuilder(node.toBuilder());
  }

  @Override
  public XmlNode toXmlNode() {
    return node;
  }

  @Override
  public XmlNode getProto() {
    return node;
  }

  @Override
  protected XmlElement getProtoElement() {
    return node.getElement();
  }

  @Override
  protected XmlProtoElement newElement(XmlElement element) {
    return new XmlProtoElement(element);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof XmlProtoNode)) {
      return false;
    }
    return node.equals(((XmlProtoNode) o).getProto());
  }

  @Override
  public int hashCode() {
    return node.hashCode();
  }
}
