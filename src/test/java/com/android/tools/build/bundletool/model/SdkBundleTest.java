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
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundleWithModules;
import static com.google.common.truth.Truth8.assertThat;

import com.android.tools.build.bundletool.io.ZipBuilder;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the SdkBundle class. */
@RunWith(JUnit4.class)
public class SdkBundleTest {

  private static final byte[] TEST_CONTENT = new byte[1];

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path bundleFile;
  private Path modulesFile;

  @Before
  public void setUp() {
    bundleFile = tmp.getRoot().toPath().resolve("bundle.asb");
    modulesFile = tmp.getRoot().toPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
  }

  @Test
  public void buildFromZipCreatesExpectedEntries() throws Exception {
    ZipBuilder modulesBuilder =
        createZipBuilderForModules()
            .addFileWithContent(ZipPath.create("base/dex/classes1.dex"), TEST_CONTENT)
            .addFileWithContent(ZipPath.create("base/dex/classes2.dex"), TEST_CONTENT);
    createZipBuilderForSdkBundleWithModules(modulesBuilder, modulesFile).writeTo(bundleFile);

    try (ZipFile sdkBundleZip = new ZipFile(bundleFile.toFile());
        ZipFile modulesZip = new ZipFile(modulesFile.toFile())) {
      SdkBundle sdkBundle = SdkBundle.buildFromZip(sdkBundleZip, modulesZip, 1);
      assertThat(sdkBundle.getModule().getEntry(ZipPath.create("dex/classes.dex"))).isPresent();
      assertThat(sdkBundle.getModule().getEntry(ZipPath.create("dex/classes2.dex"))).isPresent();
      assertThat(sdkBundle.getBundleMetadata().getFileAsByteSource("some.namespace", "metadata1"))
          .isPresent();
      assertThat(sdkBundle.getSdkInterfaceDescriptors()).isPresent();
    }
  }
}
