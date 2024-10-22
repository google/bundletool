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
package com.android.tools.build.bundletool.commands;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class DumpSdkBundleManager {

  private final PrintStream printStream;
  private final Path bundlePath;

  DumpSdkBundleManager(OutputStream outputStream, Path bundlePath) {
    this.printStream = new PrintStream(outputStream);
    this.bundlePath = bundlePath;
  }

  void printManifest(Optional<String> xPathExpression) {
    // Extract the manifest from the bundle.
    ZipPath manifestPath =
        ZipPath.create(BundleModuleName.BASE_MODULE_NAME.getName())
            .resolve(SpecialModuleEntry.ANDROID_MANIFEST.getPath());
    XmlProtoNode manifestProto =
        new XmlProtoNode(
            DumpManagerUtils.extractAndParseFromSdkBundle(
                bundlePath, manifestPath, XmlNode::parseFrom));

    DumpManagerUtils.printManifest(manifestProto, xPathExpression, printStream);
  }

  void printResources(Predicate<ResourceTableEntry> resourcePredicate, boolean printValues) {
    ImmutableList<ResourceTable> resourceTables;
    try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
      ZipEntry zipEntry = zipFile.getEntry("modules.resm");
      resourceTables =
          ZipUtils.allFileEntriesPaths(new ZipInputStream(zipFile.getInputStream(zipEntry)))
              .stream()
              .filter(path -> path.endsWith(SpecialModuleEntry.RESOURCE_TABLE.getPath()))
              .map(
                  path ->
                      DumpManagerUtils.extractAndParseFromSdkBundle(
                          bundlePath, path, ResourceTable::parseFrom))
              .collect(toImmutableList());
    } catch (IOException e) {
      throw new UncheckedIOException("Error occurred when reading the bundle.", e);
    }
    DumpManagerUtils.printResources(resourcePredicate, printValues, resourceTables, printStream);
  }

  void printBundleConfig() {
    SdkModulesConfig bundleConfig =
        DumpManagerUtils.extractAndParseFromSdkBundle(
            bundlePath, ZipPath.create("SdkModulesConfig.pb"), SdkModulesConfig::parseFrom);
    DumpManagerUtils.printBundleConfig(bundleConfig, printStream);
  }
}
