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
package com.android.tools.build.bundletool.testing;

import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipMap;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/** Checks the zip-alignment of files in an APK. */
public final class ZipAlignCheck {

  /** Uncompressed files must be aligned by 4 bytes in general. */
  private static final int FOUR_BYTE_ALIGNMENT = 4;
  /** Uncompressed native libraries must be aligned by 4096 bytes. */
  private static final int PAGE_ALIGNMENT = 4096;

  private static final Pattern NATIVE_LIB_PATTERN = Pattern.compile("lib/[^/]+/[^/]+\\.so");

  /**
   * Checks whether a zip file has been correctly zipaligned.
   *
   * @return {@code true} if the file has been correctly zipaligned.
   */
  public static boolean checkAlignment(File zip) throws IOException {
    ZipMap zipMap = ZipMap.from(zip.toPath());
    for (Entry entry : zipMap.getEntries().values()) {
      int expectedAligment =
          NATIVE_LIB_PATTERN.matcher(entry.getName()).matches()
              ? PAGE_ALIGNMENT
              : FOUR_BYTE_ALIGNMENT;
      if (!entry.isCompressed()) {
        if (entry.getPayloadLocation().first % expectedAligment != 0) {
          System.out.printf(
              "File '%s' is not aligned. dataOffset=%d, expectedAlignment=%d%n",
              entry.getName(), entry.getPayloadLocation().first, expectedAligment);
          return false;
        }
      }
    }
    return true;
  }

  private ZipAlignCheck() {}
}
