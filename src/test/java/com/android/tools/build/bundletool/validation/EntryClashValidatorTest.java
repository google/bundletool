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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withIsolatedSplits;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EntryClashValidatorTest {

  @Test
  public void differentAssetsConfig_ok() throws Exception {
    String filePath = "assets.pb";
    byte[] fileContentA = Assets.getDefaultInstance().toByteArray();
    byte[] fileContentB =
        Assets.newBuilder()
            .addDirectory(TargetedAssetsDirectory.getDefaultInstance())
            .build()
            .toByteArray();
    assertThat(fileContentA).isNotEqualTo(fileContentB);
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile(filePath, fileContentA)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(filePath, fileContentB)
            .setManifest(androidManifest("com.test.app"))
            .build();

    EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void differentDexFiles_ok() throws Exception {
    String filePath = "dex/classed13.dex";
    byte[] fileContentA = {'a'};
    byte[] fileContentB = {'b'};
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile(filePath, fileContentA)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(filePath, fileContentB)
            .setManifest(androidManifest("com.test.app"))
            .build();

    EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void differentManifests_ok() throws Exception {
    String filePath = "manifest/AndroidManifest.xml";
    byte[] fileContentA = XmlNode.getDefaultInstance().toByteArray();
    byte[] fileContentB =
        XmlNode.newBuilder().setElement(XmlElement.getDefaultInstance()).build().toByteArray();
    assertThat(fileContentA).isNotEqualTo(fileContentB);
    BundleModule moduleA = new BundleModuleBuilder("a").addFile(filePath, fileContentA).build();
    BundleModule moduleB = new BundleModuleBuilder("b").addFile(filePath, fileContentB).build();

    EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void differentNativeConfig_ok() throws Exception {
    String filePath = "native.pb";
    byte[] fileContentA = NativeLibraries.getDefaultInstance().toByteArray();
    byte[] fileContentB =
        NativeLibraries.newBuilder()
            .addDirectory(TargetedNativeDirectory.getDefaultInstance())
            .build()
            .toByteArray();
    assertThat(fileContentA).isNotEqualTo(fileContentB);
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile(filePath, fileContentA)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(filePath, fileContentB)
            .setManifest(androidManifest("com.test.app"))
            .build();

    EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void differentResourceTables_ok() throws Exception {
    String filePath = "resources.pb";
    byte[] fileContentA = ResourceTable.getDefaultInstance().toByteArray();
    byte[] fileContentB =
        ResourceTable.newBuilder().addPackage(Package.getDefaultInstance()).build().toByteArray();
    assertThat(fileContentA).isNotEqualTo(fileContentB);
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile(filePath, fileContentA)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(filePath, fileContentB)
            .setManifest(androidManifest("com.test.app"))
            .build();

    EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void entryNameCollision_filesWithSameContent_ok() throws Exception {
    String filePath = "res/file.txt";
    byte[] sharedData = "same across modules".getBytes(UTF_8);
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile(filePath, sharedData)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(filePath, sharedData)
            .setManifest(androidManifest("com.test.app"))
            .build();

    EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void entryNameCollision_filesWithDifferentContent_throws() throws Exception {
    String fileName = "assets/file.txt";
    byte[] fileContentA = {'a'};
    byte[] fileContentB = {'b'};
    BundleModule moduleA =
        new BundleModuleBuilder("a")
            .addFile(fileName, fileContentA)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(fileName, fileContentB)
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> EntryClashValidator.checkEntryClashes(ImmutableList.of(moduleA, moduleB)));

    assertThat(exception)
        .hasMessageThat()
        .contains("Modules 'a' and 'b' contain entry 'assets/file.txt' with different content.");
  }

  @Test
  public void entryNameCollision_filesWithDifferentContent_isolatedSplits_ok() throws Exception {
    String fileName = "assets/file.txt";
    byte[] fileContentA = {'a'};
    byte[] fileContentB = {'b'};
    BundleModule moduleA =
        new BundleModuleBuilder("base")
            .addFile(fileName, fileContentA)
            .setManifest(androidManifest("com.test.app", withIsolatedSplits(true)))
            .build();
    BundleModule moduleB =
        new BundleModuleBuilder("b")
            .addFile(fileName, fileContentB)
            .setManifest(androidManifest("com.test.app"))
            .build();
    new EntryClashValidator().validateAllModules(ImmutableList.of(moduleA, moduleB));
  }

  @Test
  public void entryNameCollision_theSameFileBetweenAssetModules_ok() throws Exception {
    String fileName = "assets/file.txt";
    byte[] fileContentA = {'a'};
    BundleModule base =
        new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();
    BundleModule assetModule1 =
        new BundleModuleBuilder("assets1")
            .addFile(fileName, fileContentA)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();
    BundleModule assetModule2 =
        new BundleModuleBuilder("assets2")
            .addFile(fileName, fileContentA)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();
    new EntryClashValidator()
        .validateAllModules(ImmutableList.of(base, assetModule1, assetModule2));
  }

  @Test
  public void entryNameCollision_theSameFileBetweenBaseAndAssetModule_throws() throws Exception {
    String fileName = "assets/file.txt";
    byte[] fileContentA = {'a'};
    BundleModule baseModule =
        new BundleModuleBuilder("base")
            .addFile(fileName, fileContentA)
            .setManifest(androidManifest("com.test.app"))
            .build();
    BundleModule assetModule =
        new BundleModuleBuilder("assets1")
            .addFile(fileName, fileContentA)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new EntryClashValidator()
                    .validateAllModules(ImmutableList.of(baseModule, assetModule)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Both modules 'base' and 'assets1' contain asset entry 'assets/file.txt'.");
  }
}
