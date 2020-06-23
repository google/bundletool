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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/** Helper to locate various tools in the SDK dir. */
public final class SdkToolsLocator {

  public static final String ANDROID_HOME_VARIABLE = "ANDROID_HOME";
  public static final String SYSTEM_PATH_VARIABLE = "PATH";

  private static final String ADB_PATH_GLOB = "glob:**/{adb,adb.exe}";
  private static final String ADB_SDK_GLOB = "glob:**/platform-tools/{adb,adb.exe}";
  private static final BiPredicate<Path, BasicFileAttributes> AAPT2_MATCHER =
      (file, attrs) -> file.getFileName().toString().matches("aapt2(\\.exe)?");

  private final FileSystem fileSystem;

  public SdkToolsLocator() {
    this(FileSystems.getDefault());
  }

  SdkToolsLocator(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  /**
   * Tries to extract aapt2 from the executable if found. The gradle tests extract aapt2 in a
   * corresponding folder. In this case the folder is searched.
   *
   * <p>Returns an empty instance if no aapt2 binary is found inside the folder.
   *
   * @throws CommandExecutionException if aapt2 was not in or cannot be extracted from the
   *     executable.
   */
  public Optional<Path> extractAapt2(Path tempDir) {
    String osDir = getOsSpecificJarDirectory();
    // Attempt at locating the directory in question inside the jar.
    URL osDirUrl = SdkToolsLocator.class.getResource(osDir);
    if (osDirUrl == null) {
      return Optional.empty();
    }

    Path aapt2;
    try {
      Path outputDir = tempDir.resolve("output");
      // If we are in a jar, we are running from the executable.
      // Extract aapt2 from the jar.
      if ("jar".equals(osDirUrl.getProtocol())) {
        extractFilesFromJar(outputDir, osDirUrl, osDir);
        try (Stream<Path> aapt2Binaries = Files.find(outputDir, /* maxDepth= */ 3, AAPT2_MATCHER)) {
          aapt2 = aapt2Binaries.collect(onlyElement());
        }

      } else {
        // If we are not in a jar, this might be a test.
        // Try to locate the aapt2 inside the directory.
        try (Stream<Path> aapt2Binaries =
            Files.find(Paths.get(osDirUrl.toURI()), /* maxDepth= */ 3, AAPT2_MATCHER)) {
          Optional<Path> aapt2Path = aapt2Binaries.findFirst();
          if (!aapt2Path.isPresent()) {
            return Optional.empty();
          }
          aapt2 = aapt2Path.get();
        }
      }
    } catch (NoSuchElementException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Unable to locate aapt2 inside jar.")
          .build();
    } catch (IOException | URISyntaxException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Unable to extract aapt2 from jar.")
          .withCause(e)
          .build();
    }

    // Sanity check.
    checkState(Files.exists(aapt2));

    // Ensure aapt2 is executable.
    try {
      aapt2.toFile().setExecutable(true);
    } catch (SecurityException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Unable to make aapt2 executable. This may be a permission issue. If it persists, "
                  + "consider passing the path to aapt2 using the flag --aapt2.")
          .withCause(e)
          .build();
    }

    return Optional.of(aapt2);
  }

  private void extractFilesFromJar(Path outputDir, URL directoryUrl, String startDir)
      throws IOException, URISyntaxException {
    // aapt2 is not statically built, so some other libraries are also included, sometimes in
    // subdirectories, hence we look down 3 directories down just in case to extract everything.
    try (FileSystem fs = FileSystems.newFileSystem(directoryUrl.toURI(), ImmutableMap.of());
        Stream<Path> paths = Files.walk(fs.getPath(startDir))) {
      for (Path path : paths.collect(toImmutableList())) {
        String pathStr = path.toString();
        try (InputStream is = sanitize(getClass().getResourceAsStream(pathStr))) {
          if (is.available() == 0) {
            // A directory.
            continue;
          }

          // Remove leading slash from the path because:
          //    Path("/tmp/dir/").resolve("/hello")
          // returns
          //    Path("/hello")
          Path target = outputDir.resolve(pathStr.replaceFirst("^/", ""));
          // Ensure all parent directories exist.
          Files.createDirectories(target.getParent());
          // Extract the file on disk.
          Files.copy(is, target);
        }
      }
    }
  }

