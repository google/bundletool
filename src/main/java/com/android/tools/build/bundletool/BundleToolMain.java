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
package com.android.tools.build.bundletool;

import com.android.tools.build.bundletool.commands.AddTransparencyCommand;
import com.android.tools.build.bundletool.commands.BuildApksCommand;
import com.android.tools.build.bundletool.commands.BuildBundleCommand;
import com.android.tools.build.bundletool.commands.BuildSdkApksCommand;
import com.android.tools.build.bundletool.commands.BuildSdkApksForAppCommand;
import com.android.tools.build.bundletool.commands.BuildSdkAsarCommand;
import com.android.tools.build.bundletool.commands.BuildSdkBundleCommand;
import com.android.tools.build.bundletool.commands.CheckTransparencyCommand;
import com.android.tools.build.bundletool.commands.CommandHelp;
import com.android.tools.build.bundletool.commands.DumpCommand;
import com.android.tools.build.bundletool.commands.DumpSdkBundleCommand;
import com.android.tools.build.bundletool.commands.EvaluateDeviceTargetingConfigCommand;
import com.android.tools.build.bundletool.commands.ExtractApksCommand;
import com.android.tools.build.bundletool.commands.GetDeviceSpecCommand;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.android.tools.build.bundletool.commands.InstallApksCommand;
import com.android.tools.build.bundletool.commands.InstallMultiApksCommand;
import com.android.tools.build.bundletool.commands.PrintDeviceTargetingConfigCommand;
import com.android.tools.build.bundletool.commands.ValidateBundleCommand;
import com.android.tools.build.bundletool.commands.VersionCommand;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DdmlibAdbServer;
import com.android.tools.build.bundletool.flags.FlagParser;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/**
 * Main entry point of the bundle tool.
 *
 * <p>Consider running with -Dsun.zip.disableMemoryMapping when dealing with large bundles.
 */
public final class BundleToolMain {

  public static final String HELP_CMD = "help";

  public static void main(String[] args) {
    main(args, Runtime.getRuntime());
  }

