/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.device;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the output of the "pm list packages --show-versioncode" ADB shell command. */
public final class PackagesParser {

  private static final Pattern PACKAGE_AND_VERSION =
      Pattern.compile("package:(?<package>.+?) versionCode:(?<version>\\d+)");

  private final boolean isApex;

  public PackagesParser(boolean isApex) {
    this.isApex = isApex;
  }

  /** Parses output of "pm list packages". */
  public ImmutableSet<InstalledPackageInfo> parse(ImmutableList<String> listPackagesOutput) {
    // The command lists the packages in the form
    // package:com.google.a versionCode:123
    // package:com.google.b versionCode:456
    // ...
    return listPackagesOutput.stream()
        .map(PACKAGE_AND_VERSION::matcher)
        .filter(Matcher::matches)
        .map(this::processMatch)
        .collect(toImmutableSet());
  }

  private InstalledPackageInfo processMatch(Matcher matcher) {
    return InstalledPackageInfo.create(
        matcher.group("package"), Long.parseLong(matcher.group("version")), isApex);
  }

  /** Represents an installed package and its version code. */
  @AutoValue
  public abstract static class InstalledPackageInfo {
    static InstalledPackageInfo create(String packageName, long versionCode, boolean isApex) {
      return new AutoValue_PackagesParser_InstalledPackageInfo(packageName, versionCode, isApex);
    }

    public abstract String getPackageName();

    public abstract long getVersionCode();

    public abstract boolean isApex();
  }
}
