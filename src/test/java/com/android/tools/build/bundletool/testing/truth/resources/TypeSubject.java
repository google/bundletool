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

package com.android.tools.build.bundletool.testing.truth.resources;

import static com.google.common.collect.MoreCollectors.toOptional;
import static com.google.common.truth.Fact.fact;

import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Type;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Optional;

/** A subject for Type. */
public class TypeSubject extends Subject {

  private final Type actual;
  private FailureMetadata metadata;

  public TypeSubject(FailureMetadata metadata, Type actual) {
    super(metadata, actual);
    this.actual = actual;
    this.metadata = metadata;
  }

  public EntrySubject containingResource(String entryName) {
    Optional<Entry> foundEntry = findEntry(entryName);
    if (!foundEntry.isPresent()) {
      failWithoutActual(fact("expected to contain entry", entryName));
    }
    return new EntrySubject(metadata, foundEntry.get());
  }

  public void notContainingResource(String entryName) {
    Optional<Entry> foundEntry = findEntry(entryName);
    if (foundEntry.isPresent()) {
      failWithoutActual(fact("expected not to contain entry", entryName));
    }
  }

  private Optional<Entry> findEntry(String entryName) {
    return actual.getEntryList().stream()
        .filter(entry -> entry.getName().equals(entryName))
        .collect(toOptional());
  }
}
