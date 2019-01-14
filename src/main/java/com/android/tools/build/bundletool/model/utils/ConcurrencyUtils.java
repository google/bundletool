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

package com.android.tools.build.bundletool.model.utils;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Utility methods for working with concurrent code. */
public final class ConcurrencyUtils {

  /** Retrieves results of all futures, if they succeed. If any fails, eagerly throws. */
  public static <T> ImmutableList<T> waitForAll(Iterable<ListenableFuture<T>> futures) {
    return ImmutableList.copyOf(waitFor(Futures.allAsList(futures)));
  }

  public static <K, V> ImmutableMap<K, V> waitForAll(Map<K, ListenableFuture<V>> futures) {
    ImmutableMap.Builder<K, V> finishedMap = ImmutableMap.builder();
    for (Entry<K, ListenableFuture<V>> entry : futures.entrySet()) {
      finishedMap.put(entry.getKey(), waitFor(entry.getValue()));
    }
    return finishedMap.build();
  }

  public static <T> T waitFor(Future<T> future) {
    try {
      return future.get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw new UncheckedIOException(e.getCause().getMessage(), (IOException) e.getCause());
      } else if (e.getCause() instanceof UncheckedIOException) {
        throw new UncheckedIOException(
            e.getCause().getMessage(), ((UncheckedIOException) e.getCause()).getCause());
      } else if (e.getCause() instanceof CommandExecutionException) {
        throw new CommandExecutionException(e.getCause().getMessage(), e.getCause());
      } else {
        throw new RuntimeException(e.getMessage(), e);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("One operation was interrupted.", e);
    }
  }

  private ConcurrencyUtils() {}
}
