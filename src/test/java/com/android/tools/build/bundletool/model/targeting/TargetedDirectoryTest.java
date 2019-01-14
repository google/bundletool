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

import static com.android.tools.build.bundletool.testing.TargetingUtils.assetsDirectoryTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.graphicsApiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.openGlVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetedDirectoryTest {

  @Test
  public void pathBaseName_singleLevelTargeting() {
    assertThat(
            TargetedDirectory.parse(ZipPath.create("assets/world/texture#tcf_etc1"))
                .getPathBaseName())
        .isEqualTo("assets/world/texture");
  }

  @Test
  public void pathBaseName_multiLevelTargeting() {
    assertThat(
            TargetedDirectory.parse(ZipPath.create("assets/world/gfx#opengl_3.2/texture#tcf_etc1"))
                .getPathBaseName())
        .isEqualTo("assets/world/gfx#opengl_3.2/texture");
  }

  @Test
  public void subPathBaseName_multiLevelTargeting() {
    TargetedDirectory targetedDirectory =
        TargetedDirectory.parse(ZipPath.create("assets/world/gfx#opengl_3.2/texture#tcf_etc1"));
    assertThat(targetedDirectory.getSubPathBaseName(0)).isEqualTo("assets");
    assertThat(targetedDirectory.getSubPathBaseName(1)).isEqualTo("assets/world");
    assertThat(targetedDirectory.getSubPathBaseName(2)).isEqualTo("assets/world/gfx");
    assertThat(targetedDirectory.getSubPathBaseName(3))
        .isEqualTo("assets/world/gfx#opengl_3.2/texture");
  }

  @Test
  public void pathBaseName_noTargeting() {
    TargetedDirectory targetedDirectory = TargetedDirectory.parse(ZipPath.create("assets/world"));
    assertThat(targetedDirectory.getPathBaseName()).isEqualTo("assets/world");
  }

  @Test
  public void pathBaseName_rootElement() {
    TargetedDirectory targetedDirectory = TargetedDirectory.parse(ZipPath.create("world"));
    assertThat(targetedDirectory.getPathBaseName()).isEqualTo("world");
    TargetedDirectory targetedDirectoryWithKey =
        TargetedDirectory.parse(ZipPath.create("world#opengl_1.0"));
    assertThat(targetedDirectoryWithKey.getPathBaseName()).isEqualTo("world");
  }

  @Test
  public void simpleDirectory_tokenization() {
    ZipPath path = ZipPath.create("assets/gfx#opengl_1.0");
    TargetedDirectory actual = TargetedDirectory.parse(path);

    assertThat(actual.getPathSegments()).hasSize(2);
    assertThat(actual.getPathSegments().get(0).getName()).isEqualTo("assets");
    assertThat(actual.getPathSegments().get(0).getTargeting())
        .isEqualTo(AssetsDirectoryTargeting.getDefaultInstance());
    assertThat(actual.getPathSegments().get(1).getName()).isEqualTo("gfx");
    assertThat(actual.getPathSegments().get(1).getTargeting())
        .isEqualTo(assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(1))));
  }

  @Test
  public void multiLevelDirectory_tokenization() {
    ZipPath path = ZipPath.create("assets/world/gfx#opengl_1.0/texture#tcf_etc1");
    TargetedDirectory actual = TargetedDirectory.parse(path);

    assertThat(actual.getPathSegments()).hasSize(4);
    assertThat(actual.getPathSegments().get(0).getName()).isEqualTo("assets");
    assertThat(actual.getPathSegments().get(0).getTargeting())
        .isEqualTo(AssetsDirectoryTargeting.getDefaultInstance());
    assertThat(actual.getPathSegments().get(1).getName()).isEqualTo("world");
    assertThat(actual.getPathSegments().get(1).getTargeting())
        .isEqualTo(AssetsDirectoryTargeting.getDefaultInstance());
    assertThat(actual.getPathSegments().get(2).getName()).isEqualTo("gfx");
    assertThat(actual.getPathSegments().get(2).getTargeting())
        .isEqualTo(assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(1))));
    assertThat(actual.getPathSegments().get(3).getName()).isEqualTo("texture");
    assertThat(actual.getPathSegments().get(3).getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8)));
  }

  @Test
  public void badDirectoryName_noValue_throws() {
    assertThrows(
        ValidationException.class,
        () -> TargetedDirectory.parse(ZipPath.create("assets/world/gfx#opengl")));
    assertThrows(
        ValidationException.class,
        () -> TargetedDirectory.parse(ZipPath.create("assets/world/gfx#opengl/texture#tcf_atc")));
  }

  @Test
  public void badDirectoryName_noKey_throws() {
    assertThrows(
        ValidationException.class,
        () -> TargetedDirectory.parse(ZipPath.create("assets/world/gfx#")));
    assertThrows(
        ValidationException.class,
        () -> TargetedDirectory.parse(ZipPath.create("assets/world#/gfx")));
  }

  @Test
  public void upperCaseNames_ok() {
    ZipPath path = ZipPath.create("assets/world/gFX#opengl_1.0/TEXTURE#tcf_etc1");
    TargetedDirectory actual = TargetedDirectory.parse(path);

    assertThat(actual.getPathSegments()).hasSize(4);
    assertThat(actual.getPathSegments().get(0).getName()).isEqualTo("assets");
    assertThat(actual.getPathSegments().get(0).getTargeting())
        .isEqualTo(AssetsDirectoryTargeting.getDefaultInstance());
    assertThat(actual.getPathSegments().get(1).getName()).isEqualTo("world");
    assertThat(actual.getPathSegments().get(1).getTargeting())
        .isEqualTo(AssetsDirectoryTargeting.getDefaultInstance());
    assertThat(actual.getPathSegments().get(2).getName()).isEqualTo("gFX");
    assertThat(actual.getPathSegments().get(2).getTargeting())
        .isEqualTo(assetsDirectoryTargeting(graphicsApiTargeting(openGlVersionFrom(1))));
    assertThat(actual.getPathSegments().get(3).getName()).isEqualTo("TEXTURE");
    assertThat(actual.getPathSegments().get(3).getTargeting())
        .isEqualTo(
            assetsDirectoryTargeting(
                textureCompressionTargeting(TextureCompressionFormatAlias.ETC1_RGB8)));
  }

  @Test
  public void duplicateDimensionsOnPath_throws() {
    assertThrows(
        ValidationException.class,
        () -> TargetedDirectory.parse(ZipPath.create("assets/world/gfx#tcf_etc1/texture#tcf_atc")));
    assertThrows(
        ValidationException.class,
        () ->
            TargetedDirectory.parse(
                ZipPath.create("assets/world/gfx#opengl_1.0/refine#opengl_3.0")));
    assertThrows(
        ValidationException.class,
        () ->
            TargetedDirectory.parse(
                ZipPath.create("assets/world/gfx#tcf_etc1/other/gl#opengl_2.0/texture#tcf_atc")));
  }
}
