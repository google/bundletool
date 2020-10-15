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
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.GraphicsApi;
import com.android.bundle.Targeting.GraphicsApiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.GraphicsApiUtils;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckReturnValue;

/** A single parsed name of the assets directory path. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class TargetedDirectorySegment {

  private static final Pattern DIRECTORY_SEGMENT_PATTERN =
      Pattern.compile("(?<base>.+?)#(?<key>.+?)_(?<value>.+)");

  private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-zA-Z]{2,3}$");
  private static final Pattern DEVICE_TIER_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

  private static final String OPENGL_KEY = "opengl";
  private static final String VULKAN_KEY = "vulkan";
  private static final String LANG_KEY = "lang";
  private static final String TCF_KEY = "tcf";
  private static final String DEVICE_TIER_KEY = "tier";

  private static final ImmutableMap<String, TargetingDimension> KEY_TO_DIMENSION =
      ImmutableMap.<String, TargetingDimension>builder()
          .put(OPENGL_KEY, TargetingDimension.GRAPHICS_API)
          .put(VULKAN_KEY, TargetingDimension.GRAPHICS_API)
          .put(LANG_KEY, TargetingDimension.LANGUAGE)
          .put(TCF_KEY, TargetingDimension.TEXTURE_COMPRESSION_FORMAT)
          .put(DEVICE_TIER_KEY, TargetingDimension.DEVICE_TIER)
          .build();
  private static final ImmutableSetMultimap<TargetingDimension, String> DIMENSION_TO_KEY =
      KEY_TO_DIMENSION.asMultimap().inverse();

  private static final Pattern OPENGL_VALUE_PATTERN = Pattern.compile("(\\d)\\.(\\d)");
  private static final Pattern VULKAN_VALUE_PATTERN = Pattern.compile("(\\d)\\.(\\d)");

  public abstract String getName();

  /** Positive targeting resolved from this directory name. */
  public abstract AssetsDirectoryTargeting getTargeting();

  /** Get the targeting applied on this segment (if any). */
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

  /** Remove the targeting done for a specific dimension. */
  @CheckReturnValue
  public TargetedDirectorySegment removeTargeting(TargetingDimension dimension) {
    AssetsDirectoryTargeting.Builder newTargeting = getTargeting().toBuilder();
    if (dimension.equals(TargetingDimension.ABI) && getTargeting().hasAbi()) {
      newTargeting.clearAbi();
    } else if (dimension.equals(TargetingDimension.GRAPHICS_API)
        && getTargeting().hasGraphicsApi()) {
      newTargeting.clearGraphicsApi();
    } else if (dimension.equals(TargetingDimension.LANGUAGE) && getTargeting().hasLanguage()) {
      newTargeting.clearLanguage();
    } else if (dimension.equals(TargetingDimension.TEXTURE_COMPRESSION_FORMAT)
        && getTargeting().hasTextureCompressionFormat()) {
      newTargeting.clearTextureCompressionFormat();
    } else if (dimension.equals(TargetingDimension.DEVICE_TIER) && getTargeting().hasDeviceTier()) {
      newTargeting.clearDeviceTier();
    } else {
      // Nothing to remove, return the existing immutable object.
      return this;
    }

    return new AutoValue_TargetedDirectorySegment(getName(), newTargeting.build());
  }

  public static TargetedDirectorySegment parse(String directorySegment) {
    if (!directorySegment.contains("#")) {
      return TargetedDirectorySegment.create(directorySegment);
    }
    Matcher matcher = DIRECTORY_SEGMENT_PATTERN.matcher(directorySegment);
    if (matcher.matches()) {
      return TargetedDirectorySegment.create(
          matcher.group("base"), matcher.group("key"), matcher.group("value"));
    }
    throw InvalidBundleException.builder()
        .withUserMessage(
            "Cannot tokenize targeted directory '%s'. "
                + "Expecting either '<name>' or '<name>#<key>_<value>' format.",
            directorySegment)
        .build();
  }

  /** Do the reverse of parse, returns the path represented by the segment. */
  public String toPathSegment() {
    ImmutableList<TargetingDimension> dimensions =
        TargetingUtils.getTargetingDimensions(getTargeting());
    checkState(dimensions.size() <= 1);

    Optional<String> key = getTargetingKey(getTargeting());
    Optional<String> value = getTargetingValue(getTargeting());

    if (!key.isPresent() || !value.isPresent()) {
      return getName();
    }

    return String.format("%s#%s_%s", getName(), key.get(), value.get());
  }

  /**
   * Fast check (without parsing) that verifies if a dimension can be targeted in a path. If this
   * returns true, you should construct a TargetedDirectory from the path to do any work on it. If
   * this returns false, the dimension is guaranteed not to be targeted in the specified path.
   */
  public static boolean pathMayContain(String path, TargetingDimension dimension) {
    Collection<String> keys = DIMENSION_TO_KEY.get(dimension);
    return keys.stream().anyMatch(key -> path.contains("#" + key + "_"));
  }

  private static TargetedDirectorySegment create(String name) {
    return new AutoValue_TargetedDirectorySegment(
        name, AssetsDirectoryTargeting.getDefaultInstance());
  }

  private static TargetedDirectorySegment create(String name, String key, String value) {
    return new AutoValue_TargetedDirectorySegment(
        name, toAssetsDirectoryTargeting(name, key, value));
  }

  /** Do the reverse of toAssetsDirectoryTargeting, return the key of the targeting. */
  private static Optional<String> getTargetingKey(AssetsDirectoryTargeting targeting) {
    ImmutableList<TargetingDimension> dimensions = TargetingUtils.getTargetingDimensions(targeting);
    checkArgument(
        dimensions.size() <= 1, "Multiple targeting for a same directory is not supported");

    if (targeting.hasLanguage()) {
      return Optional.of(LANG_KEY);
    } else if (targeting.hasGraphicsApi()) {
      return getGraphicsApiKey(targeting);
    } else if (targeting.hasTextureCompressionFormat()) {
      return Optional.of(TCF_KEY);
    } else if (targeting.hasDeviceTier()) {
      return Optional.of(DEVICE_TIER_KEY);
    }

    return Optional.empty();
  }

  /** Do the reverse of toAssetsDirectoryTargeting, return the value of the targeting. */
  private static Optional<String> getTargetingValue(AssetsDirectoryTargeting targeting) {
    ImmutableList<TargetingDimension> dimensions = TargetingUtils.getTargetingDimensions(targeting);
    checkArgument(
        dimensions.size() <= 1, "Multiple targeting for a same directory is not supported");

    if (targeting.hasLanguage()) {
      return Optional.of(Iterables.getOnlyElement(targeting.getLanguage().getValueList()));
    } else if (targeting.hasGraphicsApi()) {
      return getGraphicsApiValue(targeting);
    } else if (targeting.hasTextureCompressionFormat()) {
      return Optional.ofNullable(
          TextureCompressionUtils.TARGETING_TO_TEXTURE.getOrDefault(
              Iterables.getOnlyElement(targeting.getTextureCompressionFormat().getValueList())
                  .getAlias(),
              null));
    } else if (targeting.hasDeviceTier()) {
      return Optional.of(Iterables.getOnlyElement(targeting.getDeviceTier().getValueList()));
    }

    return Optional.empty();
  }

  /** Returns the targeting specified by the directory name, alternatives are not generated. */
  private static AssetsDirectoryTargeting toAssetsDirectoryTargeting(
      String name, String key, String value) {
    if (!KEY_TO_DIMENSION.containsKey(key)) {
      throw InvalidBundleException.builder()
          .withUserMessage("Directory '%s' contains unsupported key '%s'.", name, key)
          .build();
    }

    switch (KEY_TO_DIMENSION.get(key)) {
      case GRAPHICS_API:
        return parseGraphicsApi(name, key, value);
      case LANGUAGE:
        return parseLanguage(name, value);
      case TEXTURE_COMPRESSION_FORMAT:
        return parseTextureCompressionFormat(name, value);
      case DEVICE_TIER:
        return parseDeviceTier(name, value);
      default:
        throw InvalidBundleException.builder()
            .withUserMessage("Unrecognized key: '%s'.", key)
            .build();
    }
  }

  private static AssetsDirectoryTargeting parseGraphicsApi(String name, String key, String value) {
    GraphicsApiTargeting graphicsApiTargeting;
    Matcher matcher;
    switch (key) {
      case OPENGL_KEY:
        matcher = OPENGL_VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
          throw InvalidBundleException.builder()
              .withUserMessage(
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
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Could not parse Vulkan version '%s' for the directory '%s'.", value, name)
              .build();
        }
        graphicsApiTargeting =
            GraphicsApiUtils.vulkanVersionFrom(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        break;

      default:
        throw InvalidBundleException.createWithUserMessage(
            "Not a valid graphics API identifier: " + key);
    }

    return AssetsDirectoryTargeting.newBuilder().setGraphicsApi(graphicsApiTargeting).build();
  }

  /** Do the reverse of parseGraphicsApi, return the key of the graphics api. */
  private static Optional<String> getGraphicsApiKey(AssetsDirectoryTargeting targeting) {
    GraphicsApi graphicsApi = Iterables.getOnlyElement(targeting.getGraphicsApi().getValueList());
    if (graphicsApi.hasMinOpenGlVersion()) {
      return Optional.of(OPENGL_KEY);
    } else if (graphicsApi.hasMinVulkanVersion()) {
      return Optional.of(VULKAN_KEY);
    }

    return Optional.empty();
  }

  /** Do the reverse of parseGraphicsApi, return the key and value to be used in a path. */
  private static Optional<String> getGraphicsApiValue(AssetsDirectoryTargeting targeting) {
    GraphicsApi graphicsApi = Iterables.getOnlyElement(targeting.getGraphicsApi().getValueList());
    if (graphicsApi.hasMinOpenGlVersion()) {
      int majorVersion = graphicsApi.getMinOpenGlVersion().getMajor();
      int minorVersion = graphicsApi.getMinOpenGlVersion().getMinor();
      return Optional.of(String.format("%d.%d", majorVersion, minorVersion));
    } else if (graphicsApi.hasMinVulkanVersion()) {
      int majorVersion = graphicsApi.getMinVulkanVersion().getMajor();
      int minorVersion = graphicsApi.getMinVulkanVersion().getMinor();
      return Optional.of(String.format("%d.%d", majorVersion, minorVersion));
    }

    return Optional.empty();
  }

  private static AssetsDirectoryTargeting parseTextureCompressionFormat(String name, String value) {
    if (!TextureCompressionUtils.TEXTURE_TO_TARGETING.containsKey(value)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
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
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Expected 2- or 3-character language directory but got '%s' for directory '%s'.",
              value, name)
          .build();
    }
    return AssetsDirectoryTargeting.newBuilder()
        .setLanguage(LanguageTargeting.newBuilder().addValue(value.toLowerCase()))
        .build();
  }

  private static AssetsDirectoryTargeting parseDeviceTier(String name, String value) {
    Matcher matcher = DEVICE_TIER_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Device tier names should start with a letter and contain only letters, numbers and"
                  + " underscores. Found tier named '%s' in directory '%s'.",
              value, name)
          .build();
    }
    return AssetsDirectoryTargeting.newBuilder()
        .setDeviceTier(DeviceTierTargeting.newBuilder().addValue(value))
        .build();
  }
}
