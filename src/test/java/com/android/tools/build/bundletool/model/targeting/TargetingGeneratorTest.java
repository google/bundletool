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

import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeAssetsTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.nativeDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionTargeting;
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
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
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
    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> generator.generateTargetingForNativeLibraries(ImmutableList.of(directoryName)));

    assertThat(exception)
        .hasMessageThat()
        .containsMatch(
            "Expecting ABI name in file or directory 'lib/ARM64-v8a', but found 'ARM64-v8a' "
                + "which is not recognized. Did you mean 'arm64-v8a'?");
  }

  @Test
  public void generateTargetingForNativeLibraries_baseNameNotAnAbi_throws() throws Exception {
    ValidationException exception =
        assertThrows(
            ValidationException.class,
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

    ApexImages apexImages = generator.generateTargetingForApexImages(allAbiFiles);

    List<TargetedApexImage> images = apexImages.getImageList();
    assertThat(images).hasSize(allAbiFiles.size());
  }

  @Test
  public void generateTargetingForApexImages_abiBaseNamesDisallowed() throws Exception {
    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () ->
                generator.generateTargetingForApexImages(
                    ImmutableList.of(ZipPath.create("x86.ARM64-v8a.img"))));

    assertThat(exception)
        .hasMessageThat()
        .containsMatch(
            "Expecting ABI name in file or directory 'x86.ARM64-v8a.img', but found 'ARM64-v8a' "
                + "which is not recognized. Did you mean 'arm64-v8a'?");
  }

  @Test
  public void generateTargetingForApexImages_baseNameNotAnAbi_throws() throws Exception {
    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () ->
                generator.generateTargetingForApexImages(
                    ImmutableList.of(ZipPath.create("non_abi_name.img"))));

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
  public void generateTargetingForAssets_typeMismatch_leaf() throws Exception {
    Throwable t =
        assertThrows(
            ValidationException.class,
            () ->
                new TargetingGenerator()
                    .generateTargetingForAssets(
                        ImmutableList.of(
                            ZipPath.create("assets/world/gfx#opengl_3.0"),
                            ZipPath.create("assets/world/gfx#tcf_etc1"))));
    assertThat(t)
        .hasMessageThat()
        .contains(
            "Expected at most one dimension type used for targeting of 'assets/world/gfx'. "
                + "However, the following dimensions were used: "
                + "'GRAPHICS_API', 'TEXTURE_COMPRESSION_FORMAT'.");
  }

  @Test
  public void generateTargetingForAssets_typeMismatch_midPath() throws Exception {
    Throwable t =
        assertThrows(
            ValidationException.class,
            () ->
                new TargetingGenerator()
                    .generateTargetingForAssets(
                        ImmutableList.of(
                            ZipPath.create("assets/world/texture#tcf_etc1/gfx#opengl_3.0"),
                            ZipPath.create("assets/world/texture#opengl_2.0/gfx#tcf_etc1"))));
    assertThat(t)
        .hasMessageThat()
        .contains(
            "Expected at most one dimension type used for targeting of 'assets/world/texture'. "
                + "However, the following dimensions were used: "
                + "'GRAPHICS_API', 'TEXTURE_COMPRESSION_FORMAT'.");
  }

  @Test
  public void generateTargetingForAssets_different_types_leaves_ok() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_etc1"),
                    ZipPath.create("assets/world/alternative/texture#opengl_3.0")));
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
                        .setPath("assets/world/alternative/texture#opengl_3.0")
                        .setTargeting(
                            assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(3)))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_nameOverloading_works() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_etc1/texture#opengl_3.0"),
                    ZipPath.create("assets/world/texture#tcf_etc1/texture#opengl_2.0")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/texture#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8)),
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(3),
                                        ImmutableSet.of(openGlVersionFrom(2)))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/texture#opengl_2.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8)),
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(2),
                                        ImmutableSet.of(openGlVersionFrom(3)))))))
                .build());
  }

  @Test
  public void generateTargetingForAssets_basicScenario() throws Exception {
    Assets assetsConfig =
        new TargetingGenerator()
            .generateTargetingForAssets(
                ImmutableList.of(
                    ZipPath.create("assets/world/texture#tcf_etc1/gfx#opengl_3.0"),
                    ZipPath.create("assets/world/texture#tcf_etc1/gfx"),
                    ZipPath.create("assets/world/texture#tcf_atc/gfx#opengl_2.0"),
                    ZipPath.create("assets/world/texture#tcf_atc/gfx#opengl_3.0")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/gfx#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    graphicsApiTargeting(openGlVersionFrom(3))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/gfx")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    graphicsApiTargeting(
                                        ImmutableSet.of(), ImmutableSet.of(openGlVersionFrom(3)))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/gfx#opengl_2.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(2),
                                        ImmutableSet.of(openGlVersionFrom(3)))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/gfx#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(3),
                                        ImmutableSet.of(openGlVersionFrom(2)))),
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
                    ZipPath.create("assets/world/texture#tcf_atc/gfx#opengl_3.0"),
                    ZipPath.create("assets/world/texture#tcf_atc/gfx#opengl_2.0"),
                    ZipPath.create("assets/world/texture#tcf_etc1/gfx"),
                    ZipPath.create("assets/world/texture#tcf_etc1/gfx#opengl_3.0")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/gfx#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    graphicsApiTargeting(openGlVersionFrom(3))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/gfx")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    graphicsApiTargeting(
                                        ImmutableSet.of(), ImmutableSet.of(openGlVersionFrom(3)))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/gfx#opengl_2.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(2),
                                        ImmutableSet.of(openGlVersionFrom(3)))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/gfx#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(3),
                                        ImmutableSet.of(openGlVersionFrom(2)))),
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
                    ZipPath.create("assets/world/texture#tcf_atc/rest/gfx#opengl_3.0"),
                    ZipPath.create("assets/world/texture#tcf_atc/rest/gfx#opengl_2.0"),
                    // Note that the two below should not have any alternatives for OpenGL version.
                    ZipPath.create("assets/world/texture#tcf_etc1/rest/gfx#opengl_2.0"),
                    ZipPath.create("assets/world/texture#tcf_etc1/gfx#opengl_3.0")));
    assertThat(assetsConfig)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            Assets.newBuilder()
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/gfx#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    graphicsApiTargeting(openGlVersionFrom(3))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_etc1/rest/gfx#opengl_2.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    graphicsApiTargeting(openGlVersionFrom(2))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ETC1_RGB8,
                                        ImmutableSet.of(TextureCompressionFormatAlias.ATC))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/rest/gfx#opengl_2.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(2),
                                        ImmutableSet.of(openGlVersionFrom(3)))),
                                assetsDirectoryTargeting(
                                    textureCompressionTargeting(
                                        TextureCompressionFormatAlias.ATC,
                                        ImmutableSet.of(
                                            TextureCompressionFormatAlias.ETC1_RGB8))))))
                .addDirectory(
                    TargetedAssetsDirectory.newBuilder()
                        .setPath("assets/world/texture#tcf_atc/rest/gfx#opengl_3.0")
                        .setTargeting(
                            mergeAssetsTargeting(
                                assetsDirectoryTargeting(
                                    openGlVersionTargeting(
                                        openGlVersionFrom(3),
                                        ImmutableSet.of(openGlVersionFrom(2)))),
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
            ValidationException.class,
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
  public void duplicateTargetingDimensions_throws() throws Exception {
    Throwable t =
        assertThrows(
            ValidationException.class,
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
}
