/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.build.bundletool.commands;

import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.BundleModule;
import dagger.BindsInstance;
import dagger.Component;

/** Dagger component to create {@link BuildSdkApksForAppManager}. */
@Component(modules = {BuildSdkApksForAppModule.class})
public interface BuildSdkApksForAppManagerComponent {
  BuildSdkApksForAppManager create();

  /** Builder for {@link BuildSdkApksForAppManagerComponent}. */
  @Component.Builder
  interface Builder {
    BuildSdkApksForAppManagerComponent build();

    @BindsInstance
    Builder setBuildSdkApksForAppCommand(BuildSdkApksForAppCommand command);

    @BindsInstance
    Builder setModule(BundleModule module);

    @BindsInstance
    Builder setTempDirectory(TempDirectory tempDirectory);
  }
}
