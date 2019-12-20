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
package com.android.tools.build.bundletool.model;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ZipPathTest {

  @Test
  public void testCreate_root() {
    ZipPath empty = ZipPath.create("");
    ZipPath slash = ZipPath.create("/");

    assertThat((Object) empty).isEqualTo(slash);
  }

  @Test
  public void testForbiddenNames_throws() {
    assertThrows(IllegalArgumentException.class, () -> ZipPath.create(".."));
    assertThrows(IllegalArgumentException.class, () -> ZipPath.create("a/b/.."));
    assertThrows(IllegalArgumentException.class, () -> ZipPath.create("."));
    assertThrows(IllegalArgumentException.class, () -> ZipPath.create("a/./b"));
  }

  @Test
  public void testEquals() {
    ZipPath path = ZipPath.create("foo/bar");

    assertThat((Object) path).isEqualTo(ZipPath.create("foo/bar"));
    assertThat((Object) path).isNotEqualTo(ZipPath.create("foo"));
    assertThat((Object) path).isNotEqualTo(ZipPath.create("foo/bar2"));
    assertThat((Object) path).isNotEqualTo(ZipPath.create("foo/bar/hello"));
  }

  @Test
  public void testCreate_normalized() {
    ZipPath path1 = ZipPath.create("foo/bar");
    ZipPath path2 = ZipPath.create("/foo/bar");
    ZipPath path3 = ZipPath.create("foo/bar/");
    ZipPath path4 = ZipPath.create("foo//bar");

    assertThat((Object) path1).isEqualTo(path2);
    assertThat((Object) path1).isEqualTo(path3);
    assertThat((Object) path1).isEqualTo(path4);

    ZipPath path5 = ZipPath.create("bar/foo");
    assertThat((Object) path1).isNotEqualTo(path5);
  }

  @Test
  public void testCreate_fromImmutableList() {
    ZipPath path1 = ZipPath.create("foo/bar");
    ZipPath path2 = ZipPath.create(ImmutableList.of("foo", "bar"));
    assertThat((Object) path1).isEqualTo(path2);
  }

  @Test
  public void testCreate_throwsIfInvalidImmutableList() {
    assertThrows(
        IllegalArgumentException.class, () -> ZipPath.create(ImmutableList.of("foo", "", "bar")));
    assertThrows(
        IllegalArgumentException.class, () -> ZipPath.create(ImmutableList.of("foo", "/", "bar")));
  }

  @Test
  public void testResolve_fromEmptyPath() {
    assertThat((Object) ZipPath.create("").resolve("foo")).isEqualTo(ZipPath.create("foo"));
    assertThat((Object) ZipPath.create("").resolve("foo/bar")).isEqualTo(ZipPath.create("foo/bar"));
  }

  @Test
  public void testResolve_fromString() {
    assertThat((Object) ZipPath.create("foo/bar/hello").resolve(""))
        .isEqualTo(ZipPath.create("foo/bar/hello"));
    assertThat((Object) ZipPath.create("foo/bar").resolve("hello"))
        .isEqualTo(ZipPath.create("foo/bar/hello"));
    assertThat((Object) ZipPath.create("foo").resolve("bar/hello"))
        .isEqualTo(ZipPath.create("foo/bar/hello"));
  }

  @Test
  public void testResolve_fromPath() {
    assertThat((Object) ZipPath.create("foo/bar/hello").resolve(ZipPath.create("")))
        .isEqualTo(ZipPath.create("foo/bar/hello"));
    assertThat((Object) ZipPath.create("foo/bar").resolve(ZipPath.create("hello")))
        .isEqualTo(ZipPath.create("foo/bar/hello"));
    assertThat((Object) ZipPath.create("foo").resolve(ZipPath.create("bar/hello")))
        .isEqualTo(ZipPath.create("foo/bar/hello"));
  }

  @Test
  public void testResolveNull_throws() {
    ZipPath path = ZipPath.create("foo");
    assertThrows(NullPointerException.class, () -> path.resolve((String) null));
    assertThrows(NullPointerException.class, () -> path.resolve((ZipPath) null));
  }

  @Test
  public void testResolveForbiddenNames_throws() {
    ZipPath path = ZipPath.create("foo");
    assertThrows(IllegalArgumentException.class, () -> path.resolve(".."));
    assertThrows(IllegalArgumentException.class, () -> path.resolve("."));
    assertThrows(IllegalArgumentException.class, () -> path.resolve("a/.."));
    assertThrows(IllegalArgumentException.class, () -> path.resolve("a/."));
  }

  @Test
  public void testResolveSibling() {
    ZipPath path = ZipPath.create("foo/bar");
    ZipPath newPath = path.resolveSibling("hello");
    assertThat((Object) newPath).isEqualTo(ZipPath.create("foo/hello"));
  }

  @Test
  public void testResolveSiblingNull_throws() {
    ZipPath path = ZipPath.create("foo/bar");
    assertThrows(NullPointerException.class, () -> path.resolveSibling((ZipPath) null));
    assertThrows(NullPointerException.class, () -> path.resolveSibling((String) null));
  }

  @Test
  public void testResolveSiblingFromRoot_throws() {
    ZipPath root = ZipPath.create("");
    assertThrows(IllegalStateException.class, () -> root.resolveSibling("foo"));
    assertThrows(IllegalStateException.class, () -> root.resolveSibling(ZipPath.create("foo")));
  }

  @Test
  public void testSubpath() {
    ZipPath path = ZipPath.create("foo/bar");
    assertThat((Object) path.subpath(0, 1)).isEqualTo(ZipPath.create("foo"));
    assertThat((Object) path.subpath(1, 2)).isEqualTo(ZipPath.create("bar"));
    assertThat((Object) path.subpath(0, 2)).isEqualTo(ZipPath.create("foo/bar"));
  }

  @Test
  public void testSubpathFromRoot_Throws() {
    ZipPath root = ZipPath.create("");
    assertThrows(IllegalArgumentException.class, () -> root.subpath(0, 0));
    assertThrows(IllegalArgumentException.class, () -> root.subpath(0, 1));
  }

  @Test
  public void testSubpath_incorrectInputs_throws() {
    ZipPath path = ZipPath.create("foo/bar");
    assertThrows(IllegalArgumentException.class, () -> path.subpath(0, 3));
    assertThrows(IllegalArgumentException.class, () -> path.subpath(-1, 2));
    assertThrows(IllegalArgumentException.class, () -> path.subpath(2, 1));
  }

  @Test
  public void testGetParent() {
    ZipPath path = ZipPath.create("foo/bar");
    assertThat((Object) path.getParent()).isEqualTo(ZipPath.create("foo"));
    assertThat((Object) path.getParent().getParent()).isEqualTo(ZipPath.create(""));
  }

  @Test
  public void testGetParentFromRoot_returnsNull() {
    ZipPath root = ZipPath.create("");
    assertThat((Object) root.getParent()).isNull();
  }

  @Test
  public void testGetNameCount() {
    assertThat(ZipPath.create("").getNameCount()).isEqualTo(0);
    assertThat(ZipPath.create("foo").getNameCount()).isEqualTo(1);
    assertThat(ZipPath.create("foo/bar").getNameCount()).isEqualTo(2);
  }

  @Test
  public void testGetNameFromRoot_throws() {
    ZipPath root = ZipPath.create("");
    assertThrows(IllegalArgumentException.class, () -> root.getName(0));
  }

  @Test
  public void testGetName() {
    ZipPath path = ZipPath.create("foo/bar");
    assertThat((Object) path.getName(0)).isEqualTo(ZipPath.create("foo"));
    assertThat((Object) path.getName(1)).isEqualTo(ZipPath.create("bar"));
  }

  @Test
  public void testGetName_outOfBounds() {
    ZipPath path = ZipPath.create("foo/bar");
    assertThrows(IllegalArgumentException.class, () -> path.getName(2));
    assertThrows(IllegalArgumentException.class, () -> path.getName(-1));
  }

  @Test
  public void testStartsWith_everythingStartsWithRoot() {
    ZipPath root = ZipPath.create("");
    assertThat(ZipPath.create("").startsWith(root)).isTrue();
    assertThat(ZipPath.create("foo").startsWith(root)).isTrue();
    assertThat(ZipPath.create("foo/bar").startsWith(root)).isTrue();
  }

  @Test
  public void testStartsWith() {
    ZipPath path = ZipPath.create("foo/bar");

    // Positive results.
    assertThat(path.startsWith(ZipPath.create(""))).isTrue();
    assertThat(path.startsWith(ZipPath.create("foo"))).isTrue();
    assertThat(path.startsWith(ZipPath.create("foo/bar"))).isTrue();

    // Negative results.
    assertThat(path.startsWith(ZipPath.create("fo"))).isFalse();
    assertThat(path.startsWith(ZipPath.create("foo2"))).isFalse();
    assertThat(path.startsWith(ZipPath.create("foo/ba"))).isFalse();
    assertThat(path.startsWith(ZipPath.create("foo/bar2"))).isFalse();
    assertThat(path.startsWith(ZipPath.create("foo/bar/hello"))).isFalse();
  }

  @Test
  public void testEndsWith_everythingEndsWithRoot() {
    ZipPath root = ZipPath.create("");
    assertThat(ZipPath.create("").endsWith(root)).isTrue();
    assertThat(ZipPath.create("foo").endsWith(root)).isTrue();
    assertThat(ZipPath.create("foo/bar").endsWith(root)).isTrue();
  }

  @Test
  public void testEndsWith() {
    ZipPath path = ZipPath.create("foo/bar");

    // Positive results.
    assertThat(path.endsWith(ZipPath.create(""))).isTrue();
    assertThat(path.endsWith(ZipPath.create("bar"))).isTrue();
    assertThat(path.endsWith(ZipPath.create("foo/bar"))).isTrue();

    // Negative results.
    assertThat(path.endsWith(ZipPath.create("ar"))).isFalse();
    assertThat(path.endsWith(ZipPath.create("abar"))).isFalse();
    assertThat(path.endsWith(ZipPath.create("oo/bar"))).isFalse();
    assertThat(path.endsWith(ZipPath.create("afoo/bar"))).isFalse();
    assertThat(path.endsWith(ZipPath.create("a/foo/bar"))).isFalse();
  }

  @Test
  public void testHashCode_equalPaths() {
    ZipPath path1 = ZipPath.create("foo/bar");
    ZipPath path2 = ZipPath.create("foo//bar");
    assertThat((Object) path1).isEqualTo(path2);
    assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
  }

  @Test
  public void testHashCode_differentPaths() {
    ZipPath path1 = ZipPath.create("foo/bar");
    ZipPath path2 = ZipPath.create("foo/");
    ZipPath path3 = ZipPath.create("foo/bar2");
    assertThat(path1.hashCode()).isNotEqualTo(path2.hashCode());
    assertThat(path1.hashCode()).isNotEqualTo(path3.hashCode());
    assertThat(path2.hashCode()).isNotEqualTo(path3.hashCode());
  }

  @Test
  public void testToString() {
    assertThat(ZipPath.create("").toString()).isEmpty();
    assertThat(ZipPath.create("/").toString()).isEmpty();
    assertThat(ZipPath.create("foo/bar").toString()).isEqualTo("foo/bar");
    assertThat(ZipPath.create("/foo//bar/").toString()).isEqualTo("foo/bar");
  }

  @Test
  public void testComparator() {
    ImmutableList<ZipPath> paths =
        ImmutableList.of(
            ZipPath.create("foo"),
            ZipPath.create("foo/hello"),
            ZipPath.create("foo/hello2"),
            ZipPath.create("bar"),
            ZipPath.create(""));

    assertThat(ImmutableList.sortedCopyOf(paths))
        .containsExactly(
            ZipPath.create(""),
            ZipPath.create("bar"),
            ZipPath.create("foo"),
            ZipPath.create("foo/hello"),
            ZipPath.create("foo/hello2"))
        .inOrder();

    assertThat(ZipPath.create("foo/bar").compareTo(ZipPath.create("/foo//bar/"))).isEqualTo(0);
  }

  @Test
  public void testGetFileName() {
    assertThat((Object) ZipPath.create("foo").getFileName()).isEqualTo(ZipPath.create("foo"));
    assertThat((Object) ZipPath.create("foo/").getFileName()).isEqualTo(ZipPath.create("foo"));
    assertThat((Object) ZipPath.create("foo/bar").getFileName()).isEqualTo(ZipPath.create("bar"));
    assertThat((Object) ZipPath.create("foo/bar/").getFileName()).isEqualTo(ZipPath.create("bar"));
    assertThat((Object) ZipPath.create("foo/bar/test").getFileName())
        .isEqualTo(ZipPath.create("test"));
  }

  @Test
  public void testGetFileNameForRoot_throws() {
    assertThrows(IllegalArgumentException.class, () -> ZipPath.create("").getFileName());
    assertThrows(IllegalArgumentException.class, () -> ZipPath.create("/").getFileName());
  }
}
