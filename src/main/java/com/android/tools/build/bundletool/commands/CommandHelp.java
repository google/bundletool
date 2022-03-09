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

package com.android.tools.build.bundletool.commands;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.Immutable;
import java.io.PrintStream;
import java.text.BreakIterator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

/** Helper to print command helps in the console. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class CommandHelp {

  private static final String LINE_SEPARATOR = System.lineSeparator();
  private static final int MAX_WIDTH = 80;
  private static final int INDENT_SIZE = 4;

  /** Order of the flags: first mandatory ones, then alphabetically. */
  private static final Comparator<FlagDescription> FLAG_ORDER =
      Comparator.comparing(FlagDescription::isOptional).thenComparing(FlagDescription::getFlagName);

  abstract String getCommandName();

  abstract ImmutableList<String> getSubCommandNames();

  private String getSubCommandNamesAsString() {
    switch (getSubCommandNames().size()) {
      case 0:
        return "";
      case 1:
        return Iterables.getOnlyElement(getSubCommandNames());
      default:
        StringJoiner joiner = new StringJoiner("|", "<", ">");
        getSubCommandNames().forEach(joiner::add);
        return joiner.toString();
    }
  }

  abstract CommandDescription getCommandDescription();

  abstract ImmutableSortedSet<FlagDescription> getFlags();

  static Builder builder() {
    return new AutoValue_CommandHelp.Builder().setSubCommandNames(ImmutableList.of());
  }

  @AutoValue.Builder
  abstract static class Builder {
    private final ImmutableSortedSet.Builder<FlagDescription> flagsBuilder =
        ImmutableSortedSet.orderedBy(FLAG_ORDER);

    public abstract Builder setCommandName(String commandName);

    public abstract Builder setSubCommandNames(ImmutableList<String> subCommandNames);

    public abstract Builder setCommandDescription(CommandDescription commandDescription);

    abstract Builder setFlags(ImmutableSortedSet<FlagDescription> flags);

    public Builder addFlag(FlagDescription flag) {
      flagsBuilder.add(flag);
      return this;
    }

    abstract CommandHelp autoBuild();

    public CommandHelp build() {
      // Work around the fact that AutoValue doesn't work with ImmutableSortedSet.
      setFlags(flagsBuilder.build());
      return autoBuild();
    }
  }

  public void printSummary(PrintStream output) {
    output.printf("%s command:%n", getCommandName());
    output.println(
        wrap(
            getCommandDescription().getShortDescription(),
            MAX_WIDTH,
            /* firstLineIndent= */ INDENT_SIZE,
            /* otherLinesIndent= */ INDENT_SIZE));
    output.println();
  }

  public void printDetails(PrintStream output) {
    output.println("Description:");
    output.println(
        wrap(
            getCommandDescription().getShortDescription(),
            MAX_WIDTH,
            /* firstLineIndent= */ INDENT_SIZE,
            /* otherLinesIndent= */ INDENT_SIZE));
    output.println();

    for (String additionalParagraph : getCommandDescription().getAdditionalParagraphs()) {
      output.println(
          wrap(
              additionalParagraph,
              MAX_WIDTH,
              /* firstLineIndent= */ INDENT_SIZE,
              /* otherLinesIndent= */ INDENT_SIZE));
      output.println();
    }

    output.println("Synopsis:");
    output.println(
        wrap(
            String.format("bundletool %s %s", getCommandName(), getSubCommandNamesAsString()),
            MAX_WIDTH,
            INDENT_SIZE,
            INDENT_SIZE));
    for (FlagDescription flag : getFlags()) {
      output.println(
          wrap(
              flag.getPrintForSyntax(),
              MAX_WIDTH,
              /* firstLineIndent= */ INDENT_SIZE * 2,
              /* otherLinesIndent= */ INDENT_SIZE * 3));
    }
    output.println();

    output.println("Flags:");
    for (FlagDescription flag : getFlags()) {
      output.println(
          wrap(
              flag.getPrintForDescription(),
              MAX_WIDTH,
              /* firstLineIndent= */ INDENT_SIZE,
              /* otherLinesIndent= */ INDENT_SIZE * 2));
      output.println();
    }
  }

  /** Full description of a command for the command-line help. */
  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  abstract static class CommandDescription {
    abstract String getShortDescription();

    abstract ImmutableList<String> getAdditionalParagraphs();

    static Builder builder() {
      return new AutoValue_CommandHelp_CommandDescription.Builder();
    }

    /** Builder for the {@link CommandDescription} object. */
    @AutoValue.Builder
    abstract static class Builder {
      /** Sets the short description of the command. */
      abstract Builder setShortDescription(String shortDescription);

      /** Same as {@link #setShortDescription(String)} but allowing formatted string. */
      @FormatMethod
      Builder setShortDescription(String shortDescriptionFormat, Object... args) {
        return setShortDescription(String.format(shortDescriptionFormat, args));
      }

      abstract ImmutableList.Builder<String> additionalParagraphsBuilder();

      /** Adds an additional paragraph of the command description. */
      Builder addAdditionalParagraph(String additionalParaghaph) {
        additionalParagraphsBuilder().add(additionalParaghaph);
        return this;
      }

      abstract CommandDescription build();
    }
  }

  /** Full description of a flag for the command-line help. */
  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  abstract static class FlagDescription implements Comparable<FlagDescription> {
    abstract String getFlagName();

    abstract boolean isOptional();

    abstract String getDescription();

    abstract Optional<String> getExampleValue();

    static Builder builder() {
      return new AutoValue_CommandHelp_FlagDescription.Builder().setOptional(false);
    }

    /** Builder for the {@link FlagDescription} object. */
    @AutoValue.Builder
    abstract static class Builder {
      /** Sets the name of the flag (without the "--"). */
      abstract Builder setFlagName(String flagName);

      /** Sets whether this flag is optional. */
      abstract Builder setOptional(boolean optional);

      /**
       * Sets an example of value that can be used for this flag.
       *
       * <p>The example value will be printed as {@code --flagname=<exampleValue>}
       *
       * <p>If not set, the flag will be printed as {@code --flagname}.
       */
      abstract Builder setExampleValue(String exampleValue);

      /** Sets the description of the flag. */
      abstract Builder setDescription(String description);

      /** Same as {@link #setDescription(String)} but allowing formatted string. */
      @FormatMethod
      Builder setDescription(@FormatString String description, Object... args) {
        return setDescription(String.format(description, args));
      }

      abstract FlagDescription build();
    }

    public String getPrintForSyntax() {
      StringBuilder builder = new StringBuilder();
      if (isOptional()) {
        builder.append("[");
      }
      builder.append("--").append(getFlagName());
      getExampleValue().ifPresent(value -> builder.append("=<").append(value).append(">"));
      if (isOptional()) {
        builder.append("]");
      }
      return builder.toString();
    }

    public String getPrintForDescription() {
      StringBuilder builder = new StringBuilder();
      builder.append("--").append(getFlagName()).append(": ");
      if (isOptional()) {
        builder.append("(Optional) ");
      }
      builder.append(getDescription());
      return builder.toString();
    }

    /** Order in which we display the flags: First mandatory flags, then ordered alphabetically. */
    @Override
    public int compareTo(FlagDescription o) {
      return ComparisonChain.start()
          .compareFalseFirst(this.isOptional(), o.isOptional())
          .compare(this.getFlagName(), o.getFlagName())
          .result();
    }
  }

  /**
   * Wraps {@code text} so it fits within {@code maxWidth} columns.
   *
   * <p>The first line will be indented by {@code firstLineIndent} spaces while all the other lines
   * will be indented by {@code otherLinesIndent} spaces.
   */
  @VisibleForTesting
  @CheckReturnValue
  static String wrap(String text, int maxWidth, int firstLineIndent, int otherLinesIndent) {
    int newLineIdx = text.indexOf(LINE_SEPARATOR);
    if (newLineIdx != -1) {
      // If a line break is found in the sentence, then we wrap the text recursively for each part.
      return wrap(text.substring(0, newLineIdx), maxWidth, firstLineIndent, otherLinesIndent)
          + LINE_SEPARATOR
          + wrap(
              text.substring(newLineIdx + LINE_SEPARATOR.length()),
              maxWidth,
              firstLineIndent,
              otherLinesIndent);
    }

    BreakIterator boundary = BreakIterator.getLineInstance(Locale.ENGLISH);
    boundary.setText(text);

    int start = boundary.first();
    int end = boundary.next();
    // The text wrapped as it will be returned.
    StringBuilder wrappedText = new StringBuilder();
    // The current line being built.
    StringBuilder line = new StringBuilder(Strings.repeat(" ", firstLineIndent));

    while (end != BreakIterator.DONE) {
      String word = text.substring(start, end);
      if (line.length() + word.trim().length() > maxWidth) {
        wrappedText
            .append(CharMatcher.whitespace().trimTrailingFrom(line.toString()))
            .append(LINE_SEPARATOR);
        line = new StringBuilder(Strings.repeat(" ", otherLinesIndent));
      }
      line.append(word);
      start = end;
      end = boundary.next();
    }
    wrappedText.append(line);
    return wrappedText.toString();
  }
}