  /** Parses the flags and routes to the appropriate command handler. */
  static void main(String[] args, Runtime runtime) {
    final ParsedFlags flags;
    try {
      flags = new FlagParser().parse(args);
    } catch (FlagParser.FlagParseException e) {
      System.err.println("Error while parsing the flags: " + e.getMessage());
      runtime.exit(1);
      return;
    }
    Optional<String> command = flags.getMainCommand();
    if (!command.isPresent()) {
      System.err.println("Error: You have to specify a command.");
      help();
      runtime.exit(1);
      return;
    }

    try {
      switch (command.get()) {
        case BuildBundleCommand.COMMAND_NAME:
          BuildBundleCommand.fromFlags(flags).execute();
          break;
        case BuildApksCommand.COMMAND_NAME:
          try (AdbServer adbServer = DdmlibAdbServer.getInstance()) {
            BuildApksCommand.fromFlags(flags, adbServer).execute();
          }
          break;
        case BuildSdkBundleCommand.COMMAND_NAME:
          BuildSdkBundleCommand.fromFlags(flags).execute();
          break;
        case BuildSdkApksCommand.COMMAND_NAME:
          BuildSdkApksCommand.fromFlags(flags).execute();
          break;
        case BuildSdkApksForAppCommand.COMMAND_NAME:
          BuildSdkApksForAppCommand.fromFlags(flags).execute();
          break;
        case BuildSdkAsarCommand.COMMAND_NAME:
          BuildSdkAsarCommand.fromFlags(flags).execute();
          break;
        case PrintDeviceTargetingConfigCommand.COMMAND_NAME:
          PrintDeviceTargetingConfigCommand.fromFlags(flags).execute();
          break;
        case EvaluateDeviceTargetingConfigCommand.COMMAND_NAME:
          try (AdbServer adbServer = DdmlibAdbServer.getInstance()) {
            EvaluateDeviceTargetingConfigCommand.fromFlags(flags, adbServer).execute(System.out);
          }
          break;
        case ExtractApksCommand.COMMAND_NAME:
          ExtractApksCommand.fromFlags(flags).execute();
          break;
        case GetDeviceSpecCommand.COMMAND_NAME:
          // We have to destroy ddmlib resources at the end of the command.
          try (AdbServer adbServer = DdmlibAdbServer.getInstance()) {
            GetDeviceSpecCommand.fromFlags(flags, adbServer).execute();
          }
          break;
        case InstallApksCommand.COMMAND_NAME:
          try (AdbServer adbServer = DdmlibAdbServer.getInstance()) {
            InstallApksCommand.fromFlags(flags, adbServer).execute();
          }
          break;
        case InstallMultiApksCommand.COMMAND_NAME:
          try (AdbServer adbServer = DdmlibAdbServer.getInstance()) {
            InstallMultiApksCommand.fromFlags(flags, adbServer).execute();
          }
          break;
        case ValidateBundleCommand.COMMAND_NAME:
          ValidateBundleCommand.fromFlags(flags).execute();
          break;
        case DumpCommand.COMMAND_NAME:
          DumpCommand.fromFlags(flags).execute();
          break;
        case DumpSdkBundleCommand.COMMAND_NAME:
          DumpSdkBundleCommand.fromFlags(flags).execute();
          break;
        case GetSizeCommand.COMMAND_NAME:
          GetSizeCommand.fromFlags(flags).execute();
          break;
        case VersionCommand.COMMAND_NAME:
          VersionCommand.fromFlags(flags, System.out).execute();
          break;
        case AddTransparencyCommand.COMMAND_NAME:
          AddTransparencyCommand.fromFlags(flags).execute();
          break;
        case CheckTransparencyCommand.COMMAND_NAME:
          try (AdbServer adbServer = DdmlibAdbServer.getInstance()) {
            CheckTransparencyCommand.fromFlags(flags, adbServer).execute();
          }
          break;
        case HELP_CMD:
          if (flags.getSubCommand().isPresent()) {
            help(flags.getSubCommand().get(), runtime);
          } else {
            help();
          }
          break;
        default:
          System.err.printf("Error: Unrecognized command '%s'.%n%n%n", command.get());
          help();
          runtime.exit(1);
          return;
      }
    } catch (Exception e) {
      System.err.println(
          "[BT:" + BundleToolVersion.getCurrentVersion() + "] Error: " + e.getMessage());
      e.printStackTrace();
      runtime.exit(1);
      return;
    }

    // Takes care of shutting down non-daemon threads in internal thread pools.
    runtime.exit(0);
  }

  private static final ImmutableList<CommandHelp> COMMAND_HELPS =
      ImmutableList.of(
          BuildBundleCommand.help(),
          BuildApksCommand.help(),
          BuildSdkBundleCommand.help(),
          BuildSdkApksCommand.help(),
          BuildSdkApksForAppCommand.help(),
          BuildSdkAsarCommand.help(),
          PrintDeviceTargetingConfigCommand.help(),
          EvaluateDeviceTargetingConfigCommand.help(),
          ExtractApksCommand.help(),
          GetDeviceSpecCommand.help(),
          InstallApksCommand.help(),
          InstallMultiApksCommand.help(),
          ValidateBundleCommand.help(),
          DumpCommand.help(),
          DumpSdkBundleCommand.help(),
          GetSizeCommand.help(),
          VersionCommand.help(),
          AddTransparencyCommand.help(),
          CheckTransparencyCommand.help());


  /** Displays a general help. */
  public static void help() {
    System.out.println("Synopsis: bundletool <command> ...");
    System.out.println();
    System.out.println("Use 'bundletool help <command>' to learn more about the given command.");
    System.out.println();
    COMMAND_HELPS.forEach(commandHelp -> commandHelp.printSummary(System.out));
  }

  /** Displays help about a given command. */
  public static void help(String commandName, Runtime runtime) {
    for (CommandHelp commandHelp : COMMAND_HELPS) {
      if (commandHelp.getCommandName().equals(commandName)) {
        commandHelp.printDetails(System.out);
        return;
      }
    }
    System.err.printf("Error: Unrecognized command '%s'.%n%n%n", commandName);
    help();
    runtime.exit(1);
  }

  private BundleToolMain() {}
}
