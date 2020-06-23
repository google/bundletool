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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNamespace;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableMap;
import java.util.Iterator;
import java.util.stream.Stream;
import javax.xml.namespace.NamespaceContext;

/**
 * A {@link NamespaceContext} that extracts the prefix/URI mapping from the namespace declarations
 * it finds in the given XML.
 */
public final class XmlNamespaceContext implements NamespaceContext {

  private final ImmutableMap<String, String> prefixToNamespaceUri;

  public XmlNamespaceContext(XmlProtoNode manifestProto) {
    this.prefixToNamespaceUri =
        collectNamespaces(manifestProto.getElement())
            // get rid of other proto fields
            .map(xmlProto -> XmlProtoNamespace.create(xmlProto.getPrefix(), xmlProto.getUri()))
            .distinct()
            .collect(toImmutableMap(XmlProtoNamespace::getPrefix, XmlProtoNamespace::getUri));
  }

  /** Recursively collect all the namespace declarations in the given element and its children. */
  private static Stream<XmlProtoNamespace> collectNamespaces(XmlProtoElement protoElement) {
    return Stream.concat(
        protoElement.getNamespaceDeclarations(),
        protoElement.getChildrenElements().flatMap(XmlNamespaceContext::collectNamespaces));
  }

  @Override
  public String getNamespaceURI(String prefix) {
    String namespaceUri = prefixToNamespaceUri.get(prefix);
    if (namespaceUri == null) {
      // Some apps strip the namespace declarations to save space, so we keep an internal dictionary
      // to avoid crashing for the common use-cases.
      namespaceUri = XmlUtils.COMMON_NAMESPACE_PREFIXES.inverse().get(prefix);
    }
    if (namespaceUri == null) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Namespace prefix '%s' not found.", prefix)
          .build();
    }
    return namespaceUri;
  }

  @Override
  public String getPrefix(String namespaceURI) {
    // Unnecessary for XPath evaluation.
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<String> getPrefixes(String namespaceURI) {
    // Unnecessary for XPath evaluation.
    throw new UnsupportedOperationException();
  }
}
