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

package com.android.tools.build.bundletool.model;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleEntryTest {

  @Test
  public void equals_differentPath() throws Exception {
    ModuleEntry entry1 = createDirectoryEntry(ZipPath.create("a"));
    ModuleEntry entry2 = createDirectoryEntry(ZipPath.create("b"));

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_differentType() throws Exception {
    ModuleEntry entry1 = createDirectoryEntry(ZipPath.create("a"));
    ModuleEntry entry2 = createFileEntry(ZipPath.create("a"), new byte[0]);

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_differentFileContents() throws Exception {
    ModuleEntry entry1 = createFileEntry(ZipPath.create("a"), new byte[] {'a'});
    ModuleEntry entry2 = createFileEntry(ZipPath.create("a"), new byte[] {'b'});

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_sameDirectories() throws Exception {
    ModuleEntry entry = createDirectoryEntry(ZipPath.create("a"));

    assertThat(entry.equals(entry)).isTrue();
  }

  @Test
  public void equals_sameFiles() throws Exception {
    ModuleEntry entry = createFileEntry(ZipPath.create("a"), new byte[] {'a'});

    assertThat(entry.equals(entry)).isTrue();
  }

  private static ModuleEntry createFileEntry(ZipPath path, byte[] content) throws Exception {
    return createEntry(path, /* isDirectory= */ false, () -> new ByteArrayInputStream(content));
  }

  private static ModuleEntry createDirectoryEntry(ZipPath path) throws Exception {
    return createEntry(
        path,
        /* isDirectory= */ true,
        () -> {
          throw new RuntimeException("Why would you want content of a directory?");
        });
  }

  private static ModuleEntry createEntry(
      ZipPath path, boolean isDirectory, Supplier<InputStream> contentSupplier) {
    return new ModuleEntry() {
      @Override
      public InputStream getContent() {
        return contentSupplier.get();
      }

      @Override
      public ZipPath getPath() {
        return path;
      }

      @Override
      public boolean isDirectory() {
        return isDirectory;
      }

      @Override
      public boolean getShouldCompress() {
        return true;
      }
    };
  }
}
