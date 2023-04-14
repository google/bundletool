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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/** Parses the output of the "dumpsys SurfaceFlinger" ADB shell command. */
public class GlExtensionsParser {
  private static final String SURFACE_FLINGER_HEADER = "SurfaceFlinger global state:";
  private static final String GLES_PREFIX = "GLES:";

  private enum ParsingState {
    SEARCHING_SURFACE_FLINGER_HEADER,
    FOUND_SURFACE_FLINGER_HEADER,
    FOUND_GLES_HEADER,
    FOUND_GL_EXTENSIONS
  }

  /** Parses the "dumpsys SurfaceFlinger" command output. */
  public ImmutableList<String> parse(ImmutableList<String> dumpSysOutput) {
    ImmutableList.Builder<String> glExtensions = ImmutableList.builder();

    ParsingState parsingState = ParsingState.SEARCHING_SURFACE_FLINGER_HEADER;

    for (String line : dumpSysOutput) {
      if (line.isEmpty()) {
        continue;
      }

      switch (parsingState) {
        case SEARCHING_SURFACE_FLINGER_HEADER:
          if (line.startsWith(SURFACE_FLINGER_HEADER)) {
            parsingState = ParsingState.FOUND_SURFACE_FLINGER_HEADER;
          }
          break;
        case FOUND_SURFACE_FLINGER_HEADER:
          if (line.startsWith(GLES_PREFIX)) {
            parsingState = ParsingState.FOUND_GLES_HEADER;
          }
          break;
        case FOUND_GLES_HEADER:
          // GL extensions are listed on the line after the "GLES: " line,
          // there is no prefix for this list of extensions.
          glExtensions.addAll(Splitter.on(' ').split(line.trim()));
          parsingState = ParsingState.FOUND_GL_EXTENSIONS;
          break;
        case FOUND_GL_EXTENSIONS:
          break;
      }
    }

    if (!parsingState.equals(ParsingState.FOUND_GL_EXTENSIONS)) {
      System.out.println(
          "WARNING: Unexpected output of 'dumpsys SurfaceFlinger' command: no GL extensions"
              + " found.");
    }

    return glExtensions.build();
  }
}
