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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForModules;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkBundleHasOneModuleValidatorTest {

  private static final byte[] TEST_CONTENT = new byte[1];

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tempFolder;

  @Before
  public void setUp() {
    tempFolder = tmp.getRoot().toPath();
  }

  @Test
  public void sdkModulesZipFile_multipleModules_throws() throws Exception {
    Path modulesPath =
        createZipBuilderForModules()
            .addFileWithContent(
                ZipPath.create("feature/manifest/AndroidManifest.xml"), TEST_CONTENT)
            .writeTo(tempFolder.resolve(EXTRACTED_SDK_MODULES_FILE_NAME));

    try (ZipFile modulesZip = new ZipFile(modulesPath.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () -> new SdkBundleHasOneModuleValidator().validateSdkModulesZipFile(modulesZip));

      assertThat(exception).hasMessageThat().contains("SDK bundles need exactly one module");
    }
  }
}
