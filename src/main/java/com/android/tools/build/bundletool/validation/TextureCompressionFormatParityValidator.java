/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractAssetsTargetedDirectories;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractTextureCompressionFormats;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectory;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/**
 * Validate that all modules that contain directories with targeted texture formats support the same
 * set of texture formats (including "fallback" directories for untargeted textures).
 */
public class TextureCompressionFormatParityValidator extends SubValidator {

  /**
   * Represents a set of texture formats and the presence or not of directories with untargeted
   * textures.
   */
  @AutoValue
  public abstract static class SupportedTextureCompressionFormats {
    public static SupportedTextureCompressionFormats create(
        ImmutableSet<TextureCompressionFormatAlias> formats, boolean hasFallback) {
      return new AutoValue_TextureCompressionFormatParityValidator_SupportedTextureCompressionFormats(
          formats, hasFallback);
    }

    public abstract ImmutableSet<TextureCompressionFormatAlias> getFormats();

    public abstract boolean getHasFallback();

    @Override
    public final String toString() {
      return getFormats().toString()
          + (getHasFallback() ? " (with fallback directories)" : " (without fallback directories)");
    }
  }

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    BundleModule referentialModule = null;
    SupportedTextureCompressionFormats referentialTextureCompressionFormats = null;
    for (BundleModule module : modules) {
      SupportedTextureCompressionFormats moduleTextureCompressionFormats =
          getSupportedTextureCompressionFormats(module);

      if (moduleTextureCompressionFormats.getFormats().isEmpty()) {
        continue;
      }

      if (referentialTextureCompressionFormats == null) {
        referentialModule = module;
        referentialTextureCompressionFormats = moduleTextureCompressionFormats;
      } else if (!referentialTextureCompressionFormats.equals(moduleTextureCompressionFormats)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "All modules with targeted textures must have the same set of texture formats, but"
                    + " module '%s' has formats %s and module '%s' has formats %s.",
                referentialModule.getName(),
                referentialTextureCompressionFormats,
                module.getName(),
                moduleTextureCompressionFormats)
            .build();
      }
    }
  }

  private static SupportedTextureCompressionFormats getSupportedTextureCompressionFormats(
      BundleModule module) {
    // Extract targeted directories from entries (like done when generating assets targeting)
    ImmutableSet<TargetedDirectory> targetedDirectories = extractAssetsTargetedDirectories(module);

    // Inspect the targetings to extract texture compression formats.
    ImmutableSet<TextureCompressionFormatAlias> formats =
        extractTextureCompressionFormats(targetedDirectories);

    // Check if one or more targeted directories have "fallback" sibling directories.
    boolean hasFallback =
        targetedDirectories.stream()
            .anyMatch(
                directory -> {
                  Optional<AssetsDirectoryTargeting> targeting =
                      directory.getTargeting(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
                  if (targeting.isPresent()) {
                    // Check if a sibling folder without texture targeting exists. If yes, this is
                    // called a "fallback".
                    TargetedDirectory siblingFallbackDirectory =
                        directory.removeTargeting(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
                    return module
                        .findEntriesUnderPath(siblingFallbackDirectory.toZipPath())
                        .findAny()
                        .isPresent();
                  }

                  return false;
                });

    return SupportedTextureCompressionFormats.create(formats, hasFallback);
  }
}
