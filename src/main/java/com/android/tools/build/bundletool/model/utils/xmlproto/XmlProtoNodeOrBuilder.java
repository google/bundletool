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

import com.android.aapt.Resources.XmlElementOrBuilder;
import com.android.aapt.Resources.XmlNode.NodeCase;
import com.android.aapt.Resources.XmlNodeOrBuilder;

/**
 * Internal interface ensuring that {@link XmlProtoNode} and {@link XmlProtoNodeBuilder} have the
 * same getters.
 */
abstract class XmlProtoNodeOrBuilder<
    ElementProtoT extends XmlElementOrBuilder,
    ElementWrapperT extends
        XmlProtoElementOrBuilder<NodeProtoT, ?, ElementProtoT, ElementWrapperT, ?, ?>,
    NodeProtoT extends XmlNodeOrBuilder> {

  protected abstract NodeProtoT getProto();

  protected abstract ElementProtoT getProtoElement();

  protected abstract ElementWrapperT newElement(ElementProtoT element);

  public final boolean isElement() {
    return getProto().getNodeCase().equals(NodeCase.ELEMENT);
  }

  public final boolean isText() {
    return getProto().getNodeCase().equals(NodeCase.TEXT);
  }

  public final ElementWrapperT getElement() {
    if (!isElement()) {
      throw new XmlProtoException(
          "Expected node of type 'element' but found: %s", getProto().getNodeCase());
    }
    return newElement(getProtoElement());
  }

  public final String getText() {
    if (!isText()) {
      throw new XmlProtoException(
          "Expected node of type 'text' but found: %s", getProto().getNodeCase());
    }
    return getProto().getText();
  }

  @Override
  public String toString() {
    return getProto().toString();
  }
}
