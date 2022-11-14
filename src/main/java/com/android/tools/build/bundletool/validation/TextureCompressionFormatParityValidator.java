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
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectory;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;

/**
 * Validate that all modules that contain directories with targeted texture formats:
 *
 * <ul>
 *   <li>Support the same set of texture formats (including "fallback" directories for untargeted
 *       textures).
 *   <li>Default texture format in the bundle configuration is included in all modules with TCF
 *       targeting.
 * </ul>
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
      return getFormats()
          + (getHasFallback() ? " (with fallback directories)" : " (without fallback directories)");
    }
  }

  @Override
  public void validateBundle(AppBundle bundle) {
    BundleConfig bundleConfig = bundle.getBundleConfig();
    Optimizations optimizations = bundleConfig.getOptimizations();
    List<SplitDimension> splitDimensions = optimizations.getSplitsConfig().getSplitDimensionList();

    Optional<String> tcfDefaultSuffix =
        splitDimensions.stream()
            .filter(dimension -> dimension.getValue().equals(Value.TEXTURE_COMPRESSION_FORMAT))
            .map(dimension -> dimension.getSuffixStripping().getDefaultSuffix())
            .collect(toOptional());

    if (tcfDefaultSuffix.isPresent()) {
      // Get the default texture compression format targeting, or an empty optional if fallback
      // must be used.
      Optional<TextureCompressionFormatTargeting> defaultTextureCompressionFormat =
          Optional.ofNullable(
              TextureCompressionUtils.TEXTURE_TO_TARGETING.get(tcfDefaultSuffix.get()));

      validateFormatSupportedByAllModules(bundle, defaultTextureCompressionFormat);
    }
  }

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    validateAllModulesSupportSameFormats(modules);
  }

  /** Ensure the specified texture format is included in all modules. */
  private static void validateFormatSupportedByAllModules(
      AppBundle bundle,
      Optional<TextureCompressionFormatTargeting> defaultTextureCompressionFormat) {
    bundle
        .getModules()
        .values()
        .forEach(
            module -> {
              SupportedTextureCompressionFormats moduleTextureCompressionFormats =
                  getSupportedTextureCompressionFormats(module);

              if (moduleTextureCompressionFormats.getFormats().isEmpty()) {
                return;
              }

              if (!defaultTextureCompressionFormat.isPresent()) {
                if (!moduleTextureCompressionFormats.getHasFallback()) {
                  throw InvalidBundleException.builder()
                      .withUserMessage(
                          "When a standalone or universal APK is built, the fallback texture"
                              + " folders (folders without #tcf suffixes) will be used, but module"
                              + " '%s' has no such folders. Instead, it has folder(s) targeted for"
                              + " formats %s. Either add missing folders or change the"
                              + " configuration for the TEXTURE_COMPRESSION_FORMAT optimization to"
                              + " specify a default suffix corresponding to the format to use in"
                              + " the standalone and universal APKs.",
                          module.getName(), moduleTextureCompressionFormats)
                      .build();
                }
              } else {
                TextureCompressionFormatAlias defaultTextureCompressionFormatAlias =
                    defaultTextureCompressionFormat.get().getValue(0).getAlias();

                if (!moduleTextureCompressionFormats
                    .getFormats()
                    .contains(defaultTextureCompressionFormatAlias)) {
                  throw InvalidBundleException.builder()
                      .withUserMessage(
                          "When a standalone or universal APK is built, the texture folders for"
                              + " format '%s' will be used, but module '%s' has no such folders."
                              + " Instead, it has folder(s) targeted for formats %s. Either add"
                              + " missing folders or change the configuration for the"
                              + " TEXTURE_COMPRESSION_FORMAT optimization to specify a default"
                              + " suffix corresponding to the format to use in the standalone and"
                              + " universal APKs.",
                          defaultTextureCompressionFormatAlias,
                          module.getName(),
                          moduleTextureCompressionFormats)
                      .build();
                }
              }
            });
  }

  /** Ensure the textutre formats are consistent across all modules. */
  private static void validateAllModulesSupportSameFormats(ImmutableList<BundleModule> modules) {
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
