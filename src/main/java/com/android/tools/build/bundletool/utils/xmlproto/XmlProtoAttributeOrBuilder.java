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
package com.android.tools.build.bundletool.utils.xmlproto;

import com.android.aapt.Resources.Item.ValueCase;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Primitive.OneofValueCase;
import com.android.aapt.Resources.XmlAttributeOrBuilder;

/** Common methods between {@link XmlProtoAttribute} and {@link XmlProtoAttributeBuilder}. */
abstract class XmlProtoAttributeOrBuilder<AttributeProtoT extends XmlAttributeOrBuilder> {

  abstract AttributeProtoT getProto();

  public final String getName() {
    return getProto().getName();
  }

  public final String getNamespaceUri() {
    return getProto().getNamespaceUri();
  }

  public final int getResourceId() {
    return getProto().getResourceId();
  }

  public final String getValueAsString() {
    if (getProto().getCompiledItem().getValueCase() == ValueCase.STR) {
      return getProto().getCompiledItem().getStr().getValue();
    }

    // aapt2 sometimes doesn't put the value in the compiled item, so we also read this.
    return getProto().getValue();
  }

  public final boolean getValueAsBoolean() {
    Primitive primitive = getProto().getCompiledItem().getPrim();
    if (primitive.getOneofValueCase() != OneofValueCase.BOOLEAN_VALUE) {
      throw new UnexpectedAttributeTypeException(getProto(), /* expectedType= */ "boolean");
    }
    return primitive.getBooleanValue();
  }

  public final int getValueAsRefId() {
    if (getProto().getCompiledItem().getValueCase() != ValueCase.REF) {
      throw new UnexpectedAttributeTypeException(getProto(), /* expectedType= */ "reference");
    }
    return getProto().getCompiledItem().getRef().getId();
  }

  public final int getValueAsDecimalInteger() {
    Primitive primitive = getProto().getCompiledItem().getPrim();
    if (primitive.getOneofValueCase() != OneofValueCase.INT_DECIMAL_VALUE) {
      throw new UnexpectedAttributeTypeException(getProto(), /* expectedType= */ "decimal int");
    }
    return primitive.getIntDecimalValue();
  }

  @Override
  public String toString() {
    return getProto().toString();
  }
}
