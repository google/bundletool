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

package com.android.tools.build.bundletool.manifest;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Primitive;
import com.android.aapt.Resources.Primitive.OneofValueCase;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlAttributeOrBuilder;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlElementOrBuilder;
import com.android.aapt.Resources.XmlNode;
import com.android.aapt.Resources.XmlNode.Builder;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestValidationException;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Helper methods for processing of proto representation of Android Manifest. */
public final class ProtoXmlHelper {

  public static final String NO_NAMESPACE_URI = "";
  public static final String ANDROID_NAMESPACE_URI = "http://schemas.android.com/apk/res/android";
  public static final int NO_RESOURCE_ID = 0;

  /** Returns the first attribute in the element of a given name assuming empty namespace URI. */
  public static Optional<XmlAttribute> findAttributeWithName(XmlElement el, String name) {
    return findAttribute(el, NO_NAMESPACE_URI, name);
  }

  public static Optional<XmlAttribute> findAttributeWithResourceId(XmlElement el, int resourceId) {
    return el.getAttributeList().stream().filter(attributeWithResourceId(resourceId)).findFirst();
  }

  /** Returns the first attribute in the element of a given name and namespace URI. */
  public static Optional<XmlAttribute> findAttribute(
      XmlElement el, String namespaceUri, String name) {
    return el.getAttributeList()
        .stream()
        .filter(attributeWithName(name).and(attributeWithNamespaceUri(namespaceUri)))
        .findFirst();
  }

  /**
   * Returns the boolean value of the attribute.
   *
   * <p>Assumes the value is true if the data part is set. This is to conform with current state
   * where multiple values for true are used across the Android tools and the framework.
   */
  public static boolean getAttributeValueAsBoolean(XmlAttribute attribute) {
    Primitive primitive = attribute.getCompiledItem().getPrim();
    if (primitive.getOneofValueCase() != OneofValueCase.BOOLEAN_VALUE) {
      throw new ValidationException(
          "Type of the compiled item is expected to be boolean, found:\n" + primitive);
    }
    return primitive.getBooleanValue();
  }

  /** Sets the boolean value of the attribute. */
  public static XmlAttribute.Builder setAttributeValueAsBoolean(
      XmlAttribute.Builder attributeBuilder, boolean value) {
    attributeBuilder.clearValue(); // Clears the string value.
    return attributeBuilder.setCompiledItem(
        Item.newBuilder().setPrim(Primitive.newBuilder().setBooleanValue(value)));
  }

  /** Gets the attribute value as decimal integer. */
  public static Integer getAttributeValueAsDecimalInteger(
      XmlAttribute attribute,
      Function<XmlAttribute, ManifestValidationException> wrongTypeHandler) {
    Primitive primitive = attribute.getCompiledItem().getPrim();
    if (primitive.getOneofValueCase() != OneofValueCase.INT_DECIMAL_VALUE) {
      throw wrongTypeHandler.apply(attribute);
    }
    return primitive.getIntDecimalValue();
  }

  /** Sets the integer (decimal) value of the attribute. */
  public static XmlAttribute.Builder setAttributeValueAsDecimalInteger(
      XmlAttribute.Builder attributeBuilder, int value) {
    attributeBuilder.setValue(String.valueOf(value));
    return attributeBuilder.setCompiledItem(
        Item.newBuilder().setPrim(Primitive.newBuilder().setIntDecimalValue(value)));
  }

  /**
   * Returns the attribute builder of a given attribute name assuming empty namespace URI.
   *
   * <p>If attribute already exists in the {@code XmlElement}, returns that attribute's builder.
   */
  public static XmlAttribute.Builder findOrCreateAttributeBuilder(
      XmlElement.Builder el, String name) {
    return findOrCreateAttributeBuilder(el, NO_NAMESPACE_URI, name);
  }

  /**
   * Returns the attribute builder of a given attribute name and namespace URI.
   *
   * <p>If attribute already exists in the {@code XmlElement}, returns that attribute's builder.
   */
  public static XmlAttribute.Builder findOrCreateAttributeBuilder(
      XmlElement.Builder el, String namespaceUri, String name) {
    return el.getAttributeBuilderList()
        .stream()
        .filter(attributeWithName(name).and(attributeWithNamespaceUri(namespaceUri)))
        .findFirst()
        .orElseGet(
            () -> {
              el.addAttribute(
                  XmlAttribute.newBuilder().setName(name).setNamespaceUri(namespaceUri).build());
              return el.getAttributeBuilder(el.getAttributeCount() - 1);
            });
  }

