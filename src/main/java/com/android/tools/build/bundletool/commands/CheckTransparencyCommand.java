/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.transparency.CodeTransparencyChecker;
import com.android.tools.build.bundletool.transparency.TransparencyCheckResult;
import com.google.auto.value.AutoValue;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command to verify code transparency. */
@AutoValue
public abstract class CheckTransparencyCommand {

  public static final String COMMAND_NAME = "check-transparency";

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");

  public abstract Path getBundlePath();

  public static CheckTransparencyCommand.Builder builder() {
    return new AutoValue_CheckTransparencyCommand.Builder();
  }

  /** Builder for the {@link CheckTransparencyCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the input bundle. Must have the extension ".aab". */
    public abstract CheckTransparencyCommand.Builder setBundlePath(Path bundlePath);

    public abstract CheckTransparencyCommand build();
  }

  public static CheckTransparencyCommand fromFlags(ParsedFlags flags) {
    CheckTransparencyCommand.Builder checkTransparencyCommandBuilder =
        CheckTransparencyCommand.builder()
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags));
    flags.checkNoUnknownFlags();
    return checkTransparencyCommandBuilder.build();
  }

  public void execute() {
    validateInputs();
    checkTransparency(System.out);
  }

  public void checkTransparency(PrintStream outputStream) {
    try (ZipFile bundleZip = new ZipFile(getBundlePath().toFile())) {
      AppBundle inputBundle = AppBundle.buildFromZip(bundleZip);
      Optional<ByteSource> transparencyFile =
          inputBundle
              .getBundleMetadata()
              .getFileAsByteSource(
                  BundleMetadata.BUNDLETOOL_NAMESPACE, BundleMetadata.TRANSPARENCY_FILE_NAME);
      if (!transparencyFile.isPresent()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Bundle does not include code transparency metadata. Run `add-transparency`"
                    + " command to add code transparency metadata to the bundle.")
            .build();
      }
      TransparencyCheckResult transparencyCheckResult =
          CodeTransparencyChecker.checkTransparency(inputBundle, transparencyFile.get());
      if (transparencyCheckResult.verified()) {
        outputStream.print("Code transparency verified.");
      } else {
        outputStream.print(
            "Code transparency verification failed.\n" + transparencyCheckResult.getDiffAsString());
      }
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The App Bundle is not a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when processing the App Bundle.", e);
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription("Verifies code transparency for the given bundle.")
                .build())
        .build();
  }

  private void validateInputs() {
    FilePreconditions.checkFileHasExtension("AAB file", getBundlePath(), ".aab");
    FilePreconditions.checkFileExistsAndReadable(getBundlePath());
  }
}
