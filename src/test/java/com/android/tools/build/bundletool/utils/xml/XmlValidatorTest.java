/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.build.bundletool.utils.xml;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.exceptions.XmlParsingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.Document;

@RunWith(JUnit4.class)
public class XmlValidatorTest {

  private static final String SCHEMA =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
          + "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\">"
          + "  <element name=\"elevator\">"
          + "    <complexType>"
          + "      <attribute name=\"levels\" use=\"required\">"
          + "        <simpleType>"
          + "          <restriction base=\"positiveInteger\"/>"
          + "        </simpleType>"
          + "      </attribute>"
          + "    </complexType>"
          + "  </element>"
          + "</schema>";

  private XmlValidator validator = new XmlValidator(SCHEMA);

  @Test
  public void testValidXml_matchesSchema() {
    String xml = "<elevator levels=\"11\"/>";

    Document document = validator.validate(xml);
    assertThat(document.getDocumentElement().getLocalName()).isEqualTo("elevator");
  }

  @Test
  public void testInvalidXml() {
    String xml = "<elevator levels=\"11\">"; // No closing tag.

    assertThrows(XmlParsingException.class, () -> validator.validate(xml));
  }

  @Test
  public void testValidXml_doesNotMatchSchema() {
    String xml = "<elevator levels=\"-1\"/>"; // Not a positive integer

    assertThrows(XmlParsingException.class, () -> validator.validate(xml));
  }

  @Test
  public void testValidXml_extraField() {
    String xml = "<elevator levels=\"11\">Unexpected</elevator>"; // Undeclared child node.

    assertThrows(XmlParsingException.class, () -> validator.validate(xml));
  }
}
