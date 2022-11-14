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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.commands.GetSizeCommand.GetSizeSubcommand.STRING_TO_SUBCOMMAND;
import static com.android.tools.build.bundletool.model.utils.ApkSizeUtils.getCompressedSizeByApkPaths;
import static com.android.tools.build.bundletool.model.utils.ApkSizeUtils.getVariantCompressedSizeByApkPaths;
import static com.android.tools.build.bundletool.model.utils.CollectorUtils.combineMaps;
import static com.android.tools.build.bundletool.model.utils.GetSizeCsvUtils.getSizeTotalOutputInCsv;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AssetModuleSizeAggregator;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.device.VariantMatcher;
import com.android.tools.build.bundletool.device.VariantTotalSizeAggregator;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.ConfigurationSizesMerger;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.SizeFormatter;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/** Gets over-the-wire sizes of APKS that are going to be served from the APK Set. */
@AutoValue
public abstract class GetSizeCommand implements GetSizeRequest {

  public static final String COMMAND_NAME = "get-size";

  /** Sub commands supported on {@link GetSizeCommand}. */
  public enum GetSizeSubcommand {
    TOTAL("total");

    static final ImmutableMap<String, GetSizeSubcommand> STRING_TO_SUBCOMMAND =
        Arrays.stream(GetSizeSubcommand.values())
            .collect(toImmutableMap(GetSizeSubcommand::toString, identity()));

    private final String subCommand;

    GetSizeSubcommand(String subCommand) {
      this.subCommand = subCommand;
    }

    @Override
    public String toString() {
      return subCommand;
    }

    public static GetSizeSubcommand fromString(String subCommand) {
      GetSizeSubcommand result = STRING_TO_SUBCOMMAND.get(subCommand);
      if (result == null) {
        throw InvalidCommandException.builder()
            .withInternalMessage(
                "Unrecognized get-size command target: '%s'. Accepted values are: %s",
                subCommand, STRING_TO_SUBCOMMAND.keySet())
            .build();
      }
      return result;
    }
  }

  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");
  private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");
  private static final Flag<Boolean> INSTANT_FLAG = Flag.booleanFlag("instant");
  private static final Flag<ImmutableSet<Dimension>> DIMENSIONS_FLAG =
      Flag.enumSet("dimensions", Dimension.class);
  private static final Flag<Boolean> HUMAN_READABLE_SIZES_FLAG =
      Flag.booleanFlag("human-readable-sizes");
  private static final Joiner COMMA_JOINER = Joiner.on(',');

  @VisibleForTesting
  static final ImmutableSet<Dimension> SUPPORTED_DIMENSIONS =
      ImmutableSet.of(
          Dimension.SDK,
          Dimension.ABI,
          Dimension.LANGUAGE,
          Dimension.SCREEN_DENSITY,
          Dimension.TEXTURE_COMPRESSION_FORMAT,
          Dimension.DEVICE_TIER,
          Dimension.COUNTRY_SET,
          Dimension.SDK_RUNTIME);

  public abstract Path getApksArchivePath();

  @Override
  public abstract DeviceSpec getDeviceSpec();

  @Override
  public abstract Optional<ImmutableSet<String>> getModules();

  @Override
  public abstract ImmutableSet<Dimension> getDimensions();

  public abstract GetSizeSubcommand getGetSizeSubCommand();

  /** Gets whether instant APKs should be used for size calculation. */
  @Override
  public abstract boolean getInstant();

  /** Gets whether to format sizes to human readable units. */
  public abstract boolean getHumanReadableSizes();

  public static Builder builder() {
    return new AutoValue_GetSizeCommand.Builder()
        .setDeviceSpec(DeviceSpec.getDefaultInstance())
        .setInstant(false)
        .setDimensions(ImmutableSet.of())
        .setHumanReadableSizes(false);
  }

  /** Builder for the {@link GetSizeCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setApksArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

    public Builder setDeviceSpec(Path deviceSpecPath) {
      return setDeviceSpec(DeviceSpecParser.parsePartialDeviceSpec(deviceSpecPath));
    }

    public abstract Builder setModules(ImmutableSet<String> modules);

    public abstract Builder setDimensions(ImmutableSet<Dimension> dimensions);

    /**
     * Sets whether only instant APKs should be used in size calculation.
     *
     * <p>The default is {@code false}. If this is set to {@code true}, the instant APKs will be
     * used for calculating size instead of installable APKs.
     */
    public abstract Builder setInstant(boolean instant);

    /** Sets the sub-command of the get-size command, e.g. total. */
    public abstract Builder setGetSizeSubCommand(GetSizeSubcommand getSizeSubcommand);

    /** Sets whether to format sizes to human readable units. */
    public abstract Builder setHumanReadableSizes(boolean humanReadableSizes);

