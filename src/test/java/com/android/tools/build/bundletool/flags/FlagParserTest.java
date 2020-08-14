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

package com.android.tools.build.bundletool.flags;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.flags.ParsedFlags.UnknownFlagsException;
import com.android.tools.build.bundletool.model.utils.OsPlatform;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the FlagParser class. */
@RunWith(JUnit4.class)
public class FlagParserTest {

  @Test
  public void emptyCommandLine() throws Exception {
    ParsedFlags fp = new FlagParser().parse(new String[0]);
    assertThat(fp.getCommands()).isEmpty();
  }

  @Test
  public void singleCommand() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command1");
    assertThat(fp.getCommands()).containsExactly("command1");
  }

  @Test
  public void multipleCommands() throws Exception {
    ParsedFlags fp = new FlagParser().parse("help", "command1");
    assertThat(fp.getCommands()).containsExactly("help", "command1").inOrder();
  }

  @Test
  public void parsesCommandLineFlags() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=value1", "--flag2=value2");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.string("flag1").getRequiredValue(fp)).isEqualTo("value1");
    assertThat(Flag.string("flag2").getRequiredValue(fp)).isEqualTo("value2");
  }

  @Test
  public void requiredFlagNotPresent_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=value1");
    RequiredFlagNotSetException e =
        assertThrows(
            RequiredFlagNotSetException.class, () -> Flag.string("flag2").getRequiredValue(fp));
    assertThat(e).hasMessageThat().contains("Missing the required --flag2 flag");
  }

  @Test
  public void unknownFlag_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flagUnknown=value");
    UnknownFlagsException e =
        assertThrows(UnknownFlagsException.class, () -> fp.checkNoUnknownFlags());
    assertThat(e).hasMessageThat().contains("Unrecognized flags: --flagUnknown");
  }

  @Test
  public void duplicateSimpleFlag_throws() throws Exception {
    FlagParseException e =
        assertThrows(
            FlagParseException.class,
            () -> new FlagParser().parse("command", "--flag=v1", "--flag=v2").getFlagValue("flag"));
    assertThat(e).hasMessageThat().contains("flag");
  }

  @Test
  public void incorrectCommandLineFlags_throws() throws Exception {
    FlagParseException e =
        assertThrows(
            FlagParseException.class,
            () -> new FlagParser().parse("command", "--flag1=value1", "-flag2=value2"));
    assertThat(e).hasMessageThat().contains("-flag2=value2");
  }

  @Test
  public void collectorFlag_notSet() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.stringCollector("flag1").getValue(fp)).isEqualTo(Optional.empty());
  }

  @Test
  public void collectorFlag_setWithDefaultEquals() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.string("flag1").getRequiredValue(fp)).isEmpty();
  }

  @Test
  public void collectorFlag_setWithDefault() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1", "--flag2");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.stringCollector("flag1").getRequiredValue(fp)).isEmpty();
    assertThat(Flag.stringCollector("flag2").getRequiredValue(fp)).isEmpty();
  }

  @Test
  public void collectorFlag_singleValue() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=v1");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.stringCollector("flag1").getRequiredValue(fp)).containsExactly("v1");
  }

  @Test
  public void collectorFlag_multipleValues() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=v1", "--flag1=v2");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.stringCollector("flag1").getRequiredValue(fp))
        .containsExactly("v1", "v2")
        .inOrder();
  }

  @Test
  public void listFlag_notSet() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.stringList("flag1").getValue(fp)).isEqualTo(Optional.empty());
  }

  @Test
  public void listFlag_setWithDefault() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.stringList("flag1").getRequiredValue(fp)).isEmpty();
  }

  @Test
  public void listFlag_singleValue() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=val1");
    assertThat(Flag.stringList("flag1").getRequiredValue(fp)).containsExactly("val1");
  }

  @Test
  public void listFlag_multipleValues() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=v1,v2,value3");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.string("flag1").getValue(fp)).isEqualTo(Optional.of("v1,v2,value3"));
    assertThat(Flag.stringList("flag1").getRequiredValue(fp))
        .containsExactly("v1", "v2", "value3")
        .inOrder();
  }

  @Test
  public void listFlag_setMultipleTimes_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1=v1", "--flag1=v2");
    FlagParseException e =
        assertThrows(FlagParseException.class, () -> Flag.stringList("flag1").getRequiredValue(fp));
    assertThat(e).hasMessageThat().contains("Flag --flag1 has been set more than once");
  }

  @Test
  public void pathFlag() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--pathFlag=/my/path");
    assertThat((Object) Flag.path("pathFlag").getRequiredValue(fp))
        .isEqualTo(Paths.get("/my/path"));
  }

  @Test
    public void pathFlag_tildeInPathStart_linux() throws Exception {
    String currentSystem = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Linux");
      assertThat(OsPlatform.getCurrentPlatform()).isEqualTo(OsPlatform.LINUX);
      ParsedFlags fp = new FlagParser().parse("command", "--pathFlag=~/my/path");
      assertThat((Object) Flag.path("pathFlag").getRequiredValue(fp))
          .isEqualTo(Paths.get(System.getProperty("user.home"), "/my/path"));
    } finally {
      System.setProperty("os.name", currentSystem);
    }
  }

  @Test
    public void pathFlag_tildeInPathStart_macos() throws Exception {
    String currentSystem = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Mac OS X");
      assertThat(OsPlatform.getCurrentPlatform()).isEqualTo(OsPlatform.MACOS);
      ParsedFlags fp = new FlagParser().parse("command", "--pathFlag=~/my/path");
      assertThat((Object) Flag.path("pathFlag").getRequiredValue(fp))
          .isEqualTo(Paths.get(System.getProperty("user.home"), "/my/path"));
    } finally {
      System.setProperty("os.name", currentSystem);
    }
  }

  @Test
  public void pathFlag_tildeInPathMiddle_nonWindows() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--pathFlag=/my/~/path");
    assertThat((Object) Flag.path("pathFlag").getRequiredValue(fp))
        .isEqualTo(Paths.get("/my/~/path"));
  }

  @Test
    public void pathFlag_tildeInPath_windows() throws Exception {
    String currentSystem = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Windows");
      assertThat(OsPlatform.getCurrentPlatform()).isEqualTo(OsPlatform.WINDOWS);
      ParsedFlags fp = new FlagParser().parse("command", "--pathFlag=~\\my\\path");
      assertThat((Object) Flag.path("pathFlag").getRequiredValue(fp))
          .isEqualTo(Paths.get("~\\my\\path"));
    } finally {
      System.setProperty("os.name", currentSystem);
    }
  }

  @Test
  public void booleanFlag_empty_returnsTrue() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--boolFlag");
    assertThat(Flag.booleanFlag("boolFlag").getRequiredValue(fp)).isTrue();
  }

  @Test
  public void booleanFlag_true() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--boolFlag=trUe");
    assertThat(Flag.booleanFlag("boolFlag").getRequiredValue(fp)).isTrue();
  }

  @Test
  public void booleanFlag_false() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--boolFlag=False");
    assertThat(Flag.booleanFlag("boolFlag").getRequiredValue(fp)).isFalse();
  }

  @Test
  public void booleanFlag_nonTrueFalse_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--boolFlag=no");
    FlagParseException exception =
        assertThrows(
            FlagParseException.class, () -> Flag.booleanFlag("boolFlag").getRequiredValue(fp));
    assertThat(exception).hasMessageThat().contains("Error while parsing the boolean flag");
  }

  @Test
  public void passwordFlag_inClearInCommandLine_returnsValue() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--pwdFlag=pass:hello");
    assertThat(Flag.password("pwdFlag").getRequiredValue(fp).getValue().getPassword())
        .isEqualTo("hello".toCharArray());
  }

  @Test
  public void passwordFlag_inClearInCommandLineWithoutPassPrefix_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--pwdFlag=hello");

    Throwable exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> Flag.password("pwdFlag").getRequiredValue(fp).getValue());
    assertThat(exception).hasMessageThat().contains("Passwords must be prefixed with \"pass:\"");
  }

  @Test
  public void parsesCommandLineFlagsWithSpace() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1", "value1", "--flag2", "value2");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.string("flag1").getRequiredValue(fp)).isEqualTo("value1");
    assertThat(Flag.string("flag2").getRequiredValue(fp)).isEqualTo("value2");
  }

  @Test
  public void parsesCommandLineMixedSeparators() throws Exception {
    ParsedFlags fp = new FlagParser()
        .parse("command", "--flag1", "value1", "--flag2=value2", "--flag3", "value3");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.string("flag1").getRequiredValue(fp)).isEqualTo("value1");
    assertThat(Flag.string("flag2").getRequiredValue(fp)).isEqualTo("value2");
    assertThat(Flag.string("flag3").getRequiredValue(fp)).isEqualTo("value3");
  }

  @Test
  public void listFlag_setMultipleTimesWithSpace_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1", "v1", "--flag1", "v2");
    FlagParseException e =
        assertThrows(FlagParseException.class, () -> Flag.stringList("flag1").getRequiredValue(fp));
    assertThat(e).hasMessageThat().contains("Flag --flag1 has been set more than once");
  }

  @Test
  public void listFlag_setMultipleTimesMixedSeparators_throws() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1", "v1", "--flag1=v2");
    FlagParseException e =
        assertThrows(FlagParseException.class, () -> Flag.stringList("flag1").getRequiredValue(fp));
    assertThat(e).hasMessageThat().contains("Flag --flag1 has been set more than once");
  }

  @Test
  public void listFlag_multipleValuesWithSpace() throws Exception {
    ParsedFlags fp = new FlagParser().parse("command", "--flag1", "v1,v2,v3");
    assertThat(fp.getCommands()).containsExactly("command");
    assertThat(Flag.string("flag1").getValue(fp)).isEqualTo(Optional.of("v1,v2,v3"));
    assertThat(Flag.stringList("flag1").getRequiredValue(fp))
        .containsExactly("v1", "v2", "v3")
        .inOrder();
  }

  @Test
  public void listFlag_multipleValuesWithSpaceInValue_throws() throws Exception {
    FlagParseException e = assertThrows(FlagParseException.class,
        () -> new FlagParser().parse("command", "--flag1", "v1", "v2", "v3"));
    assertThat(e).hasMessageThat().contains("Syntax error: flags should start with -- (v2)");
  }
}
