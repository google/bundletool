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
package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.ClassesDexEntriesMutator.R_PACKAGE_DEX_ENTRY_REMOVER;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;

/**
 * Transforms Android SDK Bundle module so that it can be included in an Android App Bundle.
 *
 * <p>The SDK module will be delivered as the app module on devices with no SDK Runtime support.
 */
public final class SdkBundleModuleToAppBundleModuleConverter {

  private final SdkBundle sdkBundle;
  private final ResourceTablePackageIdRemapper resourceTablePackageIdRemapper;
  private final XmlPackageIdRemapper xmlPackageIdRemapper;
  private final ClassesDexEntriesMutator classesDexEntriesMutator;

  public SdkBundleModuleToAppBundleModuleConverter(
      SdkBundle sdkBundle, RuntimeEnabledSdk dependencyConfig) {
    this.sdkBundle = sdkBundle;
    this.resourceTablePackageIdRemapper =
        new ResourceTablePackageIdRemapper(dependencyConfig.getResourcesPackageId());
    this.xmlPackageIdRemapper = new XmlPackageIdRemapper(dependencyConfig.getResourcesPackageId());
    this.classesDexEntriesMutator = new ClassesDexEntriesMutator();
  }

  /**
   * Returns {@link SdkBundle#getModule()}, modified so that it can be added to an Android App
   * Bundle as a removable install-time module.
   */
  public BundleModule convert() {
    return convertNameTypeAndManifest(
        removeRPackageDexFile(
            remapResourceIdsInXmlResources(
                remapResourceIdsInResourceTable(sdkBundle.getModule()))));
  }

  private BundleModule remapResourceIdsInResourceTable(BundleModule module) {
    return resourceTablePackageIdRemapper.remap(module);
  }

  private BundleModule remapResourceIdsInXmlResources(BundleModule module) {
    return xmlPackageIdRemapper.remap(module);
  }

  private BundleModule removeRPackageDexFile(BundleModule module) {
    return classesDexEntriesMutator.applyMutation(module, R_PACKAGE_DEX_ENTRY_REMOVER);
  }

  private BundleModule convertNameTypeAndManifest(BundleModule module) {
    // We are using modified SDK package name as a new module name. Dots are removed because special
    // characters are not allowed in module names.
    String sdkModuleName = sdkBundle.getPackageName().replace(".", "");
    return module.toBuilder()
        .setName(BundleModuleName.create(sdkModuleName))
        .setModuleType(ModuleType.SDK_DEPENDENCY_MODULE)
        .setAndroidManifest(
            sdkBundle
                .getModule()
                .getAndroidManifest()
                .toEditor()
                .removeUsesSdkElement()
                .setSplitIdForFeatureSplit(sdkModuleName)
                .setDeliveryOptionsForRuntimeEnabledSdkModule()
                .save())
        .build();
  }
}
