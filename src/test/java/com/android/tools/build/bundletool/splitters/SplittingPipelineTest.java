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

package com.android.tools.build.bundletool.splitters;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link SplittingPipeline}. */
@RunWith(JUnit4.class)
public class SplittingPipelineTest {

  public static class TrivialSplitter implements ModuleSplitSplitter {
    public int splitCallCount = 0;

    @Override
    public ImmutableCollection<ModuleSplit> split(ModuleSplit split) {
      splitCallCount++;
      return ImmutableList.of(split);
    }
  }

  public static class DoublingSplitter implements ModuleSplitSplitter {
    public int splitCallCount = 0;

    @Override
    public ImmutableCollection<ModuleSplit> split(ModuleSplit split) {
      splitCallCount++;
      return ImmutableList.of(split, split);
    }
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Mock ModuleSplit baseSplit;

  @Test
  public void testAllSplittersCalled() {
    TrivialSplitter splitter = new TrivialSplitter();
    TrivialSplitter splitter2 = new TrivialSplitter();

    SplittingPipeline pipeline = new SplittingPipeline(ImmutableList.of(splitter, splitter2));
    ImmutableCollection<ModuleSplit> splits = pipeline.split(baseSplit);

    assertThat(splitter.splitCallCount).isEqualTo(1);
    assertThat(splitter2.splitCallCount).isEqualTo(1);
    assertThat(splits).containsExactly(baseSplit);
  }

  @Test
  public void testSplittersCalledInRightOrder() {
    DoublingSplitter splitter = new DoublingSplitter();
    DoublingSplitter splitter2 = new DoublingSplitter();

    SplittingPipeline pipeline = new SplittingPipeline(ImmutableList.of(splitter, splitter2));
    ImmutableCollection<ModuleSplit> splits = pipeline.split(baseSplit);
    assertThat(splitter.splitCallCount).isEqualTo(1);
    assertThat(splitter2.splitCallCount).isEqualTo(2);
    assertThat(splits).hasSize(4);
    assertThat(splits).containsExactly(baseSplit, baseSplit, baseSplit, baseSplit);
  }
}
