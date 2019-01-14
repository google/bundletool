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

package com.android.tools.build.bundletool.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.flags.Flag.RequiredFlagNotSetException;
import com.android.tools.build.bundletool.model.InputStreamSupplier;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.function.Executable;

/** Some misc utility methods for tests. */
public final class TestUtils {

  /** Tests that missing mandatory property is detected by an AutoValue.Builder. */
  public static void expectMissingRequiredBuilderPropertyException(
      String property, Executable runnable) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, runnable);

    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Missing required properties: %s", property));
  }

  /** Tests that missing mandatory command line argument is detected. */
  public static void expectMissingRequiredFlagException(String flag, Executable runnable) {
    RequiredFlagNotSetException exception =
        assertThrows(RequiredFlagNotSetException.class, runnable);

    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("Missing the required --%s flag", flag));
  }

  /**
   * Returns paths of the given {@link com.android.tools.build.bundletool.model.ModuleEntry}
   * instances, preserving the order.
   */
  public static ImmutableList<String> extractPaths(ImmutableList<ModuleEntry> entries) {
    return entries.stream()
        .map(ModuleEntry::getPath)
        .map(ZipPath::toString)
        .collect(toImmutableList());
  }

  /** Extracts paths of all files having the given path prefix. */
  public static ImmutableList<String> filesUnderPath(ZipFile zipFile, ZipPath pathPrefix) {
    return zipFile.stream()
        .map(ZipEntry::getName)
        .filter(entryName -> ZipPath.create(entryName).startsWith(pathPrefix))
        .collect(toImmutableList());
  }

  public static byte[] getEntryContent(ModuleEntry entry) {
    return toByteArray(entry.getContentSupplier());
  }

  public static byte[] toByteArray(InputStreamSupplier inputStreamSupplier) {
    try (InputStream entryContent = inputStreamSupplier.get()) {
      return ByteStreams.toByteArray(entryContent);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
