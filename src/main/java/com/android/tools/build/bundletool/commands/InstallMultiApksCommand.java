/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileHasExtension;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.androidtools.AdbCommand;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.AdbShellCommandTask;
import com.android.tools.build.bundletool.device.BadgingInfoParser;
import com.android.tools.build.bundletool.device.BadgingInfoParser.BadgingInfo;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.device.PackagesParser;
import com.android.tools.build.bundletool.device.PackagesParser.InstalledPackageInfo;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.auto.value.AutoValue;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.io.ByteStreams;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs multiple APKs on a connected device atomically.
 *
 * <p>If any APKs fails to install, the entire installation fails.
 */
@AutoValue
public abstract class InstallMultiApksCommand {
  private static final Logger logger = Logger.getLogger(InstallMultiApksCommand.class.getName());

  public static final String COMMAND_NAME = "install-multi-apks";

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");
  private static final Flag<ImmutableList<Path>> APKS_ARCHIVES_FLAG = Flag.pathList("apks");
  private static final Flag<Path> APKS_ARCHIVE_ZIP_FLAG = Flag.path("apks-zip");
  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");
  private static final Flag<Boolean> STAGED = Flag.booleanFlag("staged");
  private static final Flag<Boolean> ENABLE_ROLLBACK_FLAG = Flag.booleanFlag("enable-rollback");
  private static final Flag<Boolean> UPDATE_ONLY_FLAG = Flag.booleanFlag("update-only");
  private static final Flag<Path> AAPT2_PATH_FLAG = Flag.path("aapt2");
  private static final Flag<Integer> TIMEOUT_MILLIS_FLAG = Flag.positiveInteger("timeout-millis");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  abstract Path getAdbPath();

  abstract Optional<Aapt2Command> getAapt2Command();

  abstract Optional<AdbCommand> getAdbCommand();

  private AdbCommand getOrCreateAdbCommand() {
    return getAdbCommand().orElse(AdbCommand.create(getAdbPath()));
  }

  abstract ImmutableList<Path> getApksArchivePaths();

  abstract Optional<Path> getApksArchiveZipPath();

  abstract Optional<String> getDeviceId();

  abstract boolean getEnableRollback();

  abstract boolean getStaged();

  abstract boolean getUpdateOnly();

  abstract AdbServer getAdbServer();

  abstract Optional<Duration> getTimeout();

  public static Builder builder() {
    return new AutoValue_InstallMultiApksCommand.Builder()
        .setStaged(false)
        .setEnableRollback(false)
        .setUpdateOnly(false);
  }

  /** Builder for the {@link InstallMultiApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder setAdbPath(Path adbPath);

    @CanIgnoreReturnValue
    abstract Builder setAapt2Command(Aapt2Command value);

    abstract Builder setAdbCommand(AdbCommand value);

    @CanIgnoreReturnValue
    abstract Builder setApksArchivePaths(ImmutableList<Path> paths);

    abstract ImmutableList.Builder<Path> apksArchivePathsBuilder();

    @CanIgnoreReturnValue
    abstract Builder setApksArchiveZipPath(Path value);

    @SuppressWarnings("unused")
    @CanIgnoreReturnValue
    Builder addApksArchivePath(Path value) {
      apksArchivePathsBuilder().add(value);
      return this;
    }

    @CanIgnoreReturnValue
    abstract Builder setDeviceId(String deviceId);

    abstract Builder setEnableRollback(boolean value);

    abstract Builder setStaged(boolean value);

    abstract Builder setUpdateOnly(boolean value);

    /** The caller is responsible for the lifecycle of the {@link AdbServer}. */
    abstract Builder setAdbServer(AdbServer adbServer);

    abstract Builder setTimeout(Duration value);

