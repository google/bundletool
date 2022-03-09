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

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.model.Password;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a command-line flag of a specified type.
 *
 * @param <T> the type that the flag should be parsed as (e.g. string, file path etc).
 */
public abstract class Flag<T> {

  private static final Splitter KEY_VALUE_SPLITTER = Splitter.on(':').limit(2);

  /** Boolean flag holding a single value. */
  public static Flag<Boolean> booleanFlag(String name) {
    return new BooleanFlag(name);
  }

  /** Enum flag holding a single value. */
  public static <T extends Enum<T>> Flag<T> enumFlag(String name, Class<T> enumClass) {
    return new EnumFlag<>(name, enumClass);
  }

  /** Enum flag holding a set of comma-delimited values. */
  public static <T extends Enum<T>> Flag<ImmutableSet<T>> enumSet(String name, Class<T> enumClass) {
    return new SetFlag<>(new EnumFlag<>(name, enumClass));
  }

  public static <K, V> Flag<Map.Entry<K, V>> keyValue(
      String name, Class<K> keyClass, Class<V> valueClass) {
    return keyValueInternal(name, keyClass, valueClass);
  }

  /** Internal method returning the {@link KeyValueFlag} package-private class. */
  private static <K, V> KeyValueFlag<K, V> keyValueInternal(
      String name, Class<K> keyClass, Class<V> valueClass) {
    return new KeyValueFlag<>(name, flagForType(name, keyClass), flagForType(name, valueClass));
  }

  public static <K, V> Flag<ImmutableMap<K, V>> mapCollector(
      String name, Class<K> keyClass, Class<V> valueClass) {
    return new MapCollectorFlag<>(name, keyValueInternal(name, keyClass, valueClass));
  }

  /** Password flag holding a single value. */
  public static Flag<Password> password(String name) {
    return new PasswordFlag(name);
  }

  /** Path flag holding a single value. */
  public static Flag<Path> path(String name) {
    return new PathFlag(name);
  }

  /** Path flag holding a list of comma-delimited values. */
  public static Flag<ImmutableList<Path>> pathList(String name) {
    return new ListFlag<>(new PathFlag(name));
  }

  /** Path flag holding a set of comma-delimited values. */
  public static Flag<ImmutableSet<Path>> pathSet(String name) {
    return new SetFlag<>(new PathFlag(name));
  }

  /** Positive integer flag holding a single value. */
  public static Flag<Integer> positiveInteger(String name) {
    return new IntegerFlag(
        name, /* validator= */ n -> n > 0, /* errorMessage= */ "The value must be positive.");
  }

  /** Integer flag holding a single value. */
  public static Flag<Integer> nonNegativeInteger(String name) {
    return new IntegerFlag(
        name, /* validator= */ n -> n >= 0, /* errorMessage= */ "The value must be non-negative.");
  }

  /** String flag holding a single value. */
  public static Flag<String> string(String name) {
    return new StringFlag(name);
  }

  /** String flag that can occur arbitrary number of times, each time holding a single value. */
  public static Flag<ImmutableList<String>> stringCollector(String name) {
    return new CollectorFlag<>(new StringFlag(name));
  }

  /** String flag holding a list of comma-delimited values. */
  public static Flag<ImmutableList<String>> stringList(String name) {
    return new ListFlag<>(new StringFlag(name));
  }

  /** String flag holding a set of comma-delimited values. */
  public static Flag<ImmutableSet<String>> stringSet(String name) {
    return new SetFlag<>(new StringFlag(name));
  }

  // Implementation of the flags starts here.

  private static final Splitter ITEM_SPLITTER = Splitter.on(',');

  protected final String name;

