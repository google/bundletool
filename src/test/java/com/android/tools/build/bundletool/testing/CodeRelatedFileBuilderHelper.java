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

package com.android.tools.build.bundletool.testing;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.tools.build.bundletool.archive.ArchivedResourcesHelper;
import com.android.tools.build.bundletool.io.ResourceReader;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import java.io.IOException;

/** Helps to eliminate boilerplate code in tests */
public final class CodeRelatedFileBuilderHelper {
  private static final ResourceReader resourceReader = new ResourceReader();
  private static final ArchivedResourcesHelper archivedResourcesHelper =
      new ArchivedResourcesHelper(resourceReader);

  private CodeRelatedFileBuilderHelper() {}

  /** Returns a {@link CodeRelatedFile} for archived DEX file for specific BundleTool version */
  public static CodeRelatedFile archivedDexCodeRelatedFile(Version bundleToolVersion)
      throws IOException {
    String bundleToolRepoPath =
        archivedResourcesHelper.findArchivedClassesDexPath(
            bundleToolVersion, /* transparencyEnabled= */ true);
    ByteSource archivedClassesDex = resourceReader.getResourceByteSource(bundleToolRepoPath);
    String fileContentHash = archivedClassesDex.hash(Hashing.sha256()).toString();
    return CodeRelatedFile.newBuilder()
        .setBundletoolRepoPath(bundleToolRepoPath)
        .setType(CodeRelatedFile.Type.DEX)
        .setApkPath("")
        .setSha256(fileContentHash)
        .build();
  }
}
