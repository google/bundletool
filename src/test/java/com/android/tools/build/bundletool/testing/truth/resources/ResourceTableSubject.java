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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.toOptional;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** A subject for ResourceTable. */
public class ResourceTableSubject extends Subject {

  private static final Pattern RESOURCE_PATTERN =
      Pattern.compile("[a-zA-z][a-zA-z0-9_.]*:[a-zA-Z][a-zA-Z0-9_]*/[a-zA-Z][a-zA-Z0-9_]*");
  private static final Splitter COLON_SPLITTER = Splitter.on(':');

  private final ResourceTable actual;
  private final FailureMetadata metadata;

  public ResourceTableSubject(FailureMetadata metadata, ResourceTable actual) {
    super(metadata, actual);
    this.actual = actual;
    this.metadata = metadata;
  }

  public PackageSubject hasPackage(String packageName) {
    ImmutableList<Package> foundPackages = findPackages(packageName);
    if (foundPackages.isEmpty()) {
      failWithoutActual(fact("expected to contain package with name", packageName));
    } else if (foundPackages.size() > 1) {
      failWithoutActual(
          fact("expected to contain exactly one package with name", packageName),
          simpleFact("but contained multiple. Please also specify the package ID."));
    }
    return new PackageSubject(metadata, foundPackages.get(0));
  }

  public PackageSubject hasPackage(int packageId) {
    Optional<Package> foundPackage = findPackage(packageId);

    if (!foundPackage.isPresent()) {
      failWithoutActual(fact("expected to contain package with id", packageId));
    }
    return new PackageSubject(metadata, foundPackage.get());
  }

  public PackageSubject hasPackage(String packageName, int packageId) {
    Optional<Package> foundPackage = findPackage(packageName, packageId);

    if (!foundPackage.isPresent()) {
      failWithoutActual(
          fact("expected to contain package with id", packageId), fact("and name", packageName));
    }
    return new PackageSubject(metadata, foundPackage.get());
  }

  public void hasNoPackage(String packageName) {
    if (!findPackages(packageName).isEmpty()) {
      failWithoutActual(fact("expected not to contain package with name", packageName));
    }
  }

  /**
   * Checks if the table has a specified entry.
   *
   * <p>Resources must be referenced using the: "package:type/entry" convention.
   */
  public EntrySubject containsResource(String fullResourceName) {
    checkArgument(
        RESOURCE_PATTERN.matcher(fullResourceName).matches(),
        "Resource needs to follow pattern: %s but looks like %s",
        RESOURCE_PATTERN.pattern(),
        fullResourceName);
    String[] parts = Iterables.toArray(COLON_SPLITTER.split(fullResourceName), String.class);
    checkState(
        parts.length == 2,
        "resource name provided does not follow package:type/entry format: %s",
        fullResourceName);

    return hasPackage(parts[0]).hasResource(parts[1]);
  }

  /**
   * Checks if the table does not have a specified entry.
   *
   * <p>Resources must be referenced using the: "package:type/entry" convention.
   */
  public void doesNotContainResource(String fullResourceName) {
    checkArgument(
        RESOURCE_PATTERN.matcher(fullResourceName).matches(),
        "Resource needs to follow pattern: %s but looks like %s",
        RESOURCE_PATTERN.pattern(),
        fullResourceName);
    String[] parts = Iterables.toArray(COLON_SPLITTER.split(fullResourceName), String.class);
    checkState(
        parts.length == 2,
        "resource name provided does not follow package:type/entry format: %s",
        fullResourceName);

    findPackages(parts[0])
        .forEach(pkg -> new PackageSubject(metadata, pkg).hasNoResource(parts[1]));
  }

  private Optional<Package> findPackage(String packageName, int packageId) {
    return findPackage(
        pkg -> pkg.getPackageId().getId() == packageId && pkg.getPackageName().equals(packageName));
  }

  private Optional<Package> findPackage(int packageId) {
    return findPackage(pkg -> pkg.getPackageId().getId() == packageId);
  }

  private Optional<Package> findPackage(Predicate<Package> predicate) {
    return actual.getPackageList().stream().filter(predicate).collect(toOptional());
  }

  /**
   * Resource table can contain multiple packages with the same packageName. This non-determinism is
   * expressed by returning a list of matched packages.
   */
  private ImmutableList<Package> findPackages(String packageName) {
    return actual.getPackageList().stream()
        .filter(pkg -> pkg.getPackageName().equals(packageName))
        .collect(toImmutableList());
  }
}
