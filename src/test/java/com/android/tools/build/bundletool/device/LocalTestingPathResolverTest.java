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

package com.android.tools.build.bundletool.device;

import static com.android.tools.build.bundletool.device.LocalTestingPathResolver.resolveLocalTestingPath;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalTestingPathResolverTest {

  @Test
  public void resolveLocalTestingPath_relativePathAndPackageName_resolves() {
    String actual = resolveLocalTestingPath("foo", Optional.of("com.acme.anvil"));

    assertThat(actual).isEqualTo("/sdcard/Android/data/com.acme.anvil/files/foo");
  }

  @Test
  public void resolveLocalTestingPath_relativePathWithoutPackageName_throws() {
    assertThrows(
        CommandExecutionException.class, () -> resolveLocalTestingPath("foo", Optional.empty()));
  }

  @Test
  public void resolveLocalTestingPath_absolutePath_resolves() {
    String actual = resolveLocalTestingPath("/foo/bar", Optional.of("com.acme.anvil"));

    assertThat(actual).isEqualTo("/foo/bar");
  }
}
