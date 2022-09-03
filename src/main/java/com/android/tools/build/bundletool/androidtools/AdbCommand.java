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

package com.android.tools.build.bundletool.androidtools;

import com.android.tools.build.bundletool.androidtools.CommandExecutor.CommandOptions;
import com.android.tools.build.bundletool.model.utils.OsPlatform;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/** Interface wrapper around invoking arbitrary adb commands. */
public interface AdbCommand {
  Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  ImmutableList<String> installMultiPackage(
      ImmutableListMultimap<String, String> apkToInstallByPackage,
      boolean staged,
      boolean enableRollback,
      Optional<Duration> timeout,
      Optional<String> deviceId);

  static AdbCommand create(Path adbPath) {
    return new DefaultAdbCommand(adbPath);
  }

  /** Default implementation of AdbCommand. */
  class DefaultAdbCommand implements AdbCommand {
    private static final Logger logger = Logger.getLogger(DefaultAdbCommand.class.getName());
    private final Path adbPath;

    public DefaultAdbCommand(Path adbPath) {
      this.adbPath = adbPath;
    }

    @Override
    public ImmutableList<String> installMultiPackage(
        ImmutableListMultimap<String, String> apkToInstallByPackage,
        boolean staged,
        boolean enableRollback,
        Optional<Duration> timeout,
        Optional<String> deviceId) {
      return installMultiPackage(
          apkToInstallByPackage,
          staged,
          enableRollback,
          timeout,
          deviceId,
          new DefaultCommandExecutor());
    }

    @VisibleForTesting
    ImmutableList<String> installMultiPackage(
        ImmutableListMultimap<String, String> apkToInstallByPackage,
        boolean staged,
        boolean enableRollback,
        Optional<Duration> timeout,
        Optional<String> deviceId,
        CommandExecutor commandExecutor) {
      ImmutableList.Builder<String> commandBuilder =
          ImmutableList.<String>builder().add(adbPath.toString());
      deviceId.ifPresent(id -> commandBuilder.add("-s", id));
      commandBuilder.add("install-multi-package");
      if (staged) {
        commandBuilder.add("--staged");
      }
      if (enableRollback) {
        commandBuilder.add("--enable-rollback");
      }
      if (timeout.isPresent()) {
        commandBuilder
            .add("--staged-ready-timeout")
            .add(String.format("%d", timeout.get().toMillis()));
      }
      String splitCharacter = (OsPlatform.getCurrentPlatform() == OsPlatform.WINDOWS) ? ";" : ":";
      // Splits of a single package must be installed together.
      apkToInstallByPackage
          .keySet()
          .forEach(
              packageName ->
                  commandBuilder.add(
                      String.join(splitCharacter, apkToInstallByPackage.get(packageName))));

      logger.info(String.format("Executing: %s", String.join(" ", commandBuilder.build())));

      return commandExecutor.executeAndCapture(
          commandBuilder.build(), CommandOptions.builder().setTimeout(DEFAULT_TIMEOUT).build());
    }
  }
}
