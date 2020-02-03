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

import static com.android.tools.build.bundletool.model.AndroidManifest.NO_NAMESPACE_URI;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.stream.Collectors.toList;

import com.android.aapt.Resources.XmlAttributeOrBuilder;
import com.android.aapt.Resources.XmlElementOrBuilder;
import com.android.aapt.Resources.XmlNodeOrBuilder;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Internal interface ensuring that {@link XmlProtoElement} and {@link XmlProtoElementBuilder} have
 * the same getters.
 */
abstract class XmlProtoElementOrBuilder<
    NodeProtoT extends XmlNodeOrBuilder,
    NodeWrapperT extends XmlProtoNodeOrBuilder<?, ElementWrapperT, ?>,
    ElementProtoT extends XmlElementOrBuilder,
    ElementWrapperT extends
        XmlProtoElementOrBuilder<NodeProtoT, ?, ElementProtoT, ElementWrapperT, ?, ?>,
    AttributeProtoT extends XmlAttributeOrBuilder,
    AttributeWrapperT extends XmlProtoAttributeOrBuilder<AttributeProtoT>> {

  public abstract ElementProtoT getProto();

  protected abstract List<AttributeProtoT> getProtoAttributesList();

  protected abstract List<NodeProtoT> getProtoChildrenList();

  protected abstract NodeWrapperT newNode(NodeProtoT node);

  protected abstract AttributeWrapperT newAttribute(AttributeProtoT attribute);

  public final String getName() {
    return getProto().getName();
  }

  public final String getNamespaceUri() {
    return getProto().getNamespaceUri();
  }

  public final Stream<AttributeWrapperT> getAttributes() {
    return getProtoAttributesList().stream().map(this::newAttribute);
  }

  public final Stream<NodeWrapperT> getChildren() {
    return getProtoChildrenList().stream().map(this::newNode);
  }

  public final Stream<NodeWrapperT> getChildrenText() {
    return getChildren().filter(NodeWrapperT::isText);
  }

  public final Optional<NodeWrapperT> getChildText() {
    return getChildrenText().collect(toOptional());
  }

  public final Stream<XmlProtoNamespace> getNamespaceDeclarations() {
    return getProto().getNamespaceDeclarationList().stream().map(XmlProtoNamespace::new);
  }

  public final Optional<AttributeWrapperT> getAttribute(String name) {
    return getAttribute(NO_NAMESPACE_URI, name);
  }

  public final Optional<AttributeWrapperT> getAttribute(String namespaceUri, String name) {
    return getAttributes()
        .filter(attr -> attr.getName().equals(name) && attr.getNamespaceUri().equals(namespaceUri))
        .findFirst();
  }

  @Deprecated // Don't ignore the namespace!
  public final Optional<AttributeWrapperT> getAttributeIgnoringNamespace(String name) {
    return getAttributes().filter(attr -> attr.getName().equals(name)).findFirst();
  }

  public final Optional<AttributeWrapperT> getAndroidAttribute(int resourceId) {
    return getAttributes().filter(attr -> attr.getResourceId() == resourceId).findFirst();
  }

  public final Stream<ElementWrapperT> getChildrenElements() {
    return getChildren().filter(NodeWrapperT::isElement).map(NodeWrapperT::getElement);
  }

  /** Finds XML elements among the direct children with the given name and empty namespace URI. */
  public final Stream<ElementWrapperT> getChildrenElements(String name) {
    return getChildrenElements(NO_NAMESPACE_URI, name);
  }

  /** Finds XML elements among the direct children with the given name and namespace URI. */
  public final Stream<ElementWrapperT> getChildrenElements(String namespaceUri, String name) {
    return getChildrenElements(
        el -> el.getName().equals(name) && el.getNamespaceUri().equals(namespaceUri));
  }

  /** Finds XML elements among the direct children that satisfies the given predicate. */
  public final Stream<ElementWrapperT> getChildrenElements(Predicate<ElementWrapperT> predicate) {
    return getChildren()
        .filter(node -> node.isElement())
        .map(node -> node.getElement())
        .filter(predicate);
  }

  /**
   * Finds the unique XML element with the given name and namespace URI among the direct children.
   *
   * <p>Throws an {@link XmlProtoException} if more than one match was found.
   */
  public final Optional<ElementWrapperT> getOptionalChildElement(String namespaceUri, String name) {
    List<ElementWrapperT> matches = getChildrenElements(namespaceUri, name).collect(toList());
    if (matches.size() > 1) {
      throw new XmlProtoException(
          "At most one element <%s> with namespace '%s' was expected, but %d were found.",
          name, namespaceUri, matches.size());
    }
    return matches.isEmpty() ? Optional.empty() : Optional.of(Iterables.getOnlyElement(matches));
  }

  /** Same as {@link #getOptionalChildElement(String, String)} with an empty namespace. */
  public final Optional<ElementWrapperT> getOptionalChildElement(String name) {
    return getOptionalChildElement(NO_NAMESPACE_URI, name);
  }

  /**
   * Finds the unique XML element with the given name and namespace URI among the direct children of
   * the given getProto().
   *
   * <p>Throws an exception if more than one match was found.
   */
  public final ElementWrapperT getChildElement(String namespaceUri, String name) {
    return getOptionalChildElement(namespaceUri, name)
        .orElseThrow(
            () ->
                new XmlProtoException(
                    "One element <%s> with namespace '%s' was expected, but none were found.",
                    name, namespaceUri));
  }

  /**
   * Finds the unique XML element with the given name and an empty namespace URI among the direct
   * children of the given getProto().
   *
   * <p>Throws an exception if more than one match was found.
   */
  public final ElementWrapperT getChildElement(String name) {
    return getOptionalChildElement(NO_NAMESPACE_URI, name)
        .orElseThrow(
            () ->
                new XmlProtoException("One element <%s> was expected, but none were found.", name));
  }

  @Override
  public String toString() {
    return getProto().toString();
  }
}
