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

package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.testing.truth.zip.TruthZip.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.io.ZipBuilder.EntryOption;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.protobuf.Int32Value;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ZipBuilder}. */
@RunWith(JUnit4.class)
public class ZipBuilderTest {

  private static final byte[] DUMMY_CONTENT = new byte[1];

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  private Path tmpDir;

  private File file;

  @Before
  public void setUp() throws Exception {
    tmpDir = tmp.getRoot().toPath();
    file = new File(tmp.getRoot(), "final.zip");
  }

  @Test
  public void preservesInsertionOrder_ascending() throws Exception {
    new ZipBuilder()
        .addFileWithContent(ZipPath.create("1"), DUMMY_CONTENT)
        .addFileWithContent(ZipPath.create("2"), DUMMY_CONTENT)
        .addFileWithContent(ZipPath.create("3"), DUMMY_CONTENT)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).containsExactlyEntries("1", "2", "3").inOrder();
  }

  @Test
  public void preservesInsertionOrder_descending() throws Exception {
    new ZipBuilder()
        .addFileWithContent(ZipPath.create("3"), DUMMY_CONTENT)
        .addFileWithContent(ZipPath.create("2"), DUMMY_CONTENT)
        .addFileWithContent(ZipPath.create("1"), DUMMY_CONTENT)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).containsExactlyEntries("3", "2", "1").inOrder();
  }

  @Test
  public void treatsDirectoriesCorrectly() throws Exception {
    new ZipBuilder()
        .addFileWithContent(ZipPath.create("file"), DUMMY_CONTENT)
        .addDirectory(ZipPath.create("dir"))
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).hasFile("file");
    assertThat(zipFile).hasDirectory("dir/");
  }

  @Test
  public void addFileWithContent_respectsOptions() throws Exception {
    new ZipBuilder()
        .addFileWithContent(ZipPath.create("without"), DUMMY_CONTENT)
        .addFileWithContent(ZipPath.create("with"), DUMMY_CONTENT, EntryOption.UNCOMPRESSED)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).hasFile("without").thatIsCompressed();
    assertThat(zipFile).hasFile("with").thatIsUncompressed();
  }

  @Test
  public void addFileFromDisk_respectsOptions() throws Exception {
    File fromFile = tmp.newFile();
    Files.write(fromFile.toPath(), DUMMY_CONTENT);

    new ZipBuilder()
        .addFileFromDisk(ZipPath.create("without"), fromFile)
        .addFileFromDisk(ZipPath.create("with"), fromFile, EntryOption.UNCOMPRESSED)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).hasFile("without").thatIsCompressed();
    assertThat(zipFile).hasFile("with").thatIsUncompressed();
  }

  @Test
  public void addFileWithProtoContent_respectsOptions() throws Exception {
    Int32Value proto = Int32Value.getDefaultInstance();
    new ZipBuilder()
        .addFileWithProtoContent(ZipPath.create("without"), proto)
        .addFileWithProtoContent(ZipPath.create("with"), proto, EntryOption.UNCOMPRESSED)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).hasFile("without").thatIsCompressed();
    assertThat(zipFile).hasFile("with").thatIsUncompressed();
  }

  @Test
  public void addFileFromZip_respectsOptions() throws Exception {
    Path fromFile = tmp.newFile().toPath();
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(fromFile))) {
      zos.putNextEntry(new ZipEntry("without"));
      zos.putNextEntry(new ZipEntry("with"));
    }
    ZipFile fromZipFile = new ZipFile(fromFile.toFile());

    new ZipBuilder()
        .addFileFromZip(ZipPath.create("without"), fromZipFile, fromZipFile.getEntry("without"))
        .addFileFromZip(
            ZipPath.create("with"),
            fromZipFile,
            fromZipFile.getEntry("with"),
            EntryOption.UNCOMPRESSED)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).hasFile("without").thatIsCompressed();
    assertThat(zipFile).hasFile("with").thatIsUncompressed();
  }

  @Test
  public void addFile_respectsOptions() throws Exception {
    new ZipBuilder()
        .addFile(ZipPath.create("without"), () -> new ByteArrayInputStream(DUMMY_CONTENT))
        .addFile(
            ZipPath.create("with"),
            () -> new ByteArrayInputStream(DUMMY_CONTENT),
            EntryOption.UNCOMPRESSED)
        .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(file);
    assertThat(zipFile).hasFile("without").thatIsCompressed();
    assertThat(zipFile).hasFile("with").thatIsUncompressed();
  }

  @Test
  public void addEntry_asBytes() throws Exception {
    Path zipPath =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("in-root.txt"), "hello".getBytes(UTF_8))
            .addFileWithContent(ZipPath.create("a/b/nested.txt"), "world".getBytes(UTF_8))
            .writeTo(tmpDir.resolve("output.zip"));

    ZipFile zipFile = new ZipFile(zipPath.toFile());
    assertThat(zipFile).hasFile("in-root.txt").withContent("hello".getBytes(UTF_8));
    assertThat(zipFile).hasFile("a/b/nested.txt").withContent("world".getBytes(UTF_8));
  }

  @Test
  public void addEntry_asFile() throws Exception {
    File fromFile = tmp.newFile();
    Files.write(fromFile.toPath(), "contents".getBytes(UTF_8));

    Path zipPath =
        new ZipBuilder()
            .addFileFromDisk(ZipPath.create("file.txt"), fromFile)
            .writeTo(file.toPath());

    ZipFile zipFile = new ZipFile(zipPath.toFile());
    assertThat(zipFile).hasFile("file.txt").withContent("contents".getBytes(UTF_8));
  }

  @Test
  public void addEntry_asFilePointingToDirectory_throws() throws Exception {
    File directory = tmp.newFolder();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ZipBuilder().addFileFromDisk(ZipPath.create("file.txt"), directory));

    assertThat(exception).hasMessageThat().contains("does not denote a file");
  }

  @Test
  public void addEntry_asProto() throws Exception {
    Int32Value someProto = Int32Value.newBuilder().setValue(42).build();

    Path zipPath =
        new ZipBuilder()
            .addFileWithProtoContent(ZipPath.create("sample.pb"), someProto)
            .writeTo(tmpDir.resolve("output.zip"));

    ZipFile zipFile = new ZipFile(zipPath.toFile());
    assertThat(zipFile).hasFile("sample.pb").withContent(someProto.toByteArray());
  }

  @Test
  public void addEntry_alreadyExistsAsDirectory_throws() throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder().addDirectory(ZipPath.create("duplicate/dir/"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                zipBuilder.addFileWithContent(
                    ZipPath.create("duplicate/dir"), "let's write as file".getBytes(UTF_8)));

    assertThat(exception).hasMessageThat().contains("Path 'duplicate/dir' is already taken");
  }

  @Test
  public void addEntry_alreadyExistsAsFile_throws() throws Exception {
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("duplicate/file.txt"), "hello".getBytes(UTF_8));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                zipBuilder.addFileWithContent(
                    ZipPath.create("duplicate/file.txt"), "let's write again".getBytes(UTF_8)));

    assertThat(exception).hasMessageThat().contains("Path 'duplicate/file.txt' is already taken");
  }

  @Test
  public void addDirectory_alreadyExistsAsDirectory_throws() throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder().addDirectory(ZipPath.create("duplicate/dir/"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> zipBuilder.addDirectory(ZipPath.create("duplicate/dir/")));

    assertThat(exception).hasMessageThat().contains("Path 'duplicate/dir' is already taken");
  }

  @Test
  public void addDirectory_alreadyExistsAsFile_throws() throws Exception {
    ZipBuilder zipBuilder =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("duplicate/file"), "hello".getBytes(UTF_8));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> zipBuilder.addDirectory(ZipPath.create("duplicate/file/")));

    assertThat(exception).hasMessageThat().contains("Path 'duplicate/file' is already taken");
  }

  @Test
  public void copyAllContentsFromZip_toRoot_succeeds() throws Exception {
    Path zipToCopy =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("a/b/c.txt"), "d".getBytes(UTF_8))
            .addDirectory(ZipPath.create("dir"))
            .writeTo(tmpDir.resolve("zip-to-copy.zip"));

    Path finalZip =
        new ZipBuilder()
            .copyAllContentsFromZip(ZipPath.create(""), new ZipFile(zipToCopy.toFile()))
            .writeTo(tmpDir.resolve("final.zip"));

    ZipFile finalZipFile = new ZipFile(finalZip.toFile());
    assertThat(finalZipFile).hasFile("a/b/c.txt").withContent("d".getBytes(UTF_8));
    assertThat(finalZipFile).doesNotHaveDirectory("dir");
  }

  @Test
  public void copyAllContentsFromZip_toSubDirectory_succeeds() throws Exception {
    Path zipToCopy =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("c/d/e.txt"), "f".getBytes(UTF_8))
            .addDirectory(ZipPath.create("dir1/dir2"))
            .writeTo(tmpDir.resolve("zip-to-copy.zip"));

    Path finalZip =
        new ZipBuilder()
            .copyAllContentsFromZip(ZipPath.create("a/b"), new ZipFile(zipToCopy.toFile()))
            .writeTo(tmpDir.resolve("final.zip"));

    ZipFile finalZipFile = new ZipFile(finalZip.toFile());
    assertThat(finalZipFile).hasFile("a/b/c/d/e.txt").withContent("f".getBytes(UTF_8));
    assertThat(finalZipFile).doesNotHaveDirectory("a/b/dir1/dir2");
  }

  @Test
  public void copyAllContentsFromZip_destinationDirectoryExistWithoutConflict_succeeds()
      throws Exception {
    Path zipToCopy =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("b/x.txt"), "x".getBytes(UTF_8))
            .writeTo(tmpDir.resolve("zip-to-copy.zip"));

    Path finalZip =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("a/b/y.txt"), "y".getBytes(UTF_8))
            .copyAllContentsFromZip(ZipPath.create("a"), new ZipFile(zipToCopy.toFile()))
            .writeTo(tmpDir.resolve("final.zip"));

    ZipFile finalZipFile = new ZipFile(finalZip.toFile());
    assertThat(finalZipFile).hasFile("a/b/x.txt").withContent("x".getBytes(UTF_8));
    assertThat(finalZipFile).hasFile("a/b/y.txt").withContent("y".getBytes(UTF_8));
  }

  @Test
  public void copyAllContentsFromZip_destinationDirectoryExistWithConflict_throws()
      throws Exception {
    Path zipToCopy =
        new ZipBuilder()
            .addFileWithContent(ZipPath.create("b/x.txt"), "".getBytes(UTF_8))
            .writeTo(tmpDir.resolve("zip-to-copy.zip"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ZipBuilder()
                    .addFileWithContent(ZipPath.create("a/b/x.txt"), "".getBytes(UTF_8))
                    .copyAllContentsFromZip(ZipPath.create("a"), new ZipFile(zipToCopy.toFile())));

    assertThat(exception).hasMessageThat().contains("Path 'a/b/x.txt' is already taken.");
  }

  @Test
  public void uncompressedFile() throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder();
    zipBuilder.addFileWithContent(ZipPath.create("compressed-before"), "1111".getBytes(UTF_8));
    // Wedge the uncompressed file between compressed files (ZipBuilder preserves insertion order).
    zipBuilder.addFileWithContent(
        ZipPath.create("uncompressed"), "2222".getBytes(UTF_8), EntryOption.UNCOMPRESSED);
    zipBuilder.addFileWithContent(ZipPath.create("compressed-after"), "3333".getBytes(UTF_8));
    Path path = zipBuilder.writeTo(tmpDir.resolve("result.zip"));

    ZipFile zipFile = new ZipFile(path.toFile());
    assertThat(zipFile)
        .hasFile("compressed-before")
        .withContent("1111".getBytes(UTF_8))
        .thatIsCompressed();
    assertThat(zipFile)
        .hasFile("uncompressed")
        .withContent("2222".getBytes(UTF_8))
        .thatIsUncompressed();
    assertThat(zipFile)
        .hasFile("compressed-after")
        .withContent("3333".getBytes(UTF_8))
        .thatIsCompressed();
  }

  @Test
  public void writeTo_targetAlreadyExists_throws() throws Exception {
    Path existingFile = tmp.newFile("existing-file.zip").toPath();

    assertThrows(FileAlreadyExistsException.class, () -> new ZipBuilder().writeTo(existingFile));
  }

  @Test
  public void writeTo_invokedTwice_succeeds() throws Exception {
    ZipBuilder zipBuilder = new ZipBuilder();

    zipBuilder.addFileWithContent(ZipPath.create("common"), "1,2".getBytes(UTF_8));
    Path path1 = zipBuilder.writeTo(tmpDir.resolve("output-1.zip"));
    zipBuilder.addFileWithContent(ZipPath.create("only-in-2"), "2".getBytes(UTF_8));
    Path path2 = zipBuilder.writeTo(tmpDir.resolve("output-2.zip"));

    ZipFile zipFile1 = new ZipFile(path1.toFile());
    ZipFile zipFile2 = new ZipFile(path2.toFile());
    assertThat(zipFile1).hasFile("common").withContent("1,2".getBytes(UTF_8));
    assertThat(zipFile2).hasFile("common").withContent("1,2".getBytes(UTF_8));
    assertThat(zipFile1).doesNotHaveFile("only-in-2");
    assertThat(zipFile2).hasFile("only-in-2").withContent("2".getBytes(UTF_8));
  }
}
