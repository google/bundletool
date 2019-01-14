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

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * Builder of {@link AppBundle} instances in tests.
 *
 * <p>Note that the AppBundle is created in-memory. To test an AppBundle created from a ZIP file,
 * use {@link com.android.tools.build.bundletool.io.ZipBuilder} then call {@link
 * AppBundle#buildFromZip(ZipFile)}
 */
public class AppBundleBuilder {

  static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  private final ImmutableList.Builder<BundleModule> modules = ImmutableList.builder();

  private final BundleMetadata.Builder metadata = BundleMetadata.builder();

  private BundleConfig bundleConfig = DEFAULT_BUNDLE_CONFIG;

  public AppBundleBuilder addModule(String name, Consumer<BundleModuleBuilder> operations)
      throws IOException {
    BundleModuleBuilder moduleBuilder = new BundleModuleBuilder(name);
    operations.accept(moduleBuilder);
    modules.add(moduleBuilder.build());
    return this;
  }

  public AppBundleBuilder addMetadataFile(String namespacedDir, String fileName, Path file) {
    metadata.addFile(namespacedDir, fileName, BufferedIo.inputStreamSupplier(file));
    return this;
  }

  public AppBundleBuilder setBundleConfig(BundleConfig bundleConfig) {
    this.bundleConfig = bundleConfig;
    return this;
  }

  public AppBundle build() {
    return AppBundle.buildFromModules(modules.build(), bundleConfig, metadata.build());
  }
}
