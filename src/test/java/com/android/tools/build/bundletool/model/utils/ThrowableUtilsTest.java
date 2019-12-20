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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ThrowableUtilsTest {

  @Test
  public void anyCauseOrSuppressedMatches_argumentMatches() {
    Throwable argument = new RuntimeException("marker");

    assertThat(
            ThrowableUtils.anyInCausalChainOrSuppressedMatches(
                argument, t -> Objects.equals(t.getMessage(), "marker")))
        .isTrue();
  }

  @Test
  public void anyCauseOrSuppressedMatches_suppressedOfArgumentMatches() {
    Throwable suppressed = new RuntimeException("marker");
    Throwable argument = new RuntimeException();
    argument.addSuppressed(suppressed);

    assertThat(
            ThrowableUtils.anyInCausalChainOrSuppressedMatches(
                argument, t -> Objects.equals(t.getMessage(), "marker")))
        .isTrue();
  }

  @Test
  public void anyCauseOrSuppressedMatches_causeMatches() {
    Throwable cause = new RuntimeException("marker");
    Throwable argument = new RuntimeException(cause);

    assertThat(
            ThrowableUtils.anyInCausalChainOrSuppressedMatches(
                argument, t -> Objects.equals(t.getMessage(), "marker")))
        .isTrue();
  }

  @Test
  public void anyCauseOrSuppressedMatches_suppressedOfCauseMatches() {
    Throwable suppressed = new RuntimeException("marker");
    Throwable cause = new RuntimeException();
    cause.addSuppressed(suppressed);
    Throwable argument = new RuntimeException(cause);

    assertThat(
            ThrowableUtils.anyInCausalChainOrSuppressedMatches(
                argument, t -> Objects.equals(t.getMessage(), "marker")))
        .isTrue();
  }

  @Test
  public void anyCauseOrSuppressedMatches_nothingMatches() {
    Throwable argument = new RuntimeException();

    assertThat(
            ThrowableUtils.anyInCausalChainOrSuppressedMatches(
                argument, t -> Objects.equals(t.getMessage(), "marker")))
        .isFalse();
  }

  @Test
  public void anyCauseOrSuppressedMatches_handlesCausalLoop() {
    MutableException throwable1 = new MutableException();
    MutableException throwable2 = new MutableException();
    throwable1.cause = throwable2;
    throwable2.cause = throwable1;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ThrowableUtils.anyInCausalChainOrSuppressedMatches(
                    throwable1, t -> Objects.equals(t.getMessage(), "marker")));

    assertThat(exception).hasMessageThat().contains("causal chain detected");
  }

  static class MutableException extends Exception {
    Throwable cause;

    @Override
    public synchronized Throwable getCause() {
      return cause;
    }
  }
}
