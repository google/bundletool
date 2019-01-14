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

import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredBuilderPropertyException;
import static com.android.tools.build.bundletool.testing.TestUtils.expectMissingRequiredFlagException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ValidateBundleCommand}. */
@RunWith(JUnit4.class)
public class ValidateBundleCommandTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path bundlePath;

  @Before
  public void setUp() throws IOException {
    bundlePath = tmp.newFile("bundle").toPath();
  }

  @Test
  public void buildingViaFlagsAndBuilderHasSameResult() throws Exception {
    ValidateBundleCommand commandViaBuilder =
        ValidateBundleCommand.builder().setBundlePath(bundlePath).build();

    ValidateBundleCommand commandViaFlags =
        ValidateBundleCommand.fromFlags(new FlagParser().parse("--bundle=" + bundlePath));

    // printOutput property is different, so cannot just test the command object for equality.
    assertThat(commandViaBuilder.getBundlePath().toFile())
        .isEqualTo(commandViaFlags.getBundlePath().toFile());
  }

  @Test
  public void bundleNotSet_throws() throws Exception {
    expectMissingRequiredBuilderPropertyException(
        "bundlePath", () -> ValidateBundleCommand.builder().build());

    expectMissingRequiredFlagException(
        "bundle", () -> ValidateBundleCommand.fromFlags(new FlagParser().parse()));
  }

  @Test
  public void corruptedBundleFile_throws() throws Exception {
    ParsedFlags flags = new FlagParser().parse("--bundle=" + bundlePath);
    ValidateBundleCommand command = ValidateBundleCommand.fromFlags(flags);
    assertThrows(UncheckedIOException.class, () -> command.execute());
  }

  @Test
  public void missingBundleFile_throws() throws Exception {
    ParsedFlags flags = new FlagParser().parse("--bundle=doesnt-exist");
    ValidateBundleCommand command = ValidateBundleCommand.fromFlags(flags);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> command.execute());
    assertThat(exception.getMessage()).matches("File '.*' was not found.");
  }

  @Test
  public void printHelp_doesNotCrash() {
    ValidateBundleCommand.help();
  }
}
