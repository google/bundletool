/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.build.bundletool.device;

import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ASTC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ATC;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC1_RGB8;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.ETC2;
import static com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias.PVRTC;
import static com.android.tools.build.bundletool.testing.DeviceFactory.deviceFeatures;
import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithGlExtensions;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.TargetingUtils.textureCompressionTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TextureCompressionFormatMatcherTest {

  @Test
  public void simpleMatch_glExtensions() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(
            lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of())))
        .isTrue();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ATC, ImmutableSet.of())))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(ATC))))
        .isTrue();
  }

  @Test
  public void worseAlternatives_glExtensions() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(
            lDeviceWithGlExtensions(
                "GL_OES_compressed_ETC1_RGB8_texture", "GL_IMG_texture_compression_pvrtc"));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(PVRTC, ImmutableSet.of(ATC))))
        .isTrue();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(PVRTC, ImmutableSet.of(ETC1_RGB8))))
        .isTrue();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(PVRTC, ImmutableSet.of(ATC, ETC1_RGB8))))
        .isTrue();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(ATC))))
        .isTrue();
  }

  @Test
  public void betterAlternatives_glExtensions() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(
            lDeviceWithGlExtensions(
                "GL_OES_compressed_ETC1_RGB8_texture", "GL_IMG_texture_compression_pvrtc"));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ATC, ImmutableSet.of(PVRTC))))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(PVRTC))))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(ATC, PVRTC))))
        .isFalse();
  }

  @Test
  public void simpleMatch_openglVersion() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(deviceFeatures("reqGlEsVersion=0x30000"));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC2, ImmutableSet.of())))
        .isTrue();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ATC, ImmutableSet.of())))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC2, ImmutableSet.of(ATC))))
        .isTrue();
  }

  @Test
  public void noMatch_invalidOpenglVersion() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(deviceFeatures("reqGlEsVersion=bad_result"));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC2, ImmutableSet.of())))
        .isFalse();
  }

  @Test
  public void betterAlternatives_mix_inOpenglVersion() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(
            mergeSpecs(
                lDeviceWithGlExtensions("GL_OES_compressed_ETC1_RGB8_texture"),
                deviceFeatures("reqGlEsVersion=0x30020")));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ATC, ImmutableSet.of(PVRTC))))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(ETC2, PVRTC))))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC2, ImmutableSet.of(ETC1_RGB8, PVRTC))))
        .isTrue();
  }

  @Test
  public void betterAlternatives_mix_inGlExtensions() {
    TextureCompressionFormatMatcher textureCompressionFormatMatcher =
        new TextureCompressionFormatMatcher(
            mergeSpecs(
                lDeviceWithGlExtensions(
                    "GL_OES_compressed_ETC1_RGB8_texture", "GL_KHR_texture_compression_astc_ldr"),
                deviceFeatures("reqGlEsVersion=0x30020")));

    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ATC, ImmutableSet.of(ASTC))))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ETC1_RGB8, ImmutableSet.of(ETC2, ASTC))))
        .isFalse();
    assertThat(
            textureCompressionFormatMatcher.matchesTargeting(
                textureCompressionTargeting(ASTC, ImmutableSet.of(ETC1_RGB8, ETC2))))
        .isTrue();
  }
}
