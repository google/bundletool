/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.model;

import java.nio.file.Path;

/**
 * This unit is left for compatibility with old versions of Android Gradle Plugin. Please do not use
 * it directly.
 */
@Deprecated
public interface Aapt2Command {

  /**
   * This method is just for compatibility with old versions of Android Gradle Plugin.
   *
   * @deprecated Use com.android.tools.build.bundletool.androidtools.Aapt2Command directly.
   */
  @Deprecated
  static com.android.tools.build.bundletool.androidtools.Aapt2Command createFromExecutablePath(
      Path aapt2Path) {
    return com.android.tools.build.bundletool.androidtools.Aapt2Command.createFromExecutablePath(
        aapt2Path);
  }
}
