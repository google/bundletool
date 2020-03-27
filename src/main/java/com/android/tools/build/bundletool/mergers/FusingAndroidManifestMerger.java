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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merger that creates a new manifest by merging all activities from feature modules into base
 * module manifest.
 *
 * <p>This is needed because resource IDs might be resolved incorrectly in base manifest for
 * activities from feature modules when they use resources that share the same name in feature and
 * base (https://github.com/google/bundletool/issues/68).
 */
public class FusingAndroidManifestMerger implements AndroidManifestMerger {

  @Override
  public AndroidManifest merge(SetMultimap<BundleModuleName, AndroidManifest> manifests) {
    if (!manifests.containsKey(BASE_MODULE_NAME)) {
      throw CommandExecutionException.builder()
          .withMessage("Expected to have base module.")
          .build();
    }
    return merge(ensureOneManifestPerModule(manifests));
  }

  private static AndroidManifest merge(Map<BundleModuleName, AndroidManifest> manifests) {
    AndroidManifest baseManifest = manifests.get(BASE_MODULE_NAME);
    List<AndroidManifest> featureManifests =
        manifests.entrySet().stream()
            .filter(entry -> !BASE_MODULE_NAME.equals(entry.getKey()))
            .map(Map.Entry::getValue)
            .collect(toImmutableList());

    return mergeFeatureActivitiesToBase(baseManifest, extractActivities(featureManifests));
  }

  private static AndroidManifest mergeFeatureActivitiesToBase(
      AndroidManifest base, Map<String, XmlProtoElement> featureActivities) {
    return base.toEditor().addOrReplaceActivities(featureActivities).save();
  }

  private static ImmutableMap<BundleModuleName, AndroidManifest> ensureOneManifestPerModule(
      SetMultimap<BundleModuleName, AndroidManifest> manifests) {
    ImmutableMap.Builder<BundleModuleName, AndroidManifest> builder = ImmutableMap.builder();
    for (BundleModuleName moduleName : manifests.keys()) {
      Set<AndroidManifest> moduleManifests = manifests.get(moduleName);
      if (moduleManifests.size() != 1) {
        throw CommandExecutionException.builder()
            .withMessage(
                "Expected exactly one %s module manifest, but found %d.",
                moduleName.getName(), moduleManifests.size())
            .build();
      }
      builder.put(moduleName, Iterables.getOnlyElement(moduleManifests));
    }
    return builder.build();
  }

  private static ImmutableMap<String, XmlProtoElement> extractActivities(
      List<AndroidManifest> manifests) {
    return manifests.stream()
        .flatMap(manifest -> manifest.getActivitiesByName().entrySet().stream())
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
