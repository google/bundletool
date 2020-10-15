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

import com.android.tools.build.bundletool.commands.BuildApksCommand;
import dagger.Module;
import dagger.Provides;
import javax.inject.Provider;

/** Dagger module responsible for choosing the {@link ApkSerializerHelper}. */
@Module
public final class ApkSerializerModule {

  @Provides
  static ApkSerializerHelper provideApkSerializerHelper(
      BuildApksCommand command,
      Provider<ZipFlingerApkSerializerHelper> zipFlingerApkSerializerHelper,
      Provider<ApkzlibApkSerializerHelper> apkzlibApkSerializerHelper) {
    return command.getEnableNewApkSerializer()
        ? zipFlingerApkSerializerHelper.get()
        : apkzlibApkSerializerHelper.get();
  }

  private ApkSerializerModule() {}
}
