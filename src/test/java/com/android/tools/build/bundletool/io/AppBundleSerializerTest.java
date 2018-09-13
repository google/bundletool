/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.clearHasCode;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.createResourceTable;
import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppBundleSerializerTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path outputDirectory;

  @Before
  public void setUp() {
    outputDirectory = tmp.getRoot().toPath();
  }

  @Test
  public void allFilesPresent() throws Exception {
    XmlNode manifest = androidManifest("com.app", clearHasCode());
    Assets assetsConfig = Assets.getDefaultInstance();
    NativeLibraries nativeConfig = NativeLibraries.getDefaultInstance();
    ResourceTable resourceTable = createResourceTable();
    Path metadataFile = Files.createFile(tmp.getRoot().toPath().resolve("metadata.dat"));

    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                builder ->
                    builder
                        .setManifest(manifest)
                        .addFile("dex/classes.dex")
                        .setAssetsConfig(assetsConfig)
                        .setNativeConfig(nativeConfig)
                        .setResourceTable(resourceTable)
                        .addFile("assets/foo")
                        .addFile("lib/x86/libfoo.so"))
            .addModule(
                "feature",
                builder ->
                    builder
                        .setManifest(manifest)
                        .addFile("dex/classes.dex")
                        .addFile("dex/classes2.dex")
                        .setAssetsConfig(assetsConfig)
                        .setNativeConfig(nativeConfig)
                        .setResourceTable(resourceTable)
                        .addFile("assets/bar")
                        .addFile("lib/x86/libbar.so"))
            .addMetadataFile("com.test.namespace", "file.dat", metadataFile)
            .build();

    Path bundlePath = outputDirectory.resolve("bundle.aab");
    new AppBundleSerializer().writeToDisk(appBundle, bundlePath);

    ZipFile bundleZip = new ZipFile(bundlePath.toFile());
    List<String> fileNamesInZip =
        Collections.list(bundleZip.entries()).stream().map(ZipEntry::getName).collect(toList());
    assertThat(fileNamesInZip)
        .containsExactly(
            "BundleConfig.pb",
            "BUNDLE-METADATA/com.test.namespace/file.dat",
            "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex",
            "base/assets.pb",
            "base/native.pb",
            "base/resources.pb",
            "base/assets/foo",
            "base/lib/x86/libfoo.so",
            "feature/manifest/AndroidManifest.xml",
            "feature/dex/classes.dex",
            "feature/dex/classes2.dex",
            "feature/assets.pb",
            "feature/native.pb",
            "feature/resources.pb",
            "feature/assets/bar",
            "feature/lib/x86/libbar.so");

    assertThat(bundleZip)
        .hasFile("base/manifest/AndroidManifest.xml")
        .withContent(manifest.toByteArray());
    assertThat(bundleZip).hasFile("base/assets.pb").withContent(assetsConfig.toByteArray());
    assertThat(bundleZip).hasFile("base/native.pb").withContent(nativeConfig.toByteArray());
    assertThat(bundleZip).hasFile("base/resources.pb").withContent(resourceTable.toByteArray());
  }
}
