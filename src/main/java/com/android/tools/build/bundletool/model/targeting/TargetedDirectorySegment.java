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

package com.android.tools.build.bundletool.model.targeting;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.GraphicsApiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.GraphicsApiUtils;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A single parsed name of the assets directory path. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class TargetedDirectorySegment {

  private static final Pattern DIRECTORY_SEGMENT_PATTERN =
      Pattern.compile("(?<base>.+?)#(?<key>.+?)_(?<value>.+)");

  private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-zA-Z]{2,3}$");

  private static final String OPENGL_KEY = "opengl";
  private static final String VULKAN_KEY = "vulkan";
  private static final String LANG_KEY = "lang";
  private static final String TCF_KEY = "tcf";

  private static final ImmutableMap<String, TargetingDimension> KEY_TO_DIMENSION =
      ImmutableMap.<String, TargetingDimension>builder()
          .put(OPENGL_KEY, TargetingDimension.GRAPHICS_API)
          .put(VULKAN_KEY, TargetingDimension.GRAPHICS_API)
          .put(LANG_KEY, TargetingDimension.LANGUAGE)
          .put(TCF_KEY, TargetingDimension.TEXTURE_COMPRESSION_FORMAT)
          .build();

  private static final Pattern OPENGL_VALUE_PATTERN = Pattern.compile("(\\d)\\.(\\d)");
  private static final Pattern VULKAN_VALUE_PATTERN = Pattern.compile("(\\d)\\.(\\d)");

  public abstract String getName();

  /** Positive targeting resolved from this directory name. */
  public abstract AssetsDirectoryTargeting getTargeting();

  public Optional<TargetingDimension> getTargetingDimension() {
    ImmutableList<TargetingDimension> dimensions =
        TargetingUtils.getTargetingDimensions(getTargeting());
    checkState(dimensions.size() <= 1);

    if (dimensions.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(dimensions.get(0));
    }
  }

  public static TargetedDirectorySegment parse(ZipPath directorySegment) {
    checkArgument(directorySegment.getNameCount() == 1);
    if (!directorySegment.toString().contains("#")) {
      return TargetedDirectorySegment.create(directorySegment.toString());
    }
    Matcher matcher = DIRECTORY_SEGMENT_PATTERN.matcher(directorySegment.toString());
    if (matcher.matches()) {
      return TargetedDirectorySegment.create(
          matcher.group("base"), matcher.group("key"), matcher.group("value"));
    }
    throw ValidationException.builder()
        .withMessage(
            "Cannot tokenize targeted directory '%s'. "
                + "Expecting either '<name>' or '<name>#<key>_<value>' format.",
            directorySegment)
        .build();
  }

  private static TargetedDirectorySegment create(String name) {
    return new AutoValue_TargetedDirectorySegment(
        name, AssetsDirectoryTargeting.getDefaultInstance());
  }

  private static TargetedDirectorySegment create(String name, String key, String value) {
    return new AutoValue_TargetedDirectorySegment(
        name, toAssetsDirectoryTargeting(name, key, value));
  }

  /** Returns the targeting specified by the directory name, alternatives are not generated. */
  private static AssetsDirectoryTargeting toAssetsDirectoryTargeting(
      String name, String key, String value) {
    if (!KEY_TO_DIMENSION.containsKey(key)) {
      throw ValidationException.builder()
          .withMessage("Directory '%s' contains unsupported key '%s'.", name, key)
          .build();
    }

    switch (KEY_TO_DIMENSION.get(key)) {
      case GRAPHICS_API:
        return parseGraphicsApi(name, key, value);
      case LANGUAGE:
        return parseLanguage(name, value);
      case TEXTURE_COMPRESSION_FORMAT:
        return parseTextureCompressionFormat(name, value);
      default:
        throw ValidationException.builder().withMessage("Unrecognized key: '%s'.", key).build();
    }
  }

  private static AssetsDirectoryTargeting parseGraphicsApi(String name, String key, String value) {
    GraphicsApiTargeting graphicsApiTargeting;
    Matcher matcher;
    switch (key) {
      case OPENGL_KEY:
        matcher = OPENGL_VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
          throw ValidationException.builder()
              .withMessage(
                  "Could not parse OpenGL version '%s' for the directory '%s'.", value, name)
              .build();
        }
        graphicsApiTargeting =
            GraphicsApiUtils.openGlVersionFrom(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        break;

      case VULKAN_KEY:
        matcher = VULKAN_VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
          throw ValidationException.builder()
              .withMessage(
                  "Could not parse Vulkan version '%s' for the directory '%s'.", value, name)
              .build();
        }
        graphicsApiTargeting =
            GraphicsApiUtils.vulkanVersionFrom(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        break;

      default:
        throw new ValidationException("Not a valid graphics API identifier: " + key);
    }

    return AssetsDirectoryTargeting.newBuilder().setGraphicsApi(graphicsApiTargeting).build();
  }

  private static AssetsDirectoryTargeting parseTextureCompressionFormat(String name, String value) {
    if (!TextureCompressionUtils.TEXTURE_TO_TARGETING.containsKey(value)) {
      throw ValidationException.builder()
          .withMessage(
              "Unrecognized value of the texture compression format targeting '%s' for directory "
                  + "'%s'.",
              value, name)
          .build();
    }
    return AssetsDirectoryTargeting.newBuilder()
        .setTextureCompressionFormat(TextureCompressionUtils.TEXTURE_TO_TARGETING.get(value))
        .build();
  }

  private static AssetsDirectoryTargeting parseLanguage(String name, String value) {
    Matcher matcher = LANGUAGE_CODE_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw ValidationException.builder()
          .withMessage(
              "Expected 2- or 3-character language directory but got '%s' for directory '%s'.",
              value, name)
          .build();
    }
    return AssetsDirectoryTargeting.newBuilder()
        .setLanguage(LanguageTargeting.newBuilder().addValue(value.toLowerCase()))
        .build();
  }
}
