/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.size;

/** Categories for parts of APK. Used for APK size breakdowns. */
public enum ApkComponent {
  DEX,
  RESOURCES,
  ASSETS,
  NATIVE_LIBS,
  OTHER;

  public static ApkComponent fromEntryName(String entryName) {
    if (entryName.startsWith("res/") || entryName.equals("resources.arsc")) {
      return ApkComponent.RESOURCES;
    }
    if (entryName.startsWith("lib/")) {
      return ApkComponent.NATIVE_LIBS;
    }
    if (entryName.endsWith(".dex")) {
      return ApkComponent.DEX;
    }
    if (entryName.startsWith("assets/")) {
      return ApkComponent.ASSETS;
    }
    return ApkComponent.OTHER;
  }
}
