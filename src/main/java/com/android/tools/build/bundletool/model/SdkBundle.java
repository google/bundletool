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

import static com.android.tools.build.bundletool.model.AndroidManifest.ANDROID_NAMESPACE_URI;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_MAJOR_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.AndroidManifest.SDK_PATCH_VERSION_ATTRIBUTE_NAME;
import static com.android.tools.build.bundletool.model.ModuleSplit.DEFAULT_SDK_PATCH_VERSION;
import static com.android.tools.build.bundletool.model.utils.BundleParser.extractModules;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readBundleConfig;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readBundleMetadata;
import static com.android.tools.build.bundletool.model.utils.BundleParser.sanitize;
import static com.google.common.base.Preconditions.checkState;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoElement;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.util.zip.ZipFile;

/** Represents an SDK bundle. */
@Immutable
@AutoValue
public abstract class SdkBundle implements Bundle {

  /** Top-level directory names that are not recognized as modules. */
  public static final ImmutableSet<ZipPath> NON_MODULE_DIRECTORIES =
      ImmutableSet.of(
          ZipPath.create("BUNDLE-METADATA"), ZipPath.create("META-INF"), ZipPath.create("aar"));

  /** Builds an {@link SdkBundle} from an SDK Bundle on disk. */
  public static SdkBundle buildFromZip(ZipFile bundleFile, Integer versionCode) {
    BundleConfig bundleConfig = readBundleConfig(bundleFile);

    return builder()
        .setModule(
            sanitize(extractModules(bundleFile, bundleConfig, NON_MODULE_DIRECTORIES)).get(0))
        .setBundleConfig(bundleConfig)
        .setBundleMetadata(readBundleMetadata(bundleFile))
        .setVersionCode(versionCode)
        .build();
  }

  public abstract BundleModule getModule();

  @Override
  public BundleModule getModule(BundleModuleName moduleName) {
    checkState(getModule().getName().equals(moduleName), "Module '%s' not found.", moduleName);
    return getModule();
  }

  @Override
  public abstract BundleConfig getBundleConfig();

  @Override
  public abstract BundleMetadata getBundleMetadata();

  public abstract Integer getVersionCode();

  public Version getBundletoolVersion() {
    return Version.of(getBundleConfig().getBundletool().getVersion());
  }

  @Override
  public String getPackageName() {
    return getModule().getAndroidManifest().getPackageName();
  }

  /**
   * Gets the Major Version of the SDK bundle. The Major Version is returned as String, but will
   * always be parseable as a long.
   */
  public String getMajorVersion() {
    XmlProtoElement sdkLibraryTag = getSdkLibraryTag();
    return sdkLibraryTag
        .getAttribute(ANDROID_NAMESPACE_URI, SDK_MAJOR_VERSION_ATTRIBUTE_NAME)
        .get()
        .getValueAsString();
  }

  /**
   * Gets the Patch Version of the SDK bundle. If Patch Version is not set, {@value
   * #DEFAULT_SDK_PATCH_VERSION} is returned.
   */
  public String getPatchVersion() {
    return getModule()
        .getAndroidManifest()
        .getMetadataValue(SDK_PATCH_VERSION_ATTRIBUTE_NAME)
        .orElse(DEFAULT_SDK_PATCH_VERSION);
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_SdkBundle.Builder();
  }

  /** Builder for SDK Bundle object */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setModule(BundleModule module);

    public abstract Builder setBundleConfig(BundleConfig bundleConfig);

    public abstract Builder setBundleMetadata(BundleMetadata bundleMetadata);

    public abstract Builder setVersionCode(Integer versionCode);

    public abstract SdkBundle build();
  }

  private XmlProtoElement getSdkLibraryTag() {
    return Iterables.getOnlyElement(getModule().getAndroidManifest().getSdkLibraryElements());
  }
}
