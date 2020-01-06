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

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.ManifestBaseModuleExcludedFromFusingError;
import com.android.bundle.Errors.ManifestFusingMissingIncludeAttributeError;
import com.android.bundle.Errors.ManifestModuleFusingConfigurationMissingError;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Optional;

/** Exceptions relating to the module fusing element in the manifest */
public abstract class ManifestFusingException extends ManifestValidationException {

  @FormatMethod
  private ManifestFusingException(@FormatString String message, Object... args) {
    super(message, args);
  }

  /** Thrown when {@code <fusing include=boolean>} is missing */
  public static class FusingMissingIncludeAttribute extends ManifestFusingException {

    // The split in which the fusing attribute is missing, corresponds to (or empty if this error
    // corresponds to the base split's manifest).
    private final Optional<String> splitId;

    public FusingMissingIncludeAttribute(Optional<String> splitId) {
      super(
          "<fusing> element is missing the 'include' attribute%s.",
          splitId.map(id -> " (split: '" + id + "')").orElse("base"));
      this.splitId = splitId;
    }

    public Optional<String> getSplitId() {
      return splitId;
    }

    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestFusingMissingIncludeAttribute(
          ManifestFusingMissingIncludeAttributeError.newBuilder()
              .setModuleName(getSplitId().orElse("base")));
    }
  }
  
  /** Thrown when a bundle sets {@code <fusing include:false>} for the base module */
  public static class BaseModuleExcludedFromFusingException extends ManifestFusingException {
    public BaseModuleExcludedFromFusingException() {
      super("The base module cannot be excluded from fusing.");
    }
    
    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
       builder.setManifestFusingBaseModuleExcluded(
           ManifestBaseModuleExcludedFromFusingError.getDefaultInstance());
    }
  }
  
  /** Thrown when a (non-base) module does not configure its fusing specification, i.e.
   *  it does not declare <fusing include:true|false> */
  public static class ModuleFusingConfigurationMissingException extends ManifestFusingException {
    
    private final String moduleName;
    
    public ModuleFusingConfigurationMissingException(String moduleName) {
      super("Module '%s' must specify its fusing configuration in AndroidManifest.xml.",
          moduleName);
      this.moduleName = moduleName;
    }
    
    @Override
    protected void customizeProto(BundleToolError.Builder builder) {
      builder.setManifestFusingConfigurationMissing(
          ManifestModuleFusingConfigurationMissingError.newBuilder()
              .setModuleName(moduleName));
    }
  }
}
