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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.flags.FlagParser.FlagParseException;
import com.android.tools.build.bundletool.model.Password;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Flag}. */
@RunWith(JUnit4.class)
public class FlagTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  public enum TestEnum {
    ONE,
    TWO
  }

  @Test
  public void collectorFlag_empty() throws Exception {
    Flag<ImmutableList<String>> flag = Flag.stringCollector("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=");
    assertThat(flag.getRequiredValue(parsedFlags)).isEmpty();
  }

  @Test
  public void collectorFlag_oneItem() throws Exception {
    Flag<ImmutableList<String>> flag = Flag.stringCollector("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=v1");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("v1");
  }

  @Test
  public void collectorFlag_manyItems() throws Exception {
    Flag<ImmutableList<String>> flag = Flag.stringCollector("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=v1", "--testFlag=v2");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("v1", "v2");
  }

  @Test
  public void enumFlag_unrecognizedValue_throws() {
    Flag<TestEnum> flag = Flag.enumFlag("testFlag", TestEnum.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=THREE");
    Throwable exception = assertThrows(FlagParseException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception)
        .hasMessageThat()
        .contains("Not a valid enum value 'THREE' of flag --testFlag. Expected one of: ONE, TWO");
  }

  @Test
  public void enumFlag_handlesMixedCase() {
    Flag<TestEnum> flag = Flag.enumFlag("testFlag", TestEnum.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=oNe");
    assertThat(flag.getRequiredValue(parsedFlags)).isEqualTo(TestEnum.ONE);
  }

  @Test
  public void keyValueFlag() throws Exception {
    Flag<Map.Entry<String, String>> flag = Flag.keyValue("testFlag", String.class, String.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=key:value");
    Entry<String, String> flagValue = flag.getRequiredValue(parsedFlags);
    assertThat(flagValue.getKey()).isEqualTo("key");
    assertThat(flagValue.getValue()).isEqualTo("value");
  }

  @Test
  public void keyValueFlag_missingKeyValueDelimiter_throws() throws Exception {
    Flag<Map.Entry<String, String>> flag = Flag.keyValue("testFlag", String.class, String.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=key=value");
    FlagParseException exception =
        assertThrows(FlagParseException.class, () -> flag.getRequiredValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("must contain ':'");
  }

  @Test
  public void keyValueFlag_valueContainsDelimiter_parsedCorrectly() throws Exception {
    Flag<Map.Entry<String, String>> flag = Flag.keyValue("testFlag", String.class, String.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=key:value:with:colons");
    Entry<String, String> flagValue = flag.getRequiredValue(parsedFlags);
    assertThat(flagValue.getKey()).isEqualTo("key");
    assertThat(flagValue.getValue()).isEqualTo("value:with:colons");
  }

  @Test
  public void listFlag_empty() throws Exception {
    Flag<ImmutableList<String>> flag = Flag.stringList("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=");
    assertThat(flag.getRequiredValue(parsedFlags)).isEmpty();
  }

  @Test
  public void listFlag_oneItem() throws Exception {
    Flag<ImmutableList<String>> flag = Flag.stringList("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=one");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("one");
  }

  @Test
  public void listFlag_manyItems() throws Exception {
    Flag<ImmutableList<String>> flag = Flag.stringList("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=one,two,three");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("one", "two", "three").inOrder();
  }

  @Test
  public void mapCollectorFlag_empty() throws Exception {
    Flag<ImmutableMap<String, String>> flag =
        Flag.mapCollector("testFlag", String.class, String.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=");
    assertThat(flag.getRequiredValue(parsedFlags)).isEmpty();
  }

  @Test
  public void mapCollectorFlag_oneItem() throws Exception {
    Flag<ImmutableMap<String, String>> flag =
        Flag.mapCollector("testFlag", String.class, String.class);
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=key1:value1");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("key1", "value1");
  }

  @Test
  public void mapCollectorFlag_manyItems() throws Exception {
    Flag<ImmutableMap<String, String>> flag =
        Flag.mapCollector("testFlag", String.class, String.class);
    ParsedFlags parsedFlags =
        new FlagParser().parse("--testFlag=key1:value1", "--testFlag=key2:value2");
    assertThat(flag.getRequiredValue(parsedFlags))
        .containsExactly("key1", "value1", "key2", "value2");
  }

  @Test
  public void mapCollectorFlag_duplicate_throws() throws Exception {
    Flag<ImmutableMap<String, String>> flag =
        Flag.mapCollector("testFlag", String.class, String.class);
    ParsedFlags parsedFlags =
        new FlagParser().parse("--testFlag=key:value1", "--testFlag=key:value2");
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> flag.getRequiredValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("Multiple entries with same key");
  }

  @Test
  public void passwordFlag_noPrefix_throws() {
    Flag<Password> flag = Flag.password("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=hello");
    Throwable exception =
        assertThrows(IllegalArgumentException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception)
        .hasMessageThat()
        .contains("Passwords must be prefixed with \"pass:\" or \"file:\"");
  }

  @Test
  public void passwordFlag_inClear() {
    Flag<Password> flag = Flag.password("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=pass:hello");
    String password = new String(flag.getRequiredValue(parsedFlags).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void passwordFlag_inFile() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, ImmutableList.of("hello\n"));

    Flag<Password> flag = Flag.password("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=file:" + passwordFile);
    String password = new String(flag.getRequiredValue(parsedFlags).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void passwordFlag_inFile_withLineFeedAtTheEnd() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, new String("hello\n").getBytes(UTF_8));

    Flag<Password> flag = Flag.password("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=file:" + passwordFile);
    String password = new String(flag.getRequiredValue(parsedFlags).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void passwordFlag_inFile_withCarriageReturnAtTheEnd() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, new String("hello\r").getBytes(UTF_8));

    Flag<Password> flag = Flag.password("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=file:" + passwordFile);
    String password = new String(flag.getRequiredValue(parsedFlags).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void passwordFlag_inFile_withCarriageReturnAndLineFeedAtTheEnd() throws Exception {
    Path passwordFile = tempFolder.getRoot().toPath().resolve("myPassword.txt");
    Files.write(passwordFile, new String("hello\r\n").getBytes(UTF_8));

    Flag<Password> flag = Flag.password("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=file:" + passwordFile);
    String password = new String(flag.getRequiredValue(parsedFlags).getValue().getPassword());
    assertThat(password).isEqualTo("hello");
  }

  @Test
  public void positiveIntegerFlag_valid() throws Exception {
    Flag<Integer> flag = Flag.positiveInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=42");
    assertThat(flag.getRequiredValue(parsedFlags)).isEqualTo(42);
  }

  @Test
  public void positiveIntegerFlag_notANumber_throws() throws Exception {
    Flag<Integer> flag = Flag.positiveInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=blah");
    FlagParseException exception =
        assertThrows(FlagParseException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("Error while parsing");
  }

  @Test
  public void positiveIntegerFlag_zero_throws() throws Exception {
    Flag<Integer> flag = Flag.positiveInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=0");
    FlagParseException exception =
        assertThrows(FlagParseException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("has illegal value");
  }

  @Test
  public void positiveIntegerFlag_negative_throws() throws Exception {
    Flag<Integer> flag = Flag.positiveInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=-1");
    FlagParseException exception =
        assertThrows(FlagParseException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("has illegal value");
  }

  @Test
  public void nonNegativeIntegerFlag_valid() throws Exception {
    Flag<Integer> flag = Flag.nonNegativeInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=42");
    assertThat(flag.getRequiredValue(parsedFlags)).isEqualTo(42);
  }

  @Test
  public void nonNegativeIntegerFlag_notANumber_throws() throws Exception {
    Flag<Integer> flag = Flag.nonNegativeInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=blah");
    FlagParseException exception =
        assertThrows(FlagParseException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("Error while parsing");
  }

  @Test
  public void nonNegativeIntegerFlag_zero_valid() throws Exception {
    Flag<Integer> flag = Flag.nonNegativeInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=0");
    assertThat(flag.getRequiredValue(parsedFlags)).isEqualTo(0);
  }

  @Test
  public void nonNegativeIntegerFlag_negative_throws() throws Exception {
    Flag<Integer> flag = Flag.nonNegativeInteger("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=-1");
    FlagParseException exception =
        assertThrows(FlagParseException.class, () -> flag.getValue(parsedFlags));
    assertThat(exception).hasMessageThat().contains("has illegal value");
  }

  @Test
  public void setFlag_empty() throws Exception {
    Flag<ImmutableSet<String>> flag = Flag.stringSet("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=");
    assertThat(flag.getRequiredValue(parsedFlags)).isEmpty();
  }

  @Test
  public void setFlag_oneItem() throws Exception {
    Flag<ImmutableSet<String>> flag = Flag.stringSet("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=one");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("one");
  }

  @Test
  public void setFlag_manyItems() throws Exception {
    Flag<ImmutableSet<String>> flag = Flag.stringSet("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=one,two,three");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("one", "two", "three");
  }

  @Test
  public void setFlag_duplicates_filteredOut() throws Exception {
    Flag<ImmutableSet<String>> flag = Flag.stringSet("testFlag");
    ParsedFlags parsedFlags = new FlagParser().parse("--testFlag=one,one,one");
    assertThat(flag.getRequiredValue(parsedFlags)).containsExactly("one");
  }
}
