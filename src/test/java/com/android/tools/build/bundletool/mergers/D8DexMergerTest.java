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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_K_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.FileUtils;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class D8DexMergerTest {

  private static final Optional<Path> NO_MAIN_DEX_LIST = Optional.empty();

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;
  private Path outputDir;

  @Before
  public void setUp() throws Exception {
    this.tmpDir = tmp.getRoot().toPath();
    this.outputDir = Files.createDirectory(tmpDir.resolve("output"));
  }

  @Test
  public void outputDirectoryDoesNotExist_throws() throws Exception {
    Path dexFile = writeTestDataToFile("testdata/dex/classes.dex");
    Path nonExistentDir = tmpDir.resolve("non-existent");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new D8DexMerger()
                    .merge(
                        ImmutableList.of(dexFile),
                        nonExistentDir,
                        NO_MAIN_DEX_LIST,
                        /* isDebuggable= */ false,
                        /* minSdkVersion= */ ANDROID_K_API_VERSION));

    assertThat(exception).hasMessageThat().contains("was not found");
  }

  @Test
  public void outputDirectoryNotEmpty_throws() throws Exception {
    Path dexFile = writeTestDataToFile("testdata/dex/classes.dex");
    Files.createFile(outputDir.resolve("a-file.txt"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new D8DexMerger()
                    .merge(
                        ImmutableList.of(dexFile),
                        outputDir,
                        NO_MAIN_DEX_LIST,
                        /* isDebuggable= */ false,
                        /* minSdkVersion= */ ANDROID_K_API_VERSION));

    assertThat(exception).hasMessageThat().contains("is not empty");
  }

  @Test
  public void inputDexFileDoesNotExist_throws() throws Exception {
    Path nonExistentDex = tmpDir.resolve("classes-non-existing.dex");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new D8DexMerger()
                    .merge(
                        ImmutableList.of(nonExistentDex),
                        outputDir,
                        NO_MAIN_DEX_LIST,
                        /* isDebuggable= */ false,
                        /* minSdkVersion= */ ANDROID_K_API_VERSION));

    assertThat(exception).hasMessageThat().contains("was not found");
  }

  @Test
  public void writesDexFilesAndOnlyDexFilesIntoTheGivenDirectory() throws Exception {
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-other.dex");

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2),
                outputDir,
                NO_MAIN_DEX_LIST,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_K_API_VERSION);

    assertThat(mergedDexFiles).isNotEmpty();
    assertThat(listDirectory(outputDir)).containsExactlyElementsIn(mergedDexFiles);
  }

  @Test
  public void mergeFitsIntoSingleDex_ok() throws Exception {
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-other.dex");

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2),
                outputDir,
                NO_MAIN_DEX_LIST,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_K_API_VERSION);

    assertThat(mergedDexFiles).hasSize(1);
    assertThat(listClassesInDexFiles(mergedDexFiles))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
  }

  @Test
  public void mergeDoesNotFitIntoSingleDex_withoutMainDexList_preL_throws() throws Exception {
    // The two input dex files cannot fit into a single dex file.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes-large.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-large2.dex");

    CommandExecutionException exception =
        assertThrows(
            CommandExecutionException.class,
            () ->
                new D8DexMerger()
                    .merge(
                        ImmutableList.of(dexFile1, dexFile2),
                        outputDir,
                        NO_MAIN_DEX_LIST,
                        /* isDebuggable= */ false,
                        /* minSdkVersion= */ ANDROID_K_API_VERSION));

    assertThat(exception).hasMessageThat().contains("multidex is not supported by the input");
  }

  @Test
  public void mergeDoesNotFitIntoSingleDex_withoutMainDexList_LPlus_ok() throws Exception {
    // The two input dex files cannot fit into a single dex file.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes-large.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-large2.dex");

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2),
                outputDir,
                NO_MAIN_DEX_LIST,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_L_API_VERSION);

    assertThat(mergedDexFiles.size()).isAtLeast(2);
    assertThat(listClassesInDexFiles(mergedDexFiles))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
  }

  @Test
  public void mergeDoesNotFitIntoSingleDex_withMainDexList_preL_ok() throws Exception {
    // The two input dex files cannot fit into a single dex file.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes-large.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-large2.dex");

    Optional<Path> mainDexListFile =
        Optional.of(
            FileUtils.createFileWithLines(
                tmp, "com/google/uam/aia/myapplication/feature/MainActivity.class"));

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2),
                outputDir,
                mainDexListFile,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_K_API_VERSION);

    assertThat(mergedDexFiles.size()).isAtLeast(2);
    assertThat(listClassesInDexFiles(mergedDexFiles))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
  }

  private Path writeTestDataToFile(String testDataPath) throws Exception {
    checkArgument(testDataPath.contains("."));
    String extension = com.google.common.io.Files.getFileExtension(testDataPath);
    return Files.write(
        Files.createTempFile(tmpDir, getClass().getSimpleName(), "." + extension),
        TestData.readBytes(testDataPath));
  }

  private static ImmutableSet<String> listClassesInDexFiles(Path... dexFiles) throws Exception {
    return listClassesInDexFiles(Arrays.asList(dexFiles));
  }

  /**
   * This method is inspired by {@link com.android.tools.r8.PrintClassList} which has no Java API at
   * the time of writing these tests.
   */
  private static ImmutableSet<String> listClassesInDexFiles(Collection<Path> dexFiles)
      throws Exception {

    DexApplication dexApplication =
        new ApplicationReader(
                AndroidApp.builder().addProgramFiles(dexFiles).build(),
                new InternalOptions(),
                new Timing("irrelevant"))
            .read();

    ImmutableSet<String> classes =
        dexApplication
            .classes()
            .stream()
            .map(clazz -> clazz.type.toString())
            .collect(toImmutableSet());
    checkState(!classes.isEmpty());
    return classes;
  }

  private static ImmutableList<Path> listDirectory(Path dir) {
    return Arrays.stream(dir.toFile().listFiles()).map(File::toPath).collect(toImmutableList());
  }
}
