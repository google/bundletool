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

package com.android.tools.build.bundletool.device.activitymanager;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator on resource config string that allows saving and restoring the position.
 *
 * <p>Resource config string is the output of the specific Activity Manager shell command run on a
 * device: adb shell am get-config. The format resembles the resource strings used by the Android
 * Framework.
 *
 * <p>The iterator returns parts of resource strings separated by '-' which are referred to as
 * qualifiers (except for locales, see {@link LocaleParser}).
 */
public class ConfigStringIterator implements Iterator<String> {

  private static final Splitter SPLITTER = Splitter.on('-');
  private final ImmutableList<String> resourceQualifiers;
  private int iteratorIndex = -1;
  private int savedIndex = -1;

  public ConfigStringIterator(String resourceString) {
    this.resourceQualifiers = ImmutableList.copyOf(SPLITTER.split(resourceString));
  }

  /** Gets the current fragment. */
  public String getValue() {
    checkState(iteratorIndex >= 0 && iteratorIndex < resourceQualifiers.size());
    return resourceQualifiers.get(iteratorIndex);
  }

  @Override
  public boolean hasNext() {
    return iteratorIndex < resourceQualifiers.size() - 1;
  }

  @Override
  public String next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No more qualifiers in the string.");
    }
    iteratorIndex++;
    return getValue();
  }

  /** Saves the current position of the iterator. */
  public void savePosition() {
    this.savedIndex = this.iteratorIndex;
  }

  /**
   * Restores previously saved position of the iterator.
   *
   * <p>If {@link ConfigStringIterator#savePosition()} wasn't called it moves iterator to the
   * beginning of the string.
   */
  public void restorePosition() {
    this.iteratorIndex = this.savedIndex;
  }
}
