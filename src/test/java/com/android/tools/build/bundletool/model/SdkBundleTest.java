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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.io.ZipBuilder;
import com.android.tools.build.bundletool.testing.BundleConfigBuilder;
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

  private static final byte[] DUMMY_CONTENT = new byte[1];
  private static final String PACKAGE_NAME = "com.test.sdk.detail";
  private static final BundleConfig BUNDLE_CONFIG = BundleConfigBuilder.create().build();
  public static final XmlNode MANIFEST = androidManifest(PACKAGE_NAME);

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path bundleFile;

  @Before
  public void setUp() {
    bundleFile = tmp.getRoot().toPath().resolve("bundle.asb");
  }

  @Test
  public void buildFromZipDoesNotCrash() throws Exception {

    createBasicZipBuilderWithManifest().writeTo(bundleFile);

    try (ZipFile sdkBundleZip = new ZipFile(bundleFile.toFile())) {
      SdkBundle.buildFromZip(sdkBundleZip, 1);
    }
  }

  @Test
  public void buildFromZipCreatesExpectedEntries() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create("base/dex/classes1.dex"), DUMMY_CONTENT)
        .addFileWithContent(ZipPath.create("base/dex/classes2.dex"), DUMMY_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile sdkBundleZip = new ZipFile(bundleFile.toFile())) {
      SdkBundle sdkBundle = SdkBundle.buildFromZip(sdkBundleZip, 1);
      assertThat(sdkBundle.getModule().getEntry(ZipPath.create("dex/classes.dex"))).isPresent();
      assertThat(sdkBundle.getModule().getEntry(ZipPath.create("dex/classes2.dex"))).isPresent();
      assertThat(sdkBundle.getBundleMetadata().getFileAsByteSource("some.namespace", "metadata1"))
          .isPresent();
    }
  }

  @Test
  public void buildFromZip_aarNotLoadedAsModule() throws Exception {
    createBasicZipBuilderWithManifest()
        .addFileWithContent(ZipPath.create("aar/library.aar"), DUMMY_CONTENT)
        .writeTo(bundleFile);

    try (ZipFile sdkBundleZip = new ZipFile(bundleFile.toFile())) {
      SdkBundle sdkBundle = SdkBundle.buildFromZip(sdkBundleZip, 1);
      assertThat(sdkBundle.getModule().getName().toString()).isEqualTo("base");
    }
  }

  private ZipBuilder createBasicZipBuilderWithManifest() {
    ZipBuilder zipBuilder = new ZipBuilder();
    zipBuilder
        .addFileWithContent(ZipPath.create("BundleConfig.pb"), BUNDLE_CONFIG.toByteArray())
        .addFileWithProtoContent(ZipPath.create("base/manifest/AndroidManifest.xml"), MANIFEST)
        .addFileWithContent(ZipPath.create("base/dex/classes.dex"), DUMMY_CONTENT)
        .addFileWithContent(
            ZipPath.create("BUNDLE-METADATA/some.namespace/metadata1"), new byte[] {0x01});
    return zipBuilder;
  }
}
