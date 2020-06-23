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
package com.android.tools.build.bundletool.model.utils;

import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Utilities for Texture Compression Format support. */
public final class TextureCompressionUtils {

  public static final ImmutableBiMap<TextureCompressionFormatAlias, String>
      TEXTURE_COMPRESSION_FORMAT_TO_MANIFEST_VALUE =
          ImmutableBiMap.<TextureCompressionFormatAlias, String>builder()
              .put(TextureCompressionFormatAlias.ASTC, "GL_KHR_texture_compression_astc_ldr")
              .put(TextureCompressionFormatAlias.ATC, "GL_AMD_compressed_ATC_texture")
              .put(TextureCompressionFormatAlias.DXT1, "GL_EXT_texture_compression_dxt1")
              .put(TextureCompressionFormatAlias.ETC1_RGB8, "GL_OES_compressed_ETC1_RGB8_texture")
              .put(TextureCompressionFormatAlias.LATC, "GL_EXT_texture_compression_latc")
              .put(TextureCompressionFormatAlias.PALETTED, "GL_OES_compressed_paletted_texture")
              .put(TextureCompressionFormatAlias.PVRTC, "GL_IMG_texture_compression_pvrtc")
              .put(TextureCompressionFormatAlias.S3TC, "GL_EXT_texture_compression_s3tc")
              .put(TextureCompressionFormatAlias.THREE_DC, "GL_AMD_compressed_3DC_texture")
              .build();

  public static final ImmutableMap<String, TextureCompressionFormatTargeting> TEXTURE_TO_TARGETING =
      new ImmutableMap.Builder<String, TextureCompressionFormatTargeting>()
          .put("astc", textureCompressionFormat(TextureCompressionFormatAlias.ASTC))
          .put("atc", textureCompressionFormat(TextureCompressionFormatAlias.ATC))
          .put("dxt1", textureCompressionFormat(TextureCompressionFormatAlias.DXT1))
          .put("latc", textureCompressionFormat(TextureCompressionFormatAlias.LATC))
          .put("paletted", textureCompressionFormat(TextureCompressionFormatAlias.PALETTED))
          .put("pvrtc", textureCompressionFormat(TextureCompressionFormatAlias.PVRTC))
          .put("etc1", textureCompressionFormat(TextureCompressionFormatAlias.ETC1_RGB8))
          .put("etc2", textureCompressionFormat(TextureCompressionFormatAlias.ETC2))
          .put("s3tc", textureCompressionFormat(TextureCompressionFormatAlias.S3TC))
          .put("3dc", textureCompressionFormat(TextureCompressionFormatAlias.THREE_DC))
          .build();

  public static final ImmutableBiMap<TextureCompressionFormatAlias, String> TARGETING_TO_TEXTURE =
      ImmutableBiMap.<TextureCompressionFormatAlias, String>builder()
          .put(TextureCompressionFormatAlias.ASTC, "astc")
          .put(TextureCompressionFormatAlias.ATC, "atc")
          .put(TextureCompressionFormatAlias.DXT1, "dxt1")
          .put(TextureCompressionFormatAlias.ETC1_RGB8, "etc1")
          .put(TextureCompressionFormatAlias.ETC2, "etc2")
          .put(TextureCompressionFormatAlias.LATC, "latc")
          .put(TextureCompressionFormatAlias.PALETTED, "paletted")
          .put(TextureCompressionFormatAlias.PVRTC, "pvrtc")
          .put(TextureCompressionFormatAlias.S3TC, "s3tc")
          .put(TextureCompressionFormatAlias.THREE_DC, "3dc")
          .build();

  /** Returns the texture compression format associated to the GL extension, if any. */
  public static Optional<TextureCompressionFormatAlias> textureCompressionFormat(
      String glExtension) {
    return Optional.ofNullable(
        TEXTURE_COMPRESSION_FORMAT_TO_MANIFEST_VALUE.inverse().get(glExtension));
  }

  private static TextureCompressionFormatTargeting textureCompressionFormat(
      TextureCompressionFormatAlias alias) {
    return TextureCompressionFormatTargeting.newBuilder()
        .addValue(TextureCompressionFormat.newBuilder().setAlias(alias))
        .build();
  }

  /** Returns the texture compression formats supported by the given OpenGL version. */
  public static ImmutableList<TextureCompressionFormatAlias> textureCompressionFormatsForGl(
      int glVersion) {
    // OpenGL ES 3.0 mandates the support for ETC2
    // (see appendix C in https://www.khronos.org/registry/OpenGL/specs/es/3.0/es_spec_3.0.pdf).
    if (glVersion >= 0x30000) {
      return ImmutableList.of(TextureCompressionFormatAlias.ETC2);
    }

    return ImmutableList.of();
  }

  /**
   * Returns the minimum required GL ES version for a particular format, or empty if there are no
   * requirements.
   */
  public static Optional<Integer> getMinGlEsVersionRequired(TextureCompressionFormat tcf) {
    if (tcf.getAlias().equals(TextureCompressionFormatAlias.ETC2)) {
      return Optional.of(0x30000);
    }
    return Optional.empty();
  }

  // Do not instantiate.
  private TextureCompressionUtils() {}
}
