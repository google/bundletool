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

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.utils.ZipUtils;
import com.android.tools.build.bundletool.utils.files.BufferedIo;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
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

  private final ImmutableMap<BundleModuleName, BundleModule> modules;
  private final BundleConfig bundleConfig;
  private final BundleMetadata bundleMetadata;

  private AppBundle(
      ImmutableMap<BundleModuleName, BundleModule> modules,
      BundleConfig bundleConfig,
      BundleMetadata bundleMetadata) {
    this.modules = modules;
    this.bundleConfig = bundleConfig;
    this.bundleMetadata = bundleMetadata;
  }

  /** Builds an {@link AppBundle} from an App Bundle on disk. */
  public static AppBundle buildFromZip(ZipFile bundleFile) {
    BundleConfig bundleConfig = readBundleConfig(bundleFile);
    return new AppBundle(
        sanitize(extractModules(bundleFile), bundleConfig),
        bundleConfig,
        readBundleMetadata(bundleFile));
  }

  public static AppBundle buildFromModules(
      ImmutableCollection<BundleModule> modules,
      BundleConfig bundleConfig,
      BundleMetadata bundleMetadata) {
    return new AppBundle(
        Maps.uniqueIndex(modules, BundleModule::getName), bundleConfig, bundleMetadata);
  }

  public ImmutableMap<BundleModuleName, BundleModule> getModules() {
    return modules;
  }

  public BundleModule getBaseModule() {
    return getModule(BundleModuleName.create(BundleModuleName.BASE_MODULE_NAME));
  }

  public BundleModule getModule(BundleModuleName moduleName) {
    BundleModule module = modules.get(moduleName);
    if (module == null) {
      throw CommandExecutionException.builder()
          .withMessage("Module '%s' not found.", moduleName)
          .build();
    }
    return module;
  }

  public BundleConfig getBundleConfig() {
    return bundleConfig;
  }

  public BundleMetadata getBundleMetadata() {
    return bundleMetadata;
  }

  private static Map<BundleModuleName, BundleModule> extractModules(ZipFile bundleFile) {
    Map<BundleModuleName, BundleModule.Builder> moduleBuilders = new HashMap<>();
    Enumeration<? extends ZipEntry> entries = bundleFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      ZipPath path = ZipPath.create(entry.getName());

      // Ignoring bundle metadata files.
      if (path.startsWith(METADATA_DIRECTORY)) {
        continue;
      }

      // Ignoring signature related files.
      if (path.startsWith("META-INF")) {
        continue;
      }

      // Ignoring top-level files.
      if (path.getNameCount() <= 1) {
        continue;
      }

      // Temporarily excluding .class files.
      if (path.toString().endsWith(".class")) {
        continue;
      }

      BundleModuleName moduleName = BundleModuleName.create(path.getName(0).toString());
      BundleModule.Builder moduleBuilder =
          moduleBuilders.computeIfAbsent(moduleName, name -> BundleModule.builder().setName(name));
      try {
        moduleBuilder.addEntry(ModuleZipEntry.fromBundleZipEntry(entry, bundleFile));
      } catch (IOException e) {
        throw ValidationException.builder()
            .withCause(e)
            .withMessage(
                "Error processing zip entry '%s' of module '%s'.", entry.getName(), moduleName)
            .build();
      }
    }
    return Maps.transformValues(moduleBuilders, BundleModule.Builder::build);
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
    ZipUtils.getFileEntriesWithPathPrefix(bundleFile, METADATA_DIRECTORY)
        .forEach(
            zipEntry -> {
              ZipPath bundlePath = ZipPath.create(zipEntry.getName());
              // Strip the top-level metadata directory.
              ZipPath metadataPath = bundlePath.subpath(1, bundlePath.getNameCount());
              metadata.addFile(metadataPath, () -> BufferedIo.inputStream(bundleFile, zipEntry));
            });
    return metadata.build();
  }

  @CheckReturnValue
  private static ImmutableMap<BundleModuleName, BundleModule> sanitize(
      Map<BundleModuleName, BundleModule> moduleMap, BundleConfig bundleConfig) {
    Version bundleVersion = BundleToolVersion.getVersionFromBundleConfig(bundleConfig);
    if (bundleVersion.isOlderThan(Version.of("0.3.1"))) {
      // This is a temporary fix to cope with inconsistent ABIs.
      moduleMap = Maps.transformValues(moduleMap, new ModuleAbiSanitizer()::sanitize);
    }
    // This is a temporary fix to work around a bug in gradle that creates a file named classes1.dex
    moduleMap = Maps.transformValues(moduleMap, new ClassesDexNameSanitizer()::sanitize);

    return ImmutableMap.copyOf(moduleMap);
  }
}
