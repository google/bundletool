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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;

import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.zip.ZipFile;

/** Validates and prints information about the bundle or returns AppBundle object. */
@AutoValue
public abstract class ValidateBundleCommand {

  public static final String COMMAND_NAME = "validate";
  private static final Flag<Path> BUNDLE_FLAG = Flag.path("bundle");

  public abstract Path getBundlePath();

  public abstract Boolean getPrintOutput();

  public static Builder builder() {
    return new AutoValue_ValidateBundleCommand.Builder().setPrintOutput(false);
  }

  /** Builder for {@link ValidateBundleCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setBundlePath(Path bundlePath);

    public abstract Builder setPrintOutput(Boolean printOutput);

    public abstract ValidateBundleCommand build();
  }

  public static ValidateBundleCommand fromFlags(ParsedFlags flags) {
    ValidateBundleCommand.Builder builder =
        builder().setBundlePath(BUNDLE_FLAG.getRequiredValue(flags)).setPrintOutput(true);

    flags.checkNoUnknownFlags();

    return builder.build();
  }

  public void execute() throws CommandExecutionException {
    validateInput();

    try (ZipFile bundleZip = new ZipFile(getBundlePath().toFile())) {
      AppBundleValidator bundleValidator = AppBundleValidator.create();

      bundleValidator.validateFile(bundleZip);
      AppBundle appBundle = AppBundle.buildFromZip(bundleZip);
      bundleValidator.validate(appBundle);

      if (getPrintOutput()) {
        printBundleSummary(appBundle);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error reading zip file '%s'", getBundlePath()), e);
    }
  }

  private void validateInput() {
    checkFileExistsAndReadable(getBundlePath());
  }

  private void printBundleSummary(AppBundle appBundle) {
    System.out.printf("App Bundle information\n");
    System.out.printf("------------\n");
    System.out.printf("Feature modules:\n");
    for (Entry<BundleModuleName, BundleModule> moduleEntry :
        appBundle.getFeatureModules().entrySet()) {
      System.out.printf("\tFeature module: %s\n", moduleEntry.getKey());
      printModuleSummary(moduleEntry.getValue());
    }
    if (!appBundle.getAssetModules().isEmpty()) {
      System.out.printf("Asset packs:\n");
      for (Entry<BundleModuleName, BundleModule> moduleEntry :
          appBundle.getAssetModules().entrySet()) {
        System.out.printf("\tAsset pack: %s\n", moduleEntry.getKey());
        printModuleSummary(moduleEntry.getValue());
      }
    }
  }

  private void printModuleSummary(BundleModule bundleModule) {
    for (ModuleEntry entry : bundleModule.getEntries()) {
      System.out.printf("\t\tFile: %s\n", entry.getPath().toString());
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Verifies the given Android App Bundle is valid and prints out information "
                        + "about it.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(BUNDLE_FLAG.getName())
                .setExampleValue("bundle.aab")
                .setDescription("Path to the Android App Bundle to validate.")
                .build())
        .build();
  }
}
