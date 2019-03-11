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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.CheckReturnValue;

/**
 * Represents an app bundle.
 *
 * <p>When AppBundle is read, the optional but valid ZIP directories entries are skipped so any code
 * using the model objects can assume any ZIP entry is referring to a regular file.
 */
public class AppBundle {

  public static final ZipPath METADATA_DIRECTORY = ZipPath.create("BUNDLE-METADATA");

  public static final String BUNDLE_CONFIG_FILE_NAME = "BundleConfig.pb";

  private final ImmutableMap<BundleModuleName, BundleModule> modulesByName;
  private final BundleConfig bundleConfig;
  private final BundleMetadata bundleMetadata;
  private final ImmutableSet<ResourceId> pinnedResourceIds;

  private AppBundle(
      ImmutableMap<BundleModuleName, BundleModule> modulesByName,
      BundleConfig bundleConfig,
      BundleMetadata bundleMetadata,
      ImmutableSet<ResourceId> pinnedResourceIds) {
    this.modulesByName = modulesByName;
    this.bundleConfig = bundleConfig;
    this.bundleMetadata = bundleMetadata;
    this.pinnedResourceIds = pinnedResourceIds;
  }

  /** Builds an {@link AppBundle} from an App Bundle on disk. */
  public static AppBundle buildFromZip(ZipFile bundleFile) {
    BundleConfig bundleConfig = readBundleConfig(bundleFile);
    return buildFromModules(
        sanitize(extractModules(bundleFile, bundleConfig), bundleConfig),
        bundleConfig,
        readBundleMetadata(bundleFile));
  }

  public static AppBundle buildFromModules(
      ImmutableList<BundleModule> modules,
      BundleConfig bundleConfig,
      BundleMetadata bundleMetadata) {
    ImmutableSet<ResourceId> pinnedResourceIds =
        bundleConfig.getMasterResources().getResourceIdsList().stream()
            .map(ResourceId::create)
            .collect(toImmutableSet());
    return new AppBundle(
        Maps.uniqueIndex(modules, BundleModule::getName),
        bundleConfig,
        bundleMetadata,
        pinnedResourceIds);
  }

  public ImmutableMap<BundleModuleName, BundleModule> getFeatureModules() {
    return modulesByName.values().stream()
        .filter(module -> module.getModuleType().equals(ModuleType.FEATURE_MODULE))
        .collect(toImmutableMap(BundleModule::getName, identity()));
  }

  public ImmutableMap<BundleModuleName, BundleModule> getAssetModules() {
    return modulesByName.values().stream()
        .filter(module -> module.getModuleType().equals(ModuleType.ASSET_MODULE))
        .collect(toImmutableMap(BundleModule::getName, identity()));
  }

  public ImmutableMap<BundleModuleName, BundleModule> getModules() {
    return modulesByName;
  }

  public BundleModule getBaseModule() {
    return getModule(BundleModuleName.create(BundleModuleName.BASE_MODULE_NAME));
  }

  public BundleModule getModule(BundleModuleName moduleName) {
    BundleModule module = modulesByName.get(moduleName);
    if (module == null) {
      throw CommandExecutionException.builder()
          .withMessage("Module '%s' not found.", moduleName)
          .build();
    }
    return module;
  }

  /** Resources that must remain in the master split regardless of their targeting configuration. */
  public ImmutableSet<ResourceId> getMasterPinnedResources() {
    return pinnedResourceIds;
  }

  public boolean has32BitRenderscriptCode() {
    return getFeatureModules().values().stream().anyMatch(BundleModule::hasRenderscript32Bitcode);
  }

  public BundleConfig getBundleConfig() {
    return bundleConfig;
  }

  public BundleMetadata getBundleMetadata() {
    return bundleMetadata;
  }

  /**
   * Returns a set of ABIs that this App Bundle targets.
   *
   * <p>Note that the each module of the App Bundle must target the same set of ABIs or have no
   * native code.
   *
   * <p>Returns empty set if the App Bundle has no native code at all.
   */
  public ImmutableSet<Abi> getTargetedAbis() {
    return getFeatureModules().values().stream()
        .map(BundleModule::getNativeConfig)
        .flatMap(
            nativeConfig -> {
              if (nativeConfig.isPresent()) {
                return nativeConfig.get().getDirectoryList().stream()
                    .map(TargetedNativeDirectory::getTargeting)
                    .map(NativeDirectoryTargeting::getAbi);
              } else {
                return Stream.empty();
              }
            })
        .distinct()
        .collect(toImmutableSet());
  }

