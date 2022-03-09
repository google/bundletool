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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMetadataValue;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSdkLibraryElement;

import com.android.aapt.Resources.XmlNode;
import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.SdkBundle;
import java.util.zip.ZipFile;

/**
 * Builder of {@link SdkBundle} instances in tests.
 *
 * <p>Note that the SdkBundle is created in-memory. To test an SdkBundle created from a ZIP file,
 * use {@link com.android.tools.build.bundletool.io.ZipBuilder} then call {@link
 * SdkBundle#buildFromZip(ZipFile, Integer)}.
 */
public class SdkBundleBuilder {

  public static final BundleConfig DEFAULT_BUNDLE_CONFIG = BundleConfigBuilder.create().build();

  public static final String PACKAGE_NAME = "com.test.sdk";

  private static final BundleMetadata METADATA = BundleMetadata.builder().build();

  private BundleModule module = defaultModule();

  private Integer versionCode = 1;

  private static BundleModule defaultModule() {
    return new BundleModuleBuilder("base").setManifest(createSdkAndroidManifest()).build();
  }

  private static XmlNode createSdkAndroidManifest() {
    return androidManifest(
        PACKAGE_NAME,
        withMinSdkVersion(32),
        withSdkLibraryElement("15"),
        withMetadataValue(SDK_PATCH_VERSION_ATTRIBUTE_NAME, "5"));
  }

  public SdkBundleBuilder setModule(BundleModule module) {
    this.module = module;
    return this;
  }

  public SdkBundleBuilder setVersionCode(Integer versionCode) {
    this.versionCode = versionCode;
    return this;
  }

  public SdkBundle build() {
    return SdkBundle.builder()
        .setModule(module)
        .setBundleConfig(DEFAULT_BUNDLE_CONFIG)
        .setBundleMetadata(METADATA)
        .setVersionCode(versionCode)
        .build();
  }
}
