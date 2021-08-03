/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoFuzzerTest {

  private static final ImmutableList<String> LETTERS_A_TO_Z =
      IntStream.range(0, 'z' - 'a' + 1)
          .mapToObj(i -> String.valueOf((char) ('a' + i)))
          .collect(toImmutableList());

  @Test
  public void shuffleRepeatedFields() {
    LanguageTargeting original =
        LanguageTargeting.newBuilder()
            .addAllValue(LETTERS_A_TO_Z)
            .addAllAlternatives(LETTERS_A_TO_Z)
            .build();

    LanguageTargeting shuffled = ProtoFuzzer.shuffleRepeatedFields(original);

    // Values preserved.
    assertThat(original.getValueList()).containsExactlyElementsIn(shuffled.getValueList());
    assertThat(original.getAlternativesList())
        .containsExactlyElementsIn(shuffled.getAlternativesList());
    // Order changed
    assertThat(LETTERS_A_TO_Z).isNotEqualTo(shuffled.getValueList());
    assertThat(LETTERS_A_TO_Z).isNotEqualTo(shuffled.getAlternativesList());
  }

  @Test
  public void randomProtoMessage_messageFieldPopulated() {
    ApkTargeting randomProto = ProtoFuzzer.randomProtoMessage(ApkTargeting.class);

    assertThat(randomProto.getAbiTargeting()).isNotEqualToDefaultInstance();
  }

  @Test
  public void randomProtoMessage_repeatedNonMessageFieldPopulated() {
    LanguageTargeting randomProto = ProtoFuzzer.randomProtoMessage(LanguageTargeting.class);

    // At least some values populated.
    assertThat(randomProto).isNotEqualTo(LanguageTargeting.getDefaultInstance());
    // Not all random values are the same.
    assertThat(ImmutableSet.copyOf(randomProto.getValueList()).size()).isGreaterThan(1);
    assertThat(ImmutableSet.copyOf(randomProto.getAlternativesList()).size()).isGreaterThan(1);
  }

  @Test
  public void randomProtoMessage_repeatedMessageFieldPopulated() {
    AbiTargeting randomProto = ProtoFuzzer.randomProtoMessage(AbiTargeting.class);

    // At least some values populated.
    assertThat(randomProto).isNotEqualTo(AbiTargeting.getDefaultInstance());
    // Not all random values are the same.
    assertThat(ImmutableSet.copyOf(randomProto.getValueList()).size()).isGreaterThan(1);
    assertThat(ImmutableSet.copyOf(randomProto.getAlternativesList()).size()).isGreaterThan(1);
  }
}
