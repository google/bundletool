/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import java.io.PrintStream;

/** Command to print the version of BundleTool. */
public final class VersionCommand {

  public static final String COMMAND_NAME = "version";

  private final PrintStream out;

  private VersionCommand(PrintStream out) {
    this.out = out;
  }

  public static VersionCommand fromFlags(ParsedFlags flags, PrintStream out) {
    flags.checkNoUnknownFlags();

    return new VersionCommand(out);
  }

  public void execute() {
    out.println(BundleToolVersion.getCurrentVersion());
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription("Prints the version of BundleTool.")
                .build())
        .build();
  }
}
