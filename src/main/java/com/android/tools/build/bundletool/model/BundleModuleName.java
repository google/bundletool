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

package com.android.tools.build.bundletool.model;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Value-object that represents the name of a module with a bundle.
 *
 * <p>This class ensures that module names meet the required naming format.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class BundleModuleName implements Comparable<BundleModuleName> {

  public static final BundleModuleName BASE_MODULE_NAME = new AutoValue_BundleModuleName("base");

  // Do not use "." in the module name because it's used as a separator for split id.
  private static final Pattern MODULE_NAME_FORMAT = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

  static boolean isValid(String name) {
    return MODULE_NAME_FORMAT.matcher(name).matches();
  }

  public static BundleModuleName create(String name) {
    if (!isValid(name)) {
      throw InvalidBundleException.builder()
          .withUserMessage("Module names with special characters not supported: %s", name)
          .build();
    }
    return new AutoValue_BundleModuleName(name);
  }

  public abstract String getName();

  /** Returns the name satisfying Split APK requirements. */
  public String getNameForSplitId() {
    if (this.equals(BASE_MODULE_NAME)) {
      return "";
    }
    return getName();
  }

  @Override
  public final String toString() {
    return getName();
  }

  @Override
  public final int compareTo(BundleModuleName o) {
    return Comparator.comparing(BundleModuleName::getName).compare(this, o);
  }
}
