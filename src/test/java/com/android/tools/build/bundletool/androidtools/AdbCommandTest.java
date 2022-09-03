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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.androidtools.AdbCommand.DefaultAdbCommand;
import com.android.tools.build.bundletool.model.utils.OsPlatform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AdbCommandTest {
  private static final String DEVICE_ID = "device_id";
  private static final String SPLIT_CHARACTER =
      (OsPlatform.getCurrentPlatform() == OsPlatform.WINDOWS) ? ";" : ":";

  @Test
  public void installMultiPackage_withDevice_withRollback() {
    new DefaultAdbCommand(Paths.get("adb"))
        .installMultiPackage(
            ImmutableListMultimap.<String, String>builder()
                .putAll("foo", ImmutableList.of("foo1.apk", "foo2.apk"))
                .putAll("bar", ImmutableList.of("bar.apex"))
                .build(),
            false,
            true,
            /* timeout= */ Optional.empty(),
            Optional.of(DEVICE_ID),
            new FakeCommandExecutor(
                ImmutableList.of(
                    "adb",
                    "-s",
                    DEVICE_ID,
                    "install-multi-package",
                    "--enable-rollback",
                    "foo1.apk" + SPLIT_CHARACTER + "foo2.apk",
                    "bar.apex")));
  }

  @Test
  public void installMultiPackage_withDevice_withStaged() {
    new DefaultAdbCommand(Paths.get("adb"))
        .installMultiPackage(
            ImmutableListMultimap.<String, String>builder()
                .putAll("foo", ImmutableList.of("foo1.apk", "foo2.apk"))
                .putAll("bar", ImmutableList.of("bar.apex"))
                .build(),
            true,
            false,
            /* timeout= */ Optional.empty(),
            Optional.of(DEVICE_ID),
            new FakeCommandExecutor(
                ImmutableList.of(
                    "adb",
                    "-s",
                    DEVICE_ID,
                    "install-multi-package",
                    "--staged",
                    "foo1.apk" + SPLIT_CHARACTER + "foo2.apk",
                    "bar.apex")));
  }

  @Test
  public void installMultiPackage_withDevice_withStaged_withTimeout() {
    new DefaultAdbCommand(Paths.get("adb"))
        .installMultiPackage(
            ImmutableListMultimap.<String, String>builder()
                .putAll("foo", ImmutableList.of("foo1.apk", "foo2.apk"))
                .putAll("bar", ImmutableList.of("bar.apex"))
                .build(),
            true,
            false,
            /* timeout= */ Optional.of(Duration.ofSeconds(3)),
            Optional.of(DEVICE_ID),
            new FakeCommandExecutor(
                ImmutableList.of(
                    "adb",
                    "-s",
                    DEVICE_ID,
                    "install-multi-package",
                    "--staged",
                    "--staged-ready-timeout",
                    "3000",
                    "foo1.apk" + SPLIT_CHARACTER + "foo2.apk",
                    "bar.apex")));
  }

  @Test
  public void installMultiPackage_withoutDevice_withoutRollback() {
    new DefaultAdbCommand(Paths.get("adb"))
        .installMultiPackage(
            ImmutableListMultimap.<String, String>builder()
                .putAll("foo", ImmutableList.of("foo1.apk", "foo2.apk"))
                .putAll("bar", ImmutableList.of("bar.apex"))
                .build(),
            false,
            false,
            /* timeout= */ Optional.empty(),
            /* deviceId= */ Optional.empty(),
            new FakeCommandExecutor(
                ImmutableList.of(
                    "adb",
                    "install-multi-package",
                    "foo1.apk" + SPLIT_CHARACTER + "foo2.apk",
                    "bar.apex")));
  }

  static class FakeCommandExecutor implements CommandExecutor {
    private final ImmutableList<String> expectedCommand;

    FakeCommandExecutor(ImmutableList<String> expectedCommand) {
      this.expectedCommand = ImmutableList.copyOf(expectedCommand);
    }

    @Override
    public void execute(ImmutableList<String> command, CommandOptions options) {
      assertThat(command).containsExactlyElementsIn(expectedCommand).inOrder();
    }

    @Override
    public ImmutableList<String> executeAndCapture(
        ImmutableList<String> command, CommandOptions options) {
      assertThat(command).containsExactlyElementsIn(expectedCommand).inOrder();
      return ImmutableList.of();
    }
  }
}
