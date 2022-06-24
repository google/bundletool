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

package com.android.tools.build.bundletool.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.model.AppBundle;
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
public class MandatoryFilesPresenceValidatorTest {

  private static final byte[] TEST_CONTENT = new byte[1];

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tempFolder;

  @Before
  public void setUp() {
    tempFolder = tmp.getRoot().toPath();
  }

  @Test
  public void moduleZipFile_withoutAndroidManifest_throws() throws Exception {
    Path modulePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("assets/file.txt"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("base.zip"));

    try (ZipFile moduleZip = new ZipFile(modulePath.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () ->
                  new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES)
                      .validateModuleZipFile(moduleZip));

      assertThat(exception)
          .hasMessageThat()
          .contains("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'");
    }
  }

  @Test
  public void moduleZipFile_withAllMandatoryFiles_ok() throws Exception {
    Path modulePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("manifest/AndroidManifest.xml"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("base.zip"));

    try (ZipFile moduleZip = new ZipFile(modulePath.toFile())) {
      new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES)
          .validateModuleZipFile(moduleZip);
    }
  }

  @Test
  public void bundleZipFile_withoutBundleConfig_throws() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("base/manifest/AndroidManifest.xml"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () ->
                  new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES)
                      .validateBundleZipFile(bundleZip));

      assertThat(exception).hasMessageThat().contains("missing required file 'BundleConfig.pb'");
    }
  }

  @Test
  public void bundleZipFile_withoutAndroidManifestInModule_throws() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("base/assets/file.txt"), TEST_CONTENT)
            .addFileWithContent(ZipPath.create("BundleConfig.pb"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () ->
                  new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES)
                      .validateBundleZipFile(bundleZip));

      assertThat(exception)
          .hasMessageThat()
          .contains("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'");
    }
  }

  @Test
  public void bundleZipFile_withAllMandatoryFiles_ok() throws Exception {
    Path bundlePath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("base/manifest/AndroidManifest.xml"), TEST_CONTENT)
            .addFileWithContent(ZipPath.create("BundleConfig.pb"), TEST_CONTENT)
            .writeTo(tempFolder.resolve("bundle.aab"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES)
          .validateBundleZipFile(bundleZip);
    }
  }
}
