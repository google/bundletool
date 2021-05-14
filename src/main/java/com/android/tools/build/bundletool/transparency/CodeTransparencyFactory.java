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

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Shared static utilities for adding and verifying {@link CodeTransparency}. */
public final class CodeTransparencyFactory {

  /** Returns {@link CodeTransparency} for the given {@link AppBundle}. */
  public static CodeTransparency createCodeTransparencyMetadata(AppBundle bundle) {
    CodeTransparency.Builder transparencyBuilder = CodeTransparency.newBuilder();
    bundle
        .getFeatureModules()
        .values()
        .forEach(featureModule -> addModuleToTransparencyFile(transparencyBuilder, featureModule));
    return transparencyBuilder.build();
  }

  private static void addModuleToTransparencyFile(
      CodeTransparency.Builder codeTransparencyBuilder, BundleModule module) {
    module.getEntries().stream()
        .filter(CodeTransparencyFactory::isCodeRelatedFile)
        .forEach(
            moduleEntry ->
                codeTransparencyBuilder.addCodeRelatedFile(createCodeRelatedFile(moduleEntry)));
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