  /**
   * Returns the attribute builder of a given attribute name and namespace URI.
   *
   * <p>If attribute already exists in the {@code XmlElement}, returns that attribute's builder.
   */
  public static XmlAttribute.Builder findOrCreateAndroidAttributeBuilder(
      XmlElement.Builder el, String name, int resourceId) {
    return el.getAttributeBuilderList()
        .stream()
        .filter(
            attributeWithName(name)
                .and(attributeWithResourceId(resourceId))
                .and(attributeWithNamespaceUri(ANDROID_NAMESPACE_URI)))
        .findFirst()
        .orElseGet(
            () -> {
              el.addAttribute(
                  XmlAttribute.newBuilder()
                      .setName(name)
                      .setResourceId(resourceId)
                      .setNamespaceUri(ANDROID_NAMESPACE_URI)
                      .build());
              return el.getAttributeBuilder(el.getAttributeCount() - 1);
            });
  }

  /**
   * Returns the element builder matching the given name and namespace URI among the direct children
   * of the given element.
   *
   * <p>If the element already exists, returns that element's builder.
   */
  public static XmlElement.Builder findOrCreateElementBuilderInDirectChildren(
      XmlElement.Builder el, String namespaceUri, String name) {
    return el.getChildBuilderList()
        .stream()
        .filter(node -> node.hasElement())
        .map(node -> node.getElementBuilder())
        .filter(elementWithName(name).and(elementWithNamespace(namespaceUri)))
        .findFirst()
        .orElseGet(
            () -> {
              el.addChild(
                  XmlNode.newBuilder()
                      .setElement(
                          XmlElement.newBuilder()
                              .setName(name)
                              .setNamespaceUri(namespaceUri)
                              .build()));
              return el.getChildBuilder(el.getChildCount() - 1).getElementBuilder();
            });
  }

  /** Removes the first found attribute of the given name and namespace URI. */
  public static XmlElement.Builder removeAttribute(
      XmlElement.Builder el, String namespaceUri, String name) {
    for (int i = 0; i < el.getAttributeCount(); i++) {
      if (el.getAttribute(i).getName().equals(name)
          && el.getAttribute(i).getNamespaceUri().equals(namespaceUri)) {
        el.removeAttribute(i);
        return el;
      }
    }
    return el;
  }

  /** Returns first element of the given name and empty namespace URI (if any). */
  public static Optional<XmlElement> getFirstElement(XmlNode rootNode, String elementName) {
    List<XmlElement> elements = findElements(rootNode, elementName).collect(toList());
    return elements.isEmpty() ? Optional.empty() : Optional.of(elements.get(0));
  }

  /** Finds elements of a given name and empty namespace URI. Asserts that exactly one is found. */
  public static XmlElement getExactlyOneElement(XmlNode rootNode, String elementName) {
    return getExactlyOneElement(rootNode, elementName, NO_NAMESPACE_URI);
  }

  /** Finds elements of a given name and namespace URI. Asserts that exactly one is found. */
  public static XmlElement getExactlyOneElement(
      XmlNode rootNode, String elementName, String namespaceUri) {
    List<XmlElement> elements = findElements(rootNode, elementName, namespaceUri).collect(toList());
    if (elements.size() != 1) {
      throw ValidationException.builder()
          .withMessage(
              "Expected to find exactly one <%s> element (with namespace '%s') but got %s.",
              elementName, namespaceUri, elements.size())
          .build();
    }
    return elements.get(0);
  }

  /**
   * Finds first element of the given name and empty namespace URI and, if found, returns its
   * builder. Otherwise a new element is inserted as a child of the root node and its builder is
   * returned.
   *
   * <p>This method returns a {@link Builder} instance.
   */
  public static XmlElement.Builder getFirstOrCreateElementBuilder(
      XmlNode.Builder rootNode, String elementName) {
    List<XmlElement.Builder> elements =
        findElementBuilders(rootNode, elementName).collect(toList());

    if (!elements.isEmpty()) {
      return elements.get(0);
    } else {
      checkArgument(rootNode.hasElement(), "Expecting an element node.");
      XmlElement.Builder rootElementBuilder = rootNode.getElementBuilder();
      rootElementBuilder.addChild(
          XmlNode.newBuilder().setElement(XmlElement.newBuilder().setName(elementName)));
      return rootElementBuilder
          .getChildBuilder(rootElementBuilder.getChildCount() - 1)
          .getElementBuilder();
    }
  }

