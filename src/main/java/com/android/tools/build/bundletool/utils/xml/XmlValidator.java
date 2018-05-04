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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import com.android.tools.build.bundletool.exceptions.XmlParsingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Validates XML. */
public final class XmlValidator {

  private final Schema schema;

  public XmlValidator(URL schemaUrl) {
    SchemaFactory schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
    try {
      this.schema = schemaFactory.newSchema(schemaUrl);
    } catch (SAXException e) {
      throw new RuntimeException("Unable to read the XML schema.", e);
    }
  }

  public XmlValidator(String schema) {
    SchemaFactory schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
    try {
      this.schema =
          schemaFactory.newSchema(new SAXSource(new InputSource(new StringReader(schema))));
    } catch (SAXException e) {
      throw new RuntimeException("Unable to read the XML schema.", e);
    }
  }

  /**
   * Validates that an XML follows the given XML schema and returns the XML document.
   *
   * @return the XML document.
   * @throws XmlParsingException if the XML doesn't follow the schema.
   */
  public Document validate(String xml) throws XmlParsingException {
    return validate(new ByteArrayInputStream(xml.getBytes(UTF_8)));
  }

  /**
   * Validates that an XML follows the given XML schema and returns the XML document.
   *
   * @return the XML document.
   * @throws XmlParsingException if the XML doesn't follow the schema.
   */
  public Document validate(InputStream xmlStream) throws XmlParsingException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);

      // Setting the schema so we get XML validation.
      factory.setSchema(schema);
      factory.setIgnoringComments(true);
      factory.setIgnoringElementContentWhitespace(true);
      // Validating applies when a DTD is present, but we haven't defined one. However, we'll still
      // get validation just by having called setSchema(). The default value for setValidating() is
      // 'false', but we're setting it here explicitly just to emphasize this has been considered.
      factory.setValidating(false);

      DocumentBuilder documentBuilder = factory.newDocumentBuilder();
      documentBuilder.setErrorHandler(new XmlParsingErrorHandler());
      try {
        return documentBuilder.parse(xmlStream);
      } catch (IOException e) {
        throw new RuntimeException("Unable to read the XML", e);
      }
    } catch (SAXException | ParserConfigurationException e) {
      // All the XML validation exceptions are thrown as XmlParsingException, so anything else is
      // not XML-validation related and is unexpected.
      throw new RuntimeException("Unexpected error while trying to validate the XML.", e);
    }
  }

  /**
   * An XML {@link org.xml.sax.ErrorHandler} that throws {@link XmlParsingException} for any
   * warning, error or fatal error.
   */
  private static class XmlParsingErrorHandler implements ErrorHandler {

    @Override
    public void warning(SAXParseException e) throws SAXException {
      throw new XmlParsingException("Encountered a warning during XML parsing", e);
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
      throw new XmlParsingException("Encountered an error during XML parsing", e);
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
      throw new XmlParsingException("Encountered a fatal error during XML parsing", e);
    }
  }
}
