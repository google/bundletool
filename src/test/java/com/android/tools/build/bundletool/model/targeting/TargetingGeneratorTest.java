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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.tools.build.bundletool.model.utils.TargetingNormalizer.normalizeAssetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeCountrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeTextureCompressionTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.countrySetTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.deviceTierTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.Sanitizer.SanitizerAlias;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetingGeneratorTest {

  private final TargetingGenerator generator = new TargetingGenerator();

  @Test
  public void generateTargetingInGeneral_wrongRootDirectory_throws() throws Exception {
    IllegalArgumentException exception;

    // For assets targeting: Not an "assets/..." directory.
    exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                generator.generateTargetingForAssets(
                    ImmutableList.of(ZipPath.create("wrong/dir"))));

    assertThat(exception)
        .hasMessageThat()
        .contains("Directory 'wrong/dir/' must start with 'assets/'");

    // For native libraries' targeting: Not a "lib/..." directory.
    exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generateTargetingForNativeLibraries(ImmutableList.of("wrong/dir")));

    assertThat(exception).hasMessageThat().contains("Directory 'wrong/dir' must start with 'lib/'");
  }

  @Test
  public void generateTargetingForNativeLibraries_createsSingleDirectoryGroup() throws Exception {
    Collection<String> manyDirectories =
        Arrays.stream(AbiName.values())
            .map(AbiName::getPlatformName)
            .map(abi -> "lib/" + abi)
            .collect(toImmutableList());
    checkState(manyDirectories.size() > 1); // Otherwise this test is useless.

    NativeLibraries nativeTargeting =
        generator.generateTargetingForNativeLibraries(manyDirectories);

    List<TargetedNativeDirectory> directories = nativeTargeting.getDirectoryList();
    assertThat(directories).hasSize(manyDirectories.size());
  }

  @Test
  public void generateTargetingForNativeLibraries_sanitizer() throws Exception {
    NativeLibraries nativeTargeting =
        generator.generateTargetingForNativeLibraries(ImmutableList.of("lib/arm64-v8a-hwasan"));

    List<TargetedNativeDirectory> directories = nativeTargeting.getDirectoryList();
    assertThat(directories).hasSize(1);
    assertThat(directories.get(0))
        .isEqualTo(
            TargetedNativeDirectory.newBuilder()
                .setPath("lib/arm64-v8a-hwasan")
                .setTargeting(
                    nativeDirectoryTargeting(AbiAlias.ARM64_V8A, SanitizerAlias.HWADDRESS))
                .build());
  }

  @Test
  public void generateTargetingForNativeLibraries_abiBaseNamesDisallowed() throws Exception {
    String directoryName = "lib/ARM64-v8a";
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () -> generator.generateTargetingForNativeLibraries(ImmutableList.of(directoryName)));

    assertThat(exception)
        .hasMessageThat()
        .containsMatch(
            "Expecting ABI name in file or directory 'lib/ARM64-v8a', but found 'ARM64-v8a' "
                + "which is not recognized. Did you mean 'arm64-v8a'?");
  }

  @Test
  public void generateTargetingForNativeLibraries_baseNameNotAnAbi_throws() throws Exception {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                generator.generateTargetingForNativeLibraries(
                    ImmutableList.of("lib/non_abi_name")));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expecting ABI name in file or directory 'lib/non_abi_name', but found 'non_abi_name' "
                + "which is not recognized.");
  }

  @Test
  public void generateTargetingForApexImages_createsAllTargeting() throws Exception {
    ImmutableSet<String> abis =
        Arrays.stream(AbiName.values()).map(AbiName::getPlatformName).collect(toImmutableSet());
    Set<String> abiPairs =
        Sets.cartesianProduct(abis, abis).stream()
            .map(pair -> Joiner.on('.').join(pair))
            .collect(toImmutableSet());
    Collection<ZipPath> allAbiFiles =
        Sets.union(abis, abiPairs).stream()
            .map(abi -> ZipPath.create(abi + ".img"))
            .collect(toImmutableList());
    checkState(allAbiFiles.size() > 1); // Otherwise this test is useless.

    ApexImages apexImages =
        generator.generateTargetingForApexImages(allAbiFiles, /* hasBuildInfo= */ true);

    List<TargetedApexImage> images = apexImages.getImageList();
    assertThat(images).hasSize(allAbiFiles.size());
  }

  @Test
  public void generateTargetingForApexImages_abiBaseNamesDisallowed() throws Exception {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                generator.generateTargetingForApexImages(
                    ImmutableList.of(ZipPath.create("x86.ARM64-v8a.img")),
                    /* hasBuildInfo= */ false));

    assertThat(exception)
        .hasMessageThat()
        .containsMatch(
            "Expecting ABI name in file or directory 'x86.ARM64-v8a.img', but found 'ARM64-v8a' "
                + "which is not recognized. Did you mean 'arm64-v8a'?");
  }

  @Test
  public void generateTargetingForApexImages_baseNameNotAnAbi_throws() throws Exception {
    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                generator.generateTargetingForApexImages(
                    ImmutableList.of(ZipPath.create("non_abi_name.img")),
                    /* hasBuildInfo= */ false));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expecting ABI name in file or directory 'non_abi_name.img', but found 'non_abi_name' "
                + "which is not recognized.");
  }

  @Test
  public void generateTargetingForAssets_emptyInput_emptyOutput() throws Exception {
    Assets assetsConfig = new TargetingGenerator().generateTargetingForAssets(ImmutableList.of());
    assertThat(assetsConfig).isEqualToDefaultInstance();
  }

  @Test
  public void generateTargetingForAssets_nonTargetedDirectories() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/static"),
                    ZipPath.create("assets/world/static"),
                    ZipPath.create("assets/world/static-alt")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/static")
                        .setTargeting(AssetsDirectoryTargeting.getDefaultInstance()))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/static")
                        .setTargeting(AssetsDirectoryTargeting.getDefaultInstance()))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/static-alt")
                        .setTargeting(AssetsDirectoryTargeting.getDefaultInstance()))
                .build());
  }

  @Test
  public void generateTargetingForAssets_different_types_leaves_ok() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_etc1"),
                    ZipPath.create("assets/world/i18n#lang_en")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1")
                        .setTargeting(
                            assetsDirectoryTargeting(
                                textureCompressionTargeting(
                                    TextureCompressionFormatAlias.ETC1_RGB8))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/i18n#lang_en")
                        .setTargeting(assetsDirectoryTargeting(languageTargeting("en"))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_nameOverloading_works() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n#lang_en"),
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n#lang_ru")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8)),
                                assetsDirectoryTargeting(languageTargeting("en")))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n#lang_ru")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8)),
                                assetsDirectoryTargeting(languageTargeting("ru")))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_basicScenario() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n#lang_en"),
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n"),
                    ZipPath.create("assets/world/texture#tcf_atc/i18n#lang_en"),
                    ZipPath.create("assets/world/texture#tcf_atc/i18n#lang_ru")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(alternativeLanguageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/i18n#lang_ru")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("ru")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_basicScenario_reverseOrder() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_atc/i18n#lang_en"),
                    ZipPath.create("assets/world/texture#tcf_atc/i18n#lang_ru"),
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n"),
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n#lang_en")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(alternativeLanguageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/i18n#lang_ru")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("ru")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_NonTargeted_midpath() {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_atc/rest/i18n#lang_en"),
                    ZipPath.create("assets/world/texture#tcf_atc/rest/i18n#lang_ru"),
                    // Note that the two below should not have any alternatives for OpenGL version.
                    ZipPath.create("assets/world/texture#tcf_etc1/rest/i18n#lang_ru"),
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n#lang_en")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/rest/i18n#lang_ru")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("ru")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/rest/i18n#lang_ru")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("ru")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/rest/i18n#lang_en")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("en")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_badLanguageTargetingThrows() {
    Throwable exception =
        assertThrows(
            InvalidBundleException.class,
            () -> {
              new TargetingGenerator()
                  .generateTargetingForAssets(
                      ImmutableList.of(
                          ZipPath.create("assets/world/i18n#lang_jp"),
                          ZipPath.create("assets/world/i18n#lang_engl")));
            });
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Expected 2- or 3-character language directory but got 'engl' for directory 'i18n'.");
  }

  @Test
  public void generateTargetingForAssets_languageTargeting() {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/i18n#lang_de"),
                    ZipPath.create("assets/world/i18n#lang_jp"),
                    ZipPath.create("assets/world/i18n"),
                    // Note that the two below should not have any alternatives for texture
                    // compression format.
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n#lang_pl"),
                    ZipPath.create("assets/world/texture#tcf_etc1/i18n")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/i18n#lang_de")
                        .setTargeting(assetsDirectoryTargeting(languageTargeting("de"))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/i18n#lang_jp")
                        .setTargeting(assetsDirectoryTargeting(languageTargeting("jp"))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/i18n")
                        .setTargeting(
                            assetsDirectoryTargeting(alternativeLanguageTargeting("de", "jp"))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n#lang_pl")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(languageTargeting("pl")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8)))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/i18n")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(alternativeLanguageTargeting("pl")),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8)))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_deviceTierTargeting() {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/img#tier_0"),
                    ZipPath.create("assets/img#tier_1"),
                    ZipPath.create("assets/img#tier_2")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#tier_0")
                        .setTargeting(
                            assetsDirectoryTargeting(
                                deviceTierTargeting(
                                    /* value= */ 0, /* alternatives= */ ImmutableList.of(1, 2)))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#tier_1")
                        .setTargeting(
                            assetsDirectoryTargeting(
                                deviceTierTargeting(
                                    /* value= */ 1, /* alternatives= */ ImmutableList.of(0, 2)))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#tier_2")
                        .setTargeting(
                            assetsDirectoryTargeting(
                                deviceTierTargeting(
                                    /* value= */ 2, /* alternatives= */ ImmutableList.of(0, 1)))))
                .build());
  }

  @Test
  public void duplicateTargetingDimensions_throws() throws Exception {
    Throwable t =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new TargetingGenerator()
                    .generateTargetingForAssets(
                        ImmutableList.of(
                            ZipPath.create(
                                "assets/world/texture#tcf_etc1/"
                                    + "texture2#tcf_atc/gfx#opengl_2.0/all"))));
    assertThat(t)
        .hasMessageThat()
        // Prefix of the path below because we don't assume the order of traversal.
        .contains(
            "Duplicate targeting dimension 'TEXTURE_COMPRESSION_FORMAT' on path "
                + "'assets/world/texture#tcf_etc1/texture2#tcf_atc'");
  }

  @Test
  public void generateTargetingForAssets_assetsAtTopLevel() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(ImmutableList.of(ZipPath.create("assets")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets")
                        .setTargeting(AssetsDirectoryTargeting.getDefaultInstance()))
                .build());
  }

  @Test
  public void generateTargetingForAssets_nestedTargeting() {
    ImmutableList<ZipPath> assetDirectories =
        ImmutableList.of(
            ZipPath.create("assets/img#countries_latam#tcf_astc"),
            ZipPath.create("assets/img#countries_latam#tcf_pvrtc"),
            ZipPath.create("assets/img#countries_latam"),
            ZipPath.create("assets/img#tcf_astc"),
            ZipPath.create("assets/img#tcf_pvrtc"),
            ZipPath.create("assets/img"));

    Assets assetsConfig = new TargetingGenerator().generateTargetingForAssets(assetDirectories);

    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#countries_latam#tcf_astc")
                        .setTargeting(
                            normalizeAssetsDirectoryTargeting(
                                AssetsDirectoryTargeting.newBuilder()
                                    .setCountrySet(countrySetTargeting("latam"))
                                    .setTextureCompressionFormat(
                                        textureCompressionTargeting(
                                            /* value= */ TextureCompressionFormatAlias.ASTC,
                                            /* alternatives= */ ImmutableSet.of(
                                                TextureCompressionFormatAlias.PVRTC)))
                                    .build())))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#countries_latam#tcf_pvrtc")
                        .setTargeting(
                            normalizeAssetsDirectoryTargeting(
                                AssetsDirectoryTargeting.newBuilder()
                                    .setCountrySet(countrySetTargeting("latam"))
                                    .setTextureCompressionFormat(
                                        textureCompressionTargeting(
                                            /* value= */ TextureCompressionFormatAlias.PVRTC,
                                            /* alternatives= */ ImmutableSet.of(
                                                TextureCompressionFormatAlias.ASTC)))
                                    .build())))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#countries_latam")
                        .setTargeting(
                            normalizeAssetsDirectoryTargeting(
                                AssetsDirectoryTargeting.newBuilder()
                                    .setCountrySet(countrySetTargeting("latam"))
                                    .setTextureCompressionFormat(
                                        alternativeTextureCompressionTargeting(
                                            TextureCompressionFormatAlias.PVRTC,
                                            TextureCompressionFormatAlias.ASTC))
                                    .build())))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#tcf_astc")
                        .setTargeting(
                            normalizeAssetsDirectoryTargeting(
                                AssetsDirectoryTargeting.newBuilder()
                                    .setCountrySet(
                                        alternativeCountrySetTargeting(
                                            /* alternatives= */ ImmutableList.of("latam")))
                                    .setTextureCompressionFormat(
                                        textureCompressionTargeting(
                                            /* value= */ TextureCompressionFormatAlias.ASTC,
                                            /* alternatives= */ ImmutableSet.of(
                                                TextureCompressionFormatAlias.PVRTC)))
                                    .build())))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img#tcf_pvrtc")
                        .setTargeting(
                            normalizeAssetsDirectoryTargeting(
                                AssetsDirectoryTargeting.newBuilder()
                                    .setCountrySet(
                                        alternativeCountrySetTargeting(
                                            /* alternatives= */ ImmutableList.of("latam")))
                                    .setTextureCompressionFormat(
                                        textureCompressionTargeting(
                                            /* value= */ TextureCompressionFormatAlias.PVRTC,
                                            /* alternatives= */ ImmutableSet.of(
                                                TextureCompressionFormatAlias.ASTC)))
                                    .build())))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/img")
                        .setTargeting(
                            normalizeAssetsDirectoryTargeting(
                                AssetsDirectoryTargeting.newBuilder()
                                    .setCountrySet(
                                        alternativeCountrySetTargeting(
                                            /* alternatives= */ ImmutableList.of("latam")))
                                    .setTextureCompressionFormat(
                                        alternativeTextureCompressionTargeting(
                                            TextureCompressionFormatAlias.PVRTC,
                                            TextureCompressionFormatAlias.ASTC))
                                    .build())))
                .build());
  }
}
