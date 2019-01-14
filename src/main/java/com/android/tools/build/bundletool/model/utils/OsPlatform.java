/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.build.bundletool.model.utils;

/** OS platforms. */
public enum OsPlatform {
  WINDOWS,
  MACOS,
  LINUX,
  OTHER;

  /** Returns the OS platform this JVM is running on, relying on the "os.name" property name. */
  public static OsPlatform getCurrentPlatform() {
    String os = System.getProperty("os.name");
    if (os.startsWith("Mac OS")) {
      return MACOS;
    } else if (os.startsWith("Windows")) {
      return WINDOWS;
    } else if (os.startsWith("Linux")) {
      return LINUX;
    } else {
      return OTHER;
    }
  }
}
