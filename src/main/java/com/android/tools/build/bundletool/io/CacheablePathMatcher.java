/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.build.bundletool.io;

import com.android.tools.build.bundletool.model.utils.PathMatcher;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;

/** Path matcher which caches matching results after first invocation. */
class CacheablePathMatcher {

  private final ImmutableList<PathMatcher> pathMatchers;
  private final Map<String, Boolean> cache = new HashMap<>();

  CacheablePathMatcher(ImmutableList<PathMatcher> pathMatchers) {
    this.pathMatchers = pathMatchers;
  }

  boolean matches(String path) {
    return cache.computeIfAbsent(
        path, p -> pathMatchers.stream().anyMatch(matcher -> matcher.matches(p)));
  }
}
