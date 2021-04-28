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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.BundleModuleName.BASE_MODULE_NAME;
import static com.android.tools.build.bundletool.model.utils.ModuleDependenciesUtils.buildAdjacencyMap;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Validates dependencies between bundle modules.
 *
 * <p>The dependency graph is inferred based on:
 *
 * <ul>
 *   <li>Module names (aka names of top-level directories in the bundle).
 *   <li>Dependency declarations in form of {@code <uses-split name="parent_module"/>} manifest
 *       entries.
 * </ul>
 */
public class ModuleDependencyValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    Multimap<String, String> moduleDependenciesMap = buildAdjacencyMap(modules);
    ImmutableMap<String, BundleModule> modulesByName =
        Maps.uniqueIndex(modules, module -> module.getName().getName());
    if (BundleValidationUtils.isAssetOnlyBundle(modules)) {
      checkAssetModulesHaveNoDependencies(moduleDependenciesMap, modulesByName);
    } else {
      checkHasBaseModule(modules);
      checkNoReflexiveDependencies(moduleDependenciesMap);
      checkModulesHaveUniqueDependencies(moduleDependenciesMap);
      checkReferencedModulesExist(moduleDependenciesMap);
      checkNoCycles(moduleDependenciesMap);
      checkInstantModuleDependencies(moduleDependenciesMap, modulesByName);
      checkValidModuleDeliveryTypeDependencies(moduleDependenciesMap, modulesByName);
      checkMinSdkIsCompatibleWithDependencies(moduleDependenciesMap, modulesByName);
      checkAssetModulesHaveNoDependencies(moduleDependenciesMap, modulesByName);
      BundleModule baseModule = BundleValidationUtils.expectBaseModule(modules);
      if (baseModule.getAndroidManifest().getIsolatedSplits().orElse(false)) {
        checkIsolatedSplitsModuleDependencies(moduleDependenciesMap);
      }
    }
  }

  private static void checkHasBaseModule(ImmutableList<BundleModule> modules) {
    if (modules.stream().noneMatch(BundleModule::isBaseModule)) {
      throw InvalidBundleException.builder()
          .withUserMessage("Mandatory '%s' module is missing.", BASE_MODULE_NAME)
          .build();
    }
  }

  private static void checkReferencedModulesExist(Multimap<String, String> moduleDependenciesMap) {
    for (String referencedModule : moduleDependenciesMap.values()) {
      if (!moduleDependenciesMap.containsKey(referencedModule)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Module '%s' is referenced by <uses-split> but does not exist.", referencedModule)
            .build();
      }
    }
  }

  /** Checks that a module doesn't depend on itself. */
  private static void checkNoReflexiveDependencies(Multimap<String, String> moduleDependenciesMap) {
    for (String moduleName : moduleDependenciesMap.keySet()) {
      // The base module is the only one that will have a self-loop in the dependencies map.
      if (!moduleName.equals(BASE_MODULE_NAME.getName())) {
        if (moduleDependenciesMap.containsEntry(moduleName, moduleName)) {
          throw InvalidBundleException.builder()
              .withUserMessage("Module '%s' depends on itself via <uses-split>.", moduleName)
              .build();
        }
      }
    }
  }

  /** Checks that a module doesn't declare dependency on another module more than once. */
  private static void checkModulesHaveUniqueDependencies(
      Multimap<String, String> moduleDependenciesMap) {
    for (Entry<String, Collection<String>> entry : moduleDependenciesMap.asMap().entrySet()) {
      String moduleName = entry.getKey();
      Collection<String> moduleDeps = entry.getValue();

      Set<String> alreadyReferencedModules = new HashSet<>();
      for (String moduleDep : moduleDeps) {
        if (!alreadyReferencedModules.add(moduleDep)) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Module '%s' declares dependency on module '%s' multiple times.",
                  moduleName, moduleDep)
              .build();
        }
      }
    }
  }

  /**
   * Validates that the module dependency graph contains no cycles.
   *
   * <p>Uses two sets of nodes for better time complexity:
   *
   * <ul>
   *   <li>"safe" is a set of modules/nodes that are already known not to be participants in any
   *       dependency cycle. Such nodes don't need to be re-examined again and again.
   *   <li>"visited" is a set of modules/nodes that have been visited during a single recursive
   *       call. When the call does not throw, no cycle has been found and "visited" nodes can be
   *       added to the "safe" set to avoid re-examination later.
   * </ul>
   */
  private static void checkNoCycles(Multimap<String, String> moduleDependenciesMap) {
    Set<String> safe = new HashSet<>();

    for (String moduleName : moduleDependenciesMap.keySet()) {
      Set<String> visited = new HashSet<>();

      // Using LinkedHashSet to preserve dependency order for better error message.
      checkNoCycles(
          moduleName,
          moduleDependenciesMap,
          visited,
          safe,
          /* processing= */ new LinkedHashSet<>());

      safe.addAll(visited);
    }
  }

  private static void checkNoCycles(
      String moduleName,
      Multimap<String, String> moduleDependenciesMap,
      Set<String> visited,
      Set<String> safe,
      Set<String> processing) {
    if (safe.contains(moduleName)) {
      return;
    }

    if (processing.contains(moduleName)) {
      throw InvalidBundleException.builder()
          .withUserMessage("Found cyclic dependency between modules: %s", processing)
          .build();
    }

    visited.add(moduleName);

    processing.add(moduleName);
    for (String referencedModule : moduleDependenciesMap.get(moduleName)) {
      // Skip  reflexive dependency (base, base).
      if (!moduleName.equals(referencedModule)) {
        checkNoCycles(referencedModule, moduleDependenciesMap, visited, safe, processing);
      }
    }
    processing.remove(moduleName);
  }

  /** Checks that an install-time module does not depend on an on-demand module. */
  private static void checkValidModuleDeliveryTypeDependencies(
      Multimap<String, String> moduleDependenciesMap,
      ImmutableMap<String, BundleModule> modulesByName) {
    for (Entry<String, String> dependencyEntry : moduleDependenciesMap.entries()) {
      String moduleName = dependencyEntry.getKey();
      String moduleDep = dependencyEntry.getValue();
      ModuleDeliveryType moduleDeliveryType = modulesByName.get(moduleName).getDeliveryType();
      ModuleDeliveryType depDeliveryType = modulesByName.get(moduleDep).getDeliveryType();

      // Conditional modules can only depend on always installed modules.
      if (moduleDeliveryType.equals(ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL)
          && !depDeliveryType.equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Conditional module '%s' cannot depend on a module '%s' that is not install-time.",
                moduleName, moduleDep)
            .build();
      }

      if (moduleDeliveryType.equals(ModuleDeliveryType.NO_INITIAL_INSTALL)
          && depDeliveryType.equals(ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "An on-demand module '%s' cannot depend on a conditional module '%s'.",
                moduleName, moduleDep)
            .build();
      }

      if (moduleDeliveryType.equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)
          && !depDeliveryType.equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Install-time module '%s' cannot depend on a module '%s' that is not "
                    + "install-time.",
                moduleName, moduleDep)
            .build();
      }
    }
  }

  /** Checks that the min sdk value for a module is compatible with its dependencies. */
  private static void checkMinSdkIsCompatibleWithDependencies(
      Multimap<String, String> moduleDependenciesMap,
      ImmutableMap<String, BundleModule> modulesByName) {
    for (Entry<String, String> dependencyEntry : moduleDependenciesMap.entries()) {
      String moduleName = dependencyEntry.getKey();
      String moduleDepName = dependencyEntry.getValue();
      BundleModule module = modulesByName.get(moduleName);
      if (module.getModuleType().equals(ModuleType.ASSET_MODULE)) {
        continue; // Asset modules don't have SDK constraints.
      }
      BundleModule moduleDep = modulesByName.get(moduleDepName);
      int minSdk = module.getAndroidManifest().getEffectiveMinSdkVersion();
      int minSdkDep = moduleDep.getAndroidManifest().getEffectiveMinSdkVersion();
      if (module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)
          && minSdk != minSdkDep) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Install-time module '%s' has a minSdkVersion(%d) different than the"
                    + " minSdkVersion(%d) of its dependency '%s'.",
                moduleName, minSdk, minSdkDep, moduleDepName)
            .build();
      } else if (minSdk < minSdkDep && !moduleDep.isBaseModule()) {
        // Note that for dependencies on base module, having lower minSdk is harmless because the
        // app will not be served to devices with lower minSdk than the base.
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Conditional or on-demand module '%s' has a minSdkVersion(%d), which is"
                    + " smaller than the minSdkVersion(%d) of its dependency '%s'.",
                moduleName, minSdk, minSdkDep, moduleDepName)
            .build();
      }
    }
  }

  private static void checkAssetModulesHaveNoDependencies(
      Multimap<String, String> moduleDependenciesMap,
      ImmutableMap<String, BundleModule> modulesByName) {
    for (Entry<String, String> dependencyEntry : moduleDependenciesMap.entries()) {
      String moduleName = dependencyEntry.getKey();
      String moduleDepName = dependencyEntry.getValue();
      boolean moduleIsAsset = isAssetModule(modulesByName, moduleName);
      boolean moduleDepIsAsset = isAssetModule(modulesByName, moduleDepName);
      if (!moduleDepName.equals(BASE_MODULE_NAME.getName())
          && (moduleIsAsset || moduleDepIsAsset)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Module '%s' cannot depend on module '%s' because one of them is an asset pack.",
                moduleName, moduleDepName)
            .build();
      }
    }
  }

  /** Instant modules should only depend on other instant compatible modules. */
  private static void checkInstantModuleDependencies(
      Multimap<String, String> moduleDependenciesMap,
      ImmutableMap<String, BundleModule> modulesByName) {
    for (Entry<String, String> dependencyEntry : moduleDependenciesMap.entries()) {
      String moduleName = dependencyEntry.getKey();
      String moduleDepName = dependencyEntry.getValue();
      boolean isInstantModule = modulesByName.get(moduleName).isInstantModule();
      boolean isDepInstantModule = modulesByName.get(moduleDepName).isInstantModule();

      if (isInstantModule && !isDepInstantModule) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Instant module '%s' cannot depend on a module '%s' that is not instant.",
                moduleName, moduleDepName)
            .build();
      }
    }
  }

  /** Isolated splits may only depend on a single parent module. */
  private static void checkIsolatedSplitsModuleDependencies(
      Multimap<String, String> moduleDependenciesMap) {
    for (String moduleName : moduleDependenciesMap.keySet()) {
      Collection<String> nonBaseDependencies =
          moduleDependenciesMap.get(moduleName).stream()
              .filter(name -> !BASE_MODULE_NAME.getName().equals(name))
              .collect(toImmutableList());
      if (nonBaseDependencies.size() > 1) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Isolated module '%s' cannot depend on more than one other module, "
                    + "but it depends on [%s].",
                moduleName, nonBaseDependencies.stream().collect(joining(", ")))
            .build();
      }
    }
  }

  private static boolean isAssetModule(
      ImmutableMap<String, BundleModule> modulesByName, String moduleName) {
    return modulesByName.containsKey(moduleName)
        && modulesByName.get(moduleName).getModuleType().equals(ModuleType.ASSET_MODULE);
  }
}
