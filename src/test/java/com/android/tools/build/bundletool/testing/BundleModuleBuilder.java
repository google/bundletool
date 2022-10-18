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
import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNodeBuilder;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.Optional;

/** Builder to build a {@link BundleModule}. */
public class BundleModuleBuilder {

  private final ImmutableSet.Builder<ModuleEntry> entries = new ImmutableSet.Builder<>();

  private final BundleModuleName moduleName;

  private BundleConfig bundleConfig = DEFAULT_BUNDLE_CONFIG;

  private XmlNode androidManifest = null;

  private Optional<ModuleType> moduleTypeOptional = Optional.empty();

  private Optional<SdkModulesConfig> sdkModulesConfigOptional = Optional.empty();

  public BundleModuleBuilder(String moduleName) {
    checkNotNull(moduleName);
    this.moduleName = BundleModuleName.create(moduleName);
  }

  public BundleModuleBuilder(String moduleName, BundleConfig bundleConfig) {
    this(moduleName);
    this.bundleConfig = bundleConfig;
  }

  public BundleModuleBuilder addFile(String relativePath, byte[] content) {
    entries.add(
        ModuleEntry.builder()
            .setPath(ZipPath.create(relativePath))
            .setContent(ByteSource.wrap(content))
            .build());
    return this;
  }

  /**
   * Adds an entry that should be sourced from a zip file.
   *
   * <p>The content will not be loaded.
   *
   * @param relativePath the file path in the module that is being constructed
   * @param zipFilePath location of the on-disk zip file
   * @param entryFullZipPath the entry path inside the zip file
   */
  public BundleModuleBuilder addFile(
      String relativePath, Path zipFilePath, ZipPath entryFullZipPath) {
    entries.add(
        ModuleEntry.builder()
            .setContent(ByteSource.empty())
            .setPath(ZipPath.create(relativePath))
            .setFileLocation(ModuleEntryLocationInZipSource.create(zipFilePath, entryFullZipPath))
            .build());
    return this;
  }

  /**
   * Adds an entry that should be sourced from a zip file.
   *
   * @param relativePath the file path in the module that is being constructed
   * @param zipFilePath location of the on-disk zip file
   * @param entryFullZipPath the entry path inside the zip file
   * @param content the contents of the file
   */
  public BundleModuleBuilder addFile(
      String relativePath, Path zipFilePath, ZipPath entryFullZipPath, byte[] content) {
    entries.add(
        ModuleEntry.builder()
            .setContent(ByteSource.wrap(content))
            .setPath(ZipPath.create(relativePath))
            .setFileLocation(ModuleEntryLocationInZipSource.create(zipFilePath, entryFullZipPath))
            .build());
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

  public BundleModuleBuilder setApexConfig(ApexImages apexConfig) {
    addFile("apex.pb", apexConfig.toByteArray());
    return this;
  }

  public BundleModuleBuilder setRuntimeEnabledSdkConfig(
      RuntimeEnabledSdkConfig runtimeEnabledSdkConfig) {
    addFile("runtime_enabled_sdk_config.pb", runtimeEnabledSdkConfig.toByteArray());
    return this;
  }

  @CanIgnoreReturnValue
  public BundleModuleBuilder setSdkModulesConfig(SdkModulesConfig sdkModulesConfig) {
    this.sdkModulesConfigOptional = Optional.of(sdkModulesConfig);
    return this;
  }

  public BundleModuleBuilder setManifest(XmlNode androidManifest) {
    this.androidManifest = androidManifest;
    return this;
  }

  public BundleModuleBuilder setModuleType(ModuleType moduleType) {
    this.moduleTypeOptional = Optional.of(moduleType);
    return this;
  }

  public BundleModule build() {
    if (androidManifest != null) {
      XmlProtoNodeBuilder manifestBuilder = new XmlProtoNode(androidManifest).toBuilder();
      boolean hasCode =
          entries.build().stream().anyMatch(entry -> entry.getPath().toString().endsWith(".dex"));
      if (hasCode) {
        ManifestProtoUtils.clearHasCode().accept(manifestBuilder.getElement());
      }

      // Set the 'split' attribute if one is not already set.
      if (!moduleName.equals(BundleModuleName.BASE_MODULE_NAME)
          && !manifestBuilder.getElement().getAttribute("split").isPresent()) {
        ManifestProtoUtils.withSplitId(moduleName.getName()).accept(manifestBuilder.getElement());
      }

      addFile("manifest/AndroidManifest.xml", manifestBuilder.build().getProto().toByteArray());
    }

    BundleModule.Builder bundleModuleBuilder =
        BundleModule.builder()
            .setName(moduleName)
            .addEntries(entries.build())
            .setBundleType(bundleConfig.getType())
            .setBundletoolVersion(BundleToolVersion.getCurrentVersion());
    moduleTypeOptional.ifPresent(bundleModuleBuilder::setModuleType);
    sdkModulesConfigOptional.ifPresent(bundleModuleBuilder::setSdkModulesConfig);

    if (!bundleConfig.getBundletool().getVersion().isEmpty()) {
      bundleModuleBuilder.setBundletoolVersion(
          Version.of(bundleConfig.getBundletool().getVersion()));
    }
    if (bundleConfig.hasApexConfig()) {
      bundleModuleBuilder.setBundleApexConfig(bundleConfig.getApexConfig());
    }
    return bundleModuleBuilder.build();
  }
}
