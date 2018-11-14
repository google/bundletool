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
package com.android.tools.build.bundletool.xml;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNamespace;
import com.android.tools.build.bundletool.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Converter between the proto-XML format and real XML.
 *
 * <p>Note that there is some loss of information when converting to string, in particular the type
 * of the attributes. This doesn't matter when printing the XML, but this means that we cannot
 * convert back to a proto XML.
 *
 * <p>This converter is *not* thread-safe.
 */
public final class XmlProtoToXmlConverter {

  private static final String XMLNS_NAMESPACE_URI = "http://www.w3.org/2000/xmlns/";

  /**
   * A map of the namespace URI to prefix.
   *
   * <p>We keep a stack of all the prefixes encountered for a given URI, so we can remove the last
   * ones encountered when they go out of scope.
   */
  private final Map<String, Deque<String>> namespaceUriToPrefix = new HashMap<>();

  public static Document convert(XmlProtoNode protoXml) {
    // Use a fresh instance for every conversion, because the converter is stateful.
    return new XmlProtoToXmlConverter().convertInternal(protoXml);
  }

  private Document convertInternal(XmlProtoNode protoNode) {
    Document document;
    try {
      document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
    document.appendChild(createXmlNode(protoNode, document));

    return document;
  }

  private Node createXmlNode(XmlProtoNode protoNode, Document xmlFactory) {
    if (protoNode.isElement()) {
      XmlProtoElement protoElement = protoNode.getElement();

      // Create the element.
      Element xmlElement;
      String namespaceUri = protoElement.getNamespaceUri();
      if (namespaceUri.isEmpty()) {
        xmlElement = xmlFactory.createElement(protoElement.getName());
      } else {
        String prefix = getPrefixForNamespace(namespaceUri);
        xmlElement =
            xmlFactory.createElementNS(namespaceUri, prefix + ":" + protoElement.getName());
      }

      // Add the namespaces.
      ImmutableList<XmlProtoNamespace> namespaces =
          protoElement.getNamespaceDeclarations().collect(toImmutableList());
      for (XmlProtoNamespace namespace : namespaces) {
        String prefix = namespace.getPrefix();
        Deque<String> prefixes =
            namespaceUriToPrefix.computeIfAbsent(namespace.getUri(), k -> new ArrayDeque<>());
        prefixes.addLast(prefix);
        xmlElement.setAttributeNS(
            /* namespaceUri= */ XMLNS_NAMESPACE_URI,
            /* qualifiedName= */ prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix,
            /* value= */ namespace.getUri());
      }

      // Add the attributes.
      for (XmlProtoAttribute protoAttribute : protoElement.getAttributes().collect(toList())) {
        String attrNamespaceUri = protoAttribute.getNamespaceUri();
        if (attrNamespaceUri.isEmpty()) {
          xmlElement.setAttribute(protoAttribute.getName(), protoAttribute.getDebugString());
        } else {
          String prefix = getPrefixForNamespace(attrNamespaceUri);
          xmlElement.setAttributeNS(
              attrNamespaceUri,
              prefix + ":" + protoAttribute.getName(),
              protoAttribute.getDebugString());
        }
      }

      // Recursively add children.
      for (XmlProtoNode child : protoElement.getChildren().collect(toImmutableList())) {
        xmlElement.appendChild(createXmlNode(child, xmlFactory));
      }

      // Remove the namespace declarations that are now out of scope.
      namespaces.forEach(namespace -> namespaceUriToPrefix.get(namespace.getUri()).removeLast());

      return xmlElement;
    } else {
      return xmlFactory.createTextNode(protoNode.getText());
    }
  }

  private String getPrefixForNamespace(String attrNamespaceUri) {
    Deque<String> prefixes = namespaceUriToPrefix.get(attrNamespaceUri);
    if (prefixes == null || prefixes.isEmpty()) {
      throw ValidationException.builder()
          .withMessage("Prefix for URI '%s' not found", attrNamespaceUri)
          .build();
    }
    return prefixes.peekLast();
  }

  /** See {@link #convert(XmlProtoNode)}. */
  private XmlProtoToXmlConverter() {}
}
