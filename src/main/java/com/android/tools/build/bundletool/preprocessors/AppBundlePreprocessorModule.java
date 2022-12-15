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

import com.android.tools.build.bundletool.commands.BuildApksCommand;
import com.android.tools.build.bundletool.commands.BuildApksModule;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;

/**
 * Dagger module for the pre-processing step of the App Bundle.
 *
 * @see AppBundlePreprocessorComponent
 * @see AppBundlePreprocessorManager
 */
@Module(includes = {BuildApksModule.class})
public final class AppBundlePreprocessorModule {

  @Provides
  static ImmutableList<AppBundlePreprocessor> providePreprocessors(
      AppBundle64BitNativeLibrariesPreprocessor appBundle64BitNativeLibrariesPreprocessor,
      EmbeddedApkSigningPreprocessor embeddedApkSigningPreprocessor,
      EntryCompressionPreprocessor entryCompressionPreprocessor,
      LocalTestingPreprocessor localTestingPreprocessor,
      RuntimeEnabledSdkDependencyPreprocessor runtimeEnabledSdkDependencyPreprocessor,
      LocalRuntimeEnabledSdkConfigPreprocessor localRuntimeEnabledSdkConfigPreprocessor,
      BuildApksCommand command) {
    ImmutableList.Builder<AppBundlePreprocessor> preprocessors =
        ImmutableList.<AppBundlePreprocessor>builder()
            .add(
                appBundle64BitNativeLibrariesPreprocessor,
                embeddedApkSigningPreprocessor,
                entryCompressionPreprocessor,
                runtimeEnabledSdkDependencyPreprocessor,
                localRuntimeEnabledSdkConfigPreprocessor);
    if (command.getLocalTestingMode()) {
      preprocessors.add(localTestingPreprocessor);
    }
    return preprocessors.build();
  }

  private AppBundlePreprocessorModule() {}
}
