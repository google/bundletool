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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.DeviceTargetingUtils;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Int32Value;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A single parsed name of the assets directory path. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class TargetedDirectorySegment {

  public static final String COUNTRY_SET_KEY = "countries";
  private static final String COUNTRY_SET_NAME_REGEX_STRING = "^[a-zA-Z][a-zA-Z0-9_]*$";

  private static final String SEGMENT_SPLIT_CHARACTER = "#";
  private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-zA-Z]{2,3}$");
  private static final Pattern COUNTRY_SET_PATTERN = Pattern.compile(COUNTRY_SET_NAME_REGEX_STRING);

  private static final String LANG_KEY = "lang";
  private static final String TCF_KEY = "tcf";
  private static final String DEVICE_TIER_KEY = "tier";

  private static final ImmutableSet<TargetingDimension> ALLOWED_NESTING_DIMENSIONS =
      ImmutableSet.of(
          TargetingDimension.COUNTRY_SET,
          TargetingDimension.DEVICE_TIER,
          TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
  private static final int MAXIMUM_NESTING_DEPTH_ALLOWED = 2;

  private static final ImmutableMap<String, TargetingDimension> KEY_TO_DIMENSION =
      ImmutableMap.<String, TargetingDimension>builder()
          .put(COUNTRY_SET_KEY, TargetingDimension.COUNTRY_SET)
          .put(DEVICE_TIER_KEY, TargetingDimension.DEVICE_TIER)
          .put(LANG_KEY, TargetingDimension.LANGUAGE)
          .put(TCF_KEY, TargetingDimension.TEXTURE_COMPRESSION_FORMAT)
          .build();
  private static final ImmutableSetMultimap<TargetingDimension, String> DIMENSION_TO_KEY =
      KEY_TO_DIMENSION.asMultimap().inverse();

  public abstract String getName();

  /** Positive targeting resolved from this directory name. */
  public abstract AssetsDirectoryTargeting getTargeting();

  public abstract ImmutableList<TargetingDimension> getTargetingDimensionOrder();

  /** Get the targeting applied on this segment. */
  ImmutableList<TargetingDimension> getTargetingDimensions() {
    return TargetingUtils.getTargetingDimensions(getTargeting());
  }

  /** Remove the targeting done for a specific dimension. */
  @CheckReturnValue
  public TargetedDirectorySegment removeTargeting(TargetingDimension dimension) {
    AssetsDirectoryTargeting.Builder newTargeting = getTargeting().toBuilder();
    if (dimension.equals(TargetingDimension.ABI) && getTargeting().hasAbi()) {
      newTargeting.clearAbi();
    } else if (dimension.equals(TargetingDimension.LANGUAGE) && getTargeting().hasLanguage()) {
      newTargeting.clearLanguage();
    } else if (dimension.equals(TargetingDimension.TEXTURE_COMPRESSION_FORMAT)
        && getTargeting().hasTextureCompressionFormat()) {
      newTargeting.clearTextureCompressionFormat();
    } else if (dimension.equals(TargetingDimension.DEVICE_TIER) && getTargeting().hasDeviceTier()) {
      newTargeting.clearDeviceTier();
    } else if (dimension.equals(TargetingDimension.COUNTRY_SET) && getTargeting().hasCountrySet()) {
      newTargeting.clearCountrySet();
    } else {
      // Nothing to remove, return the existing immutable object.
      return this;
    }

    ImmutableList<TargetingDimension> updatedTargetingDimensionOrder =
        getTargetingDimensionOrder().stream()
            .filter(targetingDimension -> !targetingDimension.equals(dimension))
            .collect(toImmutableList());
    return new AutoValue_TargetedDirectorySegment(
        getName(), newTargeting.build(), updatedTargetingDimensionOrder);
  }

  public static TargetedDirectorySegment parse(String directorySegment) {
    if (!directorySegment.contains(SEGMENT_SPLIT_CHARACTER)) {
      return TargetedDirectorySegment.create(directorySegment);
    }
    if (directorySegment.startsWith(SEGMENT_SPLIT_CHARACTER)
        || directorySegment.endsWith(SEGMENT_SPLIT_CHARACTER)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Cannot tokenize targeted directory '%s'. "
                  + "Expecting either '<name>' or '<name>#<key>_<value>' format.",
              directorySegment)
          .build();
    }
    ImmutableList<String> pathFragments =
        ImmutableList.copyOf(directorySegment.split(SEGMENT_SPLIT_CHARACTER));
    String baseName = pathFragments.get(0);
    ImmutableMap<String, String> targetingKeyValues =
        pathFragments.stream()
            .skip(1) // first is base name.
            .map(DimensionKeyValue::parse)
            .collect(
                toImmutableMap(
                    DimensionKeyValue::getDimensionKey,
                    DimensionKeyValue::getDimensionValue,
                    (v1, v2) -> {
                      throw InvalidBundleException.builder()
                          .withUserMessage(
                              "No directory should be targeted more than once on the same"
                                  + " dimension. Found directory '%s' targeted multiple times on"
                                  + " same dimension.",
                              directorySegment)
                          .build();
                    }));
    validateNestedTargetingDimensions(targetingKeyValues, directorySegment);
    return TargetedDirectorySegment.create(baseName, targetingKeyValues);
  }

  /** Do the reverse of parse, returns the path represented by the segment. */
  public String toPathSegment() {
    ImmutableList.Builder<String> pathFragmentsBuilder = ImmutableList.builder();
    pathFragmentsBuilder.add(getName());
    pathFragmentsBuilder.addAll(
        getTargetingDimensionOrder().stream()
            .map(this::convertTargetingToPathSegment)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableList()));
    return String.join("", pathFragmentsBuilder.build());
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

  /** Constructs targeting of directory in the given order of targeting dimension. */
  public static String constructTargetingSegmentPath(
      AssetsDirectoryTargeting targeting, ImmutableList<TargetingDimension> targetingOrder) {
    return targetingOrder.stream()
        .filter(dimension -> getTargetingValue(targeting, dimension).isPresent())
        .map(
            dimension ->
                String.format(
                    "#%s_%s",
                    getTargetingKey(dimension).get(),
                    getTargetingValue(targeting, dimension).get()))
        .collect(joining(""));
  }

  private Optional<String> convertTargetingToPathSegment(TargetingDimension dimension) {
    Optional<String> key = getTargetingKey(dimension);
    Optional<String> value = getTargetingValue(getTargeting(), dimension);
    if (key.isPresent() && value.isPresent()) {
      return Optional.of(String.format("#%s_%s", key.get(), value.get()));
    }
    return Optional.empty();
  }

  private static Optional<String> getTargetingValue(
      AssetsDirectoryTargeting targeting, TargetingDimension dimension) {
    switch (dimension) {
      case COUNTRY_SET:
        return targeting.getCountrySet().getValueList().stream().findFirst();
      case DEVICE_TIER:
        return targeting.getDeviceTier().getValueList().stream()
            .map(tier -> Integer.toString(tier.getValue()))
            .findFirst();
      case LANGUAGE:
        return targeting.getLanguage().getValueList().stream().findFirst();
      case TEXTURE_COMPRESSION_FORMAT:
        return targeting.getTextureCompressionFormat().getValueList().stream()
            .map(
                tcfAlias ->
                    TextureCompressionUtils.TARGETING_TO_TEXTURE.getOrDefault(
                        tcfAlias.getAlias(), null))
            .filter(Objects::nonNull)
            .findFirst();
      default:
        return Optional.empty();
    }
  }

  private static void validateNestedTargetingDimensions(
      ImmutableMap<String, String> keyValues, String directorySegment) {
    if (keyValues.size() == 1) {
      return;
    }
    if (keyValues.size() > MAXIMUM_NESTING_DEPTH_ALLOWED) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "No directory should target more than two dimension. Found"
                  + " directory '%s' targeting more than two dimension.",
              directorySegment)
          .build();
    }
    keyValues
        .keySet()
        .forEach(
            key -> {
              if (!KEY_TO_DIMENSION.containsKey(key)) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Unrecognized key: '%s' used in targeting of directory '%s'.",
                        key, directorySegment)
                    .build();
              }
              if (!ALLOWED_NESTING_DIMENSIONS.contains(KEY_TO_DIMENSION.get(key))) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Targeting dimension '%s' should not be nested with other dimensions. Found"
                            + " directory '%s' which nests the dimension with other dimensions.",
                        KEY_TO_DIMENSION.get(key), directorySegment)
                    .build();
              }
            });
  }

  private static TargetedDirectorySegment create(String name) {
    return new AutoValue_TargetedDirectorySegment(
        name, AssetsDirectoryTargeting.getDefaultInstance(), ImmutableList.of());
  }

  private static TargetedDirectorySegment create(
      String name, ImmutableMap<String, String> dimensionKeyValues) {
    AssetsDirectoryTargeting directoryTargeting =
        dimensionKeyValues.entrySet().stream()
            .map(
                keyValue ->
                    toAssetsDirectoryTargeting(name, keyValue.getKey(), keyValue.getValue()))
            .reduce(
                AssetsDirectoryTargeting.newBuilder(),
                AssetsDirectoryTargeting.Builder::mergeFrom,
                (builderA, builderB) -> builderA.mergeFrom(builderB.build()))
            .build();
    ImmutableList<TargetingDimension> targetingDimensionOrder =
        dimensionKeyValues.keySet().stream().map(KEY_TO_DIMENSION::get).collect(toImmutableList());
    return new AutoValue_TargetedDirectorySegment(
        name, directoryTargeting, targetingDimensionOrder);
  }

  private static Optional<String> getTargetingKey(TargetingDimension dimension) {
    return DIMENSION_TO_KEY.get(dimension).stream().findFirst();
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
      case COUNTRY_SET:
        return parseCountrySet(name, value);
      case DEVICE_TIER:
        return parseDeviceTier(name, value);
      case LANGUAGE:
        return parseLanguage(name, value);
      case TEXTURE_COMPRESSION_FORMAT:
        return parseTextureCompressionFormat(name, value);
      default:
        throw InvalidBundleException.builder()
            .withUserMessage("Unrecognized key: '%s'.", key)
            .build();
    }
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
    DeviceTargetingUtils.validateDeviceTierForAssetsDirectory(name, value);
    return AssetsDirectoryTargeting.newBuilder()
        .setDeviceTier(
            DeviceTierTargeting.newBuilder().addValue(Int32Value.of(Integer.parseInt(value))))
        .build();
  }

  private static AssetsDirectoryTargeting parseCountrySet(String name, String value) {
    Matcher matcher = COUNTRY_SET_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Country set name should match the regex '%s' but got '%s' for directory '%s'.",
              COUNTRY_SET_NAME_REGEX_STRING, value, name)
          .build();
    }
    return AssetsDirectoryTargeting.newBuilder()
        .setCountrySet(CountrySetTargeting.newBuilder().addValue(value))
        .build();
  }
}
