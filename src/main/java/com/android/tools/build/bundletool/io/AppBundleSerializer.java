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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.model.AppBundle.BUNDLE_CONFIG_FILE_NAME;
import static com.android.tools.build.bundletool.model.AppBundle.METADATA_DIRECTORY;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.InputStreamSupplier;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;

/** Serializer of Bundle instances onto disk. */
public class AppBundleSerializer {

  /** Writes the App Bundle on disk at the given location. */
  public void writeToDisk(AppBundle bundle, Path pathOnDisk) throws IOException {
    ZipBuilder zipBuilder = new ZipBuilder();

    zipBuilder.addFileWithProtoContent(
        ZipPath.create(BUNDLE_CONFIG_FILE_NAME), bundle.getBundleConfig());

    // APEX bundles do not have metadata files.
    if (bundle.getModules().isEmpty() || !bundle.getBaseModule().getApexConfig().isPresent()) {
      for (Entry<ZipPath, InputStreamSupplier> metadataEntry :
          bundle.getBundleMetadata().getFileDataMap().entrySet()) {
        zipBuilder.addFile(
            METADATA_DIRECTORY.resolve(metadataEntry.getKey()), metadataEntry.getValue());
      }
    }

    for (BundleModule module : bundle.getModules().values()) {
      ZipPath moduleDir = ZipPath.create(module.getName().toString());

      for (ModuleEntry entry : module.getEntries()) {
        ZipPath entryPath = moduleDir.resolve(entry.getPath());
        if (entry.isDirectory()) {
          zipBuilder.addDirectory(entryPath);
        } else {
          zipBuilder.addFile(entryPath, () -> entry.getContent());
        }
      }

      // Special module files are not represented as module entries (above).
      zipBuilder.addFileWithProtoContent(
          moduleDir.resolve(BundleModule.MANIFEST_PATH),
          module.getAndroidManifest().getManifestRoot().getProto());
      module
          .getAssetsConfig()
          .ifPresent(
              assetsConfig ->
                  zipBuilder.addFileWithProtoContent(
                      moduleDir.resolve(BundleModule.ASSETS_PROTO_PATH), assetsConfig));
      module
          .getNativeConfig()
          .ifPresent(
              nativeConfig ->
                  zipBuilder.addFileWithProtoContent(
                      moduleDir.resolve(BundleModule.NATIVE_PROTO_PATH), nativeConfig));
      module
          .getResourceTable()
          .ifPresent(
              resourceTable ->
                  zipBuilder.addFileWithProtoContent(
                      moduleDir.resolve(BundleModule.RESOURCES_PROTO_PATH), resourceTable));
      module
          .getApexConfig()
          .ifPresent(
              apexConfig ->
                  zipBuilder.addFileWithProtoContent(
                      moduleDir.resolve(BundleModule.APEX_PROTO_PATH), apexConfig));
    }

    zipBuilder.writeTo(pathOnDisk);
  }
}
