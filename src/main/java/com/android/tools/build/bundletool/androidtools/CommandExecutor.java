/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.build.bundletool.androidtools;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import javax.annotation.concurrent.Immutable;

/** Helper to execute native commands. Interface provided to enable testing. */
public interface CommandExecutor {

  void execute(ImmutableList<String> command, CommandOptions options);

  ImmutableList<String> executeAndCapture(ImmutableList<String> command, CommandOptions options);

  /** Options for the execution of the native command. */
  @AutoValue
  @Immutable
  abstract class CommandOptions {
    abstract Duration getTimeout();

    public static Builder builder() {
      return new AutoValue_CommandExecutor_CommandOptions.Builder();
    }

    /** Builder for the {@link CommandOptions} class. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setTimeout(Duration timout);

      public abstract CommandOptions build();
    }
  }
}
