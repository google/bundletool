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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.MoreCollectors.toOptional;
import static com.google.common.truth.Fact.fact;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.Type;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Optional;
import java.util.regex.Pattern;

/** A subject for {@link Package}. */
public class PackageSubject extends Subject {

  private static final Pattern RESOURCE_PATTERN =
      Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*/[a-zA-Z][a-zA-Z0-9_]*");
  private final Package actual;
  private final FailureMetadata metadata;

  public PackageSubject(FailureMetadata metadata, Package actual) {
    super(metadata, actual);
    this.actual = actual;
    this.metadata = metadata;
  }

  public TypeSubject withType(String typeName) {
    Optional<Type> matchingType = findType(typeName);
    if (!matchingType.isPresent()) {
      failWithoutActual(fact("expected to contain type", typeName));
    }
    return new TypeSubject(metadata, matchingType.get());
  }

  public void withNoType(String typeName) {
    Optional<Type> matchingType = findType(typeName);
    if (matchingType.isPresent()) {
      failWithoutActual(fact("expected not to contain type", typeName));
    }
  }

  /**
   * Checks if the package does have a specific entry.
   *
   * <p>Resource must be specified in "type/entry" format.
   */
  public EntrySubject hasResource(String resourceName) {
    checkArgument(
        RESOURCE_PATTERN.matcher(resourceName).matches(),
        String.format(
            "Resource needs to follow pattern: %s but looks like %s",
            RESOURCE_PATTERN.pattern(), resourceName));
    String[] parts = resourceName.split("/");
    checkState(
        parts.length == 2,
        "Resource has incorrect format, expecting type/entry, but got: " + resourceName);

    return withType(parts[0]).containingResource(parts[1]);
  }

  /**
   * Checks if the package does not have a specific entry.
   *
   * <p>Resource must be specified in "type/entry" format.
   */
  public void hasNoResource(String resourceName) {
    checkArgument(
        RESOURCE_PATTERN.matcher(resourceName).matches(),
        String.format(
            "Resource needs to follow pattern: %s but looks like %s",
            RESOURCE_PATTERN.pattern(), resourceName));
    String[] parts = resourceName.split("/");
    checkState(
        parts.length == 2,
        "Resource has incorrect format, expecting type/entry, but got: " + resourceName);

    Optional<Type> foundType = findType(parts[0]);
    foundType.ifPresent(type -> new TypeSubject(metadata, type).notContainingResource(parts[1]));
  }

  private Optional<Type> findType(String typeName) {
    return actual.getTypeList().stream()
        .filter(type -> type.getName().equals(typeName))
        .collect(toOptional());
  }
}
