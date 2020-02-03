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

import static com.android.tools.build.bundletool.model.utils.CsvFormatter.CRLF;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CsvFormatterTest {

  @Test
  public void format_withNoQuotesNeeded() {
    assertThat(
            CsvFormatter.builder()
                .setHeader(ImmutableList.of("a", "b"))
                .addRow(ImmutableList.of("1", "2"))
                .addRow(ImmutableList.of("3", "4"))
                .build()
                .format())
        .isEqualTo("a,b" + CRLF + "1,2" + CRLF + "3,4" + CRLF);
  }

  @Test
  public void format_noHeaderWithNoQuotesNeeded() {
    assertThat(
            CsvFormatter.builder()
                .addRow(ImmutableList.of("1", "2"))
                .addRow(ImmutableList.of("3", "4"))
                .build()
                .format())
        .isEqualTo("1,2" + CRLF + "3,4" + CRLF);
  }

  @Test
  public void format_unequalSizeRows_throws() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CsvFormatter.builder()
                    .setHeader(ImmutableList.of("a", "b", "c"))
                    .addRow(ImmutableList.of("1", "2"))
                    .addRow(ImmutableList.of("3", "4"))
                    .build()
                    .format());
    assertThat(exception).hasMessageThat().contains("All rows must have the same size.");
  }

  @Test
  public void format_withCommasInData() {
    assertThat(
            CsvFormatter.builder()
                .setHeader(ImmutableList.of("a", "b"))
                .addRow(ImmutableList.of("4,5", "2"))
                .build()
                .format())
        .isEqualTo("a,b" + CRLF + "\"4,5\",2" + CRLF);
  }

  @Test
  public void format_withLineBreaksInData() {
    assertThat(
            CsvFormatter.builder()
                .setHeader(ImmutableList.of("a", "b"))
                .addRow(ImmutableList.of("4\n5", "2\r5"))
                .build()
                .format())
        .isEqualTo("a,b" + CRLF + "\"4\n5\",\"2\r5\"" + CRLF);
  }

  @Test
  public void format_withDoubleQuotesInData() {
    assertThat(
            CsvFormatter.builder()
                .setHeader(ImmutableList.of("f", "g"))
                .addRow(ImmutableList.of("4\"5", "2"))
                .build()
                .format())
        .isEqualTo("f,g" + CRLF + "\"4\"\"5\",2" + CRLF);
  }

  @Test
  public void format_withCommasLineBreaksAndQuotes() {
    assertThat(
            CsvFormatter.builder()
                .setHeader(ImmutableList.of("a", "b"))
                .addRow(ImmutableList.of("4\"5", "2,6\"\r\n"))
                .build()
                .format())
        .isEqualTo("a,b" + CRLF + "\"4\"\"5\",\"2,6\"\"\r\n\"" + CRLF);
  }
}
