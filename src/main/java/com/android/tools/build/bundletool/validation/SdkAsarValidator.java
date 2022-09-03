/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.build.bundletool.validation;

import com.android.tools.build.bundletool.model.SdkAsar;
import java.util.zip.ZipFile;

/** Validates the files and configuration for the SDK archive (ASAR). */
public final class SdkAsarValidator {

  /** Validates the given Sdk Archive zip file. */
  public static void validateFile(ZipFile asarFile) {}

  /** Validates the .resm archive inside the ASAR. */
  public static void validateModulesFile(ZipFile modulesFile) {
    SdkModulesFileValidator.create().validate(modulesFile);
  }

  /** Validates the given Sdk Archive. */
  public static void validate(SdkAsar asar) {}

  private SdkAsarValidator() {}
}
