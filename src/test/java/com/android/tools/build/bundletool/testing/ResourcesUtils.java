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
package com.android.tools.build.bundletool.testing;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Optional;

/** Utility to manipulate the resource table, reserved to testing classes. */
public class ResourcesUtils {

  /**
   * Looks up a resource by its package, type and resource name.
   *
   * <p>NOTE: This shouldn't be used in production code as the package name is not guaranteed to be
   * unique, but it's fine to use in tests.
   */
  public static Optional<Integer> resolveResourceId(
      ResourceTable resourceTable, String packageName, String typeName, String resourceName) {
    ImmutableList<Package> matchingPackages =
        resourceTable
            .getPackageList()
            .stream()
            .filter(pkg -> pkg.getPackageName().equals(packageName))
            .collect(toImmutableList());
    if (matchingPackages.isEmpty()) {
      return Optional.empty();
    }
    checkState(
        matchingPackages.size() == 1,
        "More than one package found with the name '%s'",
        packageName);
    Package matchingPackage = Iterables.getOnlyElement(matchingPackages);
    int packageId = matchingPackage.getPackageId().getId();

    Optional<Type> matchingType =
        matchingPackage
            .getTypeList()
            .stream()
            .filter(type -> type.getName().equals(typeName))
            .collect(toOptional());
    if (!matchingType.isPresent()) {
      return Optional.empty();
    }
    int typeId = matchingType.get().getTypeId().getId();

    Optional<Entry> matchingEntry =
        matchingType
            .get()
            .getEntryList()
            .stream()
            .filter(entry -> entry.getName().equals(resourceName))
            .collect(toOptional());
    if (!matchingEntry.isPresent()) {
      return Optional.empty();
    }
    int entryId = matchingEntry.get().getEntryId().getId();

    return Optional.of((packageId << 24) | (typeId << 16) | entryId);
  }
}
