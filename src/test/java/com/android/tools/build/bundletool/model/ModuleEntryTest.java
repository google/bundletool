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
  public void equal_differentPath() throws Exception {
    assertThat(
            ModuleEntry.equal(
                createDirectoryEntry(ZipPath.create("a")),
                createDirectoryEntry(ZipPath.create("b"))))
        .isFalse();
  }

  @Test
  public void equal_differentType() throws Exception {
    assertThat(
            ModuleEntry.equal(
                createDirectoryEntry(ZipPath.create("a")),
                createFileEntry(ZipPath.create("a"), new byte[0])))
        .isFalse();
  }

  @Test
  public void equal_differentFileContents() throws Exception {
    assertThat(
            ModuleEntry.equal(
                createFileEntry(ZipPath.create("a"), new byte[] {'a'}),
                createFileEntry(ZipPath.create("a"), new byte[] {'b'})))
        .isFalse();
  }

  @Test
  public void equal_sameDirectories() throws Exception {
    assertThat(
            ModuleEntry.equal(
                createDirectoryEntry(ZipPath.create("a")),
                createDirectoryEntry(ZipPath.create("a"))))
        .isTrue();
  }

  @Test
  public void equal_sameFiles() throws Exception {
    assertThat(
            ModuleEntry.equal(
                createFileEntry(ZipPath.create("a"), new byte[] {'a'}),
                createFileEntry(ZipPath.create("a"), new byte[] {'a'})))
        .isTrue();
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
      public boolean shouldCompress() {
        return true;
      }

      @Override
      public ModuleEntry setCompression(boolean shouldCompress) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
