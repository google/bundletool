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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandHelpTest {

  @Test
  public void printSummary_correctStructureAndIndentation() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    CommandHelp.builder()
        .setCommandName("MyCommandName")
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Description of the command. This is a short summary of what the command does "
                        + "and what the output is.")
                .addAdditionalParagraph("Additional paragraphs are ignored in summary.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName("foo")
                .setExampleValue("foo-value")
                .setDescription("Flags are ignored in summary.")
                .build())
        .build()
        .printSummary(new PrintStream(output));

    assertThat(asLines(output.toString()))
        .isEqualTo(
            lineList(
                "MyCommandName command:",
                "    Description of the command. This is a short summary of what the command does",
                "    and what the output is.",
                "",
                ""));
  }

  @Test
  public void printHelp_correctStructureAndIndentation() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    CommandHelp.builder()
        .setCommandName("MyCommandName")
        .setSubCommandNames(ImmutableList.of("SubCommand1", "SubCommand2"))
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Description of the command. This is a short summary of what the command does "
                        + "and what the output is.")
                .addAdditionalParagraph(
                    "The next paragraphs of the command description are shown only in detailed "
                        + "help of the command.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName("foo")
                .setExampleValue("foo-value")
                .setDescription(
                    "This is a very long explanation of what the flag foo is and how it should be "
                        + "used, to ensure that callers will know how to use it.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName("bar")
                .setOptional(true)
                .setDescription(
                    "This is a very long explanation of what the flag bar is and how it should be "
                        + "used, to ensure that callers will know how to use it.")
                .build())
        .build()
        .printDetails(new PrintStream(output));

    assertThat(asLines(output.toString()))
        .isEqualTo(
            lineList(
                "Description:",
                "    Description of the command. This is a short summary of what the command does",
                "    and what the output is.",
                "",
                "    The next paragraphs of the command description are shown only in detailed",
                "    help of the command.",
                "",
                "Synopsis:",
                "    bundletool MyCommandName <SubCommand1|SubCommand2>",
                "        --foo=<foo-value>",
                "        [--bar]",
                "",
                "Flags:",
                "    --foo: This is a very long explanation of what the flag foo is and how it",
                "        should be used, to ensure that callers will know how to use it.",
                "",
                "    --bar: (Optional) This is a very long explanation of what the flag bar is",
                "        and how it should be used, to ensure that callers will know how to use",
                "        it.",
                "",
                ""));
  }

  @Test
  public void flagDescriptionPrintForSyntax_notOptional_noExampleValue() {
    FlagDescription flagDesc =
        FlagDescription.builder().setFlagName("foo").setDescription("This is foo.").build();

    assertThat(flagDesc.getPrintForSyntax()).isEqualTo("--foo");
  }

  @Test
  public void flagDescriptionPrintForSyntax_optional_noExampleValue() {
    FlagDescription flagDesc =
        FlagDescription.builder()
            .setFlagName("foo")
            .setOptional(true)
            .setDescription("This is foo.")
            .build();

    assertThat(flagDesc.getPrintForSyntax()).isEqualTo("[--foo]");
  }

  @Test
  public void flagDescriptionPrintForSyntax_notOptional_withExampleValue() {
    FlagDescription flagDesc =
        FlagDescription.builder()
            .setFlagName("foo")
            .setExampleValue("bar")
            .setDescription("This is foo.")
            .build();

    assertThat(flagDesc.getPrintForSyntax()).isEqualTo("--foo=<bar>");
  }

  @Test
  public void flagDescriptionPrintForSyntax_optional_withExampleValue() {
    FlagDescription flagDesc =
        FlagDescription.builder()
            .setFlagName("foo")
            .setExampleValue("bar")
            .setOptional(true)
            .setDescription("This is foo.")
            .build();

    assertThat(flagDesc.getPrintForSyntax()).isEqualTo("[--foo=<bar>]");
  }

  @Test
  public void flagDescriptionPrintForDescription_notOptional() {
    FlagDescription flagDesc =
        FlagDescription.builder().setFlagName("foo").setDescription("This is foo.").build();

    assertThat(flagDesc.getPrintForDescription()).isEqualTo("--foo: This is foo.");
  }

  @Test
  public void flagDescriptionPrintForDescription_optional() {
    FlagDescription flagDesc =
        FlagDescription.builder()
            .setFlagName("foo")
            .setOptional(true)
            .setDescription("This is foo.")
            .build();

    assertThat(flagDesc.getPrintForDescription()).isEqualTo("--foo: (Optional) This is foo.");
  }

  @Test
  public void wrap_shortText_noIndentation() {
    assertThat(
            CommandHelp.wrap(
                "Hello World",
                /* maxWidth= */ 80,
                /* firstLineIndent= */ 0,
                /* otherLinesIndent= */ 0))
        .isEqualTo("Hello World");
  }

  @Test
  public void wrap_shortText_indentationFirstLine() {
    assertThat(
            CommandHelp.wrap(
                "Hello World",
                /* maxWidth= */ 80,
                /* firstLineIndent= */ 4,
                /* otherLinesIndent= */ 0))
        .isEqualTo("    Hello World");
  }

  @Test
  public void wrap_shortText_indentationOtherLines() {
    assertThat(
            CommandHelp.wrap(
                "Hello World",
                /* maxWidth= */ 80,
                /* firstLineIndent= */ 0,
                /* otherLinesIndent= */ 4))
        .isEqualTo("Hello World");
  }

  @Test
  public void wrap_textOnMultipleLines_noIndentation() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    "a b c d e f g h i j",
                    /* maxWidth= */ 5,
                    /* firstLineIndent= */ 0,
                    /* otherLinesIndent= */ 0)))
        .isEqualTo(
            lineList(
                "a b c", /* 1st line */
                "d e f", /* 2nd line */
                "g h i", /* 3rd line */
                "j" /* 4th line */));
  }

  @Test
  public void wrap_textOnMultipleLines_firstLineIndentation() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    "a b c d e f g h i j",
                    /* maxWidth= */ 5,
                    /* firstLineIndent= */ 1,
                    /* otherLinesIndent= */ 0)))
        .isEqualTo(
            lineList(
                " a b", /* 1st line */
                "c d e", /* 2nd line */
                "f g h", /* 3rd line */
                "i j" /* 4th line */));
  }

  @Test
  public void wrap_textOnMultipleLines_otherLinesIndentation() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    "a b c d e f g h i j",
                    /* maxWidth= */ 5,
                    /* firstLineIndent= */ 0,
                    /* otherLinesIndent= */ 1)))
        .isEqualTo(
            lineList(
                "a b c", /* 1st line */
                " d e", /* 2nd line */
                " f g", /* 3rd line */
                " h i", /* 4th line */
                " j" /* 5th line */));
  }

  @Test
  public void wrap_textOnMultipleLines_mixedIndentation() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    "a b c d e f g h i j",
                    /* maxWidth= */ 7,
                    /* firstLineIndent= */ 1,
                    /* otherLinesIndent= */ 2)))
        .isEqualTo(
            lineList(
                " a b c", /* 1st line */
                "  d e f", /* 2nd line */
                "  g h i", /* 3rd line */
                "  j" /* 4th line */));
  }

  @Test
  public void wrap_longText_noIndentation() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    "This sentence has exactly 40 characters. "
                        + "This sentence has exactly 40 characters. "
                        + "This sentence has exactly 40 characters.",
                    /* maxWidth= */ 40,
                    /* firstLineIndent= */ 0,
                    /* otherLinesIndent= */ 0)))
        .isEqualTo(
            lineList(
                "This sentence has exactly 40 characters.",
                "This sentence has exactly 40 characters.",
                "This sentence has exactly 40 characters."));
  }

  @Test
  public void wrap_withLineBreaks() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    String.format("This line is longer%nthan%n40 characters but has line breaks."),
                    /* maxWidth= */ 40,
                    /* firstLineIndent= */ 0,
                    /* otherLinesIndent= */ 0)))
        .isEqualTo(lineList("This line is longer", "than", "40 characters but has line breaks."));
  }

  @Test
  public void wrap_withLineBreakAtEndOfText() {
    assertThat(
            asLines(
                CommandHelp.wrap(
                    String.format("This line ends with a line break.%n"),
                    /* maxWidth= */ 40,
                    /* firstLineIndent= */ 0,
                    /* otherLinesIndent= */ 0)))
        .isEqualTo(lineList("This line ends with a line break.", ""));
  }

  /**
   * Split a string into lines in a platform-agnostic way.
   *
   * <p>Note: There will be a trailing empty string if the input string ends with a line separator.
   * For example: asLines("foo\n") returns ["foo", ""]
   */
  private static List<String> asLines(String multiLineString) {
    return Splitter.onPattern("\\R").splitToList(multiLineString);
  }

  private static List<String> lineList(String... lines) {
    return Arrays.asList(lines);
  }
}
