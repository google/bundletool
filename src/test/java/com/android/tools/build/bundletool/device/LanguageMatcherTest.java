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

package com.android.tools.build.bundletool.device;

import static com.android.tools.build.bundletool.testing.DeviceFactory.lDeviceWithLocales;
import static com.android.tools.build.bundletool.testing.TargetingUtils.alternativeLanguageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Targeting.LanguageTargeting;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LanguageMatcherTest {

  @Test
  public void simpleSingleMatch() {
    LanguageMatcher languageMatcher = new LanguageMatcher(lDeviceWithLocales("en-US"));
    assertThat(languageMatcher.matchesTargeting(LanguageTargeting.getDefaultInstance())).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("en"))).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("de"))).isFalse();
  }

  @Test
  public void simpleSingleMatch_differentRegion() {
    LanguageMatcher languageMatcher = new LanguageMatcher(lDeviceWithLocales("en-GB"));
    assertThat(languageMatcher.matchesTargeting(LanguageTargeting.getDefaultInstance())).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("en"))).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("de"))).isFalse();
  }

  @Test
  public void multipleLanguagesMatched() {
    LanguageMatcher languageMatcher = new LanguageMatcher(lDeviceWithLocales("en-US", "de-DE"));
    assertThat(languageMatcher.matchesTargeting(LanguageTargeting.getDefaultInstance())).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("en"))).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("de"))).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("jp"))).isFalse();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("zh"))).isFalse();
  }

  @Test
  public void multipleLanguagesMatched_alternativesIgnored() {
    LanguageMatcher languageMatcher =
        new LanguageMatcher(lDeviceWithLocales("en-US", "en-GB", "de-DE"));
    assertThat(languageMatcher.matchesTargeting(LanguageTargeting.getDefaultInstance())).isTrue();
    assertThat(
            languageMatcher.matchesTargeting(
                languageTargeting("en", ImmutableSet.of("de", "jp", "zh"))))
        .isTrue();
    assertThat(
            languageMatcher.matchesTargeting(
                languageTargeting("de", ImmutableSet.of("en", "jp", "zh"))))
        .isTrue();
    assertThat(
            languageMatcher.matchesTargeting(
                languageTargeting("jp", ImmutableSet.of("de", "en", "zh"))))
        .isFalse();
    assertThat(
            languageMatcher.matchesTargeting(
                languageTargeting("zh", ImmutableSet.of("de", "en", "jp"))))
        .isFalse();
  }

  @Test
  public void fusedLanguageSplitsMatched() {
    LanguageMatcher languageMatcher =
        new LanguageMatcher(lDeviceWithLocales("en-US", "en-GB", "de-DE"));
    assertThat(languageMatcher.matchesTargeting(LanguageTargeting.getDefaultInstance())).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("en", "fr"))).isTrue();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("jp", "zh"))).isFalse();
    assertThat(languageMatcher.matchesTargeting(languageTargeting("de"))).isTrue();
  }

  @Test
  public void languageFallback() {
    LanguageMatcher languageMatcher =
        new LanguageMatcher(lDeviceWithLocales("en-US", "en-GB", "de-DE"));
    // A fallback language split should be selected iff the alternatives don't fully cover the
    // device languages.
    assertThat(languageMatcher.matchesTargeting(alternativeLanguageTargeting("en", "de", "zh")))
        .isFalse();
    assertThat(languageMatcher.matchesTargeting(alternativeLanguageTargeting("en", "de")))
        .isFalse();
    assertThat(languageMatcher.matchesTargeting(alternativeLanguageTargeting("en", "zh"))).isTrue();
    assertThat(languageMatcher.matchesTargeting(alternativeLanguageTargeting("jp", "zh"))).isTrue();
  }
}
