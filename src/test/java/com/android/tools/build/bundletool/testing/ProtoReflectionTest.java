/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoReflectionTest {

  @Test
  public void getDefaultInstance() {
    assertThat(ProtoReflection.getDefaultInstance(ApkTargeting.class))
        .isEqualTo(ApkTargeting.getDefaultInstance());
  }

  @Test
  public void getJavaClassOfMessageField_forSimpleMessageField() {
    Message proto = ApkTargeting.getDefaultInstance();
    FieldDescriptor field = proto.getDescriptorForType().findFieldByName("abi_targeting");

    assertThat(ProtoReflection.getJavaClassOfMessageField(proto, field))
        .isEqualTo(AbiTargeting.class);
  }

  @Test
  public void getJavaClassOfMessageField_forRepeatedMessageField() {
    Message proto = AbiTargeting.getDefaultInstance();
    FieldDescriptor field = proto.getDescriptorForType().findFieldByName("value");

    assertThat(ProtoReflection.getJavaClassOfMessageField(proto, field)).isEqualTo(Abi.class);
  }

  @Test
  public void getJavaClassOfMessageField_forNonMessageField_throws() {
    Message proto = Abi.getDefaultInstance();
    FieldDescriptor field = proto.getDescriptorForType().findFieldByName("alias");

    assertThrows(
        IllegalArgumentException.class,
        () -> ProtoReflection.getJavaClassOfMessageField(proto, field));
  }
}
