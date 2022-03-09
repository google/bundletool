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
package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Comparator;
import javax.annotation.Nullable;

/**
 * Path to an entry in a zip file.
 *
 * <p>The separator will always be a forward slash ("/") regardless of the platform being used.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ZipPath implements Comparable<ZipPath> {

  private static final String SEPARATOR = "/";
  private static final Splitter SPLITTER = Splitter.on(SEPARATOR).omitEmptyStrings();
  private static final Joiner JOINER = Joiner.on(SEPARATOR);
  private static final ImmutableSet<String> FORBIDDEN_NAMES = ImmutableSet.of("", ".", "..");

  public static final ZipPath ROOT = ZipPath.create("");

  // Constructor with restricted visibility to avoid subclassing and ensure immutability.
  ZipPath() {}

  /**
   * List of parts of the path separated by the separator.
   *
   * <p>Note that this list can be empty when denoting the root of the zip.
   */
  public abstract ImmutableList<String> getNames();

  public static ZipPath create(String path) {
    checkNotNull(path, "Path cannot be null.");
    return create(ImmutableList.copyOf(SPLITTER.splitToList(path)));
  }

  public static ZipPath create(ImmutableList<String> names) {
    names.forEach(
        name -> {
          checkArgument(
              !name.contains("/"),
              "Name '%s' contains a forward slash and cannot be used in a path.",
              name);
          checkArgument(
              !FORBIDDEN_NAMES.contains(name), "Name '%s' is not supported inside path.", name);
        });
    return new AutoValue_ZipPath(names);
  }

  @CheckReturnValue
  public ZipPath resolve(ZipPath p) {
    checkNotNull(p, "Path cannot be null.");
    return create(ImmutableList.<String>builder().addAll(getNames()).addAll(p.getNames()).build());
  }

  @CheckReturnValue
  public ZipPath resolve(String path) {
    return resolve(ZipPath.create(path));
  }

  @CheckReturnValue
  public ZipPath resolveSibling(ZipPath path) {
    checkNotNull(path, "Path cannot be null.");
    checkState(!getNames().isEmpty(), "Root has not sibling.");
    return getParent().resolve(path);
  }

  @CheckReturnValue
  public ZipPath resolveSibling(String path) {
    return resolveSibling(ZipPath.create(path));
  }

  @CheckReturnValue
  public ZipPath subpath(int from, int to) {
    checkArgument(from >= 0 && from < getNames().size());
    checkArgument(to >= 0 && to <= getNames().size());
    checkArgument(from < to);
    return create(getNames().subList(from, to));
  }

  @Nullable
  @CheckReturnValue
  @Memoized
  public ZipPath getParent() {
    if (getNames().isEmpty()) {
      return null;
    }
    return create(getNames().subList(0, getNames().size() - 1));
  }

  public int getNameCount() {
    return getNames().size();
  }

  public ZipPath getRoot() {
    return ROOT;
  }

  public ZipPath getName(int index) {
    checkArgument(index >= 0 && index < getNames().size());
    return ZipPath.create(getNames().get(index));
  }

  public boolean startsWith(ZipPath p) {
    if (p.getNameCount() > getNameCount()) {
      return false;
    }

    ImmutableList<String> names = getNames();
    ImmutableList<String> otherNames = p.getNames();
    for (int i = 0; i < p.getNameCount(); i++) {
      if (!otherNames.get(i).equals(names.get(i))) {
        return false;
      }
    }

    return true;
  }

  public boolean startsWith(String p) {
    return startsWith(ZipPath.create(p));
  }

  public boolean endsWith(ZipPath p) {
    if (p.getNameCount() > getNameCount()) {
      return false;
    }

    ImmutableList<String> names = getNames();
    ImmutableList<String> otherNames = p.getNames();
    for (int i = 0; i < p.getNameCount(); i++) {
      if (!otherNames.get(otherNames.size() - i - 1).equals(names.get(names.size() - i - 1))) {
        return false;
      }
    }

    return true;
  }

  public boolean endsWith(String p) {
    return endsWith(ZipPath.create(p));
  }

  @Override
  public final int compareTo(ZipPath other) {
    return Comparators.lexicographical(Comparator.<String>naturalOrder())
        .compare(getNames(), other.getNames());
  }

  /** Returns the path as used in the zip file. */
  @Memoized
  @Override
  public String toString() {
    return JOINER.join(getNames());
  }

  @Memoized
  public ZipPath getFileName() {
    checkArgument(getNameCount() > 0, "Root does not have a file name.");
    return getName(getNameCount() - 1);
  }
}
