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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.stream.Stream;

/** Shared static utilities for adding and verifying {@link CodeTransparency}. */
public final class CodeTransparencyFactory {

  /** Returns {@link CodeTransparency} for the given {@link AppBundle}. */
  public static CodeTransparency createCodeTransparencyMetadata(AppBundle bundle) {
    ImmutableList<CodeRelatedFile> codeRelatedFiles =
        bundle.getFeatureModules().values().stream()
            .flatMap(bundleModule -> getCodeRelatedFileEntries(bundleModule))
            .map(CodeTransparencyFactory::createCodeRelatedFile)
            .sorted(Comparator.comparing(CodeRelatedFile::getPath))
            .collect(toImmutableList());
    return CodeTransparency.newBuilder()
        .setVersion(CodeTransparencyVersion.getCurrentVersion())
        .addAllCodeRelatedFile(codeRelatedFiles)
        .build();
  }

  /** Returns {@link CodeTransparency} parsed from transparency file JSON payload. */
  public static CodeTransparency parseFrom(String codeTransparency) {
    CodeTransparency.Builder codeTransparencyProto = CodeTransparency.newBuilder();
    try {
      JsonFormat.parser().merge(codeTransparency, codeTransparencyProto);
    } catch (IOException e) {
      throw InvalidBundleException.builder()
          .withUserMessage("Unable to parse code transparency file contents.")
          .withCause(e)
          .build();
    }
    return codeTransparencyProto.build();
  }

  private static Stream<ModuleEntry> getCodeRelatedFileEntries(BundleModule module) {
    return module.getEntries().stream().filter(CodeTransparencyFactory::isCodeRelatedFile);
  }

  private static CodeRelatedFile createCodeRelatedFile(ModuleEntry moduleEntry) {
    checkArgument(moduleEntry.getBundleLocation().isPresent());
    CodeRelatedFile.Builder codeRelatedFile =
        CodeRelatedFile.newBuilder()
            .setPath(moduleEntry.getBundleLocation().get().entryPathInBundle().toString());
    if (moduleEntry.getPath().startsWith(BundleModule.LIB_DIRECTORY)) {
      codeRelatedFile.setType(CodeRelatedFile.Type.NATIVE_LIBRARY);
      codeRelatedFile.setApkPath(moduleEntry.getPath().toString());
    } else {
      codeRelatedFile.setType(CodeRelatedFile.Type.DEX);
    }
    try {
      codeRelatedFile.setSha256(moduleEntry.getContent().hash(Hashing.sha256()).toString());
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when calculating file hash.", e);
    }
    return codeRelatedFile.build();
  }

  private static boolean isCodeRelatedFile(ModuleEntry moduleEntry) {
    return moduleEntry.getPath().startsWith(BundleModule.DEX_DIRECTORY)
        || (moduleEntry.getPath().startsWith(BundleModule.LIB_DIRECTORY)
            && moduleEntry.getPath().toString().endsWith(".so"));
  }

  private CodeTransparencyFactory() {}
}
