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

import com.google.common.annotations.VisibleForTesting;
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
}
