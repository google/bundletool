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

import com.android.aapt.Resources.XmlAttributeOrBuilder;

/** Exception thrown when an XML attribute is of an unexpected type. */
public class UnexpectedAttributeTypeException extends XmlProtoException {
  private final XmlAttributeOrBuilder attribute;
  private final String expectedType;

  UnexpectedAttributeTypeException(XmlAttributeOrBuilder attribute, String expectedType) {
    super(
        "Attribute '%s' expected to have type '%s' but found:\n %s",
        attribute.getName(), expectedType, attribute);
    this.attribute = attribute;
    this.expectedType = expectedType;
  }

  public XmlAttributeOrBuilder getAttribute() {
    return attribute;
  }

  public String getExpectedType() {
    return expectedType;
  }
}
