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
package com.android.tools.build.bundletool.splitters;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.io.ByteSource;
import java.util.Optional;

/** Copies the binary art profiles from AAB's metadata into main APK of base module. */
public final class BinaryArtProfilesInjector {

  static final String APK_LOCATION = "assets/dexopt";
  static final String METADATA_NAMESPACE = "com.android.tools.build.profiles";
  static final String LEGACY_METADATA_NAMESPACE = "assets.dexopt";

  static final String BINARY_ART_PROFILE_NAME = "baseline.prof";
  static final String BINARY_ART_PROFILE_METADATA_NAME = "baseline.profm";

  private final Optional<ByteSource> binaryArtProfile;
  private final Optional<ByteSource> binaryArtProfileMetadata;

  public BinaryArtProfilesInjector(AppBundle appBundle) {
    binaryArtProfile = extract(appBundle.getBundleMetadata(), BINARY_ART_PROFILE_NAME);
    binaryArtProfileMetadata =
        extract(appBundle.getBundleMetadata(), BINARY_ART_PROFILE_METADATA_NAME);
  }

  public ModuleSplit inject(ModuleSplit split) {
    if (!binaryArtProfileMetadata.isPresent() && !binaryArtProfile.isPresent()) {
      return split;
    }
    if (!shouldInjectBinaryArtProfile(split)) {
      return split;
    }

    ModuleSplit.Builder builder = split.toBuilder();
    binaryArtProfile.ifPresent(
        content ->
            builder.addEntry(
                ModuleEntry.builder()
                    .setForceUncompressed(true)
                    .setContent(content)
                    .setPath(ZipPath.create(APK_LOCATION).resolve(BINARY_ART_PROFILE_NAME))
                    .build()));
    binaryArtProfileMetadata.ifPresent(
        content ->
            builder.addEntry(
                ModuleEntry.builder()
                    .setForceUncompressed(true)
                    .setContent(content)
                    .setPath(ZipPath.create(APK_LOCATION).resolve(BINARY_ART_PROFILE_METADATA_NAME))
                    .build()));
    return builder.build();
  }

  private static boolean shouldInjectBinaryArtProfile(ModuleSplit split) {
    return split.getSplitType().equals(SplitType.STANDALONE)
        || (split.isMasterSplit() && split.isBaseModuleSplit());
  }

  private static Optional<ByteSource> extract(BundleMetadata metadata, String entryName) {
    Optional<ByteSource> entry = metadata.getFileAsByteSource(METADATA_NAMESPACE, entryName);
    return entry.isPresent()
        ? entry
        : metadata.getFileAsByteSource(LEGACY_METADATA_NAMESPACE, entryName);
  }
}
