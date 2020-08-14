/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore.PasswordProtection;
import java.util.function.Supplier;

/** Wrapper around a password. */
public final class Password {

  private final Supplier<PasswordProtection> passwordSupplier;

  public Password(Supplier<PasswordProtection> passwordSupplier) {
    this.passwordSupplier = passwordSupplier;
  }

  @VisibleForTesting
  public static Password createForTest(String password) {
    return new Password(() -> new PasswordProtection(password.toCharArray()));
  }

  /** Special note: It's the responsibility of the caller to destroy the password once used. */
  public final PasswordProtection getValue() {
    return passwordSupplier.get();
  }

  /**
   * Create a password from a string value. Must be prefixed with either 'pass:' (if the password is
   * passed in clear text, e.g. 'pass:qwerty') or 'file:' (if the password is the first line of a
   * file, e.g. 'file:/tmp/myPassword.txt').
   */
  public static Password createFromStringValue(String value) {
    if (value.startsWith("pass:")) {
      return new Password(
          () -> new PasswordProtection(value.substring("pass:".length()).toCharArray()));
    } else if (value.startsWith("file:")) {
      Path passwordFile = Paths.get(value.substring("file:".length()));
      checkFileExistsAndReadable(passwordFile);
      return new Password(
          () -> new PasswordProtection(readPasswordFromFile(passwordFile).toCharArray()));
    }

    throw new IllegalArgumentException("Passwords must be prefixed with \"pass:\" or \"file:\".");
  }

  private static String readPasswordFromFile(Path passwordFile) {
    try {
      return MoreFiles.asCharSource(passwordFile, UTF_8).readFirstLine();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Unable to read password from file '%s'.", passwordFile), e);
    }
  }
}