  /** Returns the name of the OS-specific directory inside bundletool executable jar. */
  private static String getOsSpecificJarDirectory() {
    switch (OsPlatform.getCurrentPlatform()) {
      case WINDOWS:
        return "/windows";
      case MACOS:
        return "/macos";
      case LINUX:
        return "/linux";
      case OTHER:
        // Unrecognized OS; let's try Linux.
        return "/linux";
    }
    throw new IllegalStateException();
  }

  /** Hack to work around https://bugs.openjdk.java.net/browse/JDK-8144977 */
  private static InputStream sanitize(InputStream is) throws IOException {
    try {
      is.available();
      return is;
    } catch (NullPointerException e) {
      // The InputStream has an underlying null stream, which is a bug fixed in JDK9 only.
      // We return a safe empty InputStream, which can be read and closed safely.
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  public Optional<Path> locateAdb(SystemEnvironmentProvider systemEnvironmentProvider) {
    Optional<Path> adbInSdkDir = locateAdbInSdkDir(systemEnvironmentProvider);
    if (adbInSdkDir.isPresent()) {
      return adbInSdkDir;
    }

    Optional<Path> adbOnPath = locateBinaryOnSystemPath(ADB_PATH_GLOB, systemEnvironmentProvider);
    if (adbOnPath.isPresent()) {
      return adbOnPath;
    }

    return Optional.empty();
  }

  /** Tries to locate adb utility under "platform-tools". */
  private Optional<Path> locateAdbInSdkDir(SystemEnvironmentProvider systemEnvironmentProvider) {
    Optional<String> sdkDir = systemEnvironmentProvider.getVariable(ANDROID_HOME_VARIABLE);
    if (!sdkDir.isPresent()) {
      return Optional.empty();
    }

    Path platformToolsDir = fileSystem.getPath(sdkDir.get(), "platform-tools");
    if (!Files.isDirectory(platformToolsDir)) {
      return Optional.empty();
    }

    // Expecting to find one entry.
    PathMatcher adbPathMatcher = fileSystem.getPathMatcher(ADB_SDK_GLOB);
    try (Stream<Path> pathStream =
        Files.find(
            platformToolsDir,
            /* maxDepth= */ 1,
            (path, attributes) -> adbPathMatcher.matches(path) && Files.isExecutable(path))) {
      return pathStream.findFirst();
    } catch (IOException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("Error while trying to locate adb in SDK dir '%s'.", sdkDir)
          .build();
    }
  }

  private Optional<Path> locateBinaryOnSystemPath(
      String binaryGlob, SystemEnvironmentProvider systemEnvironmentProvider) {

    Optional<String> rawPath = systemEnvironmentProvider.getVariable(SYSTEM_PATH_VARIABLE);
    if (!rawPath.isPresent()) {
      return Optional.empty();
    }

    // Any sane Java runtime should define this property.
    String pathSeparator = systemEnvironmentProvider.getProperty("path.separator").get();

    PathMatcher binPathMatcher = fileSystem.getPathMatcher(binaryGlob);
    for (String pathDir : Splitter.on(pathSeparator).splitToList(rawPath.get())) {
      try (Stream<Path> pathStream =
          Files.find(
              fileSystem.getPath(pathDir),
              /* maxDepth= */ 1,
              (path, attributes) ->
                  binPathMatcher.matches(path)
                      && Files.isExecutable(path)
                      && Files.isRegularFile(path))) {

        Optional<Path> binaryInDir = pathStream.findFirst();
        if (binaryInDir.isPresent()) {
          return binaryInDir;
        }
      } catch (NoSuchFileException | NotDirectoryException | InvalidPathException tolerate) {
        // Tolerate invalid PATH entries.
      } catch (IOException e) {
        throw CommandExecutionException.builder()
            .withCause(e)
            .withInternalMessage(
                "Error while trying to locate adb on system PATH in directory '%s'.", pathDir)
            .build();
      }
    }

    return Optional.empty();
  }
}
