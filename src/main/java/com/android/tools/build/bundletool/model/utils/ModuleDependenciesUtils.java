/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toSet;

import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.Variant;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.HashSet;
import java.util.Set;

/** Helpers related to dependencies of the modules. */
public final class ModuleDependenciesUtils {

  /** Gets modules including their dependencies for the requested modules. */
  public static ImmutableSet<BundleModule> getModulesIncludingDependencies(
      AppBundle appBundle, ImmutableList<BundleModule> modules) {
    Multimap<String, String> adjacencyMap = buildAdjacencyMap(modules);
    Set<String> dependencyModules =
        modules.stream().map(module -> module.getName().getName()).collect(toSet());
    for (BundleModule requestedModule : modules) {
      addModuleDependencies(requestedModule.getName().getName(), adjacencyMap, dependencyModules);
    }
    return dependencyModules.stream()
        .map(module -> appBundle.getModule(BundleModuleName.create(module)))
        .collect(toImmutableSet());
  }

  public static ImmutableSet<String> getModulesIncludingDependencies(
      Variant variant, Set<String> requestedModules) {
    ImmutableMultimap<String, String> adjacencyMap = buildAdjacencyMap(variant);
    HashSet<String> dependencyModules = new HashSet<>(requestedModules);
    for (String requestedModuleName : requestedModules) {
      addModuleDependencies(requestedModuleName, adjacencyMap, dependencyModules);
    }
    return ImmutableSet.copyOf(dependencyModules);
  }

  /** Builds a map of module dependencies. */
  public static ImmutableMultimap<String, String> buildAdjacencyMap(Variant variant) {
    ImmutableMultimap.Builder<String, String> moduleDependenciesMap = ImmutableMultimap.builder();
    variant.getApkSetList().stream()
        .map(ApkSet::getModuleMetadata)
        .forEach(
            moduleMetadata -> {
              moduleDependenciesMap.putAll(
                  moduleMetadata.getName(), moduleMetadata.getDependenciesList());
              moduleDependenciesMap.put(moduleMetadata.getName(), "base");
            });
    return moduleDependenciesMap.build();
  }

  /**
   * Builds a map of module dependencies.
   *
   * <p>If module "a" contains {@code <uses-split name="b"/>} manifest entry, then the map contains
   * entry ("a", "b").
   *
   * <p>All modules implicitly depend on the "base" module. Hence the map contains also dependency
   * ("base", "base").
   */
  public static Multimap<String, String> buildAdjacencyMap(ImmutableList<BundleModule> modules) {
    Multimap<String, String> moduleDependenciesMap = ArrayListMultimap.create();

    for (BundleModule module : modules) {
      String moduleName = module.getName().getName();
      AndroidManifest manifest = module.getAndroidManifest();

      checkArgument(
          !moduleDependenciesMap.containsKey(moduleName),
          "Module named '%s' was passed in multiple times.",
          moduleName);

      moduleDependenciesMap.putAll(moduleName, manifest.getUsesSplits());

      // Check that module does not declare explicit dependency on the "base" module
      // (whose split ID actually is empty instead of "base" anyway).
      if (moduleDependenciesMap.containsEntry(moduleName, BASE_MODULE_NAME.getName())) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Module '%s' declares dependency on the '%s' module, which is implicit.",
                moduleName, BASE_MODULE_NAME)
            .build();
      }

      // Add implicit dependency on the base. Also ensures that every module has a key in the map.
      moduleDependenciesMap.put(moduleName, BASE_MODULE_NAME.getName());
    }

    return Multimaps.unmodifiableMultimap(moduleDependenciesMap);
  }

  /** Adds module dependencies to {@code dependencyModules}. */
  public static void addModuleDependencies(
      String moduleName,
      Multimap<String, String> moduleDependenciesMap,
      Set<String> dependencyModules) {
    if (!moduleDependenciesMap.containsKey(moduleName)) {
      return;
    }

    for (String moduleDependency : moduleDependenciesMap.get(moduleName)) {
      // We do not examine again the dependency that was previously handled and added.
      if (dependencyModules.add(moduleDependency)) {
        addModuleDependencies(moduleDependency, moduleDependenciesMap, dependencyModules);
      }
    }
  }
}
