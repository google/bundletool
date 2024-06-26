/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMinSdk;
import static com.google.common.primitives.Ints.max;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Allows clients to provide a {@link SigningConfiguration} for each generated APK. */
public interface SigningConfigurationProvider {

  /** Invoked before signing each generated {@link ModuleSplit}. */
  ApksigSigningConfiguration getSigningConfiguration(ApkDescription apkDescription);

  /**
   * Checks if the signing configuration provided includes signing with rotated keys using V3
   * signature restricted for specifc Android SDK versions.
   */
  boolean hasRestrictedV3SigningConfig();

  /** Description of an APK generated by bundletool. */
  @AutoValue
  public abstract class ApkDescription {

    /** Minimum SDK version from Android manifest. */
    public abstract int getMinSdkVersionFromManifest();

    /** Targeting of the APK. */
    public abstract ApkTargeting getApkTargeting();

    /** Targeting of the variant. */
    public abstract VariantTargeting getVariantTargeting();

    /** Version code of APK. */
    public abstract Optional<Integer> getVersionCode();

    /** Variant/Derived id of APK. */
    public abstract Optional<String> getVariantId();

    /** Module name of split. */
    public abstract String getModuleName();

    /** Package name of app. */
    public abstract String getPackageName();

    /** The split type being represented by this split. */
    public abstract SplitType getSplitType();

    /** Split/Asset name of the split/AssetPack. */
    public abstract Optional<String> getSplitName();

    /**
     * Minimum platform API version that the APK will be installed on. This is derived as the
     * highest version out of the minSdkVersion (from Android manifest), the ApkTargeting, and the
     * VariantTargeting.
     */
    public int getMinSdkVersionTargeting() {
      int minApkTargetingSdkVersion = getMinSdk(getApkTargeting().getSdkVersionTargeting());
      int minVariantTargetingSdkVersion = getMinSdk(getVariantTargeting().getSdkVersionTargeting());
      return max(
          getMinSdkVersionFromManifest(), minApkTargetingSdkVersion, minVariantTargetingSdkVersion);
    }

    public static ApkDescription fromModuleSplit(ModuleSplit moduleSplit) {
      AndroidManifest androidManifest = moduleSplit.getAndroidManifest();
      return builder()
          .setMinSdkVersionFromManifest(androidManifest.getEffectiveMinSdkVersion())
          .setApkTargeting(moduleSplit.getApkTargeting())
          .setVariantTargeting(moduleSplit.getVariantTargeting())
          .setVersionCode(androidManifest.getVersionCode())
          .setModuleName(moduleSplit.getModuleName().getName())
          .setSplitType(moduleSplit.getSplitType())
          .setSplitName(androidManifest.getSplitId())
          .setPackageName(androidManifest.getPackageName())
          .build();
    }

    public static Builder builder() {
      return new AutoValue_SigningConfigurationProvider_ApkDescription.Builder();
    }

    /** Builder for {@link ApkDescription}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMinSdkVersionFromManifest(int minSdkVersionFromManifest);

      public abstract Builder setApkTargeting(ApkTargeting apkTargeting);

      public abstract Builder setVariantTargeting(VariantTargeting variantTargeting);

      public abstract Builder setVersionCode(Optional<Integer> versionCode);

      public abstract Builder setModuleName(String moduleName);

      public abstract Builder setPackageName(String packageName);

      public abstract Builder setVariantId(Optional<String> variantId);

      public abstract Builder setSplitType(SplitType splitType);

      public abstract Builder setSplitName(Optional<String> splitName);

      public abstract ApkDescription build();
    }
  }
}
