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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.Optional;

/** Class to format the output to CSV */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class CsvFormatter {

  private static final Joiner COMMA_JOINER = Joiner.on(",");

  public static final String CRLF = "\r\n";

  /** A char escaper that will escape quote characters. */
  private static final Escaper DOUBLE_QUOTER =
      new CharEscaperBuilder().addEscape('"', "\"\"").toEscaper();

  /** Char matcher to see if quotes are needed on a value. */
  private static final CharMatcher QUOTE_NEEDED = CharMatcher.anyOf("\",\n\r");

  public abstract Optional<ImmutableList<String>> getHeader();

  public abstract ImmutableList<ImmutableList<String>> getRows();

  public static CsvFormatter.Builder builder() {
    return new AutoValue_CsvFormatter.Builder();
  }

  public String format() {
    StringBuilder stringBuilder = new StringBuilder();

    getHeader().ifPresent(row -> stringBuilder.append(getRowInCsv(row)));
    getRows().forEach(row -> stringBuilder.append(getRowInCsv(row)));

    return stringBuilder.toString();
  }

  private static String getRowInCsv(ImmutableList<String> text) {
    return COMMA_JOINER.join(
            text.stream().map(CsvFormatter::encloseInQuotes).collect(toImmutableList()))
        + CRLF; // According to RFC4180 CSV standard each line should be delimited by CRLF.
  }

  /**
   * According to RFC4180 compliant CSV fields containing commas, line breaks (CRLF) and double
   * quotes should be enclosed in double-quotes.
   *
   * <p>If double-quotes are used to enclose fields, then a double-quote appearing inside a field
   * must be escaped by preceding it with another double quote
   */
  private static String encloseInQuotes(String value) {
    if (QUOTE_NEEDED.matchesAnyOf(value)) {
      return '"' + DOUBLE_QUOTER.escape(value) + '"';
    }
    return value;
  }

  /** Builder for the {@link CsvFormatter}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setHeader(ImmutableList<String> header);

    abstract ImmutableList.Builder<ImmutableList<String>> rowsBuilder();

    public Builder addRow(ImmutableList<String> row) {
      rowsBuilder().add(row);
      return this;
    }

    abstract CsvFormatter autoBuild();

    public CsvFormatter build() {
      CsvFormatter result = autoBuild();

      ImmutableList.Builder<ImmutableList<String>> allRows = ImmutableList.builder();
      result.getHeader().ifPresent(allRows::add);
      allRows.addAll(result.getRows());

      checkState(
          allRows.build().stream().mapToInt(Collection::size).distinct().count() <= 1,
          "All rows must have the same size.");

      return result;
    }
  }

  // Don't subclass. Hide the implicit constructor from IDEs/docs.
  CsvFormatter() {}
}
