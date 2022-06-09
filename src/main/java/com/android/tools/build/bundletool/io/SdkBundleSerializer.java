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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.AppBundle.METADATA_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.BundleParser.EXTRACTED_SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_BUNDLE_CONFIG_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_CONFIG_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;

/** Serializer of {@link SdkBundle} instances onto disk. */
public class SdkBundleSerializer {

  /** Writes the SDK Bundle on disk at the given location. */
  public void writeToDisk(SdkBundle sdkBundle, Path pathOnDisk) throws IOException {
    ZipBuilder zipBuilder = new ZipBuilder();

    // BUNDLE-METADATA
    for (Entry<ZipPath, ByteSource> metadataEntry :
        sdkBundle.getBundleMetadata().getFileContentMap().entrySet()) {
      zipBuilder.addFile(
          METADATA_DIRECTORY.resolve(metadataEntry.getKey()), metadataEntry.getValue());
    }

    // SdkBundleConfig
    zipBuilder.addFileWithProtoContent(
        ZipPath.create(SDK_BUNDLE_CONFIG_FILE_NAME), sdkBundle.getSdkBundleConfig());

    // Modules
    try (TempDirectory tempDir = new TempDirectory(getClass().getSimpleName())) {
      Path modulesPath = tempDir.getPath().resolve(EXTRACTED_SDK_MODULES_FILE_NAME);
      getModulesBuilder(sdkBundle).writeTo(modulesPath);
      zipBuilder.addFileFromDisk(ZipPath.create(SDK_MODULES_FILE_NAME), modulesPath.toFile());
      zipBuilder.writeTo(pathOnDisk);
    }
  }

  private ZipBuilder getModulesBuilder(SdkBundle sdkBundle) {
    ZipBuilder modulesBuilder = new ZipBuilder();

    // SdkModulesConfig.pb
    modulesBuilder.addFileWithProtoContent(
        ZipPath.create(SDK_MODULES_CONFIG_FILE_NAME), sdkBundle.getSdkModulesConfig());

    // Base module (the only module in an ASB)
    BundleModule module = sdkBundle.getModule();
    ZipPath moduleDir = ZipPath.create(module.getName().toString());

    for (ModuleEntry entry : module.getEntries()) {
      ZipPath entryPath = moduleDir.resolve(entry.getPath());
      modulesBuilder.addFile(entryPath, entry.getContent());
    }

    // Special module files are not represented as module entries (above).
    modulesBuilder.addFileWithProtoContent(
        moduleDir.resolve(SpecialModuleEntry.ANDROID_MANIFEST.getPath()),
        module.getAndroidManifest().getManifestRoot().getProto());
    module
        .getAssetsConfig()
        .ifPresent(
            assetsConfig ->
                modulesBuilder.addFileWithProtoContent(
                    moduleDir.resolve(SpecialModuleEntry.ASSETS_TABLE.getPath()), assetsConfig));
    module
        .getNativeConfig()
        .ifPresent(
            nativeConfig ->
                modulesBuilder.addFileWithProtoContent(
                    moduleDir.resolve(SpecialModuleEntry.NATIVE_LIBS_TABLE.getPath()),
                    nativeConfig));
    module
        .getResourceTable()
        .ifPresent(
            resourceTable ->
                modulesBuilder.addFileWithProtoContent(
                    moduleDir.resolve(SpecialModuleEntry.RESOURCE_TABLE.getPath()), resourceTable));

    return modulesBuilder;
  }
}
