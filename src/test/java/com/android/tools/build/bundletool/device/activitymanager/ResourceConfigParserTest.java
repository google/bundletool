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

package com.android.tools.build.bundletool.device.activitymanager;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.device.activitymanager.ResourceConfigParser.ResourceConfigHandler;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceConfigParserTest {

  @Test
  public void moto4_TwoLanguages_NoSim() {
    String resourcesString =
        "en-rUS,es-rUS-ldltr-sw360dp-w360dp-h568dp-normal-notlong-notround-port-notnight-xxhdpi-finger-keysexposed-nokeys-navhidden-nonav-v24";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en-US", "es-US");
    assertThat(actualValues.mccCode()).isEmpty();
    assertThat(actualValues.mncCode()).isEmpty();
  }

  @Test
  public void pixel_OneLanguage() {
    String resourcesString =
        "en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v28";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en-US");
    assertThat(actualValues.mccCode()).isEmpty();
    assertThat(actualValues.mncCode()).isEmpty();
  }

  @Test
  public void pixel2_ThreeLanguages() {
    String resourcesString =
        "mcc234-mnc15-en-rGB,fil-rPH,pl-rPL-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en-GB", "fil-PH", "pl-PL");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_ThreeLanguages_mixedLocaleStrings() {
    String resourcesString =
        "mcc234-mnc15-en-rGB,b+fil+PH,pl-rPL-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en-GB", "fil-PH", "pl-PL");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_ThreeLanguages_mixedLocaleStrings2() {
    String resourcesString =
        "mcc234-mnc15-b+en+GB,fil-rPH,b+pl+PL-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en-GB", "fil-PH", "pl-PL");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_ThreeLanguages_bcpLocaleStrings() {
    String resourcesString =
        "mcc234-mnc15-b+en+GB,b+in+ID,b+pl+PL-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en-GB", "in-ID", "pl-PL");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_LanguagesNoRegion() {
    String resourcesString =
        "mcc234-mnc15-en,fr,de-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en", "fr", "de");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_LanguagesNoRegion_mixedWithBcp47() {
    String resourcesString =
        "mcc234-mnc15-en,b+fr,b+de-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).containsExactly("en", "fr", "de");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_invalidRegionFormatSkipped() {
    String resourcesString =
        "mcc234-mnc15-en-rINVALIDREGION,fr-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    // We expect that we can detect that rINVALIDREGION belongs to en (because we have another
    // comma)
    // And therefore, as a policy fail to detect any other locales (one error, fails all).

    assertThat(actualValues.locales()).isEmpty();
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_invalidRegionFormatLenient() {
    // Note that currently we are not parsing past locale fragment, so the following case would
    // be detected as invalid if the following string fragment parsers were implemented.

    String resourcesString =
        "mcc234-mnc15-en-rINVALIDREGION-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-widecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).contains("en");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  @Test
  public void pixel2_localeFragmentLast() {
    String resourcesString = "mcc234-mnc15-en";
    ResourceConfigValues actualValues =
        ResourceConfigParser.parseDeviceConfig(resourcesString, new TestHandler());

    assertThat(actualValues.locales()).contains("en");
    assertThat(actualValues.mccCode()).hasValue(234);
    assertThat(actualValues.mncCode()).hasValue(15);
  }

  // Various BCP-47 specific compliance tests. Note, that we only support up to 4 segments in the
  // BCP-47 string.
  // See: http://shcneegans.de/iv

  @Test
  public void bcp47_languageOnly() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+de");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("de");
  }

  @Test
  public void bcp47_languageAndRegion() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+es+419");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("es-419");
  }

  @Test
  public void bcp47_languageAndVariant() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+sl+nedis");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("sl");
  }

  @Test
  public void bcp47_languageRegionVariant() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+de+DE+1901");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("de-DE");
  }

  @Test
  public void bcp47_languageRegionVariant2() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+sl+IT+nedis");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("sl-IT");
  }

  @Test
  public void bcp47_languageScriptRegion() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+mn+Cyrl+MN");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("mn-MN");
  }

  @Test
  public void bcp47_languageScriptRegionVariant() {
    TestHandler testHandler = new TestHandler();
    ConfigStringIterator iterator = new ConfigStringIterator("b+hy+Latn+IT+arevela");
    iterator.next();
    new LocaleParser<ResourceConfigValues>().parse(iterator, testHandler);
    ResourceConfigValues actualValues = testHandler.getOutput();

    assertThat(actualValues.locales()).containsExactly("hy-IT");
  }

  static class TestHandler implements ResourceConfigHandler<ResourceConfigValues> {
    private final ResourceConfigValues.Builder valuesBuilder = ResourceConfigValues.builder();

    @Override
    public void onLocale(String locale) {
      valuesBuilder.addLocale(locale);
    }

    @Override
    public void onMccCode(int mccCode) {
      valuesBuilder.setMccCode(mccCode);
    }

    @Override
    public void onMncCode(int mncCode) {
      valuesBuilder.setMncCode(mncCode);
    }

    @Override
    public ResourceConfigValues getOutput() {
      return valuesBuilder.build();
    }
  }

  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  abstract static class ResourceConfigValues {

    public abstract Optional<Integer> mncCode();

    public abstract Optional<Integer> mccCode();

    public abstract ImmutableList<String> locales();

    public static ResourceConfigValues.Builder builder() {
      return new AutoValue_ResourceConfigParserTest_ResourceConfigValues.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      public abstract Builder setMncCode(int value);

      public abstract Builder setMccCode(int value);

      abstract ImmutableList.Builder<String> localesBuilder();

      public Builder addLocale(String value) {
        localesBuilder().add(value);
        return this;
      }

      public abstract ResourceConfigValues build();
    }
  }
}
