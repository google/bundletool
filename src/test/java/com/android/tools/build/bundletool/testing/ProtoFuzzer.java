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

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/** A helper class to generate random protocol buffer messages for tests. */
public final class ProtoFuzzer {

  private static final Random RAND = new Random();
  private static final int REPEATED_FIELD_LENGTH = 10;

  /** Returns a new proto message with repeated fields randomly shuffled. */
  @SuppressWarnings("unchecked") // Safe by contract of the `build` method of a proto Builder.
  public static <T extends Message> T shuffleRepeatedFields(T message) {
    Message.Builder shuffled = message.toBuilder();
    shuffleRepeatedFields(shuffled);
    return (T) shuffled.build();
  }

  private static void shuffleRepeatedFields(Message.Builder shuffled) {
    for (FieldDescriptor field : shuffled.getAllFields().keySet()) {
      // Shuffle all contained proto messages recursively.
      if (field.getJavaType() == JavaType.MESSAGE) {
        if (field.isRepeated()) {
          IntStream.range(0, shuffled.getRepeatedFieldCount(field))
              .forEach(i -> shuffleRepeatedFields(shuffled.getRepeatedFieldBuilder(field, i)));
        } else {
          shuffleRepeatedFields(shuffled.getFieldBuilder(field));
        }
      }
      // Shuffle values of the field itself.
      if (field.isRepeated()) {
        int len = shuffled.getRepeatedFieldCount(field);
        for (int i = 0; i < len - 1; i++) {
          swapRepeatedFieldValues(shuffled, field, i, i + RAND.nextInt(len - i));
        }
      }
    }
  }

  private static void swapRepeatedFieldValues(
      Message.Builder mutableMsg, FieldDescriptor field, int idx1, int idx2) {
    Object value1 = mutableMsg.getRepeatedField(field, idx1);
    Object value2 = mutableMsg.getRepeatedField(field, idx2);
    mutableMsg.setRepeatedField(field, idx1, value2);
    mutableMsg.setRepeatedField(field, idx2, value1);
  }

  /** Generates a proto message of the given class with randomly populated fields. */
  @SuppressWarnings("unchecked") // Safe by contract of the `build` method of a proto Builder.
  public static <T extends Message> T randomProtoMessage(Class<T> messageClazz) {
    T prototype = ProtoReflection.getDefaultInstance(messageClazz);

    Message.Builder fuzzed = prototype.toBuilder();
    for (FieldDescriptor field : prototype.getDescriptorForType().getFields()) {
      if (field.isRepeated()) {
        fuzzed.clearField(field);
        IntStream.range(0, REPEATED_FIELD_LENGTH)
            .forEach(i -> fuzzed.addRepeatedField(field, fuzzField(field, prototype)));
      } else {
        fuzzed.setField(field, fuzzField(field, prototype));
      }
    }

    return (T) fuzzed.build();
  }

  private static <T extends Message> Object fuzzField(FieldDescriptor field, T containingMessage) {
    switch (field.getType().getJavaType()) {
      case BOOLEAN:
        return RAND.nextBoolean();
      case BYTE_STRING:
        return randomByteString();
      case DOUBLE:
        return RAND.nextDouble();
      case ENUM:
        return randomEnum(field.getEnumType());
      case FLOAT:
        return RAND.nextFloat();
      case INT:
        return RAND.nextInt();
      case LONG:
        return RAND.nextLong();
      case STRING:
        return randomString();
      case MESSAGE:
        return randomProtoMessage(
            ProtoReflection.getJavaClassOfMessageField(containingMessage, field));
    }
    throw new RuntimeException("Unhandled field type: " + field.getType());
  }

  private static ByteString randomByteString() {
    byte[] bytes = new byte[10];
    RAND.nextBytes(bytes);
    return ByteString.copyFrom(bytes);
  }

  private static Object randomEnum(EnumDescriptor enumDescriptor) {
    List<EnumValueDescriptor> enumConstants = enumDescriptor.getValues();
    return enumConstants.get(RAND.nextInt(enumConstants.size()));
  }

  private static String randomString() {
    return Base64.getEncoder().encodeToString(randomByteString().toByteArray());
  }

  private ProtoFuzzer() {}
}
