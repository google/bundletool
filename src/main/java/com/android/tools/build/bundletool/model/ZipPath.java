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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Ordering;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Path to an entry in a zip file.
 *
 * <p>The separator will always be a forward slash ("/") regardless of the platform being used.
 */
public final class ZipPath implements Path {

  private static final String SEPARATOR = "/";
  private static final Splitter SPLITTER = Splitter.on(SEPARATOR).omitEmptyStrings();
  private static final Joiner JOINER = Joiner.on(SEPARATOR);
  private static final ImmutableSet<String> FORBIDDEN_NAMES = ImmutableSet.of(".", "..");

  public static final ZipPath ROOT = ZipPath.create("");

  /**
   * List of parts of the path separated by the separator.
   *
   * <p>Note that this list can be empty when denoting the root of the zip.
   */
  protected final String[] names;

  // Cached hash code.
  private transient int hashCode;

  private ZipPath(List<String> names) {
    this(names.toArray(new String[0]));
  }

  private ZipPath(String[] names) {
    Arrays.stream(names)
        .forEach(
            name ->
                checkArgument(
                    !FORBIDDEN_NAMES.contains(name),
                    "Name '%s' is not supported inside path.",
                    name));
    this.names = names;
  }

  public static ZipPath create(String path) {
    checkNotNull(path, "Path cannot be null.");
    return new ZipPath(SPLITTER.splitToList(path));
  }

  @Override
  @CheckReturnValue
  public ZipPath resolve(Path p) {
    checkNotNull(p, "Path cannot be null.");
    ZipPath path = (ZipPath) p;
    return new ZipPath(ObjectArrays.concat(names, path.names, String.class));
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
    checkState(names.length > 0, "Root has not sibling.");
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
    checkArgument(from >= 0 && from < names.length);
    checkArgument(to >= 0 && to <= names.length);
    checkArgument(from < to);
    return new ZipPath(Arrays.copyOfRange(names, from, to));
  }

  @Override
  @Nullable
  @CheckReturnValue
  public ZipPath getParent() {
    if (names.length == 0) {
      return null;
    }
    return new ZipPath(Arrays.copyOf(names, names.length - 1));
  }

  @Override
  public int getNameCount() {
    return names.length;
  }

  @Override
  public ZipPath getRoot() {
    return ROOT;
  }

  @Override
  public ZipPath getName(int index) {
    checkArgument(index >= 0 && index < names.length);
    return ZipPath.create(names[index]);
  }

  @Override
  public boolean startsWith(Path p) {
    ZipPath path = (ZipPath) p;
    if (path.getNameCount() > getNameCount()) {
      return false;
    }

    for (int i = 0; i < path.getNameCount(); i++) {
      if (!path.names[i].equals(names[i])) {
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

    for (int i = 0; i < path.getNameCount(); i++) {
      if (!path.names[path.names.length - i - 1].equals(names[names.length - i - 1])) {
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
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = Arrays.hashCode(names);
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object path) {
    if (!(path instanceof ZipPath)) {
      return false;
    }
    return Arrays.equals(names, ((ZipPath) path).names);
  }

  @Override
  public int compareTo(Path other) {
    ZipPath path = (ZipPath) other;
    ComparisonChain chain = ComparisonChain.start();
    for (int i = 0; i < Math.max(getNameCount(), path.getNameCount()); i++) {
      chain =
          chain.compare(
              i < names.length ? names[i] : null,
              i < path.names.length ? path.names[i] : null,
              Ordering.natural().nullsFirst());
    }
    return chain.result();
  }

  @Override
  public String toString() {
    return JOINER.join(names);
  }

  @Override
  public ZipPath getFileName() {
    checkArgument(getNameCount() > 0, "Root does not have a file name.");
    return getName(getNameCount() - 1);
  }

  @Override
  public Iterator<Path> iterator() {
    return Arrays.stream(names).map(name -> (Path) ZipPath.create(name)).iterator();
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
