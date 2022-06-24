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

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.version.Version;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utility class to parse bundle modules from zip files. */
public class BundleModuleParser {
  public static BundleModule parseAppBundleModule(
      ZipFile moduleZipFile, BundleConfig bundleConfig) {
    BundleModule.Builder bundleModuleBuilder =
        parseBundleModuleInternal(
            moduleZipFile, bundleConfig.getBundletool(), bundleConfig.getType());

    if (bundleConfig.hasApexConfig()) {
      bundleModuleBuilder.setBundleApexConfig(bundleConfig.getApexConfig());
    }
    return bundleModuleBuilder.build();
  }

  public static BundleModule parseSdkBundleModule(
      ZipFile moduleZipFile, SdkModulesConfig sdkModulesConfig) {
    return parseBundleModuleInternal(
            moduleZipFile, sdkModulesConfig.getBundletool(), BundleType.REGULAR)
        .build();
  }

  private static BundleModule.Builder parseBundleModuleInternal(
      ZipFile moduleZipFile, Bundletool bundletool, BundleType bundleType) {
    BundleModule.Builder bundleModuleBuilder =
        BundleModule.builder()
            // Assigning a temporary name because the real one will be extracted from the
            // manifest, but this requires the BundleModule to be built.
            .setName(BundleModuleName.create("TEMPORARY_MODULE_NAME"))
            .setBundleType(bundleType)
            .setBundletoolVersion(Version.of(bundletool.getVersion()))
            .addEntries(
                moduleZipFile.stream()
                    .filter(not(ZipEntry::isDirectory))
                    .map(
                        zipEntry ->
                            ModuleEntry.builder()
                                .setFileLocation(
                                    ModuleEntryLocationInZipSource.create(
                                        Paths.get(moduleZipFile.getName()),
                                        ZipPath.create(zipEntry.getName())))
                                .setPath(ZipPath.create(zipEntry.getName()))
                                .setContent(ZipUtils.asByteSource(moduleZipFile, zipEntry))
                                .build())
                    .collect(toImmutableList()));

    BundleModuleName actualModuleName =
        bundleModuleBuilder
            .build()
            .getAndroidManifest()
            .getSplitId()
            .map(BundleModuleName::create)
            .orElse(BundleModuleName.BASE_MODULE_NAME);

    return bundleModuleBuilder.setName(actualModuleName);
  }

  private BundleModuleParser() {}
}
