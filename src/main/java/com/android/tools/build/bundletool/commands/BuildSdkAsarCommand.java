/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;
import static com.android.tools.build.bundletool.model.utils.BundleParser.getModulesZip;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;

import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils;
import com.android.tools.build.bundletool.validation.SdkBundleValidator;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Command to generate an ASAR from an Android SDK Bundle. */
@AutoValue
public abstract class BuildSdkAsarCommand {

  public static final String COMMAND_NAME = "build-sdk-asar";

  private static final Flag<Path> SDK_BUNDLE_LOCATION_FLAG = Flag.path("sdk-bundle");
  private static final Flag<Path> APK_SIGNING_CERTIFICATE_LOCATION_FLAG =
      Flag.path("apk-signing-key-certificate");
  private static final Flag<Path> OUTPUT_FILE_FLAG = Flag.path("output");
  private static final Flag<Boolean> OVERWRITE_OUTPUT_FLAG = Flag.booleanFlag("overwrite");

  /** Only used as a placeholder in parsing of SDK Bundles, not represented in output. */
  private static final int PLACEHOLDER_VERSION_CODE = 1;

  abstract Path getSdkBundlePath();

  abstract Optional<X509Certificate> getApkSigningCertificate();

  abstract Path getOutputFile();

  abstract boolean getOverwriteOutput();

  /** Creates a builder for the {@link BuildSdkAsarCommand} with some default settings. */
  public static BuildSdkAsarCommand.Builder builder() {
    return new AutoValue_BuildSdkAsarCommand.Builder().setOverwriteOutput(false);
  }

  /** Builder for the {@link BuildSdkAsarCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the path to the input SDK bundle. Must have the extension ".asb". */
    public abstract Builder setSdkBundlePath(Path sdkBundlePath);

    /** Sets the APK signing certificate */
    public abstract Builder setApkSigningCertificate(X509Certificate certificate);

    /** Sets path to the output produced by the command. Must have the extension ".asar". */
    public abstract Builder setOutputFile(Path outputFile);

    /**
     * Sets whether to overwrite the contents of the output file.
     *
     * <p>The default is {@code false}. If set to {@code false} and the output file is present,
     * exception is thrown.
     */
    public abstract Builder setOverwriteOutput(boolean overwriteOutput);

    public abstract BuildSdkAsarCommand build();
  }

  public static BuildSdkAsarCommand fromFlags(ParsedFlags flags) {
    Builder sdkAsarCommandBuilder =
        BuildSdkAsarCommand.builder()
            .setSdkBundlePath(SDK_BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .setOutputFile(OUTPUT_FILE_FLAG.getRequiredValue(flags));

    // Optional arguments.
    OVERWRITE_OUTPUT_FLAG.getValue(flags).ifPresent(sdkAsarCommandBuilder::setOverwriteOutput);
    APK_SIGNING_CERTIFICATE_LOCATION_FLAG
        .getValue(flags)
        .map(CodeTransparencyCryptoUtils::getX509Certificate)
        .ifPresent(sdkAsarCommandBuilder::setApkSigningCertificate);

    flags.checkNoUnknownFlags();

    return sdkAsarCommandBuilder.build();
  }

  public Path execute() {
    validateInput();

    try (ZipFile bundleZip = new ZipFile(getSdkBundlePath().toFile())) {
      TempDirectory tempDir = new TempDirectory(getClass().getSimpleName());
      SdkBundleValidator bundleValidator = SdkBundleValidator.create();
      bundleValidator.validateFile(bundleZip);

      Path modulesPath = tempDir.getPath().resolve(SDK_MODULES_FILE_NAME);
      try (ZipFile modulesZip = getModulesZip(bundleZip, modulesPath)) {
        bundleValidator.validateModulesFile(modulesZip);
        SdkBundle sdkBundle =
            SdkBundle.buildFromZip(bundleZip, modulesZip, PLACEHOLDER_VERSION_CODE);
        bundleValidator.validate(sdkBundle);

        DaggerBuildSdkAsarManagerComponent.builder()
            .setBuildSdkAsarCommand(this)
            .setSdkBundle(sdkBundle)
            .build()
            .create()
            .execute(modulesPath);
      }
    } catch (ZipException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("The SDK Bundle is not a valid zip file.")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("An error occurred when validating the Sdk Bundle.", e);
    }

    return getOutputFile();
  }

  private void validateInput() {
    FilePreconditions.checkFileExistsAndReadable(getSdkBundlePath());
    FilePreconditions.checkFileHasExtension("ASB file", getSdkBundlePath(), ".asb");

    if (!getOverwriteOutput()) {
      checkFileDoesNotExist(getOutputFile());
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription("Generates an ASAR from an Android SDK Bundle.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(SDK_BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/SDKbundle.asb")
                .setDescription("Path to SDK bundle. Must have the extension '.asb'.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APK_SIGNING_CERTIFICATE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/certificate.crt")
                .setDescription("Path to SDK APK signing certificate.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FILE_FLAG.getName())
                .setExampleValue("output.asar")
                .setDescription("Path to where the ASAR should be created.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OVERWRITE_OUTPUT_FLAG.getName())
                .setOptional(true)
                .setDescription("If set, any previous existing output will be overwritten.")
                .build())
        .build();
  }
}
