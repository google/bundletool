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

import static com.android.tools.build.bundletool.commands.CommandUtils.ANDROID_SERIAL_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;
import static com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils.getX509Certificate;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.transparency.ApkModeTransparencyChecker;
import com.android.tools.build.bundletool.transparency.BundleModeTransparencyChecker;
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils;
import com.android.tools.build.bundletool.transparency.ConnectedDeviceModeTransparencyChecker;
import com.android.tools.build.bundletool.transparency.TransparencyCheckResult;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Optional;

/** Command to verify code transparency. */
@AutoValue
public abstract class CheckTransparencyCommand {

  public static final String COMMAND_NAME = "check-transparency";

  /** Mode to run {@link CheckTransparencyCommand} against. */
  public enum Mode {
    CONNECTED_DEVICE,
    BUNDLE,
    APK;

    final String getLowerCaseName() {
      return Ascii.toLowerCase(name());
    }
  }

  private static final Flag<Mode> MODE_FLAG = Flag.enumFlag("mode", Mode.class);

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");

  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");

  private static final Flag<String> PACKAGE_NAME_FLAG = Flag.string("package-name");

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");

  private static final Flag<Path> APK_ZIP_LOCATION_FLAG = Flag.path("apk-zip");

  private static final Flag<Path> TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG =
      Flag.path("transparency-key-certificate");

  private static final Flag<Path> APK_SIGNING_KEY_CERTIFICATE_LOCATION_FLAG =
      Flag.path("apk-signing-key-certificate");

  private static final String MODE_FLAG_OPTIONS =
      stream(Mode.values()).map(Mode::getLowerCaseName).collect(joining("|"));

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Mode getMode();

  public abstract Optional<Path> getAdbPath();

  public abstract Optional<String> getDeviceId();

  public abstract Optional<String> getPackageName();

  public abstract Optional<AdbServer> getAdbServer();

  public abstract Optional<Path> getBundlePath();

  public abstract Optional<Path> getApkZipPath();

  abstract Optional<X509Certificate> getTransparencyKeyCertificate();

  abstract Optional<X509Certificate> getApkSigningKeyCertificate();

  public static CheckTransparencyCommand.Builder builder() {
    return new AutoValue_CheckTransparencyCommand.Builder();
  }

  /** Builder for the {@link CheckTransparencyCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setMode(Mode mode);

    public abstract Builder setAdbPath(Path adbPath);

    public abstract Builder setDeviceId(String deviceId);

    public abstract Builder setAdbServer(AdbServer adbServer);

    public abstract Builder setPackageName(String packageName);

    /** Sets the path to the input bundle. Must have the extension ".aab". */
    public abstract Builder setBundlePath(Path bundlePath);

    /** Sets the path to the .zip archive of device-specific APK files. */
    public abstract Builder setApkZipPath(Path apkZipPath);

    abstract Builder setTransparencyKeyCertificate(X509Certificate transparencyKeyCertificate);

    abstract Builder setApkSigningKeyCertificate(X509Certificate apkSigningKeyCertificate);

