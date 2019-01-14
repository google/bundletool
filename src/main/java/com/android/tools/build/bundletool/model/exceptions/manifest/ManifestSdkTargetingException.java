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

package com.android.tools.build.bundletool.model.exceptions.manifest;

import com.android.aapt.Resources.XmlAttribute;
import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.ManifestMaxSdkInvalidError;
import com.android.bundle.Errors.ManifestMaxSdkLessThanMinInstantSdkError;
import com.android.bundle.Errors.ManifestMinSdkGreaterThanMaxSdkError;
import com.android.bundle.Errors.ManifestMinSdkInvalidError;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/**
 * Exceptions relating to minSdkVersion and maxSdkVersion
 */
public abstract class ManifestSdkTargetingException extends ManifestValidationException {

  @FormatMethod
  private ManifestSdkTargetingException(@FormatString String message, Object... args) {
    super(message, args);
  }
  
  /** Thrown when {@code <uses-sdk android:minSdkVersion>} is invalid */
  public static class MaxSdkInvalidException extends ManifestSdkTargetingException {

    private final String maxSdk;

    public MaxSdkInvalidException(XmlAttribute attribute) {
      super("Type of maxSdkVersion is expected to be decimal integer, found: '%s'.",
          attribute.getValue());
      this.maxSdk = attribute.getValue();
    }
    
    public MaxSdkInvalidException(int value) {
      super("maxSdkVersion must be nonnegative, found: (%d).", value);
      this.maxSdk = Integer.toString(value);
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestMaxSdkInvalid(ManifestMaxSdkInvalidError.newBuilder().setMaxSdk(maxSdk));
    }
  }

  /** Thrown when {@code <uses-sdk android:maxSdkVersion>} is invalid */
  public static class MinSdkInvalidException extends ManifestSdkTargetingException {

    private final String minSdk;

    public MinSdkInvalidException(XmlAttribute attribute) {
      super("Type of minSdkVersion is expected to be decimal integer, found: '%s'.",
          attribute.getValue());
      this.minSdk = attribute.getValue();
    }
    
    public MinSdkInvalidException(int value) {
      super("minSdkVersion must be nonnegative, found: (%d).", value);
      this.minSdk = Integer.toString(value);
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestMinSdkInvalid(ManifestMinSdkInvalidError.newBuilder().setMinSdk(minSdk));
    }
  }
  
  /** Thrown when {@code android:minSdkVersion} is greater than {@code android:maxSdkVersion}  */
  public static class MinSdkGreaterThanMaxSdkException extends ManifestSdkTargetingException {

    private final int minSdk;
    private final int maxSdk;

    public MinSdkGreaterThanMaxSdkException(int minSdk, int maxSdk) {
      super("minSdkVersion (%d) is greater than maxSdkVersion (%d).", minSdk, maxSdk);
      this.minSdk = minSdk;
      this.maxSdk = maxSdk;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestMinSdkGreaterThanMax(
          ManifestMinSdkGreaterThanMaxSdkError.newBuilder().setMinSdk(minSdk).setMaxSdk(maxSdk));
    }
  }

  /** Thrown when {@code android:maxSdkVersion} is less than 21 */
  public static class MaxSdkLessThanMinInstantSdk extends ManifestSdkTargetingException {

    public static final int MIN_INSTANT_SDK_VERSION = 21;
    private final int maxSdk;

    public MaxSdkLessThanMinInstantSdk(int maxSdk) {
      super(
          "maxSdkVersion (%d) is less than minimum sdk allowed for instant apps (%d).",
          maxSdk, MIN_INSTANT_SDK_VERSION);
      this.maxSdk = maxSdk;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestMaxSdkLessThanMinInstantSdk(
          ManifestMaxSdkLessThanMinInstantSdkError.newBuilder().setMaxSdk(maxSdk));
    }
  }
}
