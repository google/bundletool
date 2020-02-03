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
import com.android.bundle.Errors.ManifestInvalidVersionCodeError;
import com.android.bundle.Errors.ManifestMissingVersionCodeError;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/**
 * Exceptions relating to android:versionCode and android:versionName
 */
public abstract class ManifestVersionException extends ManifestValidationException {

  @FormatMethod
  private ManifestVersionException(@FormatString String message, Object... args) {
    super(message, args);
  }

  /** Thrown when android:versionCode is missing */
  public static class VersionCodeMissingException extends ManifestVersionException {
    public VersionCodeMissingException() {
      super("Version code not found in manifest.");
    }
    
    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestMissingVersionCode(ManifestMissingVersionCodeError.getDefaultInstance());
    }
  }

  /** Thrown when android:versionCode is invalid */
  public static class VersionCodeInvalidException extends ManifestVersionException {
    public VersionCodeInvalidException(XmlAttribute attribute) {
      this(attribute.getValue());
    }

    public VersionCodeInvalidException(String value) {
      super("Type of versionCode is expected to be decimal integer, found: '%s'.", value);
    }
    
    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestInvalidVersionCode(ManifestInvalidVersionCodeError.getDefaultInstance());
    }
  }
}
