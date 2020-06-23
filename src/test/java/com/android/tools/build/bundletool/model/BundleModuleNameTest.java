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

import static com.android.tools.build.bundletool.model.BundleModuleName.isValid;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BundleModuleNameTest {

  @Test
  public void testEmptyName_invalid() throws Exception {
    assertThat(isValid("")).isFalse();
  }

  @Test
  public void testNonAlphaNumericChar_invalid() throws Exception {
    assertThat(isValid("@")).isFalse();
  }

  @Test
  public void testSingleAlphaChar_valid() throws Exception {
    assertThat(isValid("a")).isTrue();
  }

  @Test
  public void testSingleAlphaUppercaseChar_valid() throws Exception {
    assertThat(isValid("A")).isTrue();
  }

  @Test
  public void testSingleDigit_invalid() throws Exception {
    assertThat(isValid("1")).isFalse();
  }

  @Test
  public void testSingleDash_invalid() throws Exception {
    assertThat(isValid("-")).isFalse();
  }

  @Test
  public void testSingleUnderscore_invalid() throws Exception {
    assertThat(isValid("_")).isFalse();
  }

  @Test
  public void testSlashChar_invalid() throws Exception {
    assertThat(isValid("abc/def")).isFalse();
  }

  @Test
  public void testSingleAlphaAndDigit_valid() throws Exception {
    assertThat(isValid("a1")).isTrue();
  }

  @Test
  public void testUpperCase_valid() throws Exception {
    assertThat(isValid("ABCDE")).isTrue();
  }

  @Test
  public void testModuleNameWithDash_invalid() throws Exception {
    assertThat(isValid("hello-world")).isFalse();
  }

  @Test
  public void testModuleNameWithUnderscore_valid() throws Exception {
    assertThat(isValid("hello_world")).isTrue();
  }

  @Test
  public void testModuleNameStartingWithUnderscore_invalid() throws Exception {
    assertThat(isValid("_hello")).isFalse();
  }

  @Test
  public void testModuleNameStartingWithDash_invalid() throws Exception {
    assertThat(isValid("-hello")).isFalse();
  }

  @Test
  public void testModuleNameStartingWithDigit_invalid() throws Exception {
    assertThat(isValid("3hello")).isFalse();
  }

  @Test
  public void testThrowsWhenInvalid() throws Exception {
    assertThrows(InvalidBundleException.class, () -> BundleModuleName.create("!!invalid!!"));
  }

  @Test
  public void testObjectAttributesCorrectlySetWhenValid() throws Exception {
    BundleModuleName name = BundleModuleName.create("validName");
    assertThat(name.toString()).isEqualTo("validName");
  }
}
