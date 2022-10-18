/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.sdkmodule.SdkModuleToAppBundleModuleConverter;
import com.google.common.collect.ImmutableMap;
import javax.inject.Inject;

/**
 * Preprocessor that adds a new feature module to the app bundle per each runtime-enabled SDK
 * dependency.
 */
public class RuntimeEnabledSdkDependencyPreprocessor implements AppBundlePreprocessor {

  private final ImmutableMap<String, BundleModule> sdkBundleModules;

  @Inject
  RuntimeEnabledSdkDependencyPreprocessor(ImmutableMap<String, BundleModule> sdkBundleModules) {
    this.sdkBundleModules = sdkBundleModules;
  }

  @Override
  public AppBundle preprocess(AppBundle bundle) {
    return bundle.toBuilder()
        .addRawModules(
            sdkBundleModules.entrySet().stream()
                .map(
                    entry ->
                        new SdkModuleToAppBundleModuleConverter(
                                entry.getValue(),
                                bundle.getRuntimeEnabledSdkDependencies().get(entry.getKey()),
                                bundle.getBaseModule().getAndroidManifest())
                            .convert())
                .collect(toImmutableList()))
        .build();
  }
}
