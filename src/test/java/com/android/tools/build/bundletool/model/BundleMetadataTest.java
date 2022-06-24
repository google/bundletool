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

import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleMetadataTest {

  private static final ByteSource TEST_DATA = ByteSource.wrap(new byte[0]);

  @Test
  public void addFile_plainNamespacedDirectory() throws Exception {
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(/* namespacedDir= */ "com.namespace", /* fileName= */ "filename", TEST_DATA)
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
                TEST_DATA)
            .build();

    assertThat(metadata.getFileContentMap().keySet())
        .containsExactly(ZipPath.create("com.namespace/dir/sub-dir/filename"));
  }

  @Test
  public void addFile_pathTooShort_throws() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> BundleMetadata.builder().addFile(ZipPath.create("com.namespace"), TEST_DATA));

    assertThat(exception).hasMessageThat().contains("too shallow");
  }

  @Test
  public void addFile_pathNotNamespaced_throws() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> BundleMetadata.builder().addFile(ZipPath.create("no_dot/filename"), TEST_DATA));

    assertThat(exception)
        .hasMessageThat()
        .contains("Top-level directories for metadata files must be namespaced");
  }

  @Test
  public void getModuleEntryForSignedTransparencyFile_empty() {
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(/* namespacedDir= */ "com.namespace", /* fileName= */ "filename", TEST_DATA)
            .build();

    assertThat(metadata.getModuleEntryForSignedTransparencyFile()).isEmpty();
  }

  @Test
  public void getModuleEntryForSignedTransparencyFile() {
    ByteSource transparencyContents =
        CharSource.wrap("transparency.signed.token").asByteSource(Charset.defaultCharset());
    BundleMetadata metadata =
        BundleMetadata.builder()
            .addFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME,
                transparencyContents)
            .build();

    assertThat(metadata.getModuleEntryForSignedTransparencyFile())
        .hasValue(
            ModuleEntry.builder()
                .setContent(transparencyContents)
                // TODO(b/186621568): Fix. Bundle location is ignored in ModuleEntry.equals.
                .setFileLocation(
                    ModuleEntryLocationInZipSource.create(
                        Paths.get(""),
                        ZipPath.create("BUNDLE-METADATA")
                            .resolve(BundleMetadata.BUNDLETOOL_NAMESPACE)
                            .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME)))
                .setPath(
                    ZipPath.create("META-INF")
                        .resolve(BundleMetadata.TRANSPARENCY_SIGNED_FILE_NAME))
                .build());
  }
}
