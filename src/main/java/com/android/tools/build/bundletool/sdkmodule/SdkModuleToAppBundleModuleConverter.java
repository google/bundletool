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
package com.android.tools.build.bundletool.sdkmodule;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.SdkSplitPropertiesInheritedFromApp;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.SdkBundle;

/**
 * Transforms Runtime-enabled SDK module so that it can be included in an Android App Bundle.
 *
 * <p>The SDK module will be delivered as the app module on devices with no SDK Runtime support.
 */
public final class SdkModuleToAppBundleModuleConverter {

  private final BundleModule sdkModule;
  private final ResourceTablePackageIdRemapper resourceTablePackageIdRemapper;
  private final XmlPackageIdRemapper xmlPackageIdRemapper;
  private final DexAndResourceRepackager dexAndResourceRepackager;
  private final AndroidResourceRenamer androidResourceRenamer;
  private final SdkSplitPropertiesInheritedFromApp inheritedAppProperties;

  public SdkModuleToAppBundleModuleConverter(
      BundleModule sdkModule,
      RuntimeEnabledSdk sdkDependencyConfig,
      AndroidManifest appBaseModuleManifest) {
    this(
        sdkModule,
        SdkSplitPropertiesInheritedFromApp.newBuilder()
            .setPackageName(appBaseModuleManifest.getPackageName())
            .setVersionCode(appBaseModuleManifest.getVersionCode().get())
            .setMinSdkVersion(appBaseModuleManifest.getMinSdkVersion().get())
            .setResourcesPackageId(sdkDependencyConfig.getResourcesPackageId())
            .build());
  }

  public SdkModuleToAppBundleModuleConverter(
      BundleModule sdkModule, SdkSplitPropertiesInheritedFromApp inheritedAppProperties) {
    this.sdkModule = sdkModule;
    this.resourceTablePackageIdRemapper =
        new ResourceTablePackageIdRemapper(inheritedAppProperties.getResourcesPackageId());
    this.xmlPackageIdRemapper =
        new XmlPackageIdRemapper(inheritedAppProperties.getResourcesPackageId());
    this.dexAndResourceRepackager =
        new DexAndResourceRepackager(sdkModule.getSdkModulesConfig().get(), inheritedAppProperties);
    this.androidResourceRenamer = new AndroidResourceRenamer(sdkModule.getSdkModulesConfig().get());
    this.inheritedAppProperties = inheritedAppProperties;
  }

  /**
   * Returns {@link SdkBundle#getModule()}, modified so that it can be added to an Android App
   * Bundle as a removable install-time module.
   */
  public BundleModule convert() {
    return renameAndroidResources(
        repackageDexAndJavaResources(
            remapResourceIdsInResourceTable(
                remapResourceIdsInXmlResources(convertNameTypeAndManifest(sdkModule)))));
  }

  private BundleModule remapResourceIdsInResourceTable(BundleModule module) {
    return resourceTablePackageIdRemapper.remap(module);
  }

  private BundleModule remapResourceIdsInXmlResources(BundleModule module) {
    return xmlPackageIdRemapper.remap(module);
  }

  private BundleModule repackageDexAndJavaResources(BundleModule module) {
    return dexAndResourceRepackager.repackage(module);
  }

  private BundleModule renameAndroidResources(BundleModule module) {
    return androidResourceRenamer.renameAndroidResources(module);
  }

  private BundleModule convertNameTypeAndManifest(BundleModule module) {
    // We are using modified SDK package name as a new module name. Dots are removed because special
    // characters are not allowed in module names.
    String sdkModuleName =
        sdkModule.getSdkModulesConfig().get().getSdkPackageName().replace(".", "");
    return module.toBuilder()
        .setName(BundleModuleName.create(sdkModuleName))
        .setModuleType(ModuleType.SDK_DEPENDENCY_MODULE)
        .setAndroidManifest(
            module
                .getAndroidManifest()
                .toEditor()
                .setPackage(inheritedAppProperties.getPackageName())
                .setVersionCode(inheritedAppProperties.getVersionCode())
                .removeUsesSdkElement()
                .setMinSdkVersion(inheritedAppProperties.getMinSdkVersion())
                .setHasCode(false)
                .setSplitIdForFeatureSplit(sdkModuleName)
                .setDeliveryOptionsForRuntimeEnabledSdkModule()
                .save())
        .build();
  }
}
