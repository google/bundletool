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
import java.util.Optional;

/** Represents result of code transparency verification. */
@AutoValue
public abstract class TransparencyCheckResult {

  /**
   * Thumbprint of the public key certificate used to successfully verify transparency. Only set if
   * code transparency signature was successfully verified.
   */
  public abstract Optional<String> certificateThumbprint();

  /**
   * {@link CodeRelatedFile}s extracted from the transparency metadata, keyed by path. Only set if
   * transparency signature was successfully verified.
   */
  abstract ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromTransparencyMetadata();

  /**
   * {@link CodeRelatedFile}s extracted from the bundle or APKs, keyed by path. Only set if
   * transparency signature was successfully verified.
   */
  abstract ImmutableMap<String, CodeRelatedFile> actualCodeRelatedFiles();

  /**
   * Difference between {@link #codeRelatedFilesFromTransparencyMetadata()} and {@link
   * #actualCodeRelatedFiles()}.
   */
  abstract MapDifference<String, CodeRelatedFile> codeTransparencyDiff();

  /** Returns {@code true} if code transparency signature was successfully verified. */
  public boolean signatureVerified() {
    return certificateThumbprint().isPresent();
  }

  /**
   * Returns {@code true} if the code transparency file contents match actual code related files
   * present in the bundle or APKs.
   */
  public boolean fileContentsVerified() {
    return signatureVerified() && codeTransparencyDiff().areEqual();
  }

  /**
   * Returns string representation of difference between code transparency file contents and code
   * related files present in the bundle or the APKs.
   */
  public String getDiffAsString() {
    return "Files deleted after transparency metadata generation: "
        + codeTransparencyDiff().entriesOnlyOnLeft().keySet()
        + "\nFiles added after transparency metadata generation: "
        + codeTransparencyDiff().entriesOnlyOnRight().keySet()
        + "\nFiles modified after transparency metadata generation: "
        + codeTransparencyDiff().entriesDiffering().keySet();
  }

  static TransparencyCheckResult createForValidSignature(
      String certificateThumbprint,
      ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromTransparencyMetadata,
      ImmutableMap<String, CodeRelatedFile> actualCodeRelatedFiles) {
    return new AutoValue_TransparencyCheckResult(
        Optional.of(certificateThumbprint),
        codeRelatedFilesFromTransparencyMetadata,
        actualCodeRelatedFiles,
        Maps.difference(codeRelatedFilesFromTransparencyMetadata, actualCodeRelatedFiles));
  }

  static TransparencyCheckResult createForInvalidSignature() {
    return new AutoValue_TransparencyCheckResult(
        /* certificateThumbprint= */ Optional.empty(),
        /* codeRelatedFilesFromTransparencyMetadata= */ ImmutableMap.of(),
        /* actualCodeRelatedFiles= */ ImmutableMap.of(),
        /* codeTransparencyDiff= */ Maps.difference(ImmutableMap.of(), ImmutableMap.of()));
  }
}