  /**
   * Returns the {@link BundleModuleName} corresponding to the provided zip entry. If the zip entry
   * does not belong to a module, a null {@link BundleModuleName} is returned.
   */
  public static Optional<BundleModuleName> extractModuleName(ZipEntry entry) {
    ZipPath path = ZipPath.create(entry.getName());

    // Ignoring bundle metadata files.
    if (path.startsWith(METADATA_DIRECTORY)) {
      return Optional.empty();
    }

    // Ignoring signature related files.
    if (path.startsWith("META-INF")) {
      return Optional.empty();
    }

    // Ignoring top-level files.
    if (path.getNameCount() <= 1) {
      return Optional.empty();
    }

    // Temporarily excluding .class files.
    if (path.toString().endsWith(".class")) {
      return Optional.empty();
    }

    return Optional.of(BundleModuleName.create(path.getName(0).toString()));
  }

  public boolean isApex() {
    return getBaseModule().getApexConfig().isPresent();
  }

  private static ImmutableList<BundleModule> extractModules(
      ZipFile bundleFile, BundleConfig bundleConfig) {
    Map<BundleModuleName, BundleModule.Builder> moduleBuilders = new HashMap<>();
    Enumeration<? extends ZipEntry> entries = bundleFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      Optional<BundleModuleName> moduleName = extractModuleName(entry);
      if (!moduleName.isPresent()) {
        continue;
      }

      BundleModule.Builder moduleBuilder =
          moduleBuilders.computeIfAbsent(
              moduleName.get(),
              name -> BundleModule.builder().setName(name).setBundleConfig(bundleConfig));
      try {
        moduleBuilder.addEntry(ModuleZipEntry.fromBundleZipEntry(entry, bundleFile));
      } catch (IOException e) {
        throw ValidationException.builder()
            .withCause(e)
            .withMessage(
                "Error processing zip entry '%s' of module '%s'.",
                entry.getName(), moduleName.get())
            .build();
      }
    }
    return moduleBuilders.values().stream()
        .map(module -> module.build())
        .collect(toImmutableList());
  }

  private static BundleConfig readBundleConfig(ZipFile bundleFile) {
    ZipEntry bundleConfigEntry = bundleFile.getEntry(BUNDLE_CONFIG_FILE_NAME);
    checkState(bundleConfigEntry != null, "File '%s' was not found.", BUNDLE_CONFIG_FILE_NAME);

    try (InputStream is = BufferedIo.inputStream(bundleFile, bundleConfigEntry)) {
      return BundleConfig.parseFrom(is);
    } catch (InvalidProtocolBufferException e) {
      throw ValidationException.builder()
          .withCause(e)
          .withMessage("Bundle config '%s' could not be parsed.", BUNDLE_CONFIG_FILE_NAME)
          .build();
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Error reading file '%s'.", BUNDLE_CONFIG_FILE_NAME)
          .build();
    }
  }

  private static BundleMetadata readBundleMetadata(ZipFile bundleFile) {
    BundleMetadata.Builder metadata = BundleMetadata.builder();
    ZipUtils.allFileEntries(bundleFile)
        .filter(entry -> ZipPath.create(entry.getName()).startsWith(METADATA_DIRECTORY))
        .forEach(
            zipEntry -> {
              ZipPath bundlePath = ZipPath.create(zipEntry.getName());
              // Strip the top-level metadata directory.
              ZipPath metadataPath = bundlePath.subpath(1, bundlePath.getNameCount());
              metadata.addFile(metadataPath, BufferedIo.inputStreamSupplier(bundleFile, zipEntry));
            });
    return metadata.build();
  }

  @CheckReturnValue
  private static ImmutableList<BundleModule> sanitize(
      ImmutableList<BundleModule> modules, BundleConfig bundleConfig) {
    Version bundleVersion = BundleToolVersion.getVersionFromBundleConfig(bundleConfig);
    if (bundleVersion.isOlderThan(Version.of("0.3.1"))) {
      // This is a temporary fix to cope with inconsistent ABIs.
      modules = modules.stream().map(new ModuleAbiSanitizer()::sanitize).collect(toImmutableList());
    }
    // This is a temporary fix to work around a bug in gradle that creates a file named classes1.dex
    modules =
        modules.stream().map(new ClassesDexNameSanitizer()::sanitize).collect(toImmutableList());

    return modules;
  }
}
