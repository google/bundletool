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
package com.android.tools.build.bundletool.model.version;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VersionTest {

  @Test
  public void correctVersionScheme() {
    Version.of("1.2.3");
    Version.of("1.2.3-dev");
    Version.of("1.2.3-alpha01");
    Version.of("1.2.3-beta01");
    Version.of("1.2.3-blah");
  }

  @Test
  public void incorrectVersionScheme() {
    assertThrows(IllegalArgumentException.class, () -> Version.of("1.2"));
    assertThrows(IllegalArgumentException.class, () -> Version.of("1.2.3.4"));
    assertThrows(IllegalArgumentException.class, () -> Version.of("1.2.a"));
    assertThrows(IllegalArgumentException.class, () -> Version.of("1.2.3-"));
    assertThrows(IllegalArgumentException.class, () -> Version.of("-1.0.0"));
    assertThrows(IllegalArgumentException.class, () -> Version.of("1.-1.0"));
  }

  @Test
  public void orderingOfVersions() {
    ImmutableList<Version> versions =
        ImmutableList.<Version>of(
            Version.of("0.0.1"),
            Version.of("0.0.2"),
            Version.of("0.1.1"),
            Version.of("0.1.2"),
            Version.of("0.2.1"),
            Version.of("1.2.1"),
            Version.of("1.2.2"),
            Version.of("1.2.3"),
            Version.of("1.2.3-dev"),
            Version.of("1.2.3-alpha01"),
            Version.of("1.2.3-alpha02"),
            Version.of("1.2.3-beta1"),
            Version.of("1.2.3-beta02"),
            Version.of("1.2.3-plop"));

    // Sort and verify the order.
    assertThat(Ordering.natural().sortedCopy(versions))
        .containsExactly(
            Version.of("0.0.1"),
            Version.of("0.0.2"),
            Version.of("0.1.1"),
            Version.of("0.1.2"),
            Version.of("0.2.1"),
            Version.of("1.2.1"),
            Version.of("1.2.2"),
            Version.of("1.2.3-dev"),
            Version.of("1.2.3-alpha01"),
            Version.of("1.2.3-alpha02"),
            Version.of("1.2.3-beta02"),
            Version.of("1.2.3-beta1"),
            Version.of("1.2.3-plop"),
            Version.of("1.2.3"))
        .inOrder();
  }
}
