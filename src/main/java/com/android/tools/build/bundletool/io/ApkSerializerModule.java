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
package com.android.tools.build.bundletool.io;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import javax.inject.Qualifier;

/** Dagger module responsible for choosing the {@link ApkSerializer}. */
@Module
public abstract class ApkSerializerModule {

  @Binds
  abstract ApkSerializer provideApkSerializer(ModuleSplitSerializer moduleSplitSerializerProvider);

  @Provides
  @NativeLibrariesAlignmentInBytes
  static int provideNativeLibrariesAlignmentInBytes(ApkOptimizations apkOptimizations) {
    switch (apkOptimizations.getPageAlignment()) {
      case PAGE_ALIGNMENT_16K:
        return 16384;
      case PAGE_ALIGNMENT_64K:
        return 65536;
      case PAGE_ALIGNMENT_UNSPECIFIED:
      case PAGE_ALIGNMENT_4K:
      case UNRECOGNIZED:
        return 4096;
    }
    throw new IllegalArgumentException("Wrong native libraries alignment.");
  }

  /**
   * Qualifying annotation of a {@code int} for alignment that should be used for native libraries.
   */
  @Qualifier
  @Retention(RUNTIME)
  @interface NativeLibrariesAlignmentInBytes {}

  private ApkSerializerModule() {}
}
