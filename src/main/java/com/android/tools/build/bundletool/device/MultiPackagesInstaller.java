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

package com.android.tools.build.bundletool.device;

import static com.android.utils.ImmutableCollectors.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;

/** Installer that allows to install multiple packages in the same install session on device */
public final class MultiPackagesInstaller {
  private static final Logger logger = Logger.getLogger(MultiPackagesInstaller.class.getName());

  private final SessionIdParser sessionIdParser = new SessionIdParser();

  private final Device device;
  private final boolean enableRollback;
  private final boolean noCommit;

  public MultiPackagesInstaller(Device device, boolean enableRollback, boolean noCommit) {
    this.device = device;
    this.enableRollback = enableRollback;
    this.noCommit = noCommit;
  }

  /*
   * Performs install of multiple packages in the same commit session. Flow:   *
   * 1. Initialize multi-package session.
   * 2. Install each package in its own child session.
   * 3. Attach all child sessions to multi-package one.
   * 4. Commit multi-package session.
   */
  public void install(ImmutableListMultimap<String, InstallableApk> apksByPackageName) {
    int parentSessionId = startParentSession();
    boolean abandonSession = true;
    try {
      ImmutableList<Integer> childSessionIds =
          apksByPackageName.keySet().stream()
              .sorted()
              .map(
                  packageName ->
                      installSinglePackage(packageName, apksByPackageName.get(packageName)))
              .collect(toImmutableList());

      attachChildSessionsToParent(parentSessionId, childSessionIds);
      if (noCommit) {
        logger.info("Abandoning install session due to 'no commit' requested.");
      }
      abandonSession = noCommit;
    } finally {
      finalizeParentSession(parentSessionId, abandonSession);
      logger.info(String.format("Install %s", abandonSession ? "abandoned" : "committed"));
    }
  }

  private int startParentSession() {
    String startSessionCommand =
        String.format(
            "pm install-create --multi-package --staged%s",
            enableRollback ? " --enable-rollback" : "");
    return sessionIdParser.parse(executeAndValidateSuccess(device, startSessionCommand));
  }

  private void finalizeParentSession(int sessionId, boolean abandonSession) {
    String finalizeSessionCommand =
        String.format("pm %s %d", abandonSession ? "install-abandon" : "install-commit", sessionId);
    executeAndValidateSuccess(device, finalizeSessionCommand);
  }

  private int installSinglePackage(String packageName, ImmutableList<InstallableApk> apks) {
    logger.info(String.format("Installing %s", packageName));
    int childSessionId = createChildSession(apks);

    for (int index = 0; index < apks.size(); index++) {
      InstallableApk apkToInstall = apks.get(index);
      logger.info(String.format("\tWriting %s", apkToInstall.getPath().getFileName()));

      Path remotePath = syncPackageToDevice(apkToInstall.getPath());
      try {
        installRemoteApk(childSessionId, packageName + "_" + index, remotePath);
      } finally {
        removePackageFromDevice(remotePath);
      }
    }
    return childSessionId;
  }

  private Path syncPackageToDevice(Path apk) {
    try {
      return device.syncPackageToDevice(apk);
    } catch (TimeoutException | AdbCommandRejectedException | SyncException | IOException e) {
      throw CommandExecutionException.builder()
          .withMessage("Sync APK to device has failed")
          .withCause(e)
          .build();
    }
  }

  private void removePackageFromDevice(Path remoteApk) {
    try {
      device.removeRemotePackage(remoteApk);
    } catch (InstallException e) {
      throw CommandExecutionException.builder()
          .withMessage("Package removal has failed")
          .withCause(e)
          .build();
    }
  }

  private int createChildSession(ImmutableList<InstallableApk> apks) {
    String childSessionCommand =
        String.format(
            "pm install-create --staged%s%s",
            enableRollback ? " --enable-rollback" : "", hasApexApk(apks) ? " --apex" : "");
    return sessionIdParser.parse(executeAndValidateSuccess(device, childSessionCommand));
  }

  private void attachChildSessionsToParent(
      int parentSessionId, ImmutableList<Integer> childSessionIds) {
    String attachCommand =
        String.format(
            "pm install-add-session %d %s",
            parentSessionId, childSessionIds.stream().map(Object::toString).collect(joining(" ")));
    executeAndValidateSuccess(device, attachCommand);
  }

  private void installRemoteApk(int sessionId, String splitName, Path remoteApk) {
    String installCommand =
        String.format(
            "pm install-write %d %s %s", sessionId, splitName, remoteApk.toAbsolutePath());
    executeAndValidateSuccess(device, installCommand);
  }

  private static boolean hasApexApk(ImmutableList<InstallableApk> apks) {
    return apks.stream()
        .anyMatch(
            apk ->
                apk.getPath().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".apex"));
  }

  private static ImmutableList<String> executeAndValidateSuccess(Device device, String command) {
    ImmutableList<String> output = new AdbShellCommandTask(device, command).execute();
    AdbCommandOutputValidator.validateSuccess(output, command);
    return output;
  }

  /** Represents pair of APK path and package name of this APK. */
  @AutoValue
  public abstract static class InstallableApk {

    public static InstallableApk create(Path path, String packageName) {
      return new AutoValue_MultiPackagesInstaller_InstallableApk(path, packageName);
    }

    public abstract Path getPath();

    public abstract String getPackageName();

    InstallableApk() {}
  }
}
