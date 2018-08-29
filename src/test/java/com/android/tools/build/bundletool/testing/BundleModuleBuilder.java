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
package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.testing.AppBundleBuilder.DEFAULT_BUNDLE_CONFIG;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;

/** Builder to build a {@link BundleModule}. */
public class BundleModuleBuilder {

  private final ImmutableSet.Builder<ModuleEntry> entries = new ImmutableSet.Builder<>();

  private final BundleModuleName moduleName;

  private BundleConfig bundleConfig = DEFAULT_BUNDLE_CONFIG;

  public BundleModuleBuilder(String moduleName) {
    checkNotNull(moduleName);
    this.moduleName = BundleModuleName.create(moduleName);
  }

  public BundleModuleBuilder(String moduleName, BundleConfig bundleConfig) {
    this(moduleName);
    this.bundleConfig = bundleConfig;
  }

  public BundleModuleBuilder addFile(String relativePath, byte[] content) {
    entries.add(InMemoryModuleEntry.ofFile(relativePath, content));
    return this;
  }

  public BundleModuleBuilder addDirectory(String relativePath) {
    entries.add(InMemoryModuleEntry.ofDirectory(relativePath));
    return this;
  }

  public BundleModuleBuilder addFile(String relativePath) {
    return addFile(relativePath, new byte[1]);
  }

  public BundleModuleBuilder setNativeConfig(NativeLibraries nativeConfig) {
    addFile("native.pb", nativeConfig.toByteArray());
    return this;
  }

  public BundleModuleBuilder setAssetsConfig(Assets assetsConfig) {
    addFile("assets.pb", assetsConfig.toByteArray());
    return this;
  }

  public BundleModuleBuilder setResourceTable(ResourceTable resourceTable) {
    addFile("resources.pb", resourceTable.toByteArray());
    return this;
  }

  public BundleModuleBuilder setManifest(XmlNode androidManifest) {
    addFile("manifest/AndroidManifest.xml", androidManifest.toByteArray());
    return this;
  }

  public BundleModule build() throws IOException {
    return BundleModule.builder()
        .setName(moduleName)
        .addEntries(entries.build())
        .setBundleConfig(bundleConfig)
        .build();
  }
}
