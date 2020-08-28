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
package com.android.tools.build.bundletool.preprocessors;

import com.android.tools.build.bundletool.model.AppBundle;
import com.google.common.collect.ImmutableList;
import javax.inject.Inject;

/** Coordinates all the pre-processing steps before the build-apks command is executed. */
public final class AppBundlePreprocessorManager {

  private final ImmutableList<AppBundlePreprocessor> appBundlePreprocessors;

  @Inject
  AppBundlePreprocessorManager(ImmutableList<AppBundlePreprocessor> appBundlePreprocessors) {
    this.appBundlePreprocessors = appBundlePreprocessors;
  }

  public AppBundle processAppBundle(AppBundle appBundle) {
    AppBundle newAppBundle = appBundle;
    for (AppBundlePreprocessor preprocessor : appBundlePreprocessors) {
      newAppBundle = preprocessor.preprocess(newAppBundle);
    }
    return newAppBundle;
  }
}
