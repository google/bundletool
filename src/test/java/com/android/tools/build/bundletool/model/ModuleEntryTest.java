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

import com.google.common.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleEntryTest {
  @Test
  public void builder() throws Exception {
    ZipPath path = ZipPath.create("a");
    byte[] content = new byte[] {'a'};
    ModuleEntry entry = createEntry(path, content).toBuilder().setForceUncompressed(true).build();

    assertThat(entry.getPath()).isEqualTo(path);
    assertThat(entry.getForceUncompressed()).isTrue();
    assertThat(entry.getContent().read()).isEqualTo(content);
  }

  @Test
  public void builder_defaults() throws Exception {
    ModuleEntry entry = createEntry(ZipPath.create("a"), new byte[0]);
    assertThat(entry.getForceUncompressed()).isFalse();
  }

  @Test
  public void equals_differentPath() throws Exception {
    ModuleEntry entry1 = createEntry(ZipPath.create("a"), new byte[0]);
    ModuleEntry entry2 = createEntry(ZipPath.create("b"), new byte[0]);

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_differentShouldCompress() throws Exception {
    ModuleEntry entry1 = createEntry(ZipPath.create("a"), new byte[0]);
    ModuleEntry entry2 =
        entry1.toBuilder().setForceUncompressed(!entry1.getForceUncompressed()).build();

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_differentShouldSign() throws Exception {
    ModuleEntry entry1 = createEntry(ZipPath.create("a"), new byte[0]);
    ModuleEntry entry2 = entry1.toBuilder().setShouldSign(!entry1.getShouldSign()).build();

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_differentFileContents() throws Exception {
    ModuleEntry entry1 = createEntry(ZipPath.create("a"), new byte[] {'a'});
    ModuleEntry entry2 = createEntry(ZipPath.create("a"), new byte[] {'b'});

    assertThat(entry1.equals(entry2)).isFalse();
  }

  @Test
  public void equals_sameFiles() throws Exception {
    ModuleEntry entry = createEntry(ZipPath.create("a"), new byte[] {'a'});

    assertThat(entry.equals(entry)).isTrue();
  }

  @Test
  public void equals_ensureEntryContentIsReadOnce() throws Exception {
    ZipPath zipPath = ZipPath.create("a");
    CountingByteSource content1 = new CountingByteSource(new byte[] {'a'});
    CountingByteSource content2 = new CountingByteSource(new byte[] {'a'});

    ModuleEntry entry1 = ModuleEntry.builder().setPath(zipPath).setContent(content1).build();
    ModuleEntry entry2 = ModuleEntry.builder().setPath(zipPath).setContent(content2).build();

    for (int i = 0; i < 10; i++) {
      assertThat(entry1.equals(entry2)).isTrue();
    }
    assertThat(content1.getOpenStreamCount()).isEqualTo(1);
    assertThat(content2.getOpenStreamCount()).isEqualTo(1);
  }

  private static ModuleEntry createEntry(ZipPath path, byte[] content) throws Exception {
    return ModuleEntry.builder().setPath(path).setContent(ByteSource.wrap(content)).build();
  }

  private static class CountingByteSource extends ByteSource {
    private final byte[] content;
    private int count;

    CountingByteSource(byte[] content) {
      this.content = content;
    }

    int getOpenStreamCount() {
      return count;
    }

    @Override
    public InputStream openStream() throws IOException {
      count++;
      return new ByteArrayInputStream(content);
    }
  }
}
