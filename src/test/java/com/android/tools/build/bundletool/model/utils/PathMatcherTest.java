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
package com.android.tools.build.bundletool.model.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.utils.PathMatcher.GlobPatternSyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PathMatcherTest {

  @Test
  public void testSingleStar() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("*.png");

    assertThat(globMatcher.matches("file.png")).isTrue();
    assertThat(globMatcher.matches("dir/file.png")).isFalse();
    assertThat(globMatcher.matches(".png")).isTrue();
  }

  @Test
  public void testSingleStar_withinDirectory() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("*dir/*.png");

    assertThat(globMatcher.matches("file.png")).isFalse();
    assertThat(globMatcher.matches("dir/file.png")).isTrue();
    assertThat(globMatcher.matches("otherdir/file.png")).isTrue();
    assertThat(globMatcher.matches("other/dir/file.png")).isFalse();
    assertThat(globMatcher.matches(".png")).isFalse();
  }

  @Test
  public void testDoubleStar() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("**.png");

    assertThat(globMatcher.matches("file.png")).isTrue();
    assertThat(globMatcher.matches("dir/file.png")).isTrue();
    assertThat(globMatcher.matches(".png")).isTrue();

    assertThat(globMatcher.matches("filepng")).isFalse();
    assertThat(globMatcher.matches("file")).isFalse();
  }

  @Test
  public void testDoubleStar_withinDirectory() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("dir**/*.png");

    assertThat(globMatcher.matches("dir/file.png")).isTrue();
    assertThat(globMatcher.matches("dir2/file.png")).isTrue();
    assertThat(globMatcher.matches("dir/dir2/file.png")).isTrue();

    assertThat(globMatcher.matches("dir.png")).isFalse();
  }

  @Test
  public void testQuestionMark() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("???");

    assertThat(globMatcher.matches("abc")).isTrue();
    assertThat(globMatcher.matches("...")).isTrue();
    assertThat(globMatcher.matches("???")).isTrue();

    assertThat(globMatcher.matches("abcd")).isFalse();
    assertThat(globMatcher.matches("ab")).isFalse();
  }

  @Test
  public void testCharacterSet() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("[ab]");

    assertThat(globMatcher.matches("a")).isTrue();
    assertThat(globMatcher.matches("b")).isTrue();

    assertThat(globMatcher.matches("c")).isFalse();
    assertThat(globMatcher.matches("[")).isFalse();
    assertThat(globMatcher.matches("ab")).isFalse();
    assertThat(globMatcher.matches("abc")).isFalse();
    assertThat(globMatcher.matches("[ab]")).isFalse();
  }

  @Test
  public void testCharacterSet_numberRange() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("[0-9]");

    assertThat(globMatcher.matches("0")).isTrue();
    assertThat(globMatcher.matches("5")).isTrue();
    assertThat(globMatcher.matches("9")).isTrue();

    assertThat(globMatcher.matches("00")).isFalse();
    assertThat(globMatcher.matches("99")).isFalse();
    assertThat(globMatcher.matches("0-9")).isFalse();
    assertThat(globMatcher.matches("[0-9]")).isFalse();
  }

  @Test
  public void testCharacterSet_letterRange() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("[A-Z]");

    assertThat(globMatcher.matches("A")).isTrue();
    assertThat(globMatcher.matches("R")).isTrue();
    assertThat(globMatcher.matches("Z")).isTrue();

    assertThat(globMatcher.matches("a")).isFalse();
    assertThat(globMatcher.matches("AA")).isFalse();
    assertThat(globMatcher.matches("ZZ")).isFalse();
    assertThat(globMatcher.matches("A-Z")).isFalse();
    assertThat(globMatcher.matches("[A-Z]")).isFalse();
  }

  @Test
  public void testCharacterSet_multipleRanges() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("[A-Za-z]");

    assertThat(globMatcher.matches("A")).isTrue();
    assertThat(globMatcher.matches("B")).isTrue();
    assertThat(globMatcher.matches("Z")).isTrue();
    assertThat(globMatcher.matches("a")).isTrue();
    assertThat(globMatcher.matches("b")).isTrue();
    assertThat(globMatcher.matches("z")).isTrue();

    assertThat(globMatcher.matches("0")).isFalse();
    assertThat(globMatcher.matches("AA")).isFalse();
    assertThat(globMatcher.matches("Za")).isFalse();
  }

  @Test
  public void testCharacterSet_caretEscaped() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("[^a]");
    assertThat(globMatcher.matches("a")).isTrue();
    assertThat(globMatcher.matches("^")).isTrue();

    assertThat(globMatcher.matches("b")).isFalse();
  }

  @Test
  public void testCharacterSet_withSlash_throws() {
    Exception expected =
        assertThrows(GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("[ab/]"));
    assertThat(expected)
        .hasMessageThat()
        .contains("Character '/' is not allowed within a character set");
    assertThat(expected).hasMessageThat().contains("at character 4");
  }

  @Test
  public void testCharacterSet_emptySet_throws() {
    Exception expected =
        assertThrows(GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("abc[]"));
    assertThat(expected).hasMessageThat().contains("Empty characters set");
    assertThat(expected).hasMessageThat().contains("at character 4");
  }

  @Test
  public void testCharacterSet_notClosed_throws() {
    Exception expected =
        assertThrows(GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("a[bc"));
    assertThat(expected).hasMessageThat().contains("No matching ']' found");
    assertThat(expected).hasMessageThat().contains("at character 2");
  }

  @Test
  public void testCharacterSet_onlyClosed_throws() {
    Exception expected =
        assertThrows(GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("ab]c"));
    assertThat(expected).hasMessageThat().contains("No matching '[' found");
    assertThat(expected).hasMessageThat().contains("at character 3");
  }

  @Test
  public void testGroup() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("{temp*,tmp*}");

    assertThat(globMatcher.matches("temp")).isTrue();
    assertThat(globMatcher.matches("temporary")).isTrue();
    assertThat(globMatcher.matches("tmp")).isTrue();
    assertThat(globMatcher.matches("tmpfile")).isTrue();

    assertThat(globMatcher.matches("{tmp}")).isFalse();
    assertThat(globMatcher.matches("tmp/file")).isFalse();
  }

  @Test
  public void testGroup_emptyPart() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("file{,.png}");

    assertThat(globMatcher.matches("file")).isTrue();
    assertThat(globMatcher.matches("file.png")).isTrue();

    assertThat(globMatcher.matches("fileapng")).isFalse();
    assertThat(globMatcher.matches("file,")).isFalse();
  }

  @Test
  public void testGroup_characterSetsWithinGroup() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("file.{[0-9],[a-z][0-9]}");

    assertThat(globMatcher.matches("file.0")).isTrue();
    assertThat(globMatcher.matches("file.1")).isTrue();
    assertThat(globMatcher.matches("file.b2")).isTrue();

    assertThat(globMatcher.matches("file.a")).isFalse();
    assertThat(globMatcher.matches("file.11")).isFalse();
  }

  @Test
  public void testNestedGroup_throws() {
    Exception expected =
        assertThrows(
            GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("{tmp{1,2},temp}"));
    assertThat(expected).hasMessageThat().contains("Cannot nest groups");
    assertThat(expected).hasMessageThat().contains("at character 5");
  }

  @Test
  public void testGroupNotClosed_throws() {
    Exception expected =
        assertThrows(
            GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("a{tmp,temp"));
    assertThat(expected).hasMessageThat().contains("No matching '}' found");
    assertThat(expected).hasMessageThat().contains("at character 2");
  }

  @Test
  public void testGroupOnlyClosed_throws() {
    Exception expected =
        assertThrows(GlobPatternSyntaxException.class, () -> PathMatcher.createFromGlob("ab}c"));
    assertThat(expected).hasMessageThat().contains("No matching '{' found");
    assertThat(expected).hasMessageThat().contains("at character 3");
  }

  @Test
  public void testMultipleGroups() {
    PathMatcher pathMatcher = PathMatcher.createFromGlob("{a,b}{c,d}");
    assertThat(pathMatcher.matches("ac")).isTrue();
    assertThat(pathMatcher.matches("ad")).isTrue();
    assertThat(pathMatcher.matches("bc")).isTrue();
    assertThat(pathMatcher.matches("bd")).isTrue();

    assertThat(pathMatcher.matches("aa")).isFalse();
  }

  @Test
  public void testSpecialRegexpCharactersAreEscaped() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("<(^-=$!|)+.>");

    assertThat(globMatcher.matches("<(^-=$!|)+.>")).isTrue();
  }

  @Test
  public void testEscapeSpecialCharacters() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("\\*.png");
    assertThat(globMatcher.matches("*.png")).isTrue();
    assertThat(globMatcher.matches("file.png")).isFalse();

    globMatcher = PathMatcher.createFromGlob("\\?.png");
    assertThat(globMatcher.matches("?.png")).isTrue();
    assertThat(globMatcher.matches("f.png")).isFalse();

    globMatcher = PathMatcher.createFromGlob("\\\\?png");
    assertThat(globMatcher.matches("\\.png")).isTrue();
    assertThat(globMatcher.matches("\\png")).isFalse();
  }

  @Test
  public void testNegation() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("a[!bc]*");

    assertThat(globMatcher.matches("ad")).isTrue();
    assertThat(globMatcher.matches("adb")).isTrue();

    assertThat(globMatcher.matches("ab")).isFalse();
    assertThat(globMatcher.matches("abc")).isFalse();
    assertThat(globMatcher.matches("ac")).isFalse();
  }

  @Test
  public void testUnicodeCharacters() {
    PathMatcher globMatcher = PathMatcher.createFromGlob("emoji.\uD83D\uDE00");

    assertThat(globMatcher.matches("emoji.\uD83D\uDE00")).isTrue();
  }

  @Test
  public void testCommaWithinCharacterSetWithinGroup() {
    PathMatcher pathMatcher = PathMatcher.createFromGlob("{ab,[ab,]}");

    assertThat(pathMatcher.matches(",")).isTrue();
    assertThat(pathMatcher.matches("ab")).isTrue();
    assertThat(pathMatcher.matches("a")).isTrue();

    assertThat(pathMatcher.matches("ab,")).isFalse();
    assertThat(pathMatcher.matches("a,")).isFalse();
  }

  @Test
  public void testComplex() {
    PathMatcher pathMatcher = PathMatcher.createFromGlob("a{b[cd]*/e,[fg1-9]/h,k**.p}i");

    assertThat(pathMatcher.matches("abc/ei")).isTrue();
    assertThat(pathMatcher.matches("abdxx/ei")).isTrue();
    assertThat(pathMatcher.matches("af/hi")).isTrue();
    assertThat(pathMatcher.matches("a5/hi")).isTrue();
    assertThat(pathMatcher.matches("ak.pi")).isTrue();
    assertThat(pathMatcher.matches("ak/l/.pi")).isTrue();

    assertThat(pathMatcher.matches("abc/x/ei")).isFalse();
    assertThat(pathMatcher.matches("a/ei")).isFalse();
    assertThat(pathMatcher.matches("a/0hi")).isFalse();
  }
}
