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

package com.android.tools.build.bundletool.archive;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;

/** Utility checks for archiving APKs */
public final class ArchivedBundleUtils {

  public static final String ARCHIVE_OPT_OUT_XML_PATH =
      "res/xml/com_android_vending_archive_opt_out.xml";

  private ArchivedBundleUtils() {}

  /**
   * Verifies if archiving is enabled for the given {@link AppBundle}, and returns {@link boolean}.
   */
  public static boolean isStoreArchiveEnabled(AppBundle bundle) {
    boolean optedOutArchive =
        bundle
            .getBaseModule()
            .findEntriesUnderPath(BundleModule.RESOURCES_DIRECTORY)
            .anyMatch(entry -> entry.getPath().equals(ZipPath.create(ARCHIVE_OPT_OUT_XML_PATH)));
    if (optedOutArchive) {
      return false;
    }
    return bundle.getStoreArchive().orElse(true);
  }
}
