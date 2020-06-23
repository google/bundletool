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

package com.android.tools.build.bundletool.model.utils.files;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.WillCloseWhenClosed;

/** Utilities for efficient I/O. */
public final class BufferedIo {

  @MustBeClosed
  public static BufferedReader reader(@WillCloseWhenClosed InputStream is) {
    return new BufferedReader(new InputStreamReader(is, UTF_8));
  }

  @MustBeClosed
  public static BufferedReader reader(Path file) throws IOException {
    return Files.newBufferedReader(file, UTF_8);
  }

  @MustBeClosed
  public static InputStream inputStream(Path file) throws IOException {
    return makeBuffered(Files.newInputStream(file));
  }

  @MustBeClosed
  public static OutputStream outputStream(Path file) throws IOException {
    return makeBuffered(Files.newOutputStream(file));
  }

  @MustBeClosed
  static InputStream makeBuffered(@WillCloseWhenClosed InputStream is) {
    return (is instanceof BufferedInputStream || is instanceof ByteArrayInputStream)
        ? is
        : new BufferedInputStream(is);
  }

  @MustBeClosed
  static OutputStream makeBuffered(@WillCloseWhenClosed OutputStream os) {
    return (os instanceof BufferedOutputStream || os instanceof ByteArrayOutputStream)
        ? os
        : new BufferedOutputStream(os);
  }

  private BufferedIo() {}
}