  Flag(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Returns the flag value. Throws if the flag absent.
   *
   * @return the value of the flag
   * @throws RequiredFlagNotSetException if the flag was not set
   */
  public final T getRequiredValue(ParsedFlags flags) {
    return getValue(flags).orElseThrow(() -> new RequiredFlagNotSetException(name));
  }

  /**
   * Returns the flag value wrapped in the {@link Optional} class.
   *
   * <p>Empty {@link Optional} means that the flag was not specified.
   */
  public abstract Optional<T> getValue(ParsedFlags flags);

  /** Abstract class for flags that can hold only a single value. */
  abstract static class SingleValueFlag<T> extends Flag<T> {

    SingleValueFlag(String name) {
      super(name);
    }

    @Override
    public final Optional<T> getValue(ParsedFlags flags) {
      return flags.getFlagValue(name).map(this::parse);
    }

    protected abstract T parse(String value);
  }

  static class BooleanFlag extends SingleValueFlag<Boolean> {

    public BooleanFlag(String name) {
      super(name);
    }

    @Override
    protected Boolean parse(String value) {
      if (value != null) {
        if (value.isEmpty() || value.equalsIgnoreCase("true")) {
          return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
          return Boolean.FALSE;
        }
      }
      throw new FlagParseException(
          String.format(
              "Error while parsing the boolean flag --%s. Expected values [<empty>|true|false]"
                  + " but found '%s'.",
              name, MoreObjects.firstNonNull(value, "null")));
    }
  }

  static class EnumFlag<T extends Enum<T>> extends SingleValueFlag<T> {
    private final Class<T> enumType;

    public EnumFlag(String name, Class<T> enumType) {
      super(name);
      this.enumType = enumType;
    }

    @Override
    protected T parse(String value) {
      try {
        return Enum.valueOf(enumType, Ascii.toUpperCase(value));
      } catch (IllegalArgumentException e) {
        throw new FlagParseException(
            String.format(
                "Not a valid enum value '%s' of flag --%s. Expected one of: %s",
                value,
                name,
                Arrays.stream(enumType.getEnumConstants())
                    .map(T::name)
                    .collect(Collectors.joining(", "))));
      }
    }
  }

  static class IntegerFlag extends SingleValueFlag<Integer> {
    private final Predicate<Integer> validator;
    private final String errorMessage;

    public IntegerFlag(String name, Predicate<Integer> validator, String errorMessage) {
      super(name);
      this.validator = validator;
      this.errorMessage = errorMessage;
    }

    @Override
    protected Integer parse(String value) {
      try {
        Integer parsedValue = Integer.parseInt(value);
        if (!validator.test(parsedValue)) {
          throw new FlagParseException(
              String.format(
                  "Integer flag --%s has illegal value '%s'. %s.", name, value, errorMessage));
        }
        return parsedValue;
      } catch (NumberFormatException e) {
        throw new FlagParseException(
            String.format("Error while parsing value '%s' of the integer flag --%s.", value, name));
      }
    }
  }

  /** Flag that contains a key-value pair delimited by ':'. */
  static class KeyValueFlag<K, V> extends SingleValueFlag<Map.Entry<K, V>> {
    private final SingleValueFlag<K> keyFlag;
    private final SingleValueFlag<V> valueFlag;

    KeyValueFlag(String name, SingleValueFlag<K> keyFlag, SingleValueFlag<V> valueFlag) {
      super(name);
      this.keyFlag = keyFlag;
      this.valueFlag = valueFlag;
    }

    @Override
    protected Map.Entry<K, V> parse(String value) {
      List<String> keyValueList = KEY_VALUE_SPLITTER.splitToList(value);
      if (keyValueList.size() != 2) {
        throw new FlagParseException(
            String.format("Values of flag --%s must contain ':', but found '%s'.", name, value));
      }

      return new Map.Entry<K, V>() {
        @Override
        public K getKey() {
          return keyFlag.parse(keyValueList.get(0));
        }

        @Override
        public V getValue() {
          return valueFlag.parse(keyValueList.get(1));
        }

        @Override
        public V setValue(V value) {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  /** Flag that can occur multiple times, each occurrence holding a single key-value pair. */
  static class MapCollectorFlag<K, V> extends Flag<ImmutableMap<K, V>> {
    private final KeyValueFlag<K, V> keyValueFlag;

    MapCollectorFlag(String name, KeyValueFlag<K, V> keyValueFlag) {
      super(name);
      this.keyValueFlag = keyValueFlag;
    }

    @Override
    public final Optional<ImmutableMap<K, V>> getValue(ParsedFlags flags) {
      ImmutableList<String> rawValues = flags.getFlagValues(name);
      if (rawValues.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(parseValues(rawValues));
    }

    private ImmutableMap<K, V> parseValues(ImmutableList<String> rawValues) {
      return rawValues.stream()
          .filter(not(String::isEmpty))
          .map(keyValueFlag::parse)
          .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
  }

  /**
   * Passwords can be passed in clear (using "pass:" prefix) or via a file (using "file:" prefix).
   *
   * <p>This is to match the behaviour from "apksigner".
   */
  static class PasswordFlag extends SingleValueFlag<Password> {

    public PasswordFlag(String name) {
      super(name);
    }

    @Override
    protected Password parse(String value) {
      return Password.createFromStringValue(value);
    }
  }

  static class PathFlag extends SingleValueFlag<Path> {

    public PathFlag(String name) {
      super(name);
    }

    @Override
    protected Path parse(String value) {
      return FileUtils.getPath(value);
    }
  }

  static class StringFlag extends SingleValueFlag<String> {

    public StringFlag(String name) {
      super(name);
    }

    @Override
    protected String parse(String value) {
      return value;
    }
  }

  static class ZipPathFlag extends SingleValueFlag<ZipPath> {

    public ZipPathFlag(String name) {
      super(name);
    }

    @Override
    protected ZipPath parse(String value) {
      return ZipPath.create(value);
    }
  }

  /** Flag that can occur multiple times, each occurrence holding a single value. */
  static class CollectorFlag<T> extends Flag<ImmutableList<T>> {
    private final SingleValueFlag<T> singleFlag;

    CollectorFlag(SingleValueFlag<T> singleFlag) {
      super(singleFlag.name);
      this.singleFlag = singleFlag;
    }

    @Override
    public final Optional<ImmutableList<T>> getValue(ParsedFlags flags) {
      ImmutableList<String> rawValues = flags.getFlagValues(name);
      if (rawValues.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(parseValues(rawValues));
    }

    private ImmutableList<T> parseValues(ImmutableList<String> rawValues) {
      return rawValues.stream()
          .filter(not(String::isEmpty))
          .map(singleFlag::parse)
          .collect(toImmutableList());
    }
  }

  /** Flag that can contain multiple comma-separated values. */
  static class ListFlag<T> extends SingleValueFlag<ImmutableList<T>> {
    private final SingleValueFlag<T> singleFlag;

    ListFlag(SingleValueFlag<T> singleFlag) {
      super(singleFlag.name);
      this.singleFlag = singleFlag;
    }

    @Override
    protected ImmutableList<T> parse(String value) {
      if (value.isEmpty()) {
        return ImmutableList.of();
      }
      return ITEM_SPLITTER.splitToList(value).stream()
          .map(singleFlag::parse)
          .collect(toImmutableList());
    }
  }

  /**
   * Flag that can contain multiple comma-separated values.
   *
   * <p>Duplicates are filtered out.
   */
  static class SetFlag<T> extends SingleValueFlag<ImmutableSet<T>> {
    private final SingleValueFlag<T> singleFlag;

    SetFlag(SingleValueFlag<T> singleFlag) {
      super(singleFlag.name);
      this.singleFlag = singleFlag;
    }

    @Override
    protected ImmutableSet<T> parse(String value) {
      if (value.isEmpty()) {
        return ImmutableSet.of();
      }
      return ITEM_SPLITTER.splitToList(value).stream()
          .map(singleFlag::parse)
          .collect(toImmutableSet());
    }
  }

  @SuppressWarnings("unchecked") // safe by definitions of the individual flag types
  private static <T> SingleValueFlag<T> flagForType(String name, Class<T> clazz) {
    if (clazz.equals(Boolean.class)) {
      return (SingleValueFlag<T>) new BooleanFlag(name);
    } else if (clazz.equals(Path.class)) {
      return (SingleValueFlag<T>) new PathFlag(name);
    } else if (clazz.equals(String.class)) {
      return (SingleValueFlag<T>) new StringFlag(name);
    } else if (clazz.equals(ZipPath.class)) {
      return (SingleValueFlag<T>) new ZipPathFlag(name);
    } else {
      throw new IllegalArgumentException(String.format("Unrecognized flag type '%s'.", clazz));
    }
  }

  /**
   * Exception thrown when a required flag value is attempted to be read.
   *
   * @see Flag#getRequiredValue(ParsedFlags).
   */
  public static final class RequiredFlagNotSetException extends FlagParseException {

    RequiredFlagNotSetException(String flagName) {
      super(String.format("Missing the required --%s flag.", flagName));
    }
  }
}
