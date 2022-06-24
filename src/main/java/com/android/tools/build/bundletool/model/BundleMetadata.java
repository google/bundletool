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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.Immutable;
import java.nio.file.Paths;
import java.util.Optional;

/** Holder of the App Bundle metadata. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
@SuppressWarnings("Immutable")
public abstract class BundleMetadata {

  /** Namespaced directory where files used by BundleTool are stored. */
  public static final String BUNDLETOOL_NAMESPACE = "com.android.tools.build.bundletool";

  public static final String MAIN_DEX_LIST_FILE_NAME = "mainDexList.txt";

  /** Namespaced directory where the proguard map, if any, is stored. */
  public static final String OBFUSCATION_NAMESPACE = "com.android.tools.build.obfuscation";

  public static final String PROGUARD_MAP_FILE_NAME = "proguard.map";

  public static final String TRANSPARENCY_SIGNED_FILE_NAME = "code_transparency_signed.jwt";

  /**
   * Returns the raw metadata map.
   *
   * <p>Keys are in format {@code <namespaced-dir>/<file-name>}.
   */
  public abstract ImmutableMap<ZipPath, ByteSource> getFileContentMap();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_BundleMetadata.Builder();
  }

  /** Gets contents of metadata file {@code <namespaced-dir>/<file-name>}, if it exists. */
  public Optional<ByteSource> getFileAsByteSource(String namespacedDir, String fileName) {
    return Optional.ofNullable(getFileContentMap().get(toMetadataPath(namespacedDir, fileName)));
  }

  @Memoized
  public Optional<ModuleEntry> getModuleEntryForSignedTransparencyFile() {
    return getFileAsByteSource(BUNDLETOOL_NAMESPACE, TRANSPARENCY_SIGNED_FILE_NAME)
        .map(
            transparencySignedFileContent ->
                ModuleEntry.builder()
                    .setContent(transparencySignedFileContent)
                    // TODO(b/186621568): Set bundle path.
                    .setFileLocation(
                        ModuleEntryLocationInZipSource.create(
                            Paths.get(""),
                            ZipPath.create("BUNDLE-METADATA")
                                .resolve(BUNDLETOOL_NAMESPACE)
                                .resolve(TRANSPARENCY_SIGNED_FILE_NAME)))
                    .setPath(ZipPath.create("META-INF").resolve(TRANSPARENCY_SIGNED_FILE_NAME))
                    .build());
  }

  /** Converts the arguments to a valid key for {@link #getFileContentMap()}. */
  private static ZipPath toMetadataPath(String namespacedDir, String fileName) {
    return checkMetadataPath(ZipPath.create(namespacedDir).resolve(fileName));
  }

  /** Check whether the given path is a valid metadata path. */
  private static ZipPath checkMetadataPath(ZipPath path) {
    checkArgument(path.getNameCount() >= 2, "The metadata file path '%s' is too shallow.", path);
    checkArgument(
        path.getName(0).toString().contains("."),
        "Top-level directories for metadata files must be namespaced (eg. 'com.package'), "
            + "got %s'.",
        path.getName(0));
    return path;
  }

  /** Builder for {@link BundleMetadata}. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract ImmutableMap.Builder<ZipPath, ByteSource> fileContentMapBuilder();

    /** Adds metadata file {@code <namespaced-dir>/<file-name>}. */
    public Builder addFile(String namespacedDir, String fileName, ByteSource content) {
      return addFile(toMetadataPath(namespacedDir, fileName), content);
    }

    /**
     * Adds a metadata file.
     *
     * @param path path of the file inside the bundle metadata directory
     */
    public Builder addFile(ZipPath path, ByteSource content) {
      fileContentMapBuilder().put(checkMetadataPath(path), content);
      return this;
    }

    public abstract BundleMetadata build();
  }
}
