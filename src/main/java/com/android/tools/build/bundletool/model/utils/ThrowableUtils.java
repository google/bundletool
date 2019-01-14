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

package com.android.tools.build.bundletool.model.utils;

import com.google.common.base.Throwables;
import java.util.Arrays;
import java.util.function.Predicate;

/** Utility class for {@link Throwable}. */
public final class ThrowableUtils {

  /**
   * Tests whether the exception itself, any of its causes, or any of their suppressed exceptions
   * matches the given predicate.
   *
   * @return true if a match was found, false otherwise
   * @throws IllegalArgumentException when there is a loop in the causal chain
   */
  public static boolean anyInCausalChainOrSuppressedMatches(
      Throwable baseThrowable, Predicate<Throwable> predicate) {
    for (Throwable throwable : Throwables.getCausalChain(baseThrowable)) {
      if (predicate.test(throwable)
          || Arrays.stream(throwable.getSuppressed()).anyMatch(predicate)) {
        return true;
      }
    }
    return false;
  }

  private ThrowableUtils() {}
}
