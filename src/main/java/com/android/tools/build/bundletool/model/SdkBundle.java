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

import static com.android.tools.build.bundletool.model.utils.BundleParser.extractModules;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readBundleMetadata;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readSdkBundleConfig;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readSdkInterfaceDescriptors;
import static com.android.tools.build.bundletool.model.utils.BundleParser.readSdkModulesConfig;
import static com.android.tools.build.bundletool.model.utils.BundleParser.sanitize;
import static com.google.common.base.Preconditions.checkState;

import com.android.bundle.Config.BundleConfig.BundleType;
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import java.util.zip.ZipFile;

/** Represents an SDK bundle. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
@SuppressWarnings("Immutable") // ByteSource is not @Immutable.
public abstract class SdkBundle implements Bundle {

  /** Builds an {@link SdkBundle} from an SDK Bundle on disk. */
  public static SdkBundle buildFromZip(
      ZipFile bundleFile, ZipFile modulesFile, Integer versionCode) {
    SdkModulesConfig sdkModulesConfig = readSdkModulesConfig(modulesFile);
    SdkBundleConfig sdkBundleConfig = readSdkBundleConfig(bundleFile);
    Optional<ByteSource> sdkInterfaceDescriptors = readSdkInterfaceDescriptors(bundleFile);

    Builder bundle =
        builder()
            .setModule(
                sanitize(
                        extractModules(
                            modulesFile,
                            BundleType.REGULAR,
                            Version.of(sdkModulesConfig.getBundletool().getVersion()),
                            /* apexConfig= */ Optional.empty(),
                            /* nonModuleDirectories= */ ImmutableSet.of()))
                    .get(0))
            .setSdkModulesConfig(sdkModulesConfig)
            .setSdkBundleConfig(sdkBundleConfig)
            .setBundleMetadata(readBundleMetadata(bundleFile))
            .setVersionCode(versionCode);
    sdkInterfaceDescriptors.ifPresent(bundle::setSdkInterfaceDescriptors);
    return bundle.build();
  }

  public abstract BundleModule getModule();

  @Override
  public BundleModule getModule(BundleModuleName moduleName) {
    checkState(getModule().getName().equals(moduleName), "Module '%s' not found.", moduleName);
    return getModule();
  }

  @Override
  public ImmutableMap<BundleModuleName, BundleModule> getModules() {
    BundleModule module = getModule();
    return ImmutableMap.of(module.getName(), module);
  }

  public abstract SdkModulesConfig getSdkModulesConfig();

  public abstract SdkBundleConfig getSdkBundleConfig();

  @Override
  public abstract BundleMetadata getBundleMetadata();

  public abstract Optional<Integer> getVersionCode();

  public abstract Optional<ByteSource> getSdkInterfaceDescriptors();

  public Version getBundletoolVersion() {
    return Version.of(getSdkModulesConfig().getBundletool().getVersion());
  }

  /**
   * Gets the SDK package name.
   *
   * <p>Note that this is different from the package name used in the APK AndroidManifest, which is
   * a combination of the SDK package name and its Android version major.
   */
  @Override
  public String getPackageName() {
    return getSdkModulesConfig().getSdkPackageName();
  }

  /** Gets the major version of the SDK bundle. */
  public int getMajorVersion() {
    return getSdkModulesConfig().getSdkVersion().getMajor();
  }

  /** Gets the minor version of the SDK bundle. */
  public int getMinorVersion() {
    return getSdkModulesConfig().getSdkVersion().getMinor();
  }

  /** Gets the patch version of the SDK bundle. */
  public int getPatchVersion() {
    return getSdkModulesConfig().getSdkVersion().getPatch();
  }

  /** Gets the platform SDK provider class name. */
  public String getProviderClassName() {
    return getSdkModulesConfig().getSdkProviderClassName();
  }

  /** Gets the Jetpack SDK provider entrypoint class name. */
  public Optional<String> getCompatProviderClassName() {
    return getSdkModulesConfig().getCompatSdkProviderClassName().isEmpty()
        ? Optional.empty()
        : Optional.of(getSdkModulesConfig().getCompatSdkProviderClassName());
  }

  /**
   * Gets the android:versionMajor as represented in the <sdk-library> tag.
   *
   * <p>This is a combination of the SDK major and minor version.
   *
   * <p>For instance, for an SDK with version 2.3.4, the android:versionMajor is 20003.
   */
  public int getSdkAndroidVersionMajor() {
    return RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(
        getMajorVersion(), getMinorVersion());
  }

  /** Gets the version name of the SDK bundle. */
  public String getVersionName() {
    return getMajorVersion() + "." + getMinorVersion() + "." + getPatchVersion();
  }

  /**
   * Gets the SDK package name concatenated with the SDK Android version major.
   *
   * <p>For instance, for an SDK with package name com.foo.bar and version 2.3.4, the manifest
   * package name is com.foo.bar_20003.
   */
  public String getManifestPackageName() {
    return getPackageName() + "_" + getSdkAndroidVersionMajor();
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_SdkBundle.Builder();
  }

  /** Builder for SDK Bundle object */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setModule(BundleModule module);

    public abstract Builder setSdkModulesConfig(SdkModulesConfig sdkModulesConfig);

    public abstract Builder setSdkBundleConfig(SdkBundleConfig sdkBundleConfig);

    public abstract Builder setBundleMetadata(BundleMetadata bundleMetadata);

    public abstract Builder setVersionCode(Integer versionCode);

    public abstract Builder setSdkInterfaceDescriptors(ByteSource source);

    public abstract SdkBundle build();
  }
}
