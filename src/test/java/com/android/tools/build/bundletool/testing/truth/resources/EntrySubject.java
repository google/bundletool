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

import static java.util.function.Predicate.isEqual;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A subject for {@link Entry}. */
public class EntrySubject extends Subject<EntrySubject, Entry> {

  public EntrySubject(FailureMetadata metadata, Entry actual) {
    super(metadata, actual);
    named("This resource entry");
  }

  public EntrySubject withConfigSize(int desiredSize) {
    List<ConfigValue> configValues = actual().getConfigValueList();
    if (configValues.size() != desiredSize) {
      failWithoutActual(
          "Expected number of configurations equal to "
              + desiredSize
              + " but got: "
              + configValues.size());
    }
    return this;
  }

  public EntrySubject withConfigSizeAtMost(int maxSize) {
    List<ConfigValue> configValues = actual().getConfigValueList();
    if (configValues.size() > maxSize) {
      failWithoutActual(
          "Expected number of configurations at most "
              + maxSize
              + " but got: "
              + configValues.size());
    }
    return this;
  }

  public EntrySubject withDensity(int desiredDensity) {
    if (actual()
        .getConfigValueList()
        .stream()
        .map(ConfigValue::getConfig)
        .noneMatch(config -> config.getDensity() == desiredDensity)) {
      fail("contains a configuration with density " + desiredDensity + "dpi.");
    }

    return this;
  }

  public EntrySubject onlyWithConfigs(Configuration... desiredConfigs) {
    Set<Configuration> desiredSet = new HashSet<>(Arrays.asList(desiredConfigs));
    for (ConfigValue configValue : actual().getConfigValueList()) {
      if (!desiredSet.contains(configValue.getConfig())) {
        fail(
            "contains only configs: "
                + Arrays.toString(desiredConfigs)
                + ". It has also: "
                + configValue.getConfig());
      } else {
        desiredSet.remove(configValue.getConfig());
      }
    }
    if (!desiredSet.isEmpty()) {
      fail(
          "contains only configs: "
              + Arrays.toString(desiredConfigs)
              + ". It's missing: "
              + desiredSet.toString());
    }
    return this;
  }

  public void withStringValue(String value) {
    if (actual()
        .getConfigValueList()
        .stream()
        .map(configValue -> configValue.getValue().getItem().getStr().getValue())
        .noneMatch(isEqual(value))) {
      fail("does not contain resource with string value '%s'", value);
    }
  }

  public void withFileReference(String resFilePath) {
    if (actual()
        .getConfigValueList()
        .stream()
        .map(configValue -> configValue.getValue().getItem().getFile().getPath())
        .noneMatch(isEqual(resFilePath))) {
      fail("does not contain resource with file pointing to path '%s'", resFilePath);
    }
  }
}
