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
package com.android.tools.build.bundletool.model.utils;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.testing.FakeSystemEnvironmentProvider;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public final class SdkToolsLocatorTest {

  private static final String ANDROID_HOME_VARIABLE = "ANDROID_HOME";
  private static final String SYSTEM_PATH_VARIABLE = "PATH";
  private static final String PLATFORM_TOOLS_DIR = "platform-tools";
  private static final String ADB_FILENAME = "adb";

  // Technically the parameterized tests could be small since using JimFs but the non-parameterized
  // test is medium so all the tests in the class must be medium as well
    @RunWith(Parameterized.class)
  public static final class AdbInSdkDirTest {

    @Parameters(name = "os = {0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"Unix", Configuration.unix(), "/usr/home/android/sdk", ""},
            {"Windows", Configuration.windows(), "C:\\android\\sdk", ".exe"},
            {"OS X", Configuration.osX(), "/Users/home/android/sdk", ""}
          });
    }

    // Only used for the name of the test.
    @Parameter(0)
    public String osName;

    @Parameter(1)
    public Configuration osConfiguration;

    @Parameter(2)
    public String sdkPath;

    @Parameter(3)
    public String executableExtension;

    private FileSystem fileSystem;
    private Path platformToolsDir;
    private SystemEnvironmentProvider systemEnvironmentProvider;

    @Before
    public void setUp() throws Exception {
      fileSystem = Jimfs.newFileSystem(osConfiguration);
      platformToolsDir =
          Files.createDirectories(fileSystem.getPath(sdkPath).resolve(PLATFORM_TOOLS_DIR));

      systemEnvironmentProvider =
          new FakeSystemEnvironmentProvider(
              /* variables= */ ImmutableMap.of(
                  ANDROID_HOME_VARIABLE,
                  Files.createDirectories(fileSystem.getPath(sdkPath)).toString()));
    }

    @Test
    public void locateAdb_found() throws Exception {
      Path expectedAdb =
          createFileAndParentDirectories(platformToolsDir.resolve(getExecutableName(ADB_FILENAME)));

      Optional<Path> locatedAdb =
          new SdkToolsLocator(fileSystem).locateAdb(systemEnvironmentProvider);
      assertThat(locatedAdb).hasValue(expectedAdb);
    }

    @Test
    public void locateAdb_notFound() {
      Optional<Path> locatedAdb =
          new SdkToolsLocator(fileSystem).locateAdb(systemEnvironmentProvider);
      assertThat(locatedAdb).isEmpty();
    }

    private String getExecutableName(String baseName) {
      return baseName + executableExtension;
    }
  }

  // Technically the parameterized tests could be small since using JimFs but the non-parameterized
  // test is medium so all the tests in the class must be medium as well
    @RunWith(Parameterized.class)
  public static final class AdbOnSystemPathTest {

    @Parameters(name = "os = {0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {
              /* osName */ "Unix",
              /* osConfiguration */ Configuration.unix(),
              /* pathSeparator */ ":",
              /* systemPath */ "/nonexistent:/bin:\"invalid-path",
              /* goodPathDir */ "/bin",
              /* executableExtension */ ""
            },
            {
              /* osName */ "Windows",
              /* osConfiguration */ Configuration.windows(),
              /* pathSeparator */ ";",
              /* systemPath */ "C:\\nonexistent;C:\\bin;\"invalid-path",
              /* goodPathDir */ "C:\\bin",
              /* executableExtension */ ".exe"
            },
            {
              /* osName */ "Windows ('c:\\adb' dir)",
              /* osConfiguration */ Configuration.windows(),
              /* pathSeparator */ ";",
              /* systemPath */ "C:\\nonexistent;C:\\adb;\"invalid-path",
              /* goodPathDir */ "C:\\adb",
              /* executableExtension */ ".exe"
            },
            {
              /* osName */ "OS X",
              /* osConfiguration */ Configuration.osX(),
              /* pathSeparator */ ":",
              /* systemPath */ "/nonexistent:/bin:\"invalid-path",
              /* goodPathDir */ "/bin",
              /* executableExtension */ ""
            }
          });
    }

    // Only used for the name of the test.
    @Parameter(0)
    public String osName;

    @Parameter(1)
    public Configuration osConfiguration;

    @Parameter(2)
    public String pathSeparator;

    @Parameter(3)
    public String systemPath;

    @Parameter(4)
    public String goodPathDir;

    @Parameter(5)
    public String executableExtension;

    private FileSystem fileSystem;
    private SystemEnvironmentProvider systemEnvironmentProvider;

    @Before
    public void setUp() throws Exception {
      fileSystem = Jimfs.newFileSystem(osConfiguration);

      checkState(systemPath.contains(goodPathDir));
      Files.createDirectories(fileSystem.getPath(goodPathDir));

      systemEnvironmentProvider =
          new FakeSystemEnvironmentProvider(
              /* variables= */ ImmutableMap.of(SYSTEM_PATH_VARIABLE, systemPath),
              /* properties= */ ImmutableMap.of("path.separator", pathSeparator));
    }

    @Test
    public void locateAdb_found() throws Exception {
      Path expectedAdb =
          createFileAndParentDirectories(
              fileSystem.getPath(goodPathDir).resolve(getExecutableName(ADB_FILENAME)));

      Optional<Path> locatedAdb =
          new SdkToolsLocator(fileSystem).locateAdb(systemEnvironmentProvider);
      assertThat(locatedAdb).hasValue(expectedAdb);
    }

    @Test
    public void locateAdb_notFound() {
      Optional<Path> locatedAdb =
          new SdkToolsLocator(fileSystem).locateAdb(systemEnvironmentProvider);
      assertThat(locatedAdb).isEmpty();
    }

    private String getExecutableName(String baseName) {
      return baseName + executableExtension;
    }
  }

    @RunWith(JUnit4.class)
  public static final class SdkToolsLocatorNotParameterizedTest {

    @Rule public final TemporaryFolder tempFolderRule = new TemporaryFolder();

    private Path tempFolder;

    @Before
    public void setUp() {
      tempFolder = tempFolderRule.getRoot().toPath();
    }

    @Test
    public void locateAapt2() throws Exception {
      Path tempDir = Files.createDirectory(tempFolder.resolve("output"));
      Optional<Path> aapt2Path =
          new SdkToolsLocator(FileSystems.getDefault()).extractAapt2(tempDir);
      assertThat(aapt2Path).isPresent();
    }
  }

  private static Path createFileAndParentDirectories(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.createFile(path);
    return path;
  }
}
