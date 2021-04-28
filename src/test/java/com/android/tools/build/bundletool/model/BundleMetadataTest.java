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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleMetadataTest {

  private static final ByteSource DUMMY_DATA = ByteSource.wrap(new byte[0]);

  @Test
  public void addFile_plainNamespacedDirectory() throws Exception {
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(/* namespacedDir= */ "com.namespace", /* fileName= */ "filename", DUMMY_DATA)
            .build();

    assertThat(metadata.getFileContentMap().keySet())
        .containsExactly(ZipPath.create("com.namespace/filename"));
  }

  @Test
  public void addFile_nestedNamespacedDirectory() throws Exception {
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(
                /* namespacedDir= */ "com.namespace/dir/sub-dir",
                /* fileName= */ "filename",
                DUMMY_DATA)
            .build();

    assertThat(metadata.getFileContentMap().keySet())
        .containsExactly(ZipPath.create("com.namespace/dir/sub-dir/filename"));
  }

  @Test
  public void addFile_pathTooShort_throws() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> BundleMetadata.builder().addFile(ZipPath.create("com.namespace"), DUMMY_DATA));

    assertThat(exception).hasMessageThat().contains("too shallow");
  }

  @Test
  public void addFile_pathNotNamespaced_throws() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> BundleMetadata.builder().addFile(ZipPath.create("no_dot/filename"), DUMMY_DATA));

    assertThat(exception)
        .hasMessageThat()
        .contains("Top-level directories for metadata files must be namespaced");
  }

  @Test
  public void getModuleEntryForTransparencyFile_empty() {
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(/* namespacedDir= */ "com.namespace", /* fileName= */ "filename", DUMMY_DATA)
            .build();

    assertThat(metadata.getModuleEntryForTransparencyFile()).isEmpty();
  }

  @Test
  public void getModuleEntryForTransparencyFile() throws Exception {
    ByteSource transparencyContents =
        CharSource.wrap(
                JsonFormat.printer()
                    .print(
                        CodeTransparency.newBuilder()
                            .addCodeRelatedFile(
                                CodeRelatedFile.newBuilder()
                                    .setPath("dex/classes.dex")
                                    .setSha256("sha256"))))
            .asByteSource(Charset.defaultCharset());
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_FILE_NAME,
                transparencyContents)
            .build();

    assertThat(metadata.getModuleEntryForTransparencyFile())
        .hasValue(
            ModuleEntry.builder()
                .setContent(transparencyContents)
                .setBundlePath(
                    ZipPath.create("BUNDLE-METADATA")
                        .resolve(BundleMetadata.BUNDLETOOL_NAMESPACE)
                        .resolve(BundleMetadata.TRANSPARENCY_FILE_NAME))
                .setPath(ZipPath.create("META-INF").resolve(BundleMetadata.TRANSPARENCY_FILE_NAME))
                .build());
  }
}
