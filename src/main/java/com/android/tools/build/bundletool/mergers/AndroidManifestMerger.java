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

import com.android.tools.build.bundletool.mergers.FusingAndroidManifestMerger.Mode;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import java.util.Set;

/** Merges AndroidManifest.xml from all modules into one */
public interface AndroidManifestMerger {

  AndroidManifest merge(SetMultimap<BundleModuleName, AndroidManifest> manifests);

  /** Merger that overrides merging process with explicitly provided manifest */
  static AndroidManifestMerger manifestOverride(AndroidManifest override) {
    return (manifests) -> override;
  }

  static AndroidManifestMerger fusingMergerOnlyReplaceActivities() {
    return new FusingAndroidManifestMerger(
        ImmutableSet.of(AndroidManifest.ACTIVITY_ELEMENT_NAME), Mode.REPLACE);
  }

  static AndroidManifestMerger fusingMergerApplicationElements() {
    return new FusingAndroidManifestMerger(
        ImmutableSet.of(
            AndroidManifest.ACTIVITY_ELEMENT_NAME,
            AndroidManifest.ACTIVITY_ALIAS_ELEMENT_NAME,
            AndroidManifest.META_DATA_ELEMENT_NAME,
            AndroidManifest.PROVIDER_ELEMENT_NAME,
            AndroidManifest.RECEIVER_ELEMENT_NAME,
            AndroidManifest.SERVICE_ELEMENT_NAME),
        Mode.MERGE_CHILDREN);
  }

  /** Merger that takes manifest from base module as merged manifest */
  static AndroidManifestMerger useBaseModuleManifestMerger() {
    return (manifests) -> {
      Set<AndroidManifest> baseManifests = manifests.get(BASE_MODULE_NAME);
      if (baseManifests.size() != 1) {
        throw CommandExecutionException.builder()
            .withInternalMessage(
                "Expected exactly one base module manifest, but found %d.", baseManifests.size())
            .build();
      }
      return Iterables.getOnlyElement(baseManifests);
    };
  }
}
