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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class to match a file path against a glob pattern.
 *
 * <p>In a glob pattern: <u>
 * <li>{@code *} matches anything without crossing the directory boundary.
 * <li>{@code **} matches anything crossing the directory boundary.
 * <li>{@code ?} matches exactly one character.
 * <li>{@code [...]} matches the set of characters inside the square brackets. Ranges such as "a-z",
 *     "A-Z" and "0-9" are also supported. The character "/" (forward slash) is not allowed within
 *     square brackets.
 * <li><code>{ ... , ... }</code> matches any of the sub-patterns separated by commas in between the
 *     curly braces.</u>
 */
public final class PathMatcher {

  /** Special characters interpreted by regexp engines. */
  private static final ImmutableSet<Character> REGEXP_SPECIAL_CHARS =
      "<([{\\^-=$!|]})?*+.>".chars().mapToObj(c -> (char) c).collect(toImmutableSet());

  private final Pattern regexpPattern;

  private PathMatcher(Pattern regexpPattern) {
    this.regexpPattern = regexpPattern;
  }

  /** Builds an instance of {@link PathMatcher} that will match the given globPattern pattern. */
  public static PathMatcher createFromGlob(String globPattern) {
    try {
      Pattern regexpPattern = Pattern.compile(convertGlobToRegexp(globPattern));
      return new PathMatcher(regexpPattern);
    } catch (PatternSyntaxException e) {
      throw new GlobPatternSyntaxException(globPattern, e);
    }
  }

  public boolean matches(String input) {
    return regexpPattern.matcher(input).matches();
  }

  private static String convertGlobToRegexp(String globPattern) {
    StringBuilder regexpBuilder = new StringBuilder().append('^');

    boolean inGroup = false;
    int openingGroupIdx = 0;
    int i = 0;
    while (i < globPattern.length()) {
      switch (globPattern.charAt(i)) {
        case '\\':
          if (i == globPattern.length() - 1) {
            throw new GlobPatternSyntaxException("No character to escape.", globPattern, i);
          }
          regexpBuilder.append('\\').append(globPattern.charAt(i + 1));
          i++;
          break;

        case '*':
          if (i + 1 < globPattern.length() && globPattern.charAt(i + 1) == '*') {
            i++;
            regexpBuilder.append(".*?");
          } else {
            regexpBuilder.append("[^/]*");
          }
          break;

        case '?':
          regexpBuilder.append(".");
          break;

        case '[':
          int openBracketIdx = i;
          regexpBuilder.append('[');

          i++;
          char nextChar = i < globPattern.length() ? globPattern.charAt(i) : 0;
          if (nextChar == '^') {
            regexpBuilder.append('\\');
          } else if (nextChar == '!') {
            regexpBuilder.append('^');
          }

          while (i < globPattern.length() && globPattern.charAt(i) != ']') {
            char currentChar = globPattern.charAt(i);
            if (currentChar == '/') {
              throw new GlobPatternSyntaxException(
                  "Character '/' is not allowed within a character set", globPattern, i);
            }
            regexpBuilder.append(globPattern.charAt(i));
            i++;
          }
          if (i == globPattern.length()) {
            throw new GlobPatternSyntaxException(
                "No matching ']' found.", globPattern, openBracketIdx);
          }
          if (i == openBracketIdx + 1) {
            throw new GlobPatternSyntaxException(
                "Empty characters set.", globPattern, openBracketIdx);
          }
          regexpBuilder.append(globPattern.charAt(i));
          break;

        case '{':
          if (inGroup) {
            throw new GlobPatternSyntaxException("Cannot nest groups.", globPattern, i);
          }
          openingGroupIdx = i;
          inGroup = true;
          regexpBuilder.append("(?:");
          break;

        case '}':
          if (!inGroup) {
            throw new GlobPatternSyntaxException("No matching '{' found.", globPattern, i);
          }
          regexpBuilder.append(')');
          inGroup = false;
          break;

        case ']':
          throw new GlobPatternSyntaxException("No matching '[' found.", globPattern, i);

        case ',':
          if (inGroup) {
            regexpBuilder.append('|');
          } else {
            regexpBuilder.append(',');
          }
          break;

        default:
          char currentChar = globPattern.charAt(i);
          if (REGEXP_SPECIAL_CHARS.contains(currentChar)) {
            regexpBuilder.append('\\');
          }
          regexpBuilder.append(currentChar);
      }

      i++;
    }

    if (inGroup) {
      throw new GlobPatternSyntaxException("No matching '}' found.", globPattern, openingGroupIdx);
    }

    return regexpBuilder.append('$').toString();
  }

  /** Exception indicating that a glob pattern could not be parsed. */
  public static class GlobPatternSyntaxException extends RuntimeException {

    private GlobPatternSyntaxException(String message, String globPattern, int index) {
      super(
          String.format(
              "Unable to parse glob pattern '%s' at character %d. Error: %s",
              globPattern, index + 1, message));
    }

    private GlobPatternSyntaxException(String globPattern, Throwable cause) {
      super(String.format("Unable to parse glob pattern '%s'.", globPattern), cause);
    }
  }
}
