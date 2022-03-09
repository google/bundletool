/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_IMAGE_SUFFIX;
import static com.android.tools.build.bundletool.model.BundleModule.BUILD_INFO_SUFFIX;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.BundleModule.ROOT_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableSet;

/** Helper methods for APK serialization. */
public final class ApkSerializerHelper {
  static final ImmutableSet<String> NO_COMPRESSION_EXTENSIONS =
      ImmutableSet.of(
          "3g2", "3gp", "3gpp", "3gpp2", "aac", "amr", "awb", "gif", "imy", "jet", "jpeg", "jpg",
          "m4a", "m4v", "mid", "midi", "mkv", "mp2", "mp3", "mp4", "mpeg", "mpg", "ogg", "png",
          "rtttl", "smf", "wav", "webm", "wma", "wmv", "xmf");

  /**
   * Transforms the entry path in the module to the final path in the module split.
   *
   * <p>The entries from root/, dex/, manifest/ directories will be moved to the top level of the
   * split. Entries from apex/ will be moved to the top level and named "apex_payload.img" for
   * images or "apex_build_info.pb" for APEX build info. There should only be one such entry.
   */
  public static ZipPath toApkEntryPath(ZipPath pathInModule) {
    return toApkEntryPath(pathInModule, /* binaryApk= */ false);
  }

  /**
   * Transforms the entry path in the module to the final path in the proto or binary APK.
   *
   * <p>The entries from root/, dex/, manifest/ directories will be moved to the top level of the
   * split. Entries from apex/ will be moved to the top level and named "apex_payload.img" for
   * images or "apex_build_info.pb" for APEX build info. There should only be one such entry.
   */
  public static ZipPath toApkEntryPath(ZipPath pathInModule, boolean binaryApk) {
    if (binaryApk && pathInModule.equals(SpecialModuleEntry.RESOURCE_TABLE.getPath())) {
      return ZipPath.create("resources.arsc");
    }
    if (pathInModule.startsWith(MANIFEST_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() == 2,
          "Only files directly in the manifest directory are supported but found: %s.",
          pathInModule);
      checkFileHasExtension("File under manifest/ directory", pathInModule, ".xml");
      return pathInModule.getFileName();
    }
    if (pathInModule.startsWith(DEX_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() == 2,
          "Only files directly in the dex directory are supported but found: %s.",
          pathInModule);
      checkFileHasExtension("File under dex/ directory", pathInModule, ".dex");
      return pathInModule.getFileName();
    }
    if (pathInModule.startsWith(ROOT_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() >= 2,
          "Only files inside the root directory are supported but found: %s",
          pathInModule);
      return pathInModule.subpath(1, pathInModule.getNameCount());
    }
    if (pathInModule.startsWith(APEX_DIRECTORY)) {
      checkArgument(
          pathInModule.getNameCount() >= 2,
          "Only files inside the apex directory are supported but found: %s",
          pathInModule);
      checkArgument(
          pathInModule.toString().endsWith(APEX_IMAGE_SUFFIX)
              || pathInModule.toString().endsWith(BUILD_INFO_SUFFIX),
          "Unexpected filename in apex directory: %s",
          pathInModule);
      if (pathInModule.toString().endsWith(APEX_IMAGE_SUFFIX)) {
        return ZipPath.create("apex_payload.img");
      } else {
        return ZipPath.create("apex_build_info.pb");
      }
    }
    return pathInModule;
  }

  /** Returns {@code true} if the files are needed for the "aapt2 convert". */
  public static boolean requiresAapt2Conversion(ZipPath path) {
    return path.startsWith("res")
        || path.equals(SpecialModuleEntry.RESOURCE_TABLE.getPath())
        || path.equals(ZipPath.create(MANIFEST_FILENAME));
  }

  private ApkSerializerHelper() {}
}
