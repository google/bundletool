/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.build.bundletool.preprocessors;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.google.common.annotations.VisibleForTesting;
import javax.inject.Inject;

/**
 * Preprocessor that injects local testing metadata into the base module's manifest.
 *
 * <p>The metadata element is {@code <meta-data name="local_testing_dir" value="local_testing"/>}.
 */
public class LocalTestingPreprocessor implements AppBundlePreprocessor {
  public static final String METADATA_NAME = "local_testing_dir";
  @VisibleForTesting static final String METADATA_VALUE = "local_testing";

  @Inject
  LocalTestingPreprocessor() {}

  @Override
  public AppBundle preprocess(AppBundle bundle) {
    return bundle.toBuilder()
        .setRawModules(
            bundle.getModules().values().stream()
                .map(module -> module.isBaseModule() ? addLocalTestingMetadata(module) : module)
                .collect(toImmutableList()))
        .build();
  }

  private static BundleModule addLocalTestingMetadata(BundleModule module) {
    return module.toBuilder()
        .setAndroidManifest(addLocalTestingMetadata(module.getAndroidManifest()))
        .build();
  }

  private static AndroidManifest addLocalTestingMetadata(AndroidManifest manifest) {
    if (manifest.getMetadataValue(METADATA_NAME).isPresent()) {
      return manifest;
    }
    return manifest.toEditor().addMetaDataString(METADATA_NAME, METADATA_VALUE).save();
  }
}
