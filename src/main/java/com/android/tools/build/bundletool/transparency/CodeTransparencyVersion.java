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
package com.android.tools.build.bundletool.transparency;

import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;

/** Class representing code transparency version. */
public final class CodeTransparencyVersion {

  // Code transparency files created before the version field was introduced will be treated by
  // Bundletool as having version 0.
  private static final int FIRST_VERSION = 0;
  private static final int CURRENT_VERSION = 1;

  /** Returns current code transparency version used by Bundletool. */
  public static int getCurrentVersion() {
    return CURRENT_VERSION;
  }

  /**
   * Throws a exception if {@code codeTransparency} has version that is not supported by Bundletool.
   */
  public static void checkVersion(CodeTransparency codeTransparency) {
    if (!isSupportedVersion(codeTransparency.getVersion())) {
      throw new IllegalStateException("Code transparency file has unsupported version.");
    }
  }

  private static boolean isSupportedVersion(int version) {
    return FIRST_VERSION <= version && version <= CURRENT_VERSION;
  }

  private CodeTransparencyVersion() {}
}
