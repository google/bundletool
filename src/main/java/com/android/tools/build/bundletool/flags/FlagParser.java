/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.flags;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility for flag parsing, specific to the Bundle Tool.
 *
 * <p>The flags follow the below convention:
 *
 * <p>[bundle-tool] [command1] [command2] .. [command-n] [--flag1] [--flag2=v2] [--flag3] [v3]..
 *    [--flagn] where:
 *
 * <ul>
 *   <li>commands: cannot start with "-".
 *   <li>flags: have to start with "--". They can have the format "--flag=value" or "--flag value",
 *       but when "=" is omitted, values cannot start with "--". A value does not have to be set
 *       and is empty string by default.
 * </ul>
 */
public class FlagParser {

  private static final String KEY_VALUE_SEPARATOR = "=";
  private static final Splitter KEY_VALUE_SPLITTER = Splitter.on(KEY_VALUE_SEPARATOR).limit(2);

  /**
   * Parses the given arguments populating the structures.
   *
   * @throws FlagParseException if the input does not represent parsable command line arguments
   */
  public ParsedFlags parse(String... args) {
    List<String> commands = new ArrayList<>();
    // Need to wrap it into a proper list implementation to be able to remove elements.
    List<String> argsToProcess = new ArrayList<>(Arrays.asList(args));
    while (argsToProcess.size() > 0 && !argsToProcess.get(0).startsWith("-")) {
      commands.add(argsToProcess.get(0));
      argsToProcess.remove(0);
    }
    return new ParsedFlags(ImmutableList.copyOf(commands), parseFlags(argsToProcess));
  }

  private ImmutableListMultimap<String, String> parseFlags(List<String> args) {
    ImmutableListMultimap.Builder<String, String> flagMap = ImmutableListMultimap.builder();
    String lastFlag = null;
    for (String arg : args) {
      if (arg.startsWith("--")) {
        if (lastFlag != null) {
          flagMap.put(lastFlag, "");
          lastFlag = null;
        }
        if (arg.contains(KEY_VALUE_SEPARATOR)) {
          List<String> segments = KEY_VALUE_SPLITTER.splitToList(arg);
          flagMap.put(segments.get(0).substring(2), segments.get(1));
        } else {
          lastFlag = arg.substring(2);
        }
      } else {
        if (lastFlag == null) {
          throw new FlagParseException(
              String.format("Syntax error: flags should start with -- (%s)", arg));
        } else {
          flagMap.put(lastFlag, arg);
          lastFlag = null;
        }
      }
    }
    if (lastFlag != null) {
      flagMap.put(lastFlag, "");
    }
    return flagMap.build();
  }

  /** Exception encapsulating any flag parsing errors. */
  public static class FlagParseException extends IllegalStateException {

    public FlagParseException(String message) {
      super(message);
    }
  }
}
