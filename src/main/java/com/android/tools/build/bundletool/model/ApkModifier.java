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
package com.android.tools.build.bundletool.model;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;

/** Modifier of APKs. */
public abstract class ApkModifier {

  /** An instance of {@link ApkModifier} which doesn't modify the APK. */
  public static final ApkModifier NO_OP = new ApkModifier() {};

  @CheckReturnValue
  public AndroidManifest modifyManifest(AndroidManifest manifest, ApkDescription apkDescription) {
    return manifest;
  }

  /** Description of an APK generated by bundletool. */
  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  public abstract static class ApkDescription {
    /** Builder for the {@link ApkDescription} class. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setVariantNumber(int variantNumber);

      public abstract Builder setBase(boolean isBase);

      public abstract Builder setApkType(ApkType apkType);

      public abstract Builder setVariantTargeting(VariantTargeting variantTargeting);

      public abstract Builder setApkTargeting(ApkTargeting apkTargeting);

      public abstract ApkDescription build();
    }

    /** Returns a fresh new {@link Builder} to create an instance of {@link ApkDescription}. */
    public static Builder builder() {
      return new AutoValue_ApkModifier_ApkDescription.Builder();
    }

    /**
     * For split APKs, returns whether this is the base module.
     *
     * <p>For standalone APKs, this is always set to {@code true}.
     */
    public abstract boolean isBase();

    /** Returns the type of APK. See {@link ApkType}. */
    public abstract ApkType getApkType();

    /**
     * Returns the variant number.
     *
     * <p>A variant is a single or group of APKs that share the same base AndroidManifest.xml.
     *
     * <p>Variants are numbered from 0 to numVariants - 1.
     */
    public abstract int getVariantNumber();

    /**
     * Returns the targeting of the variant the APK belongs to.
     *
     * <p>Note: Within a variant, split APKs are identified using the APK Targeting (see {@link
     * #getApkTargeting()}).
     */
    public abstract VariantTargeting getVariantTargeting();

    /**
     * Returns the targeting of the APK.
     *
     * <p>This differs from the targeting of the variant in that it is specific to this APK (as
     * opposed to shared with all the APKs in the variant). Applies to splits APKs.
     */
    public abstract ApkTargeting getApkTargeting();

    /** The type of APK. */
    public enum ApkType {
      /** Base split APK of a feature split, common to all devices. */
      MASTER_SPLIT,

      /** Config split APK of a feature split, specific to a device category. */
      CONFIG_SPLIT,

      /**
       * Standalone APK, served to pre-L devices for lack of split support.
       *
       * <p>Note: There is only one standalone APK per variant.
       */
      STANDALONE,
    }
  }
}