    public abstract GetSizeCommand build();
  }

  public static GetSizeCommand fromFlags(ParsedFlags flags) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Optional<Path> deviceSpecPath = DEVICE_SPEC_FLAG.getValue(flags);
    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> instant = INSTANT_FLAG.getValue(flags);
    Optional<Boolean> pretty = HUMAN_READABLE_SIZES_FLAG.getValue(flags);

    ImmutableSet<Dimension> dimensions = DIMENSIONS_FLAG.getValue(flags).orElse(ImmutableSet.of());
    flags.checkNoUnknownFlags();

    checkFileExistsAndReadable(apksArchivePath);
    deviceSpecPath.ifPresent(FilePreconditions::checkFileExistsAndReadable);
    DeviceSpec deviceSpec =
        deviceSpecPath
            .map(DeviceSpecParser::parsePartialDeviceSpec)
            .orElse(DeviceSpec.getDefaultInstance());

    GetSizeCommand.Builder command =
        builder()
            .setApksArchivePath(apksArchivePath)
            .setDeviceSpec(deviceSpec)
            .setGetSizeSubCommand(parseGetSizeSubCommand(flags));

    modules.ifPresent(command::setModules);

    instant.ifPresent(command::setInstant);
    pretty.ifPresent(command::setHumanReadableSizes);

    if (dimensions.contains(Dimension.ALL)) {
      dimensions = SUPPORTED_DIMENSIONS;
    }

    command.setDimensions(dimensions);

    return command.build();
  }

  private static GetSizeSubcommand parseGetSizeSubCommand(ParsedFlags flags) {
    String subCommand =
        flags
            .getSubCommand()
            .orElseThrow(
                () ->
                    InvalidCommandException.builder()
                        .withInternalMessage("Target of the get-size command not found.")
                        .build());

    return GetSizeSubcommand.fromString(subCommand);
  }

  public void execute() {
    switch (getGetSizeSubCommand()) {
      case TOTAL:
        getSizeTotal(System.out);
        break;
    }
  }

  public void getSizeTotal(PrintStream output) {
    output.print(
        getSizeTotalOutputInCsv(getSizeTotalInternal(), getDimensions(), getSizeFormatter()));
  }

  private SizeFormatter getSizeFormatter() {
    return getHumanReadableSizes()
        ? SizeFormatter.humanReadableFormatter()
        : SizeFormatter.rawFormatter();
  }

  @VisibleForTesting
  ConfigurationSizes getSizeTotalInternal() {
    BuildApksResult buildApksResult = ResultUtils.readTableOfContents(getApksArchivePath());

    ImmutableList<Variant> variants =
        new VariantMatcher(getDeviceSpec(), getInstant()).getAllMatchingVariants(buildApksResult);
    ImmutableMap<String, Long> variantCompressedSizeByApkPaths =
        getVariantCompressedSizeByApkPaths(variants, getApksArchivePath());

    ImmutableList<String> assetModuleApks =
        buildApksResult.getAssetSliceSetList().stream()
            .flatMap(module -> module.getApkDescriptionList().stream())
            .map(ApkDescription::getPath)
            .collect(toImmutableList());
    ImmutableMap<String, Long> assetModuleCompressedSizeByApkPaths =
        getCompressedSizeByApkPaths(assetModuleApks, getApksArchivePath());

    ImmutableMap<SizeConfiguration, Long> minSizeConfigurationMap = ImmutableMap.of();
    ImmutableMap<SizeConfiguration, Long> maxSizeConfigurationMap = ImmutableMap.of();

    for (Variant variant : variants) {
      ConfigurationSizes variantConfigurationSizes =
          new VariantTotalSizeAggregator(
                  variantCompressedSizeByApkPaths,
                  Version.of(buildApksResult.getBundletool().getVersion()),
                  variant,
                  this)
              .getSize();
      ConfigurationSizes assetModuleConfigurationSizes =
          new AssetModuleSizeAggregator(
                  buildApksResult.getAssetSliceSetList(),
                  variant.getTargeting(),
                  assetModuleCompressedSizeByApkPaths,
                  this)
              .getSize();
      ConfigurationSizes configurationSizes =
          ConfigurationSizesMerger.merge(variantConfigurationSizes, assetModuleConfigurationSizes);
      minSizeConfigurationMap =
          combineMaps(
              minSizeConfigurationMap, configurationSizes.getMinSizeConfigurationMap(), Math::min);
      maxSizeConfigurationMap =
          combineMaps(
              maxSizeConfigurationMap, configurationSizes.getMaxSizeConfigurationMap(), Math::max);
    }

    return ConfigurationSizes.create(minSizeConfigurationMap, maxSizeConfigurationMap);
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setSubCommandNames(STRING_TO_SUBCOMMAND.keySet().asList())
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Computes the min and max download sizes of APKs served to different "
                        + "devices configurations from an APK Set.")
                .addAdditionalParagraph("The output is in CSV format.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APKS_ARCHIVE_FILE_FLAG.getName())
                .setExampleValue("archive.apks")
                .setDescription(
                    "Path to the archive file generated by the '%s' command.",
                    BuildApksCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_SPEC_FLAG.getName())
                .setExampleValue("device-spec.json")
                .setOptional(true)
                .setDescription(
                    "Path to the device spec file to be used for matching (defaults to empty "
                        + "device spec). Note that partial specifications are allowed in the "
                        + "file as opposed to the spec generated by '%s'.",
                    GetDeviceSpecCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DIMENSIONS_FLAG.getName())
                .setExampleValue(COMMA_JOINER.join(Dimension.values()))
                .setOptional(true)
                .setDescription(
                    "Specifies which dimensions to expand the sizes in the output "
                        + "against. Note that ALL is a shortcut to all other dimensions and "
                        + "including ALL here would cause the output to be expanded over "
                        + "all possible dimensions.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("base,module1,module2")
                .setOptional(true)
                .setDescription(
                    "List of modules to run this report on (defaults to all the modules installed "
                        + "during the first download). Note that the dependent modules will also "
                        + "be considered. We ignore standalone APKs for size calculation when this "
                        + "flag is set.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(INSTANT_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "When set, APKs of the instant modules will be considered instead of the "
                        + "installable APKs. Defaults to false.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(HUMAN_READABLE_SIZES_FLAG.getName())
                .setDescription(
                    "When set, size values are formatted to human readable units: KB, MB, GB."
                        + " Defaults to false.")
                .build())
        .build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  GetSizeCommand() {}
}
