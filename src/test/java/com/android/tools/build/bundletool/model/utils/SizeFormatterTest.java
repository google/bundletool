/*
 * Copyright (C) 2020 The Android Open Source Project
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SizeFormatterTest {

  @Test
  public void rawFormatter() {
    SizeFormatter formatter = SizeFormatter.rawFormatter();

    assertThat(formatter.format(0)).isEqualTo("0");
    assertThat(formatter.format(12)).isEqualTo("12");
    assertThat(formatter.format(1023409)).isEqualTo("1023409");
  }

  @Test
  public void humanReadableFormatter() {
    SizeFormatter formatter = SizeFormatter.humanReadableFormatter();

    assertThat(formatter.format(0)).isEqualTo("0 B");
    assertThat(formatter.format(130)).isEqualTo("130 B");
    assertThat(formatter.format(1000)).isEqualTo("1 KB");
    assertThat(formatter.format(1014)).isEqualTo("1.01 KB");
    assertThat(formatter.format(1015)).isEqualTo("1.02 KB");
    assertThat(formatter.format(23102)).isEqualTo("23.1 KB");
    assertThat(formatter.format(999994)).isEqualTo("999.99 KB");
    assertThat(formatter.format(999995)).isEqualTo("1 MB");
    assertThat(formatter.format(2000000)).isEqualTo("2 MB");
    assertThat(formatter.format(2700000)).isEqualTo("2.7 MB");
    assertThat(formatter.format(2437439)).isEqualTo("2.44 MB");
    assertThat(formatter.format(999994999)).isEqualTo("999.99 MB");
    assertThat(formatter.format(999995000)).isEqualTo("1 GB");
    assertThat(formatter.format(3123021000L)).isEqualTo("3.12 GB");
    assertThat(formatter.format(9999123021000L)).isEqualTo("9999.12 GB");
    assertThat(formatter.format(9999999021000L)).isEqualTo("10000 GB");
  }
}
