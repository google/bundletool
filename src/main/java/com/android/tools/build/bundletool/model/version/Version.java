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
package com.android.tools.build.bundletool.model.version;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.Immutable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Generic version following a 3-digit scheme.
 *
 * <p>Matches the pattern "<major>.<minor>.<revision>[-<qualifier>]", e.g. "1.1.0" or
 * "1.2.1-alpha03".
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class Version implements Comparable<Version> {

  private static final Pattern VERSION_REGEXP =
      Pattern.compile(
          "^"
              + "(?<major>\\d+?)\\."
              + "(?<minor>\\d+?)\\."
              + "(?<revision>\\d+?)"
              + "(-(?<qualifier>.+))?"
              + "$");

  abstract String getFullVersion();

  abstract int getMajorVersion();

  abstract int getMinorVersion();

  abstract int getRevisionVersion();

  @Nullable
  abstract String getQualifier();

  /**
   * Builds a custom version instance.
   *
   * @throws InvalidBundleException if the version cannot be parsed
   */
  public static Version of(String version) {
    Matcher matcher = VERSION_REGEXP.matcher(version);
    checkArgument(
        matcher.matches(),
        "Version must match the format '<major>.<minor>.<revision>[-<qualifier>]', but "
            + "found '%s'.",
        version);

    return Version.builder()
        .setFullVersion(version)
        .setMajorVersion(Integer.parseInt(matcher.group("major")))
        .setMinorVersion(Integer.parseInt(matcher.group("minor")))
        .setRevisionVersion(Integer.parseInt(matcher.group("revision")))
        .setQualifier(matcher.group("qualifier"))
        .build();
  }

  @Override
  public int compareTo(Version otherVersion) {
    return ComparisonChain.start()
        .compare(getMajorVersion(), otherVersion.getMajorVersion())
        .compare(getMinorVersion(), otherVersion.getMinorVersion())
        .compare(getRevisionVersion(), otherVersion.getRevisionVersion())
        .compare(
            getQualifier(),
            otherVersion.getQualifier(),
            // Make sure that "dev" comes first, and that stable versions (with no qualifier) come
            // last. Otherwise, use lexicographical order.
            Ordering.natural().onResultOf((String q) -> q.replaceAll("^dev$", "")).nullsLast())
        .result();
  }

  public boolean isOlderThan(Version version) {
    return this.compareTo(version) < 0;
  }

  public boolean isNewerThan(Version version) {
    return this.compareTo(version) > 0;
  }

  @Override
  public final String toString() {
    return getFullVersion();
  }

  static Builder builder() {
    return new AutoValue_Version.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setFullVersion(String fullVersion);

    abstract Builder setMajorVersion(int majorVersion);

    abstract Builder setMinorVersion(int minorVersion);

    abstract Builder setRevisionVersion(int revisionVersion);

    abstract Builder setQualifier(String qualifier);

    abstract Version build();
  }
}
