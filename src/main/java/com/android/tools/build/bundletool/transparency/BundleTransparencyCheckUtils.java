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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import java.util.Optional;
import org.jose4j.jws.JsonWebSignature;

/** Shared utilities for verifying code transparency in a given bundle. */
public final class BundleTransparencyCheckUtils {

  /**
   * Verifies code transparency for the given bundle, and returns {@link TransparencyCheckResult}.
   *
   * @throws InvalidBundleException if an error occurs during verification, or if the bundle does
   *     not contain code transparency file.
   */
  public static TransparencyCheckResult checkTransparency(AppBundle bundle) {
    Optional<ByteSource> signedTransparencyFile =
        bundle
            .getBundleMetadata()
            .getFileAsByteSource(
                BundleMetadata.BUNDLETOOL_NAMESPACE, BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME);
    if (!signedTransparencyFile.isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Bundle does not include code transparency metadata. Run `add-transparency`"
                  + " command to add code transparency metadata to the bundle.")
          .build();
    }
    return checkTransparency(bundle, signedTransparencyFile.get());
  }

  /**
   * Verifies code transparency for the given bundle, and returns {@link TransparencyCheckResult}.
   *
   * @throws InvalidBundleException if an error occurs during verification.
   */
  public static TransparencyCheckResult checkTransparency(
      AppBundle bundle, ByteSource signedTransparencyFile) {
    if (bundle.hasSharedUserId()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Transparency file is present in the bundle, but it can not be verified because"
                  + " `sharedUserId` attribute is specified in one of the manifests.")
          .build();
    }

    TransparencyCheckResult.Builder result = TransparencyCheckResult.builder();

    JsonWebSignature jws = CodeTransparencyCryptoUtils.parseJws(signedTransparencyFile);
    if (!CodeTransparencyCryptoUtils.verifySignature(jws)) {
      return result
          .errorMessage("Verification failed because code transparency signature is invalid.")
          .build();
    }
    result
        .transparencySignatureVerified(true)
        .transparencyKeyCertificateFingerprint(
            CodeTransparencyCryptoUtils.getCertificateFingerprint(jws));

    CodeTransparency parsedTransparencyFile =
        CodeTransparencyFactory.parseFrom(jws.getUnverifiedPayload());
    CodeTransparencyVersion.checkVersion(parsedTransparencyFile);

    MapDifference<String, CodeRelatedFile> difference =
        Maps.difference(
            getCodeRelatedFilesFromParsedTransparencyFile(parsedTransparencyFile),
            getCodeRelatedFilesFromBundle(bundle));
    result.fileContentsVerified(difference.areEqual());
    if (!difference.areEqual()) {
      result.errorMessage(getDiffAsString(difference));
    }
    return result.build();
  }

  private static ImmutableMap<String, CodeRelatedFile>
      getCodeRelatedFilesFromParsedTransparencyFile(CodeTransparency parsedTransparencyFile) {
    return parsedTransparencyFile.getCodeRelatedFileList().stream()
        .map(BundleTransparencyCheckUtils::addTypeToDexCodeRelatedFiles)
        .collect(toImmutableMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
  }

  // Code transparency files generated using Bundletool with version older than
  // 1.8.1 do not have type field set for dex files.
  private static CodeRelatedFile addTypeToDexCodeRelatedFiles(CodeRelatedFile codeRelatedFile) {
    if (codeRelatedFile.getType().equals(CodeRelatedFile.Type.TYPE_UNSPECIFIED)
        && codeRelatedFile.getPath().endsWith(".dex")) {
      return codeRelatedFile.toBuilder().setType(CodeRelatedFile.Type.DEX).build();
    }
    return codeRelatedFile;
  }

  private static ImmutableMap<String, CodeRelatedFile> getCodeRelatedFilesFromBundle(
      AppBundle bundle) {
    return CodeTransparencyFactory.createCodeTransparencyMetadata(bundle)
        .getCodeRelatedFileList()
        .stream()
        .collect(toImmutableMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
  }

  private static String getDiffAsString(
      MapDifference<String, CodeRelatedFile> codeTransparencyDiff) {
    if (codeTransparencyDiff.areEqual()) {
      return "";
    }
    return "Verification failed because code was modified after transparency metadata generation. "
        + "\nFiles deleted after transparency metadata generation: "
        + codeTransparencyDiff.entriesOnlyOnLeft().keySet()
        + "\nFiles added after transparency metadata generation: "
        + codeTransparencyDiff.entriesOnlyOnRight().keySet()
        + "\nFiles modified after transparency metadata generation: "
        + codeTransparencyDiff.entriesDiffering().keySet();
  }

  private BundleTransparencyCheckUtils() {}
}
