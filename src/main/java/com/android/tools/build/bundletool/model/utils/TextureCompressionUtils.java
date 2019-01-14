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
import com.google.common.collect.ImmutableMap;

/** Utilities for Texture Compression Format support. */
public final class TextureCompressionUtils {

  public static final ImmutableBiMap<TextureCompressionFormatAlias, String>
      TEXTURE_COMPRESSION_FORMAT_TO_MANIFEST_VALUE =
          ImmutableBiMap.<TextureCompressionFormatAlias, String>builder()
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
          .put("atc", textureCompressionFormat(TextureCompressionFormatAlias.ATC))
          .put("dxt1", textureCompressionFormat(TextureCompressionFormatAlias.DXT1))
          .put("latc", textureCompressionFormat(TextureCompressionFormatAlias.LATC))
          .put("paletted", textureCompressionFormat(TextureCompressionFormatAlias.PALETTED))
          .put("pvrtc", textureCompressionFormat(TextureCompressionFormatAlias.PVRTC))
          .put("etc1", textureCompressionFormat(TextureCompressionFormatAlias.ETC1_RGB8))
          .put("s3tc", textureCompressionFormat(TextureCompressionFormatAlias.S3TC))
          .put("3dc", textureCompressionFormat(TextureCompressionFormatAlias.THREE_DC))
          .build();

  private static TextureCompressionFormatTargeting textureCompressionFormat(
      TextureCompressionFormatAlias alias) {
    return TextureCompressionFormatTargeting.newBuilder()
        .addValue(TextureCompressionFormat.newBuilder().setAlias(alias))
        .build();
  }

  // Do not instantiate.
  private TextureCompressionUtils() {}
}
