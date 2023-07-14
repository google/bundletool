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

/** Wrapper around the {@link XmlNode.Builder} proto, providing a fluent API. */
public final class XmlProtoNodeBuilder
    extends XmlProtoNodeOrBuilder<XmlElement.Builder, XmlProtoElementBuilder, XmlNode.Builder>
    implements ToXmlNode {

  private final XmlNode.Builder node;

  public XmlProtoNodeBuilder(XmlNode.Builder node) {
    this.node = checkNotNull(node);
  }

  public static XmlProtoNodeBuilder createElementNode(XmlProtoElementBuilder element) {
    return new XmlProtoNodeBuilder(XmlNode.newBuilder().setElement(element.getProto()));
  }

  public static XmlProtoNodeBuilder createTextNode(String text) {
    return new XmlProtoNodeBuilder(XmlNode.newBuilder().setText(text));
  }

  @Override
  public XmlNode.Builder getProto() {
    return node;
  }

  public XmlProtoNode build() {
    return new XmlProtoNode(node.build());
  }

  @Override
  public XmlNode toXmlNode() {
    return node.build();
  }

  @Override
  protected XmlElement.Builder getProtoElement() {
    return node.getElementBuilder();
  }

  @Override
  protected XmlProtoElementBuilder newElement(XmlElement.Builder element) {
    return new XmlProtoElementBuilder(element);
  }

  public XmlProtoNodeBuilder setElement(XmlProtoElementBuilder newElement) {
    node.setElement(newElement.getProto());
    return this;
  }

  public XmlProtoNodeBuilder setText(String newText) {
    node.setText(newText);
    return this;
  }
}
