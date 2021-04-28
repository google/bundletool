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
package com.android.tools.build.bundletool.commands;

import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.mergers.D8DexMerger;
import com.android.tools.build.bundletool.mergers.DexMerger;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

/** Dagger module command to all bundletool commands. */
@Module
public abstract class BundletoolModule {

  @CommandScoped
  @Provides
  static Aapt2Command provideAapt2Command(BuildApksCommand command, TempDirectory tempDir) {
    return command
        .getAapt2Command()
        .orElseGet(() -> CommandUtils.extractAapt2FromJar(tempDir.getPath()));
  }

  @Binds
  abstract DexMerger provideDexMerger(D8DexMerger d8DexMerger);
}
