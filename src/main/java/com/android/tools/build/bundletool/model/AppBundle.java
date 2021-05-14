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
import static com.google.common.collect.MoreCollectors.onlyElement;
import static java.util.function.Function.identity;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryBundleLocation;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.Collection;
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
@Immutable
@AutoValue
public abstract class AppBundle {

  public static final ZipPath METADATA_DIRECTORY = ZipPath.create("BUNDLE-METADATA");

  public static final String BUNDLE_CONFIG_FILE_NAME = "BundleConfig.pb";

  /** Builds an {@link AppBundle} from an App Bundle on disk. */
  public static AppBundle buildFromZip(ZipFile bundleFile) {
    BundleConfig bundleConfig = readBundleConfig(bundleFile);
    return buildFromModules(
        sanitize(extractModules(bundleFile, bundleConfig)),
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

    ImmutableSet<String> pinnedResourceNames =
        ImmutableSet.copyOf(bundleConfig.getMasterResources().getResourceNamesList());

    return builder()
        .setModules(Maps.uniqueIndex(modules, BundleModule::getName))
        .setMasterPinnedResourceIds(pinnedResourceIds)
        .setMasterPinnedResourceNames(pinnedResourceNames)
        .setBundleConfig(bundleConfig)
        .setBundleMetadata(bundleMetadata)
        .build();
  }

  public abstract ImmutableMap<BundleModuleName, BundleModule> getModules();

  /**
   * Resource IDs that must remain in the master split regardless of their targeting configuration.
   */
  public abstract ImmutableSet<ResourceId> getMasterPinnedResourceIds();

  /**
   * Resource names that must remain in the master split regardless of their targeting
   * configuration.
   */
  public abstract ImmutableSet<String> getMasterPinnedResourceNames();

  public abstract BundleConfig getBundleConfig();

  public abstract BundleMetadata getBundleMetadata();

  public ImmutableMap<BundleModuleName, BundleModule> getFeatureModules() {
    return getModules().values().stream()
        .filter(module -> module.getModuleType().equals(ModuleType.FEATURE_MODULE))
        .collect(toImmutableMap(BundleModule::getName, identity()));
  }

  public ImmutableMap<BundleModuleName, BundleModule> getAssetModules() {
    return getModules().values().stream()
        .filter(module -> module.getModuleType().equals(ModuleType.ASSET_MODULE))
        .collect(toImmutableMap(BundleModule::getName, identity()));
  }

  public BundleModule getBaseModule() {
    return getModule(BundleModuleName.BASE_MODULE_NAME);
  }

  public String getPackageName() {
    if (isAssetOnly()) {
      return getModules().values().stream()
          .map(module -> module.getAndroidManifest().getPackageName())
          .distinct()
          .collect(onlyElement());
    }
    return getBaseModule().getAndroidManifest().getPackageName();
  }

  public BundleModule getModule(BundleModuleName moduleName) {
    BundleModule module = getModules().get(moduleName);
    checkState(module != null, "Module '%s' not found.", moduleName);
    return module;
  }

  public Version getVersion() {
    return Version.of(getBundleConfig().getBundletool().getVersion());
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
    return !isAssetOnly() && getBaseModule().getApexConfig().isPresent();
  }

  public boolean isAssetOnly() {
    return getBundleConfig().getType().equals(BundleType.ASSET_ONLY);
  }

  /**
   * Returns whether any of the feature modules specify `sharedUserId` attribute in the manifest.
   */
  public boolean hasSharedUserId() {
    return getFeatureModules().values().stream()
        .map(BundleModule::getAndroidManifest)
        .anyMatch(AndroidManifest::hasSharedUserId);
  }

  public abstract Builder toBuilder();

  static Builder builder() {
    return new AutoValue_AppBundle.Builder();
  }

  private static ImmutableList<BundleModule> extractModules(
      ZipFile bundleFile, BundleConfig bundleConfig) {
    Map<BundleModuleName, BundleModule.Builder> moduleBuilders = new HashMap<>();
    Enumeration<? extends ZipEntry> entries = bundleFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (entry.isDirectory()) {
        continue;
      }

      Optional<BundleModuleName> moduleName = extractModuleName(entry);
      if (!moduleName.isPresent()) {
        continue;
      }

      BundleModule.Builder moduleBuilder =
          moduleBuilders.computeIfAbsent(
              moduleName.get(),
              name -> BundleModule.builder().setName(name).setBundleConfig(bundleConfig));

      moduleBuilder.addEntry(
          ModuleEntry.builder()
              .setBundleLocation(
                  ModuleEntryBundleLocation.create(
                      Paths.get(bundleFile.getName()), ZipPath.create(entry.getName())))
              .setPath(ZipUtils.convertBundleToModulePath(ZipPath.create(entry.getName())))
              .setContent(ZipUtils.asByteSource(bundleFile, entry))
              .build());
    }
    return moduleBuilders.values().stream()
        .map(BundleModule.Builder::build)
        .collect(toImmutableList());
  }

  private static BundleConfig readBundleConfig(ZipFile bundleFile) {
    ZipEntry bundleConfigEntry = bundleFile.getEntry(BUNDLE_CONFIG_FILE_NAME);
    if (bundleConfigEntry == null) {
      throw InvalidBundleException.builder()
          .withUserMessage("File '%s' was not found.", BUNDLE_CONFIG_FILE_NAME)
          .build();
    }

    try {
      return BundleConfig.parseFrom(ZipUtils.asByteSource(bundleFile, bundleConfigEntry).read());
    } catch (InvalidProtocolBufferException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("Bundle config '%s' could not be parsed.", BUNDLE_CONFIG_FILE_NAME)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error reading file '%s'.", BUNDLE_CONFIG_FILE_NAME), e);
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
              metadata.addFile(metadataPath, ZipUtils.asByteSource(bundleFile, zipEntry));
            });
    return metadata.build();
  }

  @CheckReturnValue
  private static ImmutableList<BundleModule> sanitize(ImmutableList<BundleModule> modules) {
    // This is a temporary fix to work around a bug in gradle that creates a file named classes1.dex
    modules =
        modules.stream().map(new ClassesDexNameSanitizer()::sanitize).collect(toImmutableList());

    return modules;
  }

  /** Builder for App Bundle object */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setModules(ImmutableMap<BundleModuleName, BundleModule> modules);

    /** Convenience method to extract module names and set module map. */
    public Builder setRawModules(Collection<BundleModule> bundleModules) {
      setModules(bundleModules.stream().collect(toImmutableMap(BundleModule::getName, identity())));
      return this;
    }

    public abstract Builder setMasterPinnedResourceIds(ImmutableSet<ResourceId> pinnedResourceIds);

    public abstract Builder setMasterPinnedResourceNames(ImmutableSet<String> pinnedResourceNames);

    public abstract Builder setBundleConfig(BundleConfig bundleConfig);

    public abstract Builder setBundleMetadata(BundleMetadata bundleMetadata);

    public abstract AppBundle build();
  }
}
