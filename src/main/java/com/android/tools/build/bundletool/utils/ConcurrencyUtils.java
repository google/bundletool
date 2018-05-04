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

package com.android.tools.build.bundletool.utils;

import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

/** Utility methods for working with concurrent code. */
public final class ConcurrencyUtils {

  /** Retrieves results of all futures, if they succeed. If any fails, eagerly throws. */
  public static <T> ImmutableList<T> waitForAll(Iterable<ListenableFuture<T>> futures) {
    try {
      return ImmutableList.copyOf(Futures.allAsList(futures).get());
    } catch (InterruptedException | ExecutionException e) {
      throw new CommandExecutionException("A concurrent operation failed.", e);
    }
  }

  private ConcurrencyUtils() {}
}
