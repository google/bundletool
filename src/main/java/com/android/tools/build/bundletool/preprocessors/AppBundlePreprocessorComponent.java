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
import com.android.tools.build.bundletool.commands.CommandScoped;
import com.android.tools.build.bundletool.model.BundleModule;
import com.google.common.collect.ImmutableMap;
import dagger.BindsInstance;
import dagger.Component;

/** Dagger component to create a {@link AppBundlePreprocessorManager}. */
@CommandScoped
@Component(modules = AppBundlePreprocessorModule.class)
public interface AppBundlePreprocessorComponent {
  AppBundlePreprocessorManager create();

  /** Builder for the {@link AppBundlePreprocessorComponent}. */
  @Component.Builder
  interface Builder {
    AppBundlePreprocessorComponent build();

    @BindsInstance
    Builder setBuildApksCommand(BuildApksCommand command);

    @BindsInstance
    Builder setSdkBundleModules(ImmutableMap<String, BundleModule> sdkBundleModules);
  }
}
