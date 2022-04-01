/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.DXT1;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeLibraries;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedNativeDirectory;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssetModuleFilesValidatorTest {

  private static final String MODULE_NAME = "assetmodule";
  private static final String PKG_NAME = "com.test.app";

  @Test
  public void validModule_succeeds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifest(PKG_NAME))
            .addFile("assets/img.jpg")
            .build();
    new AssetModuleFilesValidator().validateModule(module);
  }

  @Test
  public void moduleWithResourceTable_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .setResourceTable(ResourceTable.getDefaultInstance())
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetModuleFilesValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches("Unexpected resource table found in asset pack 'assetmodule'.");
  }

  @Test
  public void moduleWithInvalidEntry_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .addFile("dex/classes.dex")
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetModuleFilesValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches("Invalid entry found in asset pack 'assetmodule': 'dex/classes.dex'.");
  }

  @Test
  public void moduleWithInvalidEntries_throws() throws Exception {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .addFile("res/string.xml")
            .addFile("lib/x86/awesomelib.so")
            .addFile("assets/kitten.jpg")
            .addFile("root/groot.jpg")
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetModuleFilesValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches(
            "Invalid entries found in asset pack 'assetmodule': 'res/string.xml',"
                + " 'lib/x86/awesomelib.so', 'root/groot.jpg'.");
  }

  @Test
  public void moduleWithNativeConfig_throws() throws Exception {
    NativeLibraries config =
        nativeLibraries(targetedNativeDirectory("lib/x86", nativeDirectoryTargeting(DXT1)));
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .setNativeConfig(config)
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetModuleFilesValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches("Native libraries config not allowed in asset packs, but found in 'assetmodule'.");
  }

  @Test
  public void moduleWithApexConfig_throws() throws Exception {
    ApexImages apexConfig =
        ApexImages.newBuilder()
            .addImage(TargetedApexImage.newBuilder().setPath("apex/x86_64.img"))
            .build();
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .setApexConfig(apexConfig)
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetModuleFilesValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches("Apex config not allowed in asset packs, but found in 'assetmodule'.");
  }

  @Test
  public void moduleWithRuntimeEnabledSdkConfig_throws() {
    BundleModule module =
        new BundleModuleBuilder(MODULE_NAME)
            .setManifest(androidManifestForAssetModule(PKG_NAME))
            .setRuntimeEnabledSdkConfig(RuntimeEnabledSdkConfig.getDefaultInstance())
            .build();
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetModuleFilesValidator().validateModule(module));
    assertThat(exception)
        .hasMessageThat()
        .matches(
            "Runtime-enabled SDK config not allowed in asset packs, but found in 'assetmodule'.");
  }
}
