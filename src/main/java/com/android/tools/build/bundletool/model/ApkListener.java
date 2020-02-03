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

package com.android.tools.build.bundletool.model;

import com.android.bundle.Commands.ApkDescription;

/** Allows to be notified about various stages of APK creation. */
public class ApkListener {

  public static final ApkListener NO_OP = new ApkListener() {};

  /** Invoked when APK has been finalized and signed (if signing config was provided). */
  public void onApkFinalized(ApkDescription apkDesc) {
    // no-op by default
  }
}
