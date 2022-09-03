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

package com.android.tools.build.bundletool.validation;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.common.reflect.Reflection;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This test validates properties across multiple validator classes and is NOT a unit test for a
 * particular class.
 */
@RunWith(JUnit4.class)
public class ValidatorsTest {

  @Test
  public void eachSubValidatorIsRegistered() throws Exception {
    // Load sub-classes of SubValidator that live within the same package as top-level classes.
    ImmutableSet<Class<?>> existingSubValidators =
        ClassPath.from(SubValidator.class.getClassLoader())
            .getTopLevelClasses(Reflection.getPackageName(SubValidator.class))
            .stream()
            .map(ClassInfo::load)
            .filter(clazz -> isConcreteSubValidator(clazz))
            .collect(toImmutableSet());

    ImmutableSet<Class<?>> registeredSubValidators =
        ImmutableSet.<Class<?>>builder()
            .addAll(toClasses(AppBundleValidator.DEFAULT_BUNDLE_FILE_SUB_VALIDATORS))
            .addAll(toClasses(AppBundleValidator.DEFAULT_BUNDLE_SUB_VALIDATORS))
            .addAll(toClasses(BundleModulesValidator.MODULE_FILE_SUB_VALIDATORS))
            .addAll(toClasses(BundleModulesValidator.MODULES_SUB_VALIDATORS))
            .addAll(toClasses(SdkBundleValidator.DEFAULT_BUNDLE_FILE_SUB_VALIDATORS))
            .addAll(toClasses(SdkBundleValidator.DEFAULT_BUNDLE_SUB_VALIDATORS))
            .addAll(toClasses(SdkModulesFileValidator.DEFAULT_MODULES_FILE_SUB_VALIDATORS))
            .build();

    assertThat(existingSubValidators).containsExactlyElementsIn(registeredSubValidators);
  }

  @Test
  public void sameOrderOfCommonSubValidators() throws Exception {
    assertSameOrderOfCommonClasses(
        AppBundleValidator.DEFAULT_BUNDLE_FILE_SUB_VALIDATORS,
        BundleModulesValidator.MODULE_FILE_SUB_VALIDATORS);

    assertSameOrderOfCommonClasses(
        AppBundleValidator.DEFAULT_BUNDLE_SUB_VALIDATORS,
        BundleModulesValidator.MODULES_SUB_VALIDATORS);
  }

  private static void assertSameOrderOfCommonClasses(
      ImmutableList<SubValidator> aValidators, ImmutableList<SubValidator> bValidators) {

    List<String> aClasses = Lists.transform(aValidators, obj -> obj.getClass().getCanonicalName());
    List<String> bClasses = Lists.transform(bValidators, obj -> obj.getClass().getCanonicalName());

    Set<String> commonClasses =
        Sets.intersection(ImmutableSet.copyOf(aClasses), ImmutableSet.copyOf(bClasses));

    Iterable<String> aCommonClasses = Iterables.filter(aClasses, commonClasses::contains);
    Iterable<String> bCommonClasses = Iterables.filter(bClasses, commonClasses::contains);

    assertThat(aCommonClasses).containsExactlyElementsIn(bCommonClasses).inOrder();
  }

  private static boolean isConcreteSubValidator(Class<?> clazz) {
    return !Modifier.isAbstract(clazz.getModifiers()) && SubValidator.class.isAssignableFrom(clazz);
  }

  private static ImmutableList<Class<?>> toClasses(ImmutableList<?> objects) {
    return objects.stream().map(Object::getClass).collect(toImmutableList());
  }
}
