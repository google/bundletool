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

import static com.android.tools.build.bundletool.model.AndroidManifest.THEME_RESOURCE_ID;
import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForFeature;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withCustomThemeActivity;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withDebuggableAttribute;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FusingAndroidManifestMergerTest {

  private static final int BASE_THEME_REF_ID = 1;
  private static final int FEATURE1_THEME_REF_ID = 2;
  private static final int FEATURE2_THEME_REF_ID = 3;

  private final FusingAndroidManifestMerger merger = new FusingAndroidManifestMerger();

  @Test
  public void merge_featureActivitiesIntoBaseManifest() {
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

    AndroidManifest merged = merger.merge(manifests);

    Map<String, Integer> refIdByActivity =
        Maps.transformValues(
            merged.getActivitiesByName(),
            activity -> activity.getAndroidAttribute(THEME_RESOURCE_ID).get().getValueAsRefId());

    assertThat(merged.getPackageName()).isEqualTo("com.testapp");
    assertThat(refIdByActivity)
        .containsExactly(
            "activity1",
            FEATURE1_THEME_REF_ID,
            "activity2",
            BASE_THEME_REF_ID,
            "activity3",
            FEATURE2_THEME_REF_ID);
  }

  @Test
  public void merge_onlyBaseManifest() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        createManifests(
            androidManifest(
                "com.testapp",
                withDebuggableAttribute(true),
                withCustomThemeActivity("activity", BASE_THEME_REF_ID)));

    AndroidManifest merged = merger.merge(manifests);

    assertThat(merged).isEqualTo(getOnlyElement(manifests.get(BASE_MODULE_NAME)));
  }

  @Test
  public void merge_noBaseManifest() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        ImmutableSetMultimap.of(
            BundleModuleName.create("feature"),
            AndroidManifest.create(androidManifest("com.testapp")));

    CommandExecutionException exception =
        assertThrows(CommandExecutionException.class, () -> merger.merge(manifests));
    assertThat(exception).hasMessageThat().isEqualTo("Expected to have base module.");
  }

  @Test
  public void merge_duplicateManifest() {
    SetMultimap<BundleModuleName, AndroidManifest> manifests =
        ImmutableSetMultimap.of(
            BASE_MODULE_NAME,
            AndroidManifest.create(androidManifest("com.testapp1")),
            BASE_MODULE_NAME,
            AndroidManifest.create(androidManifest("com.testapp2")));

    CommandExecutionException exception =
        assertThrows(CommandExecutionException.class, () -> merger.merge(manifests));
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
}
