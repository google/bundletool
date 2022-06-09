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

import static com.android.tools.build.bundletool.model.AndroidManifest.ACTIVITY_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.NAME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.AndroidManifest.SERVICE_ELEMENT_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withCustomThemeActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDebuggableAttribute;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitNameService;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.mergers.FusingAndroidManifestMerger.Mode;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoAttributeBuilder;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElementBuilder;
import com.android.tools.build.bundletool.testing.ManifestProtoUtils.ManifestMutator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class FusingAndroidManifestMergerTest {

  private static final int BASE_THEME_REF_ID = 1;
  private static final int FEATURE1_THEME_REF_ID = 2;
  private static final int FEATURE2_THEME_REF_ID = 3;

  @Test
  @Theory
  public void mergeFeatureActivitiesIntoBaseManifest(FusingAndroidManifestMerger.Mode mode) {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withCustomThemeActivity("activity1", BASE_THEME_REF_ID),
                withCustomThemeActivity("activity2", BASE_THEME_REF_ID),
                withCustomThemeActivity("activity3", BASE_THEME_REF_ID)),
            androidManifestForFeature(
                "com.testapp.feature1",
                withCustomThemeActivity("activity1", FEATURE1_THEME_REF_ID)),
            androidManifestForFeature(
                "com.testapp.feature2",
                withCustomThemeActivity("activity3", FEATURE2_THEME_REF_ID)));

    AndroidManifest merged = createMerger(mode).merge(manifests);

    ListMultimap<String, Integer> refIdByActivity =
        Multimaps.transformValues(
            merged.getActivitiesByName(),
            activities ->
                activities.getAndroidAttribute(THEME_RESOURCE_ID).get().getValueAsRefId());

    assertThat(merged.getPackageName()).isEqualTo("com.testapp");
    assertThat(refIdByActivity)
        .containsExactly(
            "activity1",
            FEATURE1_THEME_REF_ID,
            "activity2",
            BASE_THEME_REF_ID,
            "activity3",
            FEATURE2_THEME_REF_ID)
        .inOrder();
  }

  @Test
  @Theory
  public void mergeFeatureElementsIntoBaseManifest(FusingAndroidManifestMerger.Mode mode) {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withSplitNameService("onlyBaseService", "base"),
                withSplitNameService("baseAndFeatureService", "base"),
                withSplitNameActivity("myActivity", "base")),
            androidManifestForFeature(
                "com.testapp.feature1",
                withSplitNameService("baseAndFeatureService", "feature1"),
                withSplitNameActivity("anotherActivity", "feature1")),
            androidManifestForFeature(
                "com.testapp.feature2",
                withSplitNameService("justFeatureService", "feature2"),
                withSplitNameActivity("myActivity", "feature2")));

    AndroidManifest merged = createMerger(mode).merge(manifests);

    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                withSplitNameService("onlyBaseService", "base"),
                withSplitNameService("baseAndFeatureService", "feature1"),
                withSplitNameActivity("myActivity", "feature2"),
                withSplitNameActivity("anotherActivity", "feature1"),
                withSplitNameService("justFeatureService", "feature2")));

    assertThat(merged).isEqualTo(expected);
  }

  @Test
  @Theory
  public void onlyBaseManifest(FusingAndroidManifestMerger.Mode mode) {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withDebuggableAttribute(true),
                withCustomThemeActivity("activity", BASE_THEME_REF_ID)));

    AndroidManifest merged = createMerger(mode).merge(manifests);

    assertThat(merged).isEqualTo(getOnlyElement(manifests.get(BASE_MODULE_NAME)));
  }

  @Test
  public void replaceMode_fullyReplaceBaseElement() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("baseAction"),
                    /* metadata= */ ImmutableList.of("baseMeta")),
                activityWithIntentFiltersAndMetadata(
                    "anotherActivity",
                    /* intentFilters= */ ImmutableList.of("baseAction"),
                    /* metadata= */ ImmutableList.of("baseMeta"))),
            androidManifestForFeature(
                "com.testapp.feature1",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("feature1Action"),
                    /* metadata= */ ImmutableList.of("feature1Meta"))),
            androidManifestForFeature(
                "com.testapp.feature2",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("feature2Action"),
                    /* metadata= */ ImmutableList.of()),
                activityWithIntentFiltersAndMetadata(
                    "anotherActivity",
                    /* intentFilters= */ ImmutableList.of("feature2Action"),
                    /* metadata= */ ImmutableList.of())));

    AndroidManifest merged = createMerger(Mode.REPLACE).merge(manifests);

    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("feature1Action"),
                    /* metadata= */ ImmutableList.of("feature1Meta")),
                activityWithIntentFiltersAndMetadata(
                    "anotherActivity",
                    /* intentFilters= */ ImmutableList.of("feature2Action"),
                    /* metadata= */ ImmutableList.of())));
    assertThat(merged).isEqualTo(expected);
  }

  @Test
  public void mergeChildrenMode_gatherAllMetadataAndIntentFilters() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("baseAction", "baseAction2"),
                    /* metadata= */ ImmutableList.of("baseMeta"))),
            androidManifestForFeature(
                "com.testapp.feature1",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("feature1Action"),
                    /* metadata= */ ImmutableList.of("feature1Meta"))),
            androidManifestForFeature(
                "com.testapp.feature2",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of("feature2Action"),
                    /* metadata= */ ImmutableList.of())));

    AndroidManifest merged = createMerger(Mode.MERGE_CHILDREN).merge(manifests);

    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of(
                        "feature1Action", "feature2Action", "baseAction", "baseAction2"),
                    /* metadata= */ ImmutableList.of("feature1Meta", "baseMeta"))));
    assertThat(merged).isEqualTo(expected);
  }

  @Test
  public void mergeChildrenMode_onlyOneMetadataPerName() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of(),
                    /* metadata= */ ImmutableList.of("sharedMeta", "baseMeta"))),
            androidManifestForFeature(
                "com.testapp.feature1",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of(),
                    /* metadata= */ ImmutableList.of("sharedMeta"))));

    AndroidManifest merged = createMerger(Mode.MERGE_CHILDREN).merge(manifests);

    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                activityWithIntentFiltersAndMetadata(
                    "myActivity",
                    /* intentFilters= */ ImmutableList.of(),
                    /* metadata= */ ImmutableList.of("sharedMeta", "baseMeta"))));
    assertThat(merged).isEqualTo(expected);
  }

  @Test
  public void mergeChildrenMode_nonIntentFilterOrMetadataElements_notMerged() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(XmlProtoElementBuilder.create("elementBase")))),
            androidManifestForFeature(
                "com.testapp.feature1",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("elementFeature")))));

    AndroidManifest merged = createMerger(Mode.MERGE_CHILDREN).merge(manifests);

    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("elementFeature")))));
    assertThat(merged).isEqualTo(expected);
  }

  @Test
  public void mergeChildrenMode_conflictMetadata_throws() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("meta-data")
                                .addAttribute(
                                    XmlProtoAttributeBuilder.createAndroidAttribute(
                                            NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                        .setValueAsString("meta"))
                                .addChildText("Base text")))),
            androidManifestForFeature(
                "com.testapp.feature1",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("meta-data")
                                .addAttribute(
                                    XmlProtoAttributeBuilder.createAndroidAttribute(
                                            NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                        .setValueAsString("meta"))
                                .addChildText("Feature text")))));

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () -> createMerger(Mode.MERGE_CHILDREN).merge(manifests));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Multiple meta-data entries with the same name are found inside activity:myActivity");
  }

  @Test
  public void mergeChildrenMode_keepTextNodesInsideElement() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("meta-data")
                                .addAttribute(
                                    XmlProtoAttributeBuilder.createAndroidAttribute(
                                            NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                        .setValueAsString("meta"))))),
            androidManifestForFeature(
                "com.testapp.feature1",
                withActivity("myActivity", activity -> activity.addChildText("text"))));

    AndroidManifest merged = createMerger(Mode.MERGE_CHILDREN).merge(manifests);
    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity
                            .addChildText("text")
                            .addChildElement(
                                XmlProtoElementBuilder.create("meta-data")
                                    .addAttribute(
                                        XmlProtoAttributeBuilder.createAndroidAttribute(
                                                NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                            .setValueAsString("meta"))))));
    assertThat(merged).isEqualTo(expected);
  }

  @Test
  public void mergeChildrenMode_metadataWithoutName_notMerged() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("meta-data")
                                .addChildElement(XmlProtoElementBuilder.create("data"))))),
            androidManifestForFeature(
                "com.testapp.feature",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("meta-data")
                                .addChildElement(XmlProtoElementBuilder.create("action"))))));

    AndroidManifest merged = createMerger(Mode.MERGE_CHILDREN).merge(manifests);
    AndroidManifest expected =
        AndroidManifest.create(
            androidManifest(
                "com.testapp",
                withActivity(
                    "myActivity",
                    activity ->
                        activity.addChildElement(
                            XmlProtoElementBuilder.create("meta-data")
                                .addChildElement(XmlProtoElementBuilder.create("action"))))));
    assertThat(merged).isEqualTo(expected);
  }

  @Test
  public void noBaseManifest_throws() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        ImmutableSetMultimap.of(
            BundleModuleName.create("feature"),
            AndroidManifest.create(androidManifest("com.testapp")));

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class, () -> createMerger(Mode.REPLACE).merge(manifests));
    assertThat(exception).hasMessageThat().isEqualTo("Expected to have base module.");
  }

  @Test
  public void duplicateManifest_throws() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        ImmutableSetMultimap.of(
            BASE_MODULE_NAME,
            AndroidManifest.create(androidManifest("com.testapp1")),
            BASE_MODULE_NAME,
            AndroidManifest.create(androidManifest("com.testapp2")));

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class, () -> createMerger(Mode.REPLACE).merge(manifests));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Expected exactly one base module manifest, but found 2.");
  }

  private static ImmutableSetMultimap<BundleModuleName, AndroidManifest> createManifests(
      XmlNode base, XmlNode... features) {
    ImmutableSetMultimap.Builder<BundleModuleName, AndroidManifest> builder =
        ImmutableSetMultimap.builder();

    builder.put(BASE_MODULE_NAME, AndroidManifest.create(base));
    for (int i = 0; i < features.length; i++) {
      builder.put(BundleModuleName.create("feature" + i), AndroidManifest.create(features[i]));
    }
    return builder.build();
  }

  private static FusingAndroidManifestMerger createMerger(FusingAndroidManifestMerger.Mode mode) {
    return new FusingAndroidManifestMerger(
        ImmutableSet.of(ACTIVITY_ELEMENT_NAME, SERVICE_ELEMENT_NAME), mode);
  }

  private static ManifestMutator activityWithIntentFiltersAndMetadata(
      String name, List<String> intentFilters, List<String> metadata) {
    return withActivity(
        name,
        builder -> {
          intentFilters.stream()
              .map(
                  intentFilter ->
                      XmlProtoElementBuilder.create(AndroidManifest.INTENT_FILTER_ELEMENT_NAME)
                          .addChildElement(
                              XmlProtoElementBuilder.create("action")
                                  .addAttribute(
                                      XmlProtoAttributeBuilder.createAndroidAttribute(
                                              NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                          .setValueAsString(intentFilter))))
              .forEach(builder::addChildElement);
          metadata.stream()
              .map(
                  metadataName ->
                      XmlProtoElementBuilder.create(AndroidManifest.META_DATA_ELEMENT_NAME)
                          .addAttribute(
                              XmlProtoAttributeBuilder.createAndroidAttribute(
                                      NAME_ATTRIBUTE_NAME, NAME_RESOURCE_ID)
                                  .setValueAsString(metadataName)))
              .forEach(builder::addChildElement);
          return builder;
        });
  }
}
