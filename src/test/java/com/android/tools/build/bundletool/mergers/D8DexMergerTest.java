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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.TestData;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.testing.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class D8DexMergerTest {

  private static final Optional<Path> NO_FILE = Optional.empty();

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
                        /* mainDexListFile= */ NO_FILE,
                        /* proguardMap= */ NO_FILE,
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
                        /* mainDexListFile= */ NO_FILE,
                        /* proguardMap= */ NO_FILE,
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
                        /* mainDexListFile= */ NO_FILE,
                        /* proguardMap= */ NO_FILE,
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
                /* mainDexListFile= */ NO_FILE,
                /* proguardMap= */ NO_FILE,
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
                /* mainDexListFile= */ NO_FILE,
                /* proguardMap= */ NO_FILE,
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
                        /* mainDexListFile= */ NO_FILE,
                        /* proguardMap= */ NO_FILE,
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
                /* mainDexListFile= */ NO_FILE,
                /* proguardMap= */ NO_FILE,
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
                /* proguardMap= */ NO_FILE,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_K_API_VERSION);

    assertThat(mergedDexFiles.size()).isAtLeast(2);
    assertThat(listClassesInDexFiles(mergedDexFiles))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
  }

  @Test
  public void mergeCoreDesugaringLibrary_ok() throws Exception {
    // Two application dex files together with code desugaring dex.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-other.dex");
    Path dexFile3 = writeTestDataToFile("testdata/dex/classes-emulated-coredesugar.dex");

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2, dexFile3),
                outputDir,
                /* mainDexListFile= */ Optional.empty(),
                /* proguardMap= */ NO_FILE,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_K_API_VERSION);
    ImmutableList<String> mergedDexFilenames =
        mergedDexFiles.stream().map(dex -> dex.getFileName().toString()).collect(toImmutableList());

    assertThat(mergedDexFiles.size()).isAtLeast(2);
    assertThat(mergedDexFilenames).containsExactly("classes.dex", "classes2.dex");
    assertThat(listClassesInDexFiles(mergedDexFiles.get(0)))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
    // Core desugaring dex must not be merged with application dex.
    assertThat(Files.readAllBytes(mergedDexFiles.get(1))).isEqualTo(Files.readAllBytes(dexFile3));
  }

  @Test
  public void mergeCoreDesugaringLibrary_ok_with_java() throws Exception {
    // Two application dex files together with code desugaring dex.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-other.dex");
    Path dexFile3 = writeTestDataToFile("testdata/dex/classes-coredesugar-with-java-package.dex");

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2, dexFile3),
                outputDir,
                /* mainDexListFile= */ Optional.empty(),
                /* proguardMap= */ NO_FILE,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_K_API_VERSION);
    ImmutableList<String> mergedDexFilenames =
        mergedDexFiles.stream().map(dex -> dex.getFileName().toString()).collect(toImmutableList());

    assertThat(mergedDexFiles.size()).isAtLeast(2);
    assertThat(mergedDexFilenames).containsExactly("classes.dex", "classes2.dex");
    assertThat(listClassesInDexFiles(mergedDexFiles.get(0)))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
    // Core desugaring dex must not be merged with application dex.
    assertThat(Files.readAllBytes(mergedDexFiles.get(1))).isEqualTo(Files.readAllBytes(dexFile3));
  }

  @Test
  public void bogusMapFileWorks() throws Exception {
    // The two input dex files cannot fit into a single dex file.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes-large.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-large2.dex");

    Optional<Path> bogusMapFile =
        Optional.of(FileUtils.createFileWithLines(tmp, "NOT_A_VALID::MAPPING->::file->x"));

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2),
                outputDir,
                /* mainDexListFile= */ NO_FILE,
                /* proguardMap= */ bogusMapFile,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_L_API_VERSION);
    assertThat(mergedDexFiles.size()).isAtLeast(2);
    assertThat(listClassesInDexFiles(mergedDexFiles))
        .isEqualTo(listClassesInDexFiles(dexFile1, dexFile2));
  }

  @Test
  public void mapFileArgument() throws Exception {
    // The two input dex files cannot fit into a single dex file.
    Path dexFile1 = writeTestDataToFile("testdata/dex/classes-large.dex");
    Path dexFile2 = writeTestDataToFile("testdata/dex/classes-large2.dex");

    // We are not testing actual distribution, just that a valid map
    // file is passed through correctly.
    Optional<Path> mapFile =
        Optional.of(
            FileUtils.createFileWithLines(
                tmp,
                "android.arch.core.executor.DefaultTaskExecutor -> c:",
                "    android.os.Handler mMainHandler -> c",
                "    java.lang.Object mLock -> a",
                "    java.util.concurrent.ExecutorService mDiskIO -> b",
                "    1:3:void <init>():31:33 -> <init>",
                "    1:1:boolean isMainThread():58:58 -> a",
                "    1:4:void postToMainThread(java.lang.Runnable):45:48 -> b",
                "    5:5:void postToMainThread(java.lang.Runnable):50:50 -> b",
                "    6:6:void postToMainThread(java.lang.Runnable):53:53 -> b"));

    ImmutableList<Path> mergedDexFiles =
        new D8DexMerger()
            .merge(
                ImmutableList.of(dexFile1, dexFile2),
                outputDir,
                /* mainDexListFile= */ NO_FILE,
                /* proguardMap= */ mapFile,
                /* isDebuggable= */ false,
                /* minSdkVersion= */ ANDROID_L_API_VERSION);
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

  private static ImmutableSet<String> listClassesInDexFiles(Collection<Path> dexPaths)
      throws Exception {
    ImmutableSet.Builder<String> classes = ImmutableSet.builder();
    for (Path dexPath : dexPaths) {
      DexBackedDexFile dexFile = DexFileFactory.loadDexFile(dexPath.toFile(), Opcodes.getDefault());
      for (DexBackedClassDef clazz : dexFile.getClasses()) {
        classes.add(clazz.getType());
      }
    }
    return classes.build();
  }

  private static ImmutableList<Path> listDirectory(Path dir) {
    return Arrays.stream(dir.toFile().listFiles()).map(File::toPath).collect(toImmutableList());
  }
}
