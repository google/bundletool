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
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.nio.charset.Charset;

/** Shared utilities for verifying code transparency. */
public final class CodeTransparencyChecker {

  /**
   * Verifies code transparency for the given bundle, and returns {@link TransparencyCheckResult}.
   *
   * @throws InvalidBundleException if an error occurs during verification.
   */
  public static TransparencyCheckResult checkTransparency(
      AppBundle bundle, ByteSource transparencyFile) {
    if (bundle.hasSharedUserId()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Transparency file is present in the bundle, but it can not be verified because"
                  + " `sharedUserId` attribute is specified in one of the manifests.")
          .build();
    }
    ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromTransparencyMetadata;
    ImmutableMap<String, CodeRelatedFile> codeRelatedFilesFromBundle;
    try {
      codeRelatedFilesFromTransparencyMetadata =
          getCodeRelatedFilesFromTransparencyMetadata(transparencyFile);
      codeRelatedFilesFromBundle = getCodeRelatedFilesFromBundle(bundle);
    } catch (IOException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Unable to verify code transparency for bundle.")
          .withCause(e)
          .build();
    }
    return TransparencyCheckResult.create(
        codeRelatedFilesFromTransparencyMetadata, codeRelatedFilesFromBundle);
  }

  private static ImmutableMap<String, CodeRelatedFile> getCodeRelatedFilesFromTransparencyMetadata(
      ByteSource transparencyFile) throws IOException {
    CodeTransparency.Builder transparencyMetadata = CodeTransparency.newBuilder();
    JsonFormat.parser()
        .merge(
            transparencyFile.asCharSource(Charset.defaultCharset()).read(), transparencyMetadata);
    return transparencyMetadata.getCodeRelatedFileList().stream()
        .collect(toImmutableMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
  }

  private static ImmutableMap<String, CodeRelatedFile> getCodeRelatedFilesFromBundle(
      AppBundle bundle) {
    return CodeTransparencyFactory.createCodeTransparencyMetadata(bundle)
        .getCodeRelatedFileList()
        .stream()
        .collect(toImmutableMap(CodeRelatedFile::getPath, codeRelatedFile -> codeRelatedFile));
  }

  private CodeTransparencyChecker() {}
}
