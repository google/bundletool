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

import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;

/**
 * A repeatable action returning fresh instances of {@link InputStream}.
 *
 * <p>To be used to access various types of data, such as files on disk, zip file entries or raw
 * byte data.
 *
 * <p>All subclasses must be immutable, and return the same {@link InputStream} at each invocation.
 */
@Immutable
public interface InputStreamSupplier {
  @MustBeClosed
  InputStream get() throws IOException;
}
