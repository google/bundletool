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

package com.android.tools.build.bundletool.validation;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validates structure of the bundle zip file. */
public class BundleZipValidator extends SubValidator {

  @Override
  public void validateBundleZipEntry(ZipFile bundleFile, ZipEntry zipEntry) {
    if (zipEntry.isDirectory()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The bundle zip file contains directory zip entry '%s' which is not allowed.",
              zipEntry.getName())
          .build();
    }
    if (zipEntry.getName().startsWith("/")) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The bundle zip file contains a zip entry starting with /, which is not "
                  + "allowed: '%s'.",
              zipEntry.getName())
          .build();
    }
  }
}
