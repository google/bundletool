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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GlExtensionsParserTest {

  static final ImmutableList<String> SIMPLE_DUMPSYS_OUTPUT =
      ImmutableList.of(
          "",
          "SurfaceFlinger global state:",
          "EGL implementation : 1.4",
          "EGL_KHR_image EGL_KHR_image_base EGL_QCOM_create_image EGL_KHR_lock_surface"
              + " EGL_KHR_lock_surface2  ",
          "GLES: Qualcomm, Adreno (TM) 540, OpenGL ES 3.2 V@258.0 (GIT@594927b, I916dfac403)"
              + " (Date:10/11/17)",
          "GL_OES_EGL_image GL_OES_EGL_image_external GL_OES_EGL_sync GL_OES_vertex_half_float"
              + " GL_OES_framebuffer_object GL_OES_rgb8_rgba8 GL_OES_compressed_ETC1_RGB8_texture"
              + "  ");

  static final ImmutableList<String> OUTPUT_WITH_MULTIPLE_GLES =
      ImmutableList.of(
          "GLES: Unrelated",
          "GL_extensions that_should not_be_read",
          "",
          "SurfaceFlinger global state:",
          "GLES: TheProperOne",
          "GL_OES_EGL_image GL_OES_EGL_image_external GL_OES_EGL_sync GL_OES_vertex_half_float"
              + " GL_OES_framebuffer_object GL_OES_rgb8_rgba8 GL_OES_compressed_ETC1_RGB8_texture"
              + "  ");

  static final ImmutableList<String> EXPECTED_GL_EXTENSIONS =
      ImmutableList.of(
          "GL_OES_EGL_image",
          "GL_OES_EGL_image_external",
          "GL_OES_EGL_sync",
          "GL_OES_vertex_half_float",
          "GL_OES_framebuffer_object",
          "GL_OES_rgb8_rgba8",
          "GL_OES_compressed_ETC1_RGB8_texture");

  @Test
  public void simpleOutput() {
    ImmutableList<String> glExtensions = new GlExtensionsParser().parse(SIMPLE_DUMPSYS_OUTPUT);
    assertThat(glExtensions).containsExactlyElementsIn(EXPECTED_GL_EXTENSIONS);
  }

  @Test
  public void outputWithMultipleGLES() {
    ImmutableList<String> glExtensions = new GlExtensionsParser().parse(OUTPUT_WITH_MULTIPLE_GLES);
    assertThat(glExtensions).containsExactlyElementsIn(EXPECTED_GL_EXTENSIONS);
  }

  @Test
  public void invalidOutput_returnsEmpty() {
    ImmutableList<String> glExtensions =
        new GlExtensionsParser()
            .parse(
                ImmutableList.of(
                    "", "SurfaceFlinger global state:", "unrelated information", "that's all"));
    assertThat(glExtensions).isEmpty();
  }
}
