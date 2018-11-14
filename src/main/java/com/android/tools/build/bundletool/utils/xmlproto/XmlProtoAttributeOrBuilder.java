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

import com.android.aapt.Resources.Item;
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

  public final int getValueAsHexInteger() {
    Primitive primitive = getProto().getCompiledItem().getPrim();
    if (primitive.getOneofValueCase() != OneofValueCase.INT_HEXADECIMAL_VALUE) {
      throw new UnexpectedAttributeTypeException(getProto(), /* expectedType= */ "hexadecimal int");
    }
    return primitive.getIntHexadecimalValue();
  }

  /** Returns integer value either from decimal or hexadecimal integer. */
  public final int getValueAsInteger() {
    Primitive primitive = getProto().getCompiledItem().getPrim();
    switch (primitive.getOneofValueCase()) {
      case INT_DECIMAL_VALUE:
        return primitive.getIntDecimalValue();
      case INT_HEXADECIMAL_VALUE:
        return primitive.getIntHexadecimalValue();
      default:
        throw new UnexpectedAttributeTypeException(
            getProto(), /* expectedType= */ "decimal|hexadecimal int");
    }
  }

  /**
   * Returns the value of the attribute as a string regardless of its type.
   *
   * <p>When not a string, the conversion is done on a best-effort basis, and may change in the
   * future.
   */
  public final String getDebugString() {
    if (!getProto().hasCompiledItem()) {
      return getProto().getValue();
    }

    Item item = getProto().getCompiledItem();
    switch (item.getValueCase()) {
      case PRIM:
        Primitive primitive = item.getPrim();
        switch (primitive.getOneofValueCase()) {
          case NULL_VALUE:
            return "null";
          case EMPTY_VALUE:
            return "empty";
          case FLOAT_VALUE:
            return String.valueOf(primitive.getFloatValue());
          case DIMENSION_VALUE:
            return String.valueOf(primitive.getDimensionValue());
          case FRACTION_VALUE:
            return String.valueOf(primitive.getFractionValue());
          case INT_DECIMAL_VALUE:
            return String.valueOf(primitive.getIntDecimalValue());
          case INT_HEXADECIMAL_VALUE:
            return String.valueOf(primitive.getIntHexadecimalValue());
          case BOOLEAN_VALUE:
            return String.valueOf(primitive.getBooleanValue());
          case COLOR_ARGB8_VALUE:
            return String.valueOf(primitive.getColorArgb8Value());
          case COLOR_RGB8_VALUE:
            return String.valueOf(primitive.getColorRgb8Value());
          case COLOR_ARGB4_VALUE:
            return String.valueOf(primitive.getColorArgb4Value());
          case COLOR_RGB4_VALUE:
            return String.valueOf(primitive.getColorRgb4Value());
          default:
            // Not supported.
            return primitive.toString();
        }
      case FILE:
        return item.getFile().getPath();
      case ID:
        return "<ID>";
      case RAW_STR:
        return item.getRawStr().getValue();
      case REF:
        // Return the name of the resource if available, else the ID.
        if (!item.getRef().getName().isEmpty()) {
          return "@" + item.getRef().getName();
        }
        return String.format("0x%08x", item.getRef().getId());
      case STR:
        return item.getStr().getValue();
      case STYLED_STR:
      default:
        // Not supported.
        return item.toString();
    }
  }

  @Override
  public String toString() {
    return getProto().toString();
  }
}
