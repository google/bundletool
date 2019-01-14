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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.base.Splitter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VersionCommandTest {

  @Test
  public void executePrintsVersion() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    VersionCommand.fromFlags(new FlagParser().parse(), new PrintStream(out)).execute();

    assertThat(asLines(out.toString()))
        .containsExactly(BundleToolVersion.getCurrentVersion().toString(), "")
        .inOrder();
  }

  @Test
  public void printHelp_doesNotCrash() {
    VersionCommand.help();
  }

  /**
   * Split a string into lines in a platform-agnostic way.
   *
   * <p>Note: There will be a trailing empty string if the input string ends with a line separator.
   * For example: asLines("foo\n") returns ["foo", ""]
   */
  private static List<String> asLines(String multiLineString) {
    return Splitter.onPattern("\\R").splitToList(multiLineString);
  }
}
