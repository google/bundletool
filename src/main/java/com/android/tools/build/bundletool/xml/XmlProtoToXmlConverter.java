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

import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttribute;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNamespace;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
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
   * Index appended at the end of the namespace prefix created when the namespace declarations have
   * been stripped out.
   */
  private int nextPrefixIndex = 0;

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
          xmlElement.setAttribute(
              getAttributeTagName(protoAttribute), protoAttribute.getDebugString());
        } else {
          String prefix = getPrefixForNamespace(attrNamespaceUri);
          xmlElement.setAttributeNS(
              attrNamespaceUri,
              prefix + ":" + getAttributeTagName(protoAttribute),
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

  /**
   * Extract the XML tag name from the attribute we want to print.
   *
   * <p>Because the XML spec doesn't allow empty tags, if the tag is not set in the proto, we return
   * the resource ID if set, else "_unknown_".
   */
  private static String getAttributeTagName(XmlProtoAttribute protoAttribute) {
    if (!protoAttribute.getName().isEmpty()) {
      return protoAttribute.getName();
    }

    if (protoAttribute.getResourceId() == 0) {
      // Something is wrong, but at least we won't crash by passing an empty tag.
      return "_unknown_";
    }

    // Some optimization tools clear the name because the Android platform doesn't need it
    // if it's an Android attribute because the resoure ID is used instead.
    // Surrounding with underscores because a tag name cannot start with a digit.
    return String.format("_0x%08x_", protoAttribute.getResourceId());
  }

  private String getPrefixForNamespace(String attrNamespaceUri) {
    Deque<String> prefixes =
        namespaceUriToPrefix.computeIfAbsent(
            attrNamespaceUri,
            uri -> createDequeWithElement(getCommonPrefix(uri).orElseGet(() -> createNewPrefix())));
    return prefixes.peekLast();
  }

  private Optional<String> getCommonPrefix(String uri) {
    String prefix = XmlUtils.COMMON_NAMESPACE_PREFIXES.get(uri);
    if (prefix == null || isNamespacePrefixInScope(prefix)) {
      return Optional.empty();
    }
    return Optional.of(prefix);
  }

  /** Returns whether the given namespace {@code prefix} is used currently in the scope. */
  private boolean isNamespacePrefixInScope(String prefix) {
    return namespaceUriToPrefix.values().stream()
        .flatMap(queue -> Streams.stream(queue.iterator()))
        .anyMatch(Predicate.isEqual(prefix));
  }

  private String createNewPrefix() {
    return String.format("_unknown%d_", nextPrefixIndex++);
  }

  private static Deque<String> createDequeWithElement(String element) {
    Deque<String> queue = new ArrayDeque<>();
    queue.add(element);
    return queue;
  }

  /** See {@link #convert(XmlProtoNode)}. */
  private XmlProtoToXmlConverter() {}
}
