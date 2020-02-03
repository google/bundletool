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

import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.Type;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.errorprone.annotations.Immutable;

/** Represents an entry in a resource table. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ResourceTableEntry {

  public static ResourceTableEntry create(Package pkg, Type type, Entry entry) {
    return new AutoValue_ResourceTableEntry(pkg, type, entry);
  }

  public abstract Package getPackage();

  public abstract Type getType();

  public abstract Entry getEntry();

  @Memoized
  public ResourceId getResourceId() {
    return ResourceId.create(getPackage(), getType(), getEntry());
  }
}
