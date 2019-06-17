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

package com.android.tools.build.bundletool.testing.truth.resources;

import static com.google.common.truth.Fact.fact;
import static java.util.function.Predicate.isEqual;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A subject for {@link Entry}. */
public class EntrySubject extends Subject {

  private final Entry actual;

  public EntrySubject(FailureMetadata metadata, Entry actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  @Override
  protected String actualCustomStringRepresentation() {
    return "This resource entry (<" + actual + ">)";
  }

  public EntrySubject withConfigSize(int desiredSize) {
    check("getConfigValueList()").that(actual.getConfigValueList()).hasSize(desiredSize);
    return this;
  }

  public EntrySubject withConfigSizeAtMost(int maxSize) {
    check("getConfigValueList().size()").that(actual.getConfigValueList().size()).isAtMost(maxSize);
    return this;
  }

  public EntrySubject withDensity(int desiredDensity) {
    if (actual.getConfigValueList().stream()
        .map(ConfigValue::getConfig)
        .noneMatch(config -> config.getDensity() == desiredDensity)) {
      failWithActual("expected to contain a configuration with density", desiredDensity);
    }

    return this;
  }

  public EntrySubject onlyWithConfigs(Configuration... desiredConfigs) {
    Set<Configuration> desiredSet = new HashSet<>(Arrays.asList(desiredConfigs));
    for (ConfigValue configValue : actual.getConfigValueList()) {
      if (!desiredSet.contains(configValue.getConfig())) {
        failWithoutActual(
            fact("expected to contain only configs", Arrays.toString(desiredConfigs)),
            fact("but also contained config", configValue.getConfig()),
            fact("entry was", actual));
      } else {
        desiredSet.remove(configValue.getConfig());
      }
    }
    if (!desiredSet.isEmpty()) {
      failWithoutActual(
          fact("expected to contain only configs", Arrays.toString(desiredConfigs)),
          fact("but was missing", desiredSet),
          fact("entry was", actual));
    }
    return this;
  }

  public void withStringValue(String value) {
    if (actual.getConfigValueList().stream()
        .map(configValue -> configValue.getValue().getItem().getStr().getValue())
        .noneMatch(isEqual(value))) {
      failWithActual("expected not to contain resource with string value '%s'", value);
    }
  }

  public void withFileReference(String resFilePath) {
    if (actual.getConfigValueList().stream()
        .map(configValue -> configValue.getValue().getItem().getFile().getPath())
        .noneMatch(isEqual(resFilePath))) {
      failWithActual(
          "expected not to contain resource with file pointing to path '%s'", resFilePath);
    }
  }
}
