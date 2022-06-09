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

import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TestUtils.createZipBuilderForSdkBundle;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.io.ZipBuilder;
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
public class SdkBundleMandatoryFilesPresenceValidatorTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tempFolder;

  @Before
  public void setUp() {
    tempFolder = tmp.getRoot().toPath();
  }

  @Test
  public void sdkModuleZipFile_noManifest_throws() throws Exception {
    Path module =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("res/drawable/icon.png"), "image".getBytes(UTF_8))
            .writeTo(tempFolder.resolve("base.zip"));

    try (ZipFile moduleZip = new ZipFile(module.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () ->
                  new SdkBundleMandatoryFilesPresenceValidator().validateModuleZipFile(moduleZip));

      assertThat(exception)
          .hasMessageThat()
          .isEqualTo("Module 'base' is missing mandatory file 'manifest/AndroidManifest.xml'.");
    }
  }

  @Test
  public void sdkModuleZipFile_withManifest_ok() throws Exception {
    XmlNode manifest = androidManifest("com.sdk");
    Path module =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("manifest/AndroidManifest.xml"), manifest)
            .addFileWithContent(ZipPath.create("assets/anything.dat"), "any".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("dex/classes.dex"), "dex".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("res/drawable/icon.png"), "image".getBytes(UTF_8))
            .writeTo(tempFolder.resolve("base.zip"));

    try (ZipFile moduleZip = new ZipFile(module.toFile())) {
      new SdkBundleMandatoryFilesPresenceValidator().validateModuleZipFile(moduleZip);
    }
  }

  @Test
  public void sdkBundleZipFile_noModulesFile_throws() throws Exception {
    Path bundlePath = createZipBuilderForSdkBundle().writeTo(tempFolder.resolve("bundle.asb"));

    try (ZipFile bundleZip = new ZipFile(bundlePath.toFile())) {
      InvalidBundleException exception =
          assertThrows(
              InvalidBundleException.class,
              () ->
                  new SdkBundleMandatoryFilesPresenceValidator().validateBundleZipFile(bundleZip));

      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              "The archive doesn't seem to be an SDK Bundle, it is missing required file '"
                  + SDK_MODULES_FILE_NAME
                  + "'.");
    }
  }
}
