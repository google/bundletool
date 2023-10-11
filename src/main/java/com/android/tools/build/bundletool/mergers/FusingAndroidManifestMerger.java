/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.toOptional;
import static com.google.common.collect.Streams.stream;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ManifestEditor;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNodeBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Merger that creates a new manifest by replacing/merging children elements of application from
 * feature modules into base module manifest.
 */
public class FusingAndroidManifestMerger implements AndroidManifestMerger {

  /** Defines mode how elements should be fused. */
  public enum Mode {
    /**
     * Instructs to take element declaration from feature module and fully replace corresponding one
     * in the base.
     */
    REPLACE,
    /**
     * Instructs to take element declaration from feature module but gather all 'intent-filter',
     * 'meta-data' child elements from other modules and add them into fused element.
     */
    MERGE_CHILDREN
  }

  private final ImmutableSet<String> elementsToMerge;
  private final Mode mode;

  public FusingAndroidManifestMerger(ImmutableSet<String> elementsToMerge, Mode mode) {
    this.elementsToMerge = elementsToMerge;
    this.mode = mode;
  }

  @Override
  public AndroidManifest merge(SetMultimap<BundleModuleName, AndroidManifest> manifests) {
    if (!manifests.containsKey(BASE_MODULE_NAME)) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Expected to have base module.")
          .build();
    }
    return merge(ensureOneManifestPerModule(manifests));
  }

  private AndroidManifest merge(Map<BundleModuleName, AndroidManifest> manifests) {
    AndroidManifest baseManifest = manifests.get(BASE_MODULE_NAME);
    List<AndroidManifest> featureManifests =
        manifests.entrySet().stream()
            .filter(entry -> !BASE_MODULE_NAME.equals(entry.getKey()))
            .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
            .map(Map.Entry::getValue)
            .collect(toImmutableList());

    if (featureManifests.isEmpty()) {
      return baseManifest;
    }
    return mergeManifests(baseManifest, featureManifests);
  }

  private AndroidManifest mergeManifests(
      AndroidManifest baseManifest, List<AndroidManifest> featureManifests) {
    // Gather all child elements of 'application' from all manifest. If element with the same name
    // and type is presented in more than one manifest we give precedency to one in feature module.
    // All feature manifests are sorted by feature module name in this method.
    ImmutableListMultimap<ApplicationElementId, XmlProtoElement> applicationElements =
        gatherApplicationElementsManifests(
            ImmutableList.<AndroidManifest>builder()
                .addAll(featureManifests)
                .add(baseManifest)
                .build(),
            elementsToMerge);

    // This is optimization that allows to skip merging if there is no mergeable elements in
    // feature modules.
    long numberOfMergeableElementsInBase =
        baseManifest
            .getManifestRoot()
            .getElement()
            .getChildrenElements(AndroidManifest.APPLICATION_ELEMENT_NAME)
            .flatMap(application -> application.getChildrenElements())
            .filter(element -> elementsToMerge.contains(element.getName()))
            .count();
    if (numberOfMergeableElementsInBase == applicationElements.size()) {
      return baseManifest;
    }

    // Merge manifest elements with the same name and type based on specified mode.
    ImmutableMap<ApplicationElementId, XmlProtoElement> mergedElements =
        applicationElements.keySet().stream()
            .collect(
                toImmutableMap(
                    Function.identity(), key -> mergeElements(key, applicationElements.get(key))));

    ManifestEditor manifestEditor = baseManifest.toEditor();
    XmlProtoElementBuilder applicationElement =
        manifestEditor
            .getRawProto()
            .getOrCreateChildElement(AndroidManifest.APPLICATION_ELEMENT_NAME);

    // Replace original elements from the base manifest with merged ones. This is done in a way to
    // preserve original elements ordering and additional elements are added to the end.
    Set<XmlProtoElement> replacedElements = Sets.newIdentityHashSet();
    applicationElement.modifyChildElements(
        child ->
            stream(getCorrespondingElementFromMergedElements(child, mergedElements))
                .peek(replacedElements::add)
                .map(element -> XmlProtoNodeBuilder.createElementNode(element.toBuilder()))
                .collect(toOptional())
                .orElse(child));

    mergedElements.values().stream()
        .filter(not(replacedElements::contains))
        .forEach(element -> applicationElement.addChildElement(element.toBuilder()));

    return manifestEditor.save();
  }

  /**
   * Merges element with the same name and type from different manifests. {@code elements} list in
   * this method contains data from feature modules first and element from the base module is the
   * last one in the list.
   *
   * <p>If element is presented in more than one feature module elements are sorted by name of
   * feature module. Example: if service with name 'myService' is defined in base module and
   * features 'a' and 'b', {@code elements} list will contain its declaration in the following
   * order: 'a' feature, 'b' feature, base.
   */
  private XmlProtoElement mergeElements(
      ApplicationElementId elementId, List<XmlProtoElement> elements) {
    // If we don't need to merge nested elements and just replace declarations from base module
    // we just take the first element from the list.
    if (mode.equals(Mode.REPLACE) || elements.size() == 1) {
      return elements.get(0);
    }

    // Remove source data from nested elements as this data is meaningless for functionality and
    // just contains information about line/column where element appeared in the original xml.
    List<XmlProtoElement> elementsNoSource =
        elements.stream()
            .map(element -> element.toBuilder().removeSourceDataRecursive().build())
            .collect(toImmutableList());

    // For intent filters we gather all distinct filters defined in all modules.
    Set<XmlProtoElement> intentFilters =
        elementsNoSource.stream()
            .flatMap(
                element -> element.getChildrenElements(AndroidManifest.INTENT_FILTER_ELEMENT_NAME))
            .collect(toImmutableSet());

    // For meta-data we group them by name and take one per each name.
    ImmutableMap<String, XmlProtoElement> metadataByName =
        elementsNoSource.stream()
            .flatMap(element -> element.getChildrenElements(AndroidManifest.META_DATA_ELEMENT_NAME))
            .filter(meta -> meta.getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID).isPresent())
            .collect(
                toImmutableMap(
                    meta ->
                        meta.getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID)
                            .get()
                            .getValueAsString(),
                    Function.identity(),
                    (a, b) -> {
                      // If meta-data with the same name but different value is defined in
                      // different modules throw conflict exception.
                      if (!a.equals(b)) {
                        throw CommandExecutionException.builder()
                            .withInternalMessage(
                                "Multiple meta-data entries with the same name are found inside"
                                    + " %s:%s: %s, %s",
                                elementId.getType(), elementId.getName(), a, b)
                            .build();
                      }
                      return a;
                    }));

    // Take element declaration from feature module and add all intent filters and meta data to it.
    XmlProtoElementBuilder builder = elementsNoSource.get(0).toBuilder();
    builder.removeChildrenElementsIf(
        child -> {
          if (!child.isElement()) {
            return false;
          }
          XmlProtoElementBuilder childElement = child.getElement();
          String tag = childElement.getName();
          Optional<String> name =
              childElement
                  .getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID)
                  .map(XmlProtoAttributeBuilder::getValueAsString);

          return tag.equals(AndroidManifest.INTENT_FILTER_ELEMENT_NAME)
              || (tag.equals(AndroidManifest.META_DATA_ELEMENT_NAME)
                  && name.map(metadataByName::containsKey).orElse(false));
        });
    intentFilters.forEach(e -> builder.addChildElement(e.toBuilder()));
    metadataByName.values().forEach(e -> builder.addChildElement(e.toBuilder()));
    return builder.build();
  }

  private static ImmutableListMultimap<ApplicationElementId, XmlProtoElement>
      gatherApplicationElementsManifests(
          List<AndroidManifest> featureManifests, ImmutableSet<String> elementsToMerge) {
    ImmutableListMultimap.Builder<ApplicationElementId, XmlProtoElement> featureElementsBuilder =
        ImmutableListMultimap.builder();
    featureManifests.forEach(
        manifest -> gatherApplicationElements(manifest, elementsToMerge, featureElementsBuilder));
    return featureElementsBuilder.build();
  }

  private static void gatherApplicationElements(
      AndroidManifest manifest,
      ImmutableSet<String> elementsToMerge,
      ImmutableListMultimap.Builder<ApplicationElementId, XmlProtoElement> featureElementsBuilder) {
    Optional<XmlProtoElement> manifestElement =
        manifest
            .getManifestRoot()
            .getElement()
            .getOptionalChildElement(AndroidManifest.APPLICATION_ELEMENT_NAME);
    stream(manifestElement)
        .flatMap(application -> application.getChildrenElements())
        .filter(child -> elementsToMerge.contains(child.getName()))
        .filter(
            child ->
                child.getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID).isPresent()
                    && !isMetaDataGmsCoreVersion(child))
        .forEach(
            child ->
                featureElementsBuilder.put(
                    ApplicationElementId.create(child.getName(), getNameAttribute(child)), child));
  }

  private static Optional<XmlProtoElement> getCorrespondingElementFromMergedElements(
      XmlProtoNodeBuilder node,
      ImmutableMap<ApplicationElementId, XmlProtoElement> mergedElements) {
    if (!node.isElement()) {
      return Optional.empty();
    }
    Optional<XmlProtoAttributeBuilder> name =
        node.getElement().getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID);
    return name.map(
        xmlProtoAttributeBuilder ->
            mergedElements.get(
                ApplicationElementId.create(
                    node.getElement().getName(), xmlProtoAttributeBuilder.getValueAsString())));
  }

  private static String getNameAttribute(XmlProtoElement element) {
    return element.getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID).get().getValueAsString();
  }

  private static ImmutableMap<BundleModuleName, AndroidManifest> ensureOneManifestPerModule(
      SetMultimap<BundleModuleName, AndroidManifest> manifests) {
    ImmutableMap.Builder<BundleModuleName, AndroidManifest> builder = ImmutableMap.builder();
    for (BundleModuleName moduleName : manifests.keySet()) {
      Set<AndroidManifest> moduleManifests = manifests.get(moduleName);
      if (moduleManifests.size() != 1) {
        throw CommandExecutionException.builder()
            .withInternalMessage(
                "Expected exactly one %s module manifest, but found %d.",
                moduleName.getName(), moduleManifests.size())
            .build();
      }
      builder.put(moduleName, Iterables.getOnlyElement(moduleManifests));
    }
    return builder.build();
  }

  private static boolean isMetaDataGmsCoreVersion(XmlProtoElement element) {
    return element.getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID).isPresent()
        && element
            .getAndroidAttribute(AndroidManifest.NAME_RESOURCE_ID)
            .get()
            .getValueAsString()
            .equals("com.google.android.gms.version");
  }

  @AutoValue
  abstract static class ApplicationElementId {
    abstract String getType();

    abstract String getName();

    static ApplicationElementId create(String type, String name) {
      return new AutoValue_FusingAndroidManifestMerger_ApplicationElementId(type, name);
    }
  }
}
