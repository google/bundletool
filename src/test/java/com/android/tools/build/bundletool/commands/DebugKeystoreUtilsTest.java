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

package com.android.tools.build.bundletool.commands;

import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebugKeystoreUtilsTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.newFolder().toPath();
  }

  @Test
  public void debugKeystore_notFound() throws Exception {
    Path androidSdkHome = tmpDir.resolve("androidSdkHome").resolve(".android");
    Files.createDirectories(androidSdkHome);

    Path homeDir = tmpDir.resolve("home");
    Files.createDirectories(homeDir);

    FakeSystemEnvironmentProvider fakeEnvironmentVariableProvider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                "ANDROID_SDK_HOME", tmpDir.resolve("androidSdkHome").toString(),
                "HOME", homeDir.toString()),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), homeDir.toString()));

    assertThat(DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.get(fakeEnvironmentVariableProvider))
        .isEmpty();
  }

  @Test
  public void debugKeyStore_prefersAndroidSdkHome() throws Exception {
    Path androidSdkHome = tmpDir.resolve("androidSdkHome");
    Path androidSdkHomeKey = androidSdkHome.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(androidSdkHomeKey);
    Files.write(androidSdkHomeKey, new byte[0]);

    Path homeDir = tmpDir.resolve("home");
    Path homeDebugKey = homeDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(homeDebugKey);
    Files.write(homeDebugKey, new byte[0]);

    FakeSystemEnvironmentProvider fakeEnvironmentVariableProvider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                "ANDROID_SDK_HOME", androidSdkHome.toString(),
                "HOME", homeDir.toString()),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), homeDir.toString()));

    assertThat(DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.get(fakeEnvironmentVariableProvider))
        .hasValue(androidSdkHomeKey);
  }

  @Test
  public void debugKeystore_userHome_secondPreference() throws Exception {
    Path androidSdkHome = tmpDir.resolve("androidSdkHome");
    Files.createDirectories(androidSdkHome);

    Path homeDir = tmpDir.resolve("home");
    Path homeDebugKey = homeDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(homeDebugKey);
    Files.write(homeDebugKey, new byte[0]);

    Path homeEnvDir = tmpDir.resolve("homeEnv");
    Path homeEnvDebugKey = homeEnvDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(homeEnvDebugKey);
    Files.write(homeEnvDebugKey, new byte[0]);

    FakeSystemEnvironmentProvider fakeEnvironmentVariableProvider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                "ANDROID_SDK_HOME", androidSdkHome.toString(),
                "HOME", homeEnvDir.toString()),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), homeDir.toString()));

    assertThat(DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.get(fakeEnvironmentVariableProvider))
        .hasValue(homeDebugKey);
  }

  @Test
  public void debugKeystore_homeEnvironmentVariable_thirdPreference() throws Exception {
    Path androidSdkHome = tmpDir.resolve("androidSdkHome");
    Files.createDirectories(androidSdkHome);

    Path homeDir = tmpDir.resolve("home");
    Files.createDirectories(homeDir);

    Path homeEnvDir = tmpDir.resolve("homeEnv");
    Path homeEnvDebugKey = homeEnvDir.resolve(".android").resolve("debug.keystore");
    FileUtils.createParentDirectories(homeEnvDebugKey);
    Files.write(homeEnvDebugKey, new byte[0]);

    FakeSystemEnvironmentProvider fakeEnvironmentVariableProvider =
        new FakeSystemEnvironmentProvider(
            /* variables= */ ImmutableMap.of(
                "ANDROID_SDK_HOME", androidSdkHome.toString(),
                "HOME", homeEnvDir.toString()),
            /* properties= */ ImmutableMap.of(USER_HOME.key(), homeDir.toString()));

    assertThat(DebugKeystoreUtils.DEBUG_KEYSTORE_CACHE.get(fakeEnvironmentVariableProvider))
        .hasValue(homeEnvDebugKey);
  }
}
