/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Config.ApexConfig;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ClassesDexSanitizer;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utility class to help parse bundles */
public class BundleParser {

  private BundleParser() {}

  public static final ZipPath METADATA_DIRECTORY = ZipPath.create("BUNDLE-METADATA");

  public static final String BUNDLE_CONFIG_FILE_NAME = "BundleConfig.pb";

  public static final String SDK_MODULES_CONFIG_FILE_NAME = "SdkModulesConfig.pb";

  public static final String SDK_BUNDLE_CONFIG_FILE_NAME = "SdkBundleConfig.pb";

  public static final String SDK_INTERFACE_DESCRIPTORS_FILE_NAME = "sdk-interface-descriptors.jar";

  /**
   * File name of the zip that contains runtime enabled SDK modules. This zip is located in the top
   * level of an ASB zip.
   */
  public static final String SDK_MODULES_FILE_NAME = "modules.resm";

  /**
   * File name of the zip which is created when {@value SDK_MODULES_FILE_NAME} is extracted in order
   * to be processed.
   */
  public static final String EXTRACTED_SDK_MODULES_FILE_NAME = "extracted-modules.resm";

  /**
   * Returns the {@link BundleModuleName} corresponding to the provided zip entry. If the zip entry
   * does not belong to a module, a null {@link BundleModuleName} is returned.
   */
  public static Optional<BundleModuleName> extractModuleName(
      ZipEntry entry, ImmutableSet<ZipPath> nonModuleDirectories) {
    ZipPath path = ZipPath.create(entry.getName());

    for (ZipPath nonModuleDirectory : nonModuleDirectories) {
      if (path.startsWith(nonModuleDirectory)) {
        return Optional.empty();
      }
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

  /** Extracts all modules from bundle zip file and returns them in a list */
  public static ImmutableList<BundleModule> extractModules(
      ZipFile bundleFile,
      BundleType bundleType,
      Version bundletoolVersion,
      Optional<ApexConfig> apexConfig,
      ImmutableSet<ZipPath> nonModuleDirectories) {
    Map<BundleModuleName, BundleModule.Builder> moduleBuilders = new HashMap<>();
    Enumeration<? extends ZipEntry> entries = bundleFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (entry.isDirectory()) {
        continue;
      }

      Optional<BundleModuleName> moduleName = extractModuleName(entry, nonModuleDirectories);
      if (!moduleName.isPresent()) {
        continue;
      }

      BundleModule.Builder moduleBuilder =
          moduleBuilders.computeIfAbsent(
              moduleName.get(),
              name -> {
                BundleModule.Builder bundleModuleBuilder =
                    BundleModule.builder()
                        .setName(name)
                        .setBundleType(bundleType)
                        .setBundletoolVersion(bundletoolVersion);
                apexConfig.ifPresent(bundleModuleBuilder::setBundleApexConfig);
                return bundleModuleBuilder;
              });

      moduleBuilder.addEntry(
          ModuleEntry.builder()
              .setFileLocation(
                  ModuleEntryLocationInZipSource.create(
                      Paths.get(bundleFile.getName()), ZipPath.create(entry.getName())))
              .setPath(ZipUtils.convertBundleToModulePath(ZipPath.create(entry.getName())))
              .setContent(ZipUtils.asByteSource(bundleFile, entry))
              .build());
    }

    // We verify the presence of the manifest before building the BundleModule objects because the
    // manifest is a required field of the BundleModule class.
    checkModulesHaveManifest(moduleBuilders.values());

    return moduleBuilders.values().stream()
        .map(BundleModule.Builder::build)
        .collect(toImmutableList());
  }

  private static void checkModulesHaveManifest(Collection<BundleModule.Builder> bundleModules) {
    ImmutableSet<String> modulesWithoutManifest =
        bundleModules.stream()
            .filter(bundleModule -> !bundleModule.hasAndroidManifest())
            .map(module -> module.getName().getName())
            .collect(toImmutableSet());
    if (!modulesWithoutManifest.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Found modules in the App Bundle without an AndroidManifest.xml: %s",
              modulesWithoutManifest)
          .build();
    }
  }