    public abstract CheckTransparencyCommand build();
  }

  public static CheckTransparencyCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static CheckTransparencyCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    Mode mode = MODE_FLAG.getRequiredValue(flags);
    switch (mode) {
      case CONNECTED_DEVICE:
        return fromFlagsInConnectedDeviceMode(flags, systemEnvironmentProvider, adbServer);
      case BUNDLE:
        return fromFlagsInBundleMode(flags);
      case APK:
        return fromFlagsInApkMode(flags);
    }
    throw new IllegalStateException("Unrecognized value of --mode flag.");
  }

  private static CheckTransparencyCommand fromFlagsInBundleMode(ParsedFlags flags) {
    CheckTransparencyCommand.Builder checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.BUNDLE)
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags));
    TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG
        .getValue(flags)
        .ifPresent(
            path ->
                checkTransparencyCommand.setTransparencyKeyCertificate(getX509Certificate(path)));
    flags.checkNoUnknownFlags();
    return checkTransparencyCommand.build();
  }

  private static CheckTransparencyCommand fromFlagsInApkMode(ParsedFlags flags) {
    CheckTransparencyCommand.Builder checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.APK)
            .setApkZipPath(APK_ZIP_LOCATION_FLAG.getRequiredValue(flags));
    TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG
        .getValue(flags)
        .ifPresent(
            path ->
                checkTransparencyCommand.setTransparencyKeyCertificate(getX509Certificate(path)));
    APK_SIGNING_KEY_CERTIFICATE_LOCATION_FLAG
        .getValue(flags)
        .ifPresent(
            path -> checkTransparencyCommand.setApkSigningKeyCertificate(getX509Certificate(path)));
    flags.checkNoUnknownFlags();
    return checkTransparencyCommand.build();
  }

  private static CheckTransparencyCommand fromFlagsInConnectedDeviceMode(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    CheckTransparencyCommand.Builder checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.CONNECTED_DEVICE)
            .setPackageName(PACKAGE_NAME_FLAG.getRequiredValue(flags));
    Optional<String> deviceSerialName = DEVICE_ID_FLAG.getValue(flags);
    if (!deviceSerialName.isPresent()) {
      deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
    }
    deviceSerialName.ifPresent(checkTransparencyCommand::setDeviceId);

    Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, systemEnvironmentProvider);
    checkTransparencyCommand.setAdbPath(adbPath).setAdbServer(adbServer);
    TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG
        .getValue(flags)
        .ifPresent(
            path ->
                checkTransparencyCommand.setTransparencyKeyCertificate(getX509Certificate(path)));
    APK_SIGNING_KEY_CERTIFICATE_LOCATION_FLAG
        .getValue(flags)
        .ifPresent(
            path -> checkTransparencyCommand.setApkSigningKeyCertificate(getX509Certificate(path)));
    flags.checkNoUnknownFlags();
    return checkTransparencyCommand.build();
  }

  public void execute() {
    validateInput();
    checkTransparency(System.out);
  }

  public void checkTransparency(PrintStream outputStream) {
    TransparencyCheckResult result = TransparencyCheckResult.empty();
    switch (getMode()) {
      case CONNECTED_DEVICE:
        result = ConnectedDeviceModeTransparencyChecker.checkTransparency(this);
        break;
      case BUNDLE:
        result = BundleModeTransparencyChecker.checkTransparency(this);
        break;
      case APK:
        result = ApkModeTransparencyChecker.checkTransparency(this);
        break;
    }
    printResult(outputStream, result);
  }

  private void printResult(PrintStream outputStream, TransparencyCheckResult result) {
    boolean apkSignatureVerificationSuccess = verifyAndPrintApkSignatureCert(outputStream, result);
    if (apkSignatureVerificationSuccess) {
      printCodeTransparencyVerificationResult(outputStream, result);
    }
  }

  private boolean verifyAndPrintApkSignatureCert(
      PrintStream outputStream, TransparencyCheckResult result) {
    Preconditions.checkState(
        getMode().equals(Mode.BUNDLE)
            || !result.verified()
            || result.apkSigningKeyCertificateFingerprint().isPresent(),
        "APK signing key certificate fingerprint must be present in TransparencyCheckResult.");
    if (getMode().equals(Mode.BUNDLE)) {
      outputStream.println("No APK present. APK signature was not checked.");
      return true;
    }
    if (!result.apkSigningKeyCertificateFingerprint().isPresent()) {
      Preconditions.checkState(
          !result.verified(),
          "Successful TransparencyCheckResult must specify APK signing key certificate.");
      outputStream.println(result.getErrorMessage());
      return false;
    }
    if (!getApkSigningKeyCertificate().isPresent()) {
      outputStream.println(
          "APK signature is valid. SHA-256 fingerprint of the apk signing key certificate (must be"
              + " compared with the developer's public key manually): "
              + result.getApkSigningKeyCertificateFingerprint());
      return true;
    }
    String providedApkSigningKeyCertificateFingerprint =
        CodeTransparencyCryptoUtils.getCertificateFingerprint(getApkSigningKeyCertificate().get());
    if (result
        .getApkSigningKeyCertificateFingerprint()
        .equals(providedApkSigningKeyCertificateFingerprint)) {
      outputStream.println("APK signature verified for the provided apk signing key certificate.");
      return true;
    }
    outputStream.println(
        "APK signature verification failed because the provided public key certificate does"
            + " not match the APK signature."
            + "\nSHA-256 fingerprint of the certificate that was used to sign the APKs: "
            + result.getApkSigningKeyCertificateFingerprint()
            + "\nSHA-256 fingerprint of the certificate that was provided: "
            + providedApkSigningKeyCertificateFingerprint);
    return false;
  }

  private void printCodeTransparencyVerificationResult(
      PrintStream outputStream, TransparencyCheckResult result) {
    if (!result.verified()) {
      outputStream.println(result.getErrorMessage());
      return;
    }
    if (!getTransparencyKeyCertificate().isPresent()) {
      outputStream.println(
          "Code transparency signature is valid. SHA-256 fingerprint of the code transparency key"
              + " certificate (must be compared with the developer's public key manually): "
              + result.getTransparencyKeyCertificateFingerprint());
      outputStream.println(
          "Code transparency verified: code related file contents match the code transparency"
              + " file.");
      return;
    }
    String providedTransparencyKeyCertificateFingerprint =
        CodeTransparencyCryptoUtils.getCertificateFingerprint(
            getTransparencyKeyCertificate().get());
    if (result
        .getTransparencyKeyCertificateFingerprint()
        .equals(providedTransparencyKeyCertificateFingerprint)) {
      outputStream.println(
          "Code transparency signature verified for the provided code transparency key"
              + " certificate.");
      outputStream.println(
          "Code transparency verified: code related file contents match the code transparency"
              + " file.");
    } else {
      outputStream.println(
          "Code transparency verification failed because the provided public key certificate does"
              + " not match the code transparency file."
              + "\nSHA-256 fingerprint of the certificate that was used to sign code transparency"
              + " file: "
              + result.getTransparencyKeyCertificateFingerprint()
              + "\nSHA-256 fingerprint of the certificate that was provided: "
              + providedTransparencyKeyCertificateFingerprint);
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder().setShortDescription("Verifies code transparency.").build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODE_FLAG.getName())
                .setExampleValue(MODE_FLAG_OPTIONS)
                .setOptional(true)
                .setDescription(
                    "Specifies which mode to run '%s' command against. Acceptable values are '%s'."
                        + " If set to '%s' we verify code transparency for a given app installed"
                        + " on a connected device. If set to '%s', we verify code transparency for"
                        + " a given Android App Bundle file. If set to '%s', we verify code"
                        + " transparency for a given set of device-specific APK files.",
                    CheckTransparencyCommand.COMMAND_NAME,
                    MODE_FLAG_OPTIONS,
                    Mode.CONNECTED_DEVICE.getLowerCaseName(),
                    Mode.BUNDLE.getLowerCaseName(),
                    Mode.APK.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. Used only in '%s' mode. If absent, an attempt will"
                        + " be made to locate it if the %s or %s environment variable is set.",
                    Mode.CONNECTED_DEVICE.getLowerCaseName(),
                    ANDROID_HOME_VARIABLE,
                    SYSTEM_PATH_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. Used only in '%s' mode. If absent, this uses"
                        + " the %s environment variable. Either this flag or the environment"
                        + " variable is required when more than one device or emulator is"
                        + " connected.",
                    Mode.CONNECTED_DEVICE.getLowerCaseName(), ANDROID_SERIAL_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(PACKAGE_NAME_FLAG.getName())
                .setExampleValue("package-name")
                .setOptional(true)
                .setDescription(
                    "Package name of the app that code transparency will be verified for. Used"
                        + " only in '%s' mode.",
                    Mode.CONNECTED_DEVICE.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/bundle.aab")
                .setOptional(true)
                .setDescription(
                    "Path to the Android App Bundle that we want to verify code transparency for."
                        + " Used only in '%s' mode. Must have extension .aab.",
                    Mode.BUNDLE.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APK_ZIP_LOCATION_FLAG.getName())
                .setExampleValue("path/to/apks.zip")
                .setOptional(true)
                .setDescription(
                    "Path to the zip archive of device specific APKs that we want to verify code"
                        + " transparency for. Used only in '%s' mode. Must have extension .zip.",
                    Mode.APK.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(TRANSPARENCY_KEY_CERTIFICATE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/certificate.cert")
                .setOptional(true)
                .setDescription(
                    "Path to the file containing the public key certificate that should be used"
                        + " for code transparency signature verification. If not set, fingerprint"
                        + " of the certificate that is used will be printed.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APK_SIGNING_KEY_CERTIFICATE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/certificate.cert")
                .setOptional(true)
                .setDescription(
                    "Path to the file containing the public key certificate that should be used"
                        + " for APK signature verification. Can only be set in '%s and '%s' modes."
                        + " If not set, fingerprint of the certificate that is used will be"
                        + " printed.",
                    Mode.APK.getLowerCaseName(), Mode.CONNECTED_DEVICE.getLowerCaseName())
                .build())
        .build();
  }

  private void validateInput() {
    if (getBundlePath().isPresent()) {
      FilePreconditions.checkFileHasExtension("AAB file", getBundlePath().get(), ".aab");
      FilePreconditions.checkFileExistsAndReadable(getBundlePath().get());
    }
    if (getApkZipPath().isPresent()) {
      FilePreconditions.checkFileHasExtension("Zip file", getApkZipPath().get(), ".zip");
      FilePreconditions.checkFileExistsAndReadable(getApkZipPath().get());
    }
  }
}