  /**
   * Finds elements of a given name and empty namespace URI. Asserts that exactly one is found.
   *
   * <p>This method returns a {@link Builder} instance.
   */
  public static XmlElement.Builder getExactlyOneElementBuilder(
      XmlNode.Builder rootNode, String elementName) {
    List<XmlElement.Builder> elements =
        findElementBuilders(rootNode, elementName).collect(toList());
    if (elements.size() != 1) {
      throw ValidationException.builder()
          .withMessage(
              "Expected to find exactly one <%s> element but got %s.", elementName, elements.size())
          .build();
    }
    return elements.get(0);
  }

  /** Returns a stream of XmlNodes by doing a DFS traversal on the XML tree. */
  public static Stream<? extends XmlNode> flattened(XmlNode node) {
    return Stream.concat(
        Stream.of(node),
        node.hasElement()
            ? node.getElement().getChildList().stream().flatMap(ProtoXmlHelper::flattened)
            : Stream.empty());
  }

  /** Returns a stream of {@link XmlNode.Builder} by doing a DFS traversal on the XML tree. */
  public static Stream<? extends XmlNode.Builder> flattenedBuilders(XmlNode.Builder node) {
    return Stream.concat(
        Stream.of(node),
        node.hasElement()
            ? node.getElementBuilder()
                .getChildBuilderList()
                .stream()
                .flatMap(ProtoXmlHelper::flattenedBuilders)
            : Stream.empty());
  }

  /** Finds XML elements with the given name and empty namespace URI. */
  public static Stream<XmlElement> findElements(XmlNode rootNode, String name) {
    return flattened(rootNode)
        .map(node -> node.getElement())
        .filter(elementWithName(name).and(elementWithEmptyNamespace()));
  }

  /** Finds XML elements with the given name and namespace URI. */
  public static Stream<XmlElement> findElements(
      XmlNode rootNode, String name, String namespaceUri) {
    return flattened(rootNode)
        .map(node -> node.getElement())
        .filter(elementWithName(name).and(elementWithNamespace(namespaceUri)));
  }

  /**
   * Finds XML elements with the given name and namespace URI among the direct children of the given
   * element.
   */
  public static Stream<XmlElement> findElementsFromDirectChildren(
      XmlElement xmlElement, String name, String namespaceUri) {
    return xmlElement
        .getChildList()
        .stream()
        .map(node -> node.getElement())
        .filter(elementWithName(name).and(elementWithNamespace(namespaceUri)));
  }

  /**
   * Finds the unique XML element with the given name and namespace URI among the direct children of
   * the given element.
   *
   * <p>Throws an exception if more than one match was found.
   */
  public static Optional<XmlElement> findElementFromDirectChildren(
      XmlElement xmlElement, String name, String namespaceUri) {
    List<XmlElement> matches =
        findElementsFromDirectChildren(xmlElement, name, namespaceUri).collect(toList());
    if (matches.size() > 1) {
      throw ValidationException.builder()
          .withMessage(
              "At most one element <%s> with namespace '%s' was expected, but %s were found.",
              name, namespaceUri, matches.size())
          .build();
    }
    return matches.isEmpty() ? Optional.empty() : Optional.of(Iterables.getOnlyElement(matches));
  }

  /** Finds XML elements with the given name and empty namespace URI. */
  public static Stream<XmlElement.Builder> findElementBuilders(
      XmlNode.Builder rootNode, String name) {
    return flattenedBuilders(rootNode)
        .filter(XmlNode.Builder::hasElement)
        .map(XmlNode.Builder::getElementBuilder)
        .filter(elementWithName(name).and(elementWithEmptyNamespace()));
  }

  public static Predicate<XmlElementOrBuilder> elementWithEmptyNamespace() {
    return element -> element.getNamespaceUri().isEmpty();
  }

  public static Predicate<XmlElementOrBuilder> elementWithNamespace(String namespaceUri) {
    return element -> namespaceUri.equals(element.getNamespaceUri());
  }

  public static Predicate<XmlElementOrBuilder> elementWithName(String name) {
    checkNotNull(name);
    return element -> name.equals(element.getName());
  }

  public static Predicate<XmlAttributeOrBuilder> attributeWithName(String name) {
    checkNotNull(name);
    return attribute -> name.equals(attribute.getName());
  }

  public static Predicate<XmlAttributeOrBuilder> attributeWithNamespaceUri(String namespaceUri) {
    checkNotNull(namespaceUri);
    return attribute -> namespaceUri.equals(attribute.getNamespaceUri());
  }

  public static Predicate<XmlAttributeOrBuilder> attributeWithResourceId(int resourceId) {
    return attribute -> attribute.getResourceId() == resourceId;
  }

  // Do not instantiate.
  private ProtoXmlHelper() {}
}
