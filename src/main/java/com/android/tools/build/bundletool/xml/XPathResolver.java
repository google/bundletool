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

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableMap;
import java.util.StringJoiner;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Resolver of XPath expressions against an XML node. */
public final class XPathResolver {

  public static XPathResult resolve(Node node, XPathExpression xPathExpression) {
    try {
      NodeList nodeList = (NodeList) xPathExpression.evaluate(node, XPathConstants.NODESET);
      return new XPathResult(nodeList);
    } catch (XPathExpressionException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Error evaluating the XPath expression.")
          .withCause(e)
          .build();
    }
  }

  /** Result of an XPath evaluation. */
  public static final class XPathResult {

    private static final ImmutableMap<Short, String> TYPE_VALUE_TO_NAME =
        ImmutableMap.<Short, String>builder()
            .put(Node.ATTRIBUTE_NODE, "attribute")
            .put(Node.CDATA_SECTION_NODE, "CDATA section")
            .put(Node.COMMENT_NODE, "comment")
            .put(Node.DOCUMENT_FRAGMENT_NODE, "document fragment")
            .put(Node.DOCUMENT_NODE, "document")
            .put(Node.DOCUMENT_TYPE_NODE, "document type")
            .put(Node.ELEMENT_NODE, "element")
            .put(Node.ENTITY_NODE, "entity")
            .put(Node.ENTITY_REFERENCE_NODE, "entity reference")
            .put(Node.NOTATION_NODE, "notation")
            .put(Node.PROCESSING_INSTRUCTION_NODE, "processing instruction")
            .put(Node.TEXT_NODE, "text")
            .build();

    private final NodeList nodeList;

    private XPathResult(NodeList nodeList) {
      this.nodeList = nodeList;
    }

    /**
     * String representation of the XPath result.
     *
     * <p>If multiple values were matched, each value will be printed on a new line.
     */
    @Override
    public String toString() {
      StringJoiner output = new StringJoiner(System.lineSeparator());

      for (int i = 0; i < nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        switch (node.getNodeType()) {
          case Node.ATTRIBUTE_NODE:
            output.add(((Attr) node).getValue());
            break;

          default:
            throw new UnsupportedOperationException(
                "Unsupported XPath expression: cannot extract nodes of type: "
                    + TYPE_VALUE_TO_NAME.getOrDefault(node.getNodeType(), "<unrecognized>"));
        }
      }

      return output.toString();
    }
  }
}
