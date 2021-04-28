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
package com.android.tools.build.bundletool.validation;

import static com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile.Type.DEX;
import static com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile.Type.NATIVE_LIBRARY;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.CodeTransparencyOuterClass.CodeRelatedFile;
import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.android.tools.build.bundletool.io.AppBundleSerializer;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CodeTransparencyValidatorTest {

  private static final String DEX_PATH = "dex/classes.dex";
  private static final String NATIVE_LIB_PATH = "lib/arm64-v8a/libnative.so";
  private static final byte[] DEX_FILE_CONTENT = new byte[] {1, 2, 3};
  private static final byte[] NATIVE_LIB_FILE_CONTENT = new byte[] {2, 3, 4};

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path bundlePath;

  @Before
  public void setUp() {
    bundlePath = tmp.getRoot().toPath().resolve("bundle.aab");
  }

  @Test
  public void transparencyFileNotPresent() {
    new CodeTransparencyValidator()
        .validateBundle(
            new AppBundleBuilder()
                .addModule(
                    "base", module -> module.setManifest(androidManifest("com.test.app")).build())
                .build());
  }

  @Test
  public void transparencyVerified() throws Exception {
    CodeTransparency validCodeTransparency =
        CodeTransparency.newBuilder()
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(DEX)
                    .setPath("base/" + DEX_PATH)
                    .setSha256(ByteSource.wrap(DEX_FILE_CONTENT).hash(Hashing.sha256()).toString()))
            .addCodeRelatedFile(
                CodeRelatedFile.newBuilder()
                    .setType(NATIVE_LIBRARY)
                    .setPath("base/" + NATIVE_LIB_PATH)
                    .setSha256(
                        ByteSource.wrap(NATIVE_LIB_FILE_CONTENT).hash(Hashing.sha256()).toString())
                    .setApkPath(NATIVE_LIB_PATH))
            .build();
    createBundle(bundlePath, validCodeTransparency);
    AppBundle bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));

    new CodeTransparencyValidator().validateBundle(bundle);
  }

  @Test
  public void transparencyVerificationFailed() throws Exception {
    createBundle(bundlePath, CodeTransparency.getDefaultInstance());
    AppBundle bundle = AppBundle.buildFromZip(new ZipFile(bundlePath.toFile()));

    Exception e =
        assertThrows(
            InvalidBundleException.class,
            () -> new CodeTransparencyValidator().validateBundle(bundle));
    assertThat(e).hasMessageThat().contains("Code transparency verification failed.");
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Files added after transparency metadata generation: [base/dex/classes.dex,"
                + " base/lib/arm64-v8a/libnative.so]");
  }

  private void createBundle(Path path, CodeTransparency codeTransparency) throws Exception {
    AppBundleBuilder appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module
                        .setManifest(androidManifest("com.test.app"))
                        .addFile(DEX_PATH, DEX_FILE_CONTENT)
                        .addFile(NATIVE_LIB_PATH, NATIVE_LIB_FILE_CONTENT))
            .addMetadataFile(
                BundleMetadata.BUNDLETOOL_NAMESPACE,
                BundleMetadata.TRANSPARENCY_FILE_NAME,
                CharSource.wrap(JsonFormat.printer().print(codeTransparency))
                    .asByteSource(Charset.defaultCharset()));
    new AppBundleSerializer().writeToDisk(appBundle.build(), path);
  }
}
