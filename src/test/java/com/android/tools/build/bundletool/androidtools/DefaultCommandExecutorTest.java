/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.build.bundletool.androidtools.CommandExecutor.CommandOptions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DefaultCommandExecutorTest {

  private static File createTempFile(String fileName, String content) throws IOException {
    File file = File.createTempFile(fileName, null);
    try (PrintWriter writer = new PrintWriter(file, UTF_8)) {
      writer.println(content);
    }
    return file;
  }

  @Test
  public void executeAndCapture_capturesOutput() {
    DefaultCommandExecutor commandExecutor = new DefaultCommandExecutor();
    ImmutableList<String> command = ImmutableList.of("echo", "Hello World");
    CommandOptions options = CommandOptions.builder().setTimeout(Duration.ofSeconds(1)).build();
    ImmutableList<String> output = commandExecutor.executeAndCapture(command, options);
    assertThat(output).containsExactly("Hello World");
  }

  @Test
  public void executeAndCapture_capturesLongOutput() throws IOException {
    String longString = new String(new char[1024 * 1024]).replace('\0', 'a');
    File f = createTempFile("long_output.txt", longString);
    DefaultCommandExecutor commandExecutor = new DefaultCommandExecutor();
    ImmutableList<String> command = ImmutableList.of("cat", f.getAbsolutePath());
    CommandOptions options = CommandOptions.builder().setTimeout(Duration.ofSeconds(1)).build();
    ImmutableList<String> output = commandExecutor.executeAndCapture(command, options);
    assertThat(output).containsExactly(longString);
  }
}
