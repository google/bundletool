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
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifestForAssetModule;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assets;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.targetedAssetsDirectory;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Files.Assets;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
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
                "assets/dir_other#tcf_etc1",
                assetsDirectoryTargeting(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8))));
    BundleModule module =
        new BundleModuleBuilder("testModule")
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

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("directory must start with 'assets/'");
  }

  @Test
  public void validateModule_emptyTargetedDirectory_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir#tcf_etc1",
                assetsDirectoryTargeting(
                    textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8))));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .setAssetsConfig(config)
            .setManifest(androidManifest("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("Targeted directory 'assets/dir#tcf_etc1' is empty.");
  }

  @Test
  public void validateModule_validAssetModuleSucceeds() throws Exception {
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .addFile("assets/dir_other/raw.dat")
            .setAssetsConfig(Assets.getDefaultInstance())
            .setManifest(androidManifestForAssetModule("com.test.app"))
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
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Language targeting for asset packs is not supported, but found in module testModule.");
  }

  @Test
  public void validateModule_defaultInstanceOfAbiTargeting_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir",
                AssetsDirectoryTargeting.newBuilder()
                    .setAbi(AbiTargeting.getDefaultInstance())
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("set but empty ABI targeting");
  }

  @Test
  public void validateModule_defaultInstanceOfLanguageTargeting_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir",
                AssetsDirectoryTargeting.newBuilder()
                    .setLanguage(LanguageTargeting.getDefaultInstance())
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("set but empty language targeting");
  }

  @Test
  public void validateModule_defaultInstanceOfTcfTargeting_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir",
                AssetsDirectoryTargeting.newBuilder()
                    .setTextureCompressionFormat(
                        TextureCompressionFormatTargeting.getDefaultInstance())
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("set but empty Texture Compression Format targeting");
  }

  @Test
  public void validateModule_defaultInstanceOfCountrySetTargeting_throws() throws Exception {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir#countries_latam",
                AssetsDirectoryTargeting.newBuilder()
                    .setCountrySet(CountrySetTargeting.getDefaultInstance())
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir#countries_latam/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e).hasMessageThat().contains("set but empty Country Set targeting");
  }

  @Test
  public void conflictingValuesAndAlternatives_abi() {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir",
                AssetsDirectoryTargeting.newBuilder()
                    .setAbi(
                        AbiTargeting.newBuilder()
                            .addValue(Abi.newBuilder().setAlias(X86))
                            .addAlternatives(Abi.newBuilder().setAlias(X86))
                            .addAlternatives(Abi.newBuilder().setAlias(AbiAlias.ARM64_V8A)))
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Expected targeting values and alternatives to be mutually exclusive, but directory"
                + " 'assets/dir' has ABI targeting that contains [X86] in both.");
  }

  @Test
  public void conflictingValuesAndAlternatives_language() {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir",
                AssetsDirectoryTargeting.newBuilder()
                    .setLanguage(
                        LanguageTargeting.newBuilder()
                            .addValue("en")
                            .addAlternatives("en")
                            .addAlternatives("es"))
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Expected targeting values and alternatives to be mutually exclusive, but directory"
                + " 'assets/dir' has language targeting that contains [en] in both.");
  }

  @Test
  public void conflictingValuesAndAlternatives_textureCompressionFormat() {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir",
                AssetsDirectoryTargeting.newBuilder()
                    .setTextureCompressionFormat(
                        TextureCompressionFormatTargeting.newBuilder()
                            .addValue(
                                TextureCompressionFormat.newBuilder()
                                    .setAlias(TextureCompressionFormatAlias.ASTC))
                            .addAlternatives(
                                TextureCompressionFormat.newBuilder()
                                    .setAlias(TextureCompressionFormatAlias.ASTC))
                            .addAlternatives(
                                TextureCompressionFormat.newBuilder()
                                    .setAlias(TextureCompressionFormatAlias.ATC)))
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Expected targeting values and alternatives to be mutually exclusive, but directory"
                + " 'assets/dir' has texture compression format targeting that contains [ASTC] in"
                + " both.");
  }

  @Test
  public void conflictingValuesAndAlternatives_countrySet() {
    Assets config =
        assets(
            targetedAssetsDirectory(
                "assets/dir#countries_latam",
                AssetsDirectoryTargeting.newBuilder()
                    .setCountrySet(
                        CountrySetTargeting.newBuilder().addValue("latam").addAlternatives("latam"))
                    .build()));
    BundleModule module =
        new BundleModuleBuilder("testModule")
            .addFile("assets/dir#countries_latam/raw.dat")
            .setAssetsConfig(config)
            .setManifest(androidManifestForAssetModule("com.test.app"))
            .build();

    InvalidBundleException e =
        assertThrows(
            InvalidBundleException.class,
            () -> new AssetsTargetingValidator().validateModule(module));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Expected targeting values and alternatives to be mutually exclusive, but directory"
                + " 'assets/dir#countries_latam' has country set targeting that contains [latam] in"
                + " both.");
  }
}
