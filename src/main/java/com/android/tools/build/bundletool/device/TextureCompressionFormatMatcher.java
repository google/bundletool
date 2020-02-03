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

package com.android.tools.build.bundletool.device;

import static com.android.tools.build.bundletool.model.targeting.TargetingComparators.sortTextureCompressionFormat;
import static com.android.tools.build.bundletool.model.utils.TextureCompressionUtils.textureCompressionFormat;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Streams;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link TargetingDimensionMatcher} that provides matching on texture compression format.
 *
 * <p>It matches the best Texture Compression Format supported on the device, as described by the
 * device supported OpenGL extensions and device supported OpenGL version.
 */
public class TextureCompressionFormatMatcher
    extends TargetingDimensionMatcher<TextureCompressionFormatTargeting> {
  private static final String GL_ES_VERSION_FEATURE_PREFIX = "reqGlEsVersion=";
  private final ImmutableList<String> deviceGlExtensions;
  private final ImmutableSet<TextureCompressionFormatAlias>
      deviceSupportedTextureCompressionFormats;

  public TextureCompressionFormatMatcher(DeviceSpec deviceSpec) {
    super(deviceSpec);
    this.deviceGlExtensions = ImmutableList.copyOf(deviceSpec.getGlExtensionsList());

    ImmutableSet<TextureCompressionFormatAlias> glExtensionSupportedFormats =
        this.deviceGlExtensions.stream()
            .map(glExtension -> textureCompressionFormat(glExtension))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableSet());
    ImmutableList<TextureCompressionFormatAlias> glVersionSupportedFormats =
        deviceSpec.getDeviceFeaturesList().stream()
            .flatMap(
                deviceFeature -> {
                  if (!deviceFeature.startsWith(GL_ES_VERSION_FEATURE_PREFIX)) {
                    return Stream.of();
                  }

                  try {
                    int glVersion =
                        Integer.decode(
                            deviceFeature.substring(GL_ES_VERSION_FEATURE_PREFIX.length()));
                    return TextureCompressionUtils.textureCompressionFormatsForGl(glVersion)
                        .stream();
                  } catch (NumberFormatException e) {
                    System.out.println(
                        "WARNING: the OpenGL ES version in the device spec is not a valid number. "
                            + " It will be considered as missing for texture compression format"
                            + " matching with the device.");
                  }

                  return Stream.of();
                })
            .collect(toImmutableList());

    this.deviceSupportedTextureCompressionFormats =
        ImmutableSet.<TextureCompressionFormatAlias>builder()
            .addAll(glExtensionSupportedFormats)
            .addAll(glVersionSupportedFormats)
            .build();
  }

  @Override
  protected TextureCompressionFormatTargeting getTargetingValue(ApkTargeting apkTargeting) {
    return apkTargeting.getTextureCompressionFormatTargeting();
  }

  @Override
  protected TextureCompressionFormatTargeting getTargetingValue(VariantTargeting variantTargeting) {
    return variantTargeting.getTextureCompressionFormatTargeting();
  }

  @Override
  public boolean matchesTargeting(TextureCompressionFormatTargeting targeting) {
    if (targeting.equals(TextureCompressionFormatTargeting.getDefaultInstance())) {
      return true;
    }

    ImmutableSet<TextureCompressionFormatAlias> values =
        targeting.getValueList().stream()
            .map(TextureCompressionFormat::getAlias)
            .collect(toImmutableSet());
    ImmutableSet<TextureCompressionFormatAlias> alternatives =
        targeting.getAlternativesList().stream()
            .map(TextureCompressionFormat::getAlias)
            .collect(toImmutableSet());
    SetView<TextureCompressionFormatAlias> intersection = Sets.intersection(values, alternatives);
    checkArgument(
        intersection.isEmpty(),
        "Expected targeting values and alternatives to be mutually exclusive, but both contain: %s",
        intersection);

    // The device supported formats are sorted in the order of preference for texture formats.
    // We try looking for a given format in the values and alternatives.
    // If we find it in alternatives first, it means there is a better match.
    ImmutableSortedSet<TextureCompressionFormatAlias> orderedSupportedTextureCompressionFormats =
        sortTextureCompressionFormat(deviceSupportedTextureCompressionFormats);
    for (TextureCompressionFormatAlias textureCompressionFormatAlias :
        orderedSupportedTextureCompressionFormats) {
      if (values.contains(textureCompressionFormatAlias)) {
        return true;
      }
      if (alternatives.contains(textureCompressionFormatAlias)) {
        return false;
      }
    }

    return false;
  }

  @Override
  protected boolean isDeviceDimensionPresent() {
    return !deviceGlExtensions.isEmpty();
  }

  @Override
  protected void checkDeviceCompatibleInternal(TextureCompressionFormatTargeting targeting) {
    if (targeting.equals(TextureCompressionFormatTargeting.getDefaultInstance())) {
      return;
    }

    ImmutableSet<TextureCompressionFormatAlias> valuesAndAlternativesSet =
        Streams.concat(
                targeting.getValueList().stream().map(TextureCompressionFormat::getAlias),
                targeting.getAlternativesList().stream().map(TextureCompressionFormat::getAlias))
            .collect(toImmutableSet());

    SetView<TextureCompressionFormatAlias> intersection =
        Sets.intersection(valuesAndAlternativesSet, this.deviceSupportedTextureCompressionFormats);

    if (intersection.isEmpty()) {
      throw IncompatibleDeviceException.builder()
          .withMessage(
              "The app doesn't support texture compression formats of the device. "
                  + "Device formats: %s, app formats: %s.",
              this.deviceSupportedTextureCompressionFormats, valuesAndAlternativesSet)
          .build();
    }
  }
}
