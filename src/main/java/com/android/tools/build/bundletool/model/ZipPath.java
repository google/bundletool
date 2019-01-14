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
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.Iterator;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Path to an entry in a zip file.
 *
 * <p>The separator will always be a forward slash ("/") regardless of the platform being used.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ZipPath implements Path {

  private static final String SEPARATOR = "/";
  private static final Splitter SPLITTER = Splitter.on(SEPARATOR).omitEmptyStrings();
  private static final Joiner JOINER = Joiner.on(SEPARATOR);
  private static final ImmutableSet<String> FORBIDDEN_NAMES = ImmutableSet.of(".", "..");

  public static final ZipPath ROOT = ZipPath.create("");

  // Constructor with restricted visibility to avoid subclassing and ensure immutability.
  ZipPath() {}

  /**
   * List of parts of the path separated by the separator.
   *
   * <p>Note that this list can be empty when denoting the root of the zip.
   */
  abstract ImmutableList<String> getNames();

  public static ZipPath create(String path) {
    checkNotNull(path, "Path cannot be null.");
    return create(ImmutableList.copyOf(SPLITTER.splitToList(path)));
  }

  private static ZipPath create(ImmutableList<String> names) {
    names.forEach(
        name ->
            checkArgument(
                !FORBIDDEN_NAMES.contains(name), "Name '%s' is not supported inside path.", name));
    return new AutoValue_ZipPath(names);
  }

  @Override
  @CheckReturnValue
  public ZipPath resolve(Path p) {
    checkNotNull(p, "Path cannot be null.");
    ZipPath path = (ZipPath) p;
    return create(
        ImmutableList.<String>builder().addAll(getNames()).addAll(path.getNames()).build());
  }

  @Override
  @CheckReturnValue
  public ZipPath resolve(String path) {
    return resolve(ZipPath.create(path));
  }

  @Override
  @CheckReturnValue
  public ZipPath resolveSibling(Path path) {
    checkNotNull(path, "Path cannot be null.");
    checkState(!getNames().isEmpty(), "Root has not sibling.");
    return getParent().resolve(path);
  }

  @Override
  @CheckReturnValue
  public ZipPath resolveSibling(String path) {
    return resolveSibling(ZipPath.create(path));
  }

  @Override
  @CheckReturnValue
  public ZipPath subpath(int from, int to) {
    checkArgument(from >= 0 && from < getNames().size());
    checkArgument(to >= 0 && to <= getNames().size());
    checkArgument(from < to);
    return create(getNames().subList(from, to));
  }

  @Override
  @Nullable
  @CheckReturnValue
  public ZipPath getParent() {
    if (getNames().isEmpty()) {
      return null;
    }
    return create(getNames().subList(0, getNames().size() - 1));
  }

  @Override
  public int getNameCount() {
    return getNames().size();
  }

  @Override
  public ZipPath getRoot() {
    return ROOT;
  }

  @Override
  public ZipPath getName(int index) {
    checkArgument(index >= 0 && index < getNames().size());
    return ZipPath.create(getNames().get(index));
  }

  @Override
  public boolean startsWith(Path p) {
    ZipPath path = (ZipPath) p;
    if (path.getNameCount() > getNameCount()) {
      return false;
    }

    ImmutableList<String> names = getNames();
    ImmutableList<String> otherNames = path.getNames();
    for (int i = 0; i < path.getNameCount(); i++) {
      if (!otherNames.get(i).equals(names.get(i))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean startsWith(String p) {
    return startsWith(ZipPath.create(p));
  }

  @Override
  public boolean endsWith(Path p) {
    ZipPath path = (ZipPath) p;
    if (path.getNameCount() > getNameCount()) {
      return false;
    }

    ImmutableList<String> names = getNames();
    ImmutableList<String> otherNames = path.getNames();
    for (int i = 0; i < path.getNameCount(); i++) {
      if (!otherNames.get(otherNames.size() - i - 1).equals(names.get(names.size() - i - 1))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean endsWith(String p) {
    return endsWith(ZipPath.create(p));
  }

  @Override
  public final int compareTo(Path other) {
    return Comparators.lexicographical(Comparator.<String>naturalOrder())
        .compare(getNames(), ((ZipPath) other).getNames());
  }

  /** Returns the path as used in the zip file. */
  @Override
  public final String toString() {
    return JOINER.join(getNames());
  }

  @Override
  public ZipPath getFileName() {
    checkArgument(getNameCount() > 0, "Root does not have a file name.");
    return getName(getNameCount() - 1);
  }

  @Override
  public Iterator<Path> iterator() {
    return getNames().stream().map(name -> (Path) ZipPath.create(name)).iterator();
  }

  @Override
  @CheckReturnValue
  public ZipPath normalize() {
    // We don't support ".." or ".", so the current path is already normalized.
    return this;
  }

  @Override
  public ZipPath toRealPath(LinkOption... options) {
    return this;
  }

  @Override
  public ZipPath toAbsolutePath() {
    return this;
  }

  @Override
  public boolean isAbsolute() {
    return true;
  }

  @Override
  @CheckReturnValue
  public ZipPath relativize(Path p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(
      WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public File toFile() {
    throw new UnsupportedOperationException("Zip entries don't match to a file on disk.");
  }

  @Override
  public URI toUri() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }
}
