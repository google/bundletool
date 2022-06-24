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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.testing.SdkBundleBuilder.DEFAULT_SDK_MODULES_CONFIG;
import static com.android.tools.build.bundletool.testing.TestUtils.DEFAULT_SDK_METADATA;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkAsarWithModules;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the SdkAsar class. */
@RunWith(JUnit4.class)
public class SdkAsarTest {

  private static final byte[] TEST_CONTENT = new byte[1];

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path asarFile;
  private Path modulesFile;

  @Before
  public void setUp() {
    asarFile = tmp.getRoot().toPath().resolve("archive.asar");
    modulesFile = tmp.getRoot().toPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
  }

  @Test
  public void buildFromZipCreatesExpectedEntries() throws Exception {
    ZipBuilder modulesBuilder =
        createZipBuilderForModules()
            .addFileWithContent(ZipPath.create("base/dex/classes1.dex"), TEST_CONTENT)
            .addFileWithContent(ZipPath.create("base/dex/classes2.dex"), TEST_CONTENT);
    createZipBuilderForSdkAsarWithModules(modulesBuilder, modulesFile).writeTo(asarFile);
    ZipFile sdkAsarZip = new ZipFile(asarFile.toFile());
    ZipFile modulesZip = new ZipFile(modulesFile.toFile());

    SdkAsar sdkAsar = SdkAsar.buildFromZip(sdkAsarZip, modulesZip, modulesFile);

    assertThat(sdkAsar.getModule().getEntry(ZipPath.create("dex/classes.dex"))).isPresent();
    assertThat(sdkAsar.getModule().getEntry(ZipPath.create("dex/classes2.dex"))).isPresent();
    assertThat(sdkAsar.getSdkModulesConfig()).isEqualTo(DEFAULT_SDK_MODULES_CONFIG);
    assertThat(sdkAsar.getSdkMetadata()).isEqualTo(DEFAULT_SDK_METADATA);
    assertThat(sdkAsar.getManifest()).isNotNull();
  }

  @Test
  public void buildFromZip_noSdkMetadataPresent_throws() throws Exception {
    ZipBuilder modulesBuilder = createZipBuilderForModules();
    modulesBuilder.writeTo(modulesFile);
    new ZipBuilder()
        .addFileFromDisk(ZipPath.create("modules.resm"), modulesFile.toFile())
        .writeTo(asarFile);
    ZipFile sdkAsarZip = new ZipFile(asarFile.toFile());
    ZipFile modulesZip = new ZipFile(modulesFile.toFile());

    Throwable e =
        assertThrows(
            InvalidBundleException.class,
            () -> SdkAsar.buildFromZip(sdkAsarZip, modulesZip, modulesFile));
    assertThat(e).hasMessageThat().isEqualTo("ASAR is expected to contain 'SdkMetadata.pb' file.");
  }
}
