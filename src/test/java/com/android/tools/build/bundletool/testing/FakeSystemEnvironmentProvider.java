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

package com.android.tools.build.bundletool.testing;

import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Fake implementation of {@link SystemEnvironmentProvider}. */
public class FakeSystemEnvironmentProvider implements SystemEnvironmentProvider {

  private final ImmutableMap<String, String> variables;
  private final ImmutableMap<String, String> properties;

  public static final String ANDROID_HOME = "ANDROID_HOME";
  public static final String ANDROID_SERIAL = "ANDROID_SERIAL";

  /**
   * Creates {@link FakeSystemEnvironmentProvider} instance.
   *
   * @param variables mapping between fake environment variable names and their values.
   * @param properties mapping between fake system properties and their values.
   */
  public FakeSystemEnvironmentProvider(
      ImmutableMap<String, String> variables, ImmutableMap<String, String> properties) {
    this.variables = variables;
    this.properties = properties;
  }

  /** Convenience constructor with empty mapping of system properties. */
  public FakeSystemEnvironmentProvider(ImmutableMap<String, String> variables) {
    this(variables, ImmutableMap.of());
  }

  @Override
  public Optional<String> getVariable(String name) {
    return Optional.ofNullable(variables.get(name));
  }

  @Override
  public Optional<String> getProperty(String name) {
    return Optional.ofNullable(properties.get(name));
  }
}