  /** Loads BundleConfig.pb from zip file into {@link BundleConfig} */
  @SuppressWarnings("ProtoParseWithRegistry")
  public static BundleConfig readBundleConfig(ZipFile bundleFile) {
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

  /** Loads {@value #SDK_MODULES_CONFIG_FILE_NAME} from zip file into {@link SdkModulesConfig}. */
  @SuppressWarnings("ProtoParseWithRegistry")
  public static SdkModulesConfig readSdkModulesConfig(ZipFile modulesFile) {
    ZipEntry sdkModulesConfigEntry = modulesFile.getEntry(SDK_MODULES_CONFIG_FILE_NAME);
    if (sdkModulesConfigEntry == null) {
      throw InvalidBundleException.builder()
          .withUserMessage("File '%s' was not found.", SDK_MODULES_CONFIG_FILE_NAME)
          .build();
    }

    try {
      return SdkModulesConfig.parseFrom(
          ZipUtils.asByteSource(modulesFile, sdkModulesConfigEntry).read());
    } catch (InvalidProtocolBufferException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage(
              "SDK modules config file '%s' could not be parsed.", SDK_MODULES_CONFIG_FILE_NAME)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error reading file '%s'.", SDK_MODULES_CONFIG_FILE_NAME), e);
    }
  }

  /** Loads {@value #SDK_BUNDLE_CONFIG_FILE_NAME} from zip file into {@link SdkBundleConfig}. */
  @SuppressWarnings("ProtoParseWithRegistry")
  public static SdkBundleConfig readSdkBundleConfig(ZipFile bundleFile) {
    ZipEntry sdkBundleConfigEntry = bundleFile.getEntry(SDK_BUNDLE_CONFIG_FILE_NAME);
    if (sdkBundleConfigEntry == null) {
      return SdkBundleConfig.getDefaultInstance();
    }

    try {
      return SdkBundleConfig.parseFrom(
          ZipUtils.asByteSource(bundleFile, sdkBundleConfigEntry).read());
    } catch (InvalidProtocolBufferException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage(
              "SDK bundle config file '%s' could not be parsed.", SDK_BUNDLE_CONFIG_FILE_NAME)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error reading file '%s'.", SDK_BUNDLE_CONFIG_FILE_NAME), e);
    }
  }

  /** Loads BUNDLE-METADATA into {@link BundleMetadata} */
  public static BundleMetadata readBundleMetadata(ZipFile bundleFile) {
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

  public static Optional<ByteSource> readSdkInterfaceDescriptors(ZipFile bundleFile) {
    ZipEntry entry = bundleFile.getEntry(SDK_INTERFACE_DESCRIPTORS_FILE_NAME);
    return Optional.ofNullable(entry).map(value -> ZipUtils.asByteSource(bundleFile, value));
  }

  /**
   * Renames classes1.dex files to classes.dex in the given modules. This is a temporary fix to work
   * around a bug in gradle that creates a file named classes1.dex
   */
  @CheckReturnValue
  public static ImmutableList<BundleModule> sanitize(ImmutableList<BundleModule> modules) {
    modules =
        modules.stream()
            .map(module -> new ClassesDexSanitizer().applyMutation(module))
            .collect(toImmutableList());

    return modules;
  }

  /**
   * Performs the following steps:
   *
   * <ol>
   *   <li>Extracts the {@value BundleParser#SDK_MODULES_FILE_NAME} zip from within an Android SDK
   *       Bundle zip.
   *   <li>Writes the {@value BundleParser#SDK_MODULES_FILE_NAME} zip to the provided {@code Path}.
   *   <li>Returns the {@code ZipFile} that has been written to the {@code Path}.
   * </ol>
   */
  public static ZipFile getModulesZip(ZipFile bundleZip, Path modulesPath) throws IOException {
    ZipEntry modulesEntry = bundleZip.getEntry(SDK_MODULES_FILE_NAME);
    try (InputStream modulesInputStream = bundleZip.getInputStream(modulesEntry)) {
      Files.copy(modulesInputStream, modulesPath);
      return new ZipFile(modulesPath.toFile());
    }
  }
}
