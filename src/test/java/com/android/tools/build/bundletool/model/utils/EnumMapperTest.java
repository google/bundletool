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
package com.android.tools.build.bundletool.model.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EnumMapperTest {

  @Test
  public void mapByName_identicalValues() {
    Map<GreekLetters, MathsSymbols> enumMap =
        EnumMapper.mapByName(GreekLetters.class, MathsSymbols.class);

    assertThat(enumMap)
        .containsExactlyEntriesIn(
            ImmutableMap.<GreekLetters, MathsSymbols>builder()
                .put(GreekLetters.ALPHA, MathsSymbols.ALPHA)
                .put(GreekLetters.BETA, MathsSymbols.BETA)
                .put(GreekLetters.GAMMA, MathsSymbols.GAMMA)
                .build());
  }

  @Test
  public void mapByName_differentValues_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> EnumMapper.mapByName(GreekLetters.class, MoreGreekLetters.class));
  }

  @Test
  public void mapByName_differentValuesIgnored() {
    Map<MoreGreekLetters, GreekLetters> enumMap =
        EnumMapper.mapByName(
            MoreGreekLetters.class, GreekLetters.class, ImmutableSet.of(MoreGreekLetters.DELTA));

    assertThat(enumMap)
        .containsExactlyEntriesIn(
            ImmutableMap.<MoreGreekLetters, GreekLetters>builder()
                .put(MoreGreekLetters.ALPHA, GreekLetters.ALPHA)
                .put(MoreGreekLetters.BETA, GreekLetters.BETA)
                .put(MoreGreekLetters.GAMMA, GreekLetters.GAMMA)
                .build());
  }

  private enum GreekLetters {
    ALPHA,
    BETA,
    GAMMA
  }

  private enum MathsSymbols {
    ALPHA,
    BETA,
    GAMMA
  }

  private enum MoreGreekLetters {
    ALPHA,
    BETA,
    GAMMA,
    DELTA
  }
}
