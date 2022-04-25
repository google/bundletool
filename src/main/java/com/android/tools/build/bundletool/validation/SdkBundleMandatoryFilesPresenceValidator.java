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

import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.BundleParser;
import java.util.zip.ZipFile;

/**
 * Validates the that the SDK bundle archive contains a {@value BundleParser#SDK_MODULES_FILE_NAME}
 * archive file.
 */
public class SdkBundleMandatoryFilesPresenceValidator extends SubValidator {

  @Override
  public void validateBundleZipFile(ZipFile bundleFile) {
    if (bundleFile.getEntry(SDK_MODULES_FILE_NAME) == null) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The archive doesn't seem to be an SDK Bundle, it is missing required file '%s'.",
              SDK_MODULES_FILE_NAME)
          .build();
    }
  }
}
