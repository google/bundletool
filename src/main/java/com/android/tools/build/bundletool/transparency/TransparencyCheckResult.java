/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.build.bundletool.transparency;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

/** Represents result of code transparency verification. */
@AutoValue
public abstract class TransparencyCheckResult {

  /** {@link CodeRelatedFile}s extracted from the transparency metadata, keyed by path. */
  abstract ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromTransparencyMetadata();

  /** {@link CodeRelatedFile}s extracted from the bundle, keyed by path. */
  abstract ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromBundle();

  /**
   * Difference between {@link #codeRelatedFilesFromTransparencyMetadata()} and {@link
   * #codeRelatedFilesFromBundle()}.
   */
  abstract MapDifference<String, CodeRelatedFile> codeTransparencyDiff();

  /**
   * Returns {@code true} if code transparency was successfully verified, and {@code false}
   * otherwise.
   */
  public boolean verified() {
    return codeTransparencyDiff().areEqual();
  }

  /**
   * Returns string representation of difference between code transparency file contents and code
   * related files present in the bundle.
   */
  public String getDiffAsString() {
    return "Files deleted after transparency metadata generation: "
        + codeTransparencyDiff().entriesOnlyOnLeft().keySet()
        + "\nFiles added after transparency metadata generation: "
        + codeTransparencyDiff().entriesOnlyOnRight().keySet()
        + "\nFiles modified after transparency metadata generation: "
        + codeTransparencyDiff().entriesDiffering().keySet();
  }

  public static TransparencyCheckResult create(
      ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromTransparencyMetadata,
      ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromBundle) {
    return new AutoValue_TransparencyCheckResult(
        codeRelatedFilesFromTransparencyMetadata,
        codeRelatedFilesFromBundle,
        Maps.difference(codeRelatedFilesFromTransparencyMetadata, codeRelatedFilesFromBundle));
  }
}
