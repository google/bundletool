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

import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_ASSET_VALUE;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTypeAttribute;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Files.Assets;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AssetsTargetingValidatorTest {

  @Test
  public void validateModule_pointDirectlyToAssets_succeeds() throws Exception {
    Assets config =
        assets(targetedAssetsDirectory("assets", AssetsDirectoryTargeting.getDefaultInstance()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/file.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    new AssetsTargetingValidator().validateModule(module);
  }

  @Test
  public void validateModule_valid_succeeds() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir#opengl_2.0",
                assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(2)))),
            targetedAssetsDirectory(
                "assets/dir_other#tcf_etc1",
                assetsDirectoryTargeting(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8))));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir#opengl_2.0/raw.dat")
            .addFile("assets/dir_other#tcf_etc1/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    new AssetsTargetingValidator().validateModule(module);
  }

  @Test
  public void validateModule_pathOutsideAssets_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory("lib/x86", assetsDirectoryTargeting(abiTargeting(X86))),
            targetedAssetsDirectory(
                "assets/dir#tcf_etc1",
                assetsDirectoryTargeting(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8))));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setAssetsConfig(config)
            .addFile("assets/dir#tcf_etc1/file.txt")
            .setManifest(androidManifest("com.test.app"))
            .build();

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("directory must start with 'assets/'");
  }

  @Test
  public void validateModule_emptyTargetedDirectory_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir#opengl_3.0",
                assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(3)))));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setAssetsConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("Targeted directory 'assets/dir#opengl_3.0' is empty.");
  }

  @Test
  public void validateModule_validAssetModuleSucceeds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .addFile("assets/dir_other/raw.dat")
            .setAssetsConfig(Assets.getDefaultInstance())
            .setManifest(
                androidManifest("com.test.app", withTypeAttribute(MODULE_TYPE_ASSET_VALUE)))
            .build();

    new AssetsTargetingValidator().validateModule(module);
  }

  @Test
  public void validateModule_assetModuleLanguageTargeting_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir#lang_en", assetsDirectoryTargeting(languageTargeting("en"))));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir#lang_en/raw.dat")
            .setAssetsConfig(config)
            .setManifest(
                androidManifest("com.test.app", withTypeAttribute(MODULE_TYPE_ASSET_VALUE)))
            .build();

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Language targeting for asset packs is not supported, but found in module testModule.");
  }
}
