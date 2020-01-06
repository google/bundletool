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

package com.android.tools.build.bundletool.model.utils;

import java.util.Optional;

/** Interface for fetching the environment variables and system properties. */
public interface SystemEnvironmentProvider {

  SystemEnvironmentProvider DEFAULT_PROVIDER = new DefaultSystemEnvironmentProvider();

  String ANDROID_HOME = "ANDROID_HOME";

  /** Returns the value of the environment variable of a given name if it exists. */
  Optional<String> getVariable(String name);

  /** Returns the value of the system property if it exists. */
  Optional<String> getProperty(String name);

  default Optional<String> getAndroidHomePath() {
    return getVariable(ANDROID_HOME);
  }
}