    public abstract InstallMultiApksCommand build();
  }

  public static InstallMultiApksCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static InstallMultiApksCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, systemEnvironmentProvider);

    InstallMultiApksCommand.Builder command = builder().setAdbPath(adbPath).setAdbServer(adbServer);
    CommandUtils.getDeviceSerialName(flags, DEVICE_ID_FLAG, systemEnvironmentProvider)
        .ifPresent(command::setDeviceId);
    ENABLE_ROLLBACK_FLAG.getValue(flags).ifPresent(command::setEnableRollback);
    UPDATE_ONLY_FLAG.getValue(flags).ifPresent(command::setUpdateOnly);
    STAGED.getValue(flags).ifPresent(command::setStaged);
    AAPT2_PATH_FLAG
        .getValue(flags)
        .ifPresent(
            aapt2Path -> command.setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path)));
    TIMEOUT_MILLIS_FLAG
        .getValue(flags)
        .ifPresent(timeoutMillis -> command.setTimeout(Duration.ofMillis(timeoutMillis)));

    Optional<ImmutableList<Path>> apksPaths = APKS_ARCHIVES_FLAG.getValue(flags);
    Optional<Path> apksArchiveZip = APKS_ARCHIVE_ZIP_FLAG.getValue(flags);
    if (apksPaths.isPresent() == apksArchiveZip.isPresent()) {
      throw InvalidCommandException.builder()
          .withInternalMessage("Exactly one of --apks or --apks-zip must be set.")
          .build();
    }
    apksPaths.ifPresent(command::setApksArchivePaths);
    apksArchiveZip.ifPresent(command::setApksArchiveZipPath);

    flags.checkNoUnknownFlags();

    return command.build();
  }

  public void execute() throws TimeoutException, IOException {
    validateInput();

    AdbServer adbServer = getAdbServer();
    adbServer.init(getAdbPath());

    try (TempDirectory tempDirectory = new TempDirectory()) {
      DeviceAnalyzer deviceAnalyzer = new DeviceAnalyzer(adbServer);
      DeviceSpec deviceSpec = deviceAnalyzer.getDeviceSpec(getDeviceId());
      Device device = deviceAnalyzer.getAndValidateDevice(getDeviceId());

      if (getTimeout().isPresent()
          && !device.getVersion().isGreaterOrEqualThan(Versions.ANDROID_S_API_VERSION)) {
        throw InvalidCommandException.builder()
            .withInternalMessage(
                "'%s' flag is supported for Android 12+ devices.", TIMEOUT_MILLIS_FLAG.getName())
            .build();
      }

      Path aapt2Dir = tempDirectory.getPath().resolve("aapt2");
      Files.createDirectory(aapt2Dir);
      Supplier<Aapt2Command> aapt2CommandSupplier =
          Suppliers.memoize(() -> getOrExtractAapt2Command(aapt2Dir));

      ImmutableMap<String, InstalledPackageInfo> existingPackages =
          getPackagesInstalledOnDevice(device);

      ImmutableList<PackagePathVersion> installableApksFilesWithBadgingInfo =
          getActualApksPaths(tempDirectory).stream()
              .flatMap(
                  apksArchivePath ->
                      stream(
                          apksWithPackageName(apksArchivePath, deviceSpec, aapt2CommandSupplier)))
              .filter(apks -> shouldInstall(apks, existingPackages))
              .collect(toImmutableList());

      ImmutableList<PackagePathVersion> apkFilesToInstall =
          uniqueApksByPackageName(installableApksFilesWithBadgingInfo).stream()
              .flatMap(
                  apks ->
                      extractApkListFromApks(
                          deviceSpec,
                          apks,
                          Optional.ofNullable(existingPackages.get(apks.getPackageName())),
                          tempDirectory)
                          .stream())
              .collect(toImmutableList());
      ImmutableListMultimap<String, String> apkToInstallByPackage =
          apkFilesToInstall.stream()
              .collect(
                  toImmutableListMultimap(
                      PackagePathVersion::getPackageName,
                      packagePathVersion ->
                          packagePathVersion.getPath().toAbsolutePath().toString()));

      if (apkFilesToInstall.isEmpty()) {
        logger.warning("No packages found to install! Exiting...");
        return;
      }

      AdbCommand adbCommand = getOrCreateAdbCommand();
      ImmutableList<String> commandResults =
          adbCommand.installMultiPackage(
              apkToInstallByPackage, getStaged(), getEnableRollback(), getTimeout(), getDeviceId());
      logger.info(String.format("Output:\n%s", String.join("\n", commandResults)));
      logger.info("Please reboot device to complete installation.");
    }
  }

  /**
   * The package should be installed if:
   *
   * <ul>
   *   <li>If it is not already present on the device and --update-only is not set, or
   *   <li>The installable version has a equal or higher version code than the one already
   *       installed.
   * </ul>
   */
  private boolean shouldInstall(
      PackagePathVersion apk, ImmutableMap<String, InstalledPackageInfo> existingPackages) {
    if (getUpdateOnly() && !existingPackages.containsKey(apk.getPackageName())) {
      logger.info(
          String.format(
              "Package '%s' not present on device, skipping due to --%s.",
              apk.getPackageName(), UPDATE_ONLY_FLAG.getName()));
      return false;
    }

    if (!existingPackages.containsKey(apk.getPackageName())) {
      return true;
    }

    InstalledPackageInfo existingPackage =
        requireNonNull(existingPackages.get(apk.getPackageName()));

    if (existingPackage.getVersionCode() <= apk.getVersionCode()) {
      return true;
    }

    // If the user is attempting to install a mixture of lower and higher version .apks, that
    // likely indicates something is wrong.
    logger.warning(
        String.format(
            "A higher version of package '%s' (%d vs %d) is already present on device,"
                + " skipping.",
            apk.getPackageName(), apk.getVersionCode(), existingPackage.getVersionCode()));

    return false;
  }

  private static ImmutableMap<String, InstalledPackageInfo> getPackagesInstalledOnDevice(
      Device device) {
    // List standard packages (excluding apex)
    ImmutableList<String> listPackagesOutput =
        new AdbShellCommandTask(device, "pm list packages --show-versioncode").execute();
    // List .apex packages.
    ImmutableList<String> listApexPackagesOutput =
        new AdbShellCommandTask(device, "pm list packages --apex-only --show-versioncode")
            .execute();

    ImmutableSet<InstalledPackageInfo> installedApks =
        new PackagesParser(/* isApex= */ false).parse(listPackagesOutput);
    ImmutableSet<InstalledPackageInfo> installedApexPackages =
        new PackagesParser(/* isApex= */ true).parse(listApexPackagesOutput);

    return Streams.concat(installedApks.stream(), installedApexPackages.stream())
        .collect(
            toImmutableMap(
                InstalledPackageInfo::getPackageName,
                installedPackageInfo -> installedPackageInfo));
  }

  private static Optional<PackagePathVersion> apksWithPackageName(
      Path apksArchivePath, DeviceSpec deviceSpec, Supplier<Aapt2Command> aapt2CommandSupplier) {
    try (TempDirectory tempDirectory = new TempDirectory()) {
      // Any of the extracted .apk/.apex files will work.
      Path extractedFile =
          ExtractApksCommand.builder()
              .setApksArchivePath(apksArchivePath)
              .setDeviceSpec(deviceSpec)
              .setOutputDirectory(tempDirectory.getPath())
              .build()
              .execute()
              .get(0);

      BadgingInfo badgingInfo =
          BadgingInfoParser.parse(aapt2CommandSupplier.get().dumpBadging(extractedFile));
      return Optional.of(
          PackagePathVersion.create(
              apksArchivePath, badgingInfo.getPackageName(), badgingInfo.getVersionCode()));
    } catch (IncompatibleDeviceException e) {
      logger.warning(
          String.format(
              "Unable to determine package name of %s, as it is not compatible with the attached"
                  + " device. Skipping.",
              apksArchivePath));
      return Optional.empty();
    }
  }

  private void validateInput() {
    getApksArchiveZipPath()
        .ifPresent(
            zip -> {
              checkFileExistsAndReadable(zip);
              checkFileHasExtension("ZIP file", zip, ".zip");
            });
    getApksArchivePaths().forEach(InstallMultiApksCommand::checkValidApksFile);
    checkFileExistsAndExecutable(getAdbPath());
  }

  private static void checkValidApksFile(Path path) {
    checkFileExistsAndReadable(path);
    checkFileHasExtension("APKS file", path, ".apks");
  }

  /** Extracts the apk/apex files that will be installed from a given .apks. */
  private static ImmutableList<PackagePathVersion> extractApkListFromApks(
      DeviceSpec deviceSpec,
      PackagePathVersion apksArchive,
      Optional<InstalledPackageInfo> installedPackage,
      TempDirectory tempDirectory) {
    logger.info(String.format("Extracting package '%s'", apksArchive.getPackageName()));
    try {
      Path output = tempDirectory.getPath().resolve(apksArchive.getPackageName());
      Files.createDirectory(output);

      ExtractApksCommand.Builder extractApksCommand =
          ExtractApksCommand.builder()
              .setApksArchivePath(apksArchive.getPath())
              .setDeviceSpec(deviceSpec)
              .setOutputDirectory(output);

      ImmutableList<Path> extractedPaths =
          fixExtension(
              extractApksCommand.build().execute(),
              installedPackage.map(InstalledPackageInfo::isApex).orElse(false));

      return extractedPaths.stream()
          .map(
              path ->
                  PackagePathVersion.create(
                      path, apksArchive.getPackageName(), apksArchive.getVersionCode()))
          .collect(toImmutableList());
    } catch (IncompatibleDeviceException e) {
      logger.warning(
          String.format(
              "Package '%s' is not supported by the attached device (SDK version %d). Skipping.",
              apksArchive.getPackageName(), deviceSpec.getSdkVersion()));
      return ImmutableList.of();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ImmutableList<Path> fixExtension(
      ImmutableList<Path> extractedPaths, boolean isApex) throws IOException {
    if (!isApex) {
      return extractedPaths;
    }
    ImmutableList.Builder<Path> newPaths = ImmutableList.builder();
    for (Path path : extractedPaths) {
      Path withApexExtension =
          path.resolveSibling(
              com.google.common.io.Files.getNameWithoutExtension(path.toString()) + ".apex");
      Files.move(path, withApexExtension);
      newPaths.add(withApexExtension);
    }
    return newPaths.build();
  }

  /**
   * Gets the list of actual .apks files to install, extracting them from the .zip file to a temp
   * directory if necessary.
   */
  private ImmutableList<Path> getActualApksPaths(TempDirectory tempDirectory) throws IOException {
    return getApksArchiveZipPath().isPresent()
        ? extractApksFromZip(getApksArchiveZipPath().get(), tempDirectory)
        : getApksArchivePaths();
  }

  /** Extract the .apks files from a zip file containing multiple .apks files. */
  private static ImmutableList<Path> extractApksFromZip(Path zipPath, TempDirectory tempDirectory)
      throws IOException {
    ImmutableList.Builder<Path> extractedApks = ImmutableList.builder();
    Path zipExtractedSubDirectory = tempDirectory.getPath().resolve("extracted");
    Files.createDirectory(zipExtractedSubDirectory);
    try (ZipFile apksArchiveContainer = new ZipFile(zipPath.toFile())) {
      ImmutableList<ZipEntry> apksToExtractList =
          apksArchiveContainer.stream()
              .filter(
                  zipEntry ->
                      !zipEntry.isDirectory()
                          && zipEntry.getName().toLowerCase(Locale.ROOT).endsWith(".apks")
                          // Compressed .apks cannot be installed via bundletool, only included in
                          // System Images.
                          && !zipEntry
                              .getName()
                              .toLowerCase(Locale.ROOT)
                              .endsWith("compressed.apks"))
              .collect(toImmutableList());
      for (ZipEntry apksToExtract : apksToExtractList) {
        Path extractedApksPath =
            zipExtractedSubDirectory.resolve(ZipPath.create(apksToExtract.getName()).toString());
        Files.createDirectories(extractedApksPath.getParent());
        try (InputStream inputStream = apksArchiveContainer.getInputStream(apksToExtract);
            OutputStream outputApks = Files.newOutputStream(extractedApksPath)) {
          ByteStreams.copy(inputStream, outputApks);
          extractedApks.add(extractedApksPath);
        }
      }
    }
    return extractedApks.build();
  }

  /**
   * If multiple APKS files for the same package are present and installable, only the APKS with
   * higher version code should be installed.
   */
  private static ImmutableList<PackagePathVersion> uniqueApksByPackageName(
      ImmutableList<PackagePathVersion> installableApksFiles) {
    return installableApksFiles.stream()
        .collect(
            groupingBy(
                PackagePathVersion::getPackageName,
                maxBy(comparing(PackagePathVersion::getVersionCode))))
        .values()
        .stream()
        .flatMap(Streams::stream)
        .collect(toImmutableList());
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Atomically install APKs and APEXs from multiple APK Sets to a connected"
                        + " device.")
                .addAdditionalParagraph(
                    "This will extract and install from the APK Sets only the APKs that would be"
                        + " served to that device. If the app is not compatible with the device or"
                        + " if the APK Set was generated for a different type of device,"
                        + " this command will fail. If any one of the APK Sets fails to install,"
                        + " none of the APK Sets will be installed.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. If absent, an attempt will be made to locate it if "
                        + "the %s or %s environment variable is set.",
                    ANDROID_HOME_VARIABLE, SYSTEM_PATH_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. If absent, this uses the %s environment variable. Either "
                        + "this flag or the environment variable is required when more than one "
                        + "device or emulator is connected.",
                    ANDROID_SERIAL_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APKS_ARCHIVES_FLAG.getName())
                .setExampleValue("/path/to/apks1.apks,/path/to/apks2.apks")
                .setOptional(true)
                .setDescription(
                    "The list of .apks files to install. Either --apks or --apks-zip"
                        + " is required.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APKS_ARCHIVE_ZIP_FLAG.getName())
                .setExampleValue("/path/to/apks_containing.zip")
                .setOptional(true)
                .setDescription(
                    "Zip file containing .apks files to install. Either --apks or"
                        + " --apks-zip is required.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(STAGED.getName())
                .setOptional(true)
                .setDescription(
                    "Marks the installation as staged, to be applied on device reboot. Enabled"
                        + " automatically for APEX packages.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ENABLE_ROLLBACK_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "Enables rollback of the entire atomic install by rolling back any one of the"
                        + " packages.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(UPDATE_ONLY_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "If set, only packages that are already installed on the device will be"
                        + " updated. Entirely new packages will not be installed.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(AAPT2_PATH_FLAG.getName())
                .setExampleValue("path/to/aapt2")
                .setOptional(true)
                .setDescription("Path to the aapt2 binary to use.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(TIMEOUT_MILLIS_FLAG.getName())
                .setExampleValue("60000")
                .setOptional(true)
                .setDescription(
                    "Timeout in milliseconds which is passed to 'adb install-multi-package'"
                        + " command. One minute, by default. Only available for Android 12+"
                        + " devices.")
                .build())
        .build();
  }

  /** Utility for providing an Aapt2Command if it is needed, to be used with Suppliers.memoize. */
  private Aapt2Command getOrExtractAapt2Command(Path tempDirectoryForJarCommand) {
    if (getAapt2Command().isPresent()) {
      return getAapt2Command().get();
    }
    return CommandUtils.extractAapt2FromJar(tempDirectoryForJarCommand);
  }

  /** Represents a Package with a path to a relevant file and a version code. */
  @AutoValue
  abstract static class PackagePathVersion {

    public static PackagePathVersion create(Path path, String packageName, long versionCode) {
      return new AutoValue_InstallMultiApksCommand_PackagePathVersion(
          path, packageName, versionCode);
    }

    public abstract Path getPath();

    public abstract String getPackageName();

    public abstract long getVersionCode();

    PackagePathVersion() {}
  }
}
