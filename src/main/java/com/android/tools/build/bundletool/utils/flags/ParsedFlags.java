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

package com.android.tools.build.bundletool.utils.flags;

import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.utils.flags.FlagParser.FlagParseException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the result of parsing the command line arguments of this invocation of the tool.
 *
 * <p>You should not need to use this class directly, flag values should be accessed via the {@code
 * Flag} class.
 *
 * <pre>
 * static Flag<String> MY_FLAG = Flag.string("myFlag")
 *
 * void execute(ParsedFlags flags) {
 *   Optional<String> flagValue = MY_FLAG.value(flags);
 *   ...
 * }
 * </pre>
 */
@AutoValue
public abstract class ParsedFlags {

  private final Set<String> accessedFlags = new HashSet<>();

  static ParsedFlags create(List<String> commands, ImmutableListMultimap<String, String> flags) {
    return new AutoValue_ParsedFlags(commands, flags);
  }

  /**
   * Returns the list of commands that were parsed.
   *
   * @return a list of the commands specified on the command line
   */
  public abstract List<String> getCommands();

  /**
   * Returns the first command provided on the command line if provided.
   *
   * <p>e.g. for "bundletool dump manifest --module=base", the command is 'dump' and the sub-command
   * is 'manifest'.
   */
  public Optional<String> getMainCommand() {
    return getSubCommand(0);
  }

  /**
   * Returns the second command provided on the command line if provided.
   *
   * <p>e.g. for "bundletool dump manifest --module=base", the command is 'dump' and the sub-command
   * is 'manifest'.
   */
  public Optional<String> getSubCommand() {
    return getSubCommand(1);
  }

  private Optional<String> getSubCommand(int index) {
    return getCommands().size() > index ? Optional.of(getCommands().get(index)) : Optional.empty();
  }

  protected abstract ImmutableListMultimap<String, String> getFlags();

  /**
   * Gets value of the flag, if it has been set.
   *
   * @throws FlagParseException if the flag has been set multiple times
   */
  Optional<String> getFlagValue(String name) {
    ImmutableList<String> values = getFlagValues(name);
    switch (values.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(values.get(0));
      default:
        throw new FlagParseException(String.format("Flag --%s has been set more than once.", name));
    }
  }

  ImmutableList<String> getFlagValues(String name) {
    accessedFlags.add(name);
    return getFlags().get(name);
  }

  public void checkNoUnknownFlags() {
    Set<String> unknownFlags = Sets.difference(getFlags().keySet(), accessedFlags);
    if (!unknownFlags.isEmpty()) {
      throw new UnknownFlagsException(unknownFlags);
    }
  }

  /** Exception throws when not all flags have been read. */
  public static class UnknownFlagsException extends FlagParseException {

    public UnknownFlagsException(Collection<String> flags) {
      super("Unrecognized flags: " + flags.stream().map(f -> "--" + f).collect(joining(", ")));
    }
  }
}
