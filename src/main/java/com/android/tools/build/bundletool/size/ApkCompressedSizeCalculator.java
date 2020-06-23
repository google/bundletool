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

package com.android.tools.build.bundletool.size;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.Deflater;

final class ApkCompressedSizeCalculator {

  // Each time we add an entry to the deflater a syncronization entry is added.
  // This would not be present when we acually compress the APK for serving, it's just an artifact
  // of flushing after each file.
  static final int DEFLATER_SYNC_OVERHEAD_BYTES = 5;

  private static final int INPUT_BUFFER_SIZE = 8192;

  private final Supplier<ApkGzipDeflater> deflaterSupplier;

  ApkCompressedSizeCalculator(Supplier<ApkGzipDeflater> deflaterSupplier) {
    this.deflaterSupplier = deflaterSupplier;
  }

  /**
   * Given a list of {@link ByteSource} computes the GZIP size increments attributed to each stream.
   */
  public ImmutableList<Long> calculateGZipSizeForEntries(List<ByteSource> byteSources)
      throws IOException {
    ImmutableList.Builder<Long> gzipSizeIncrements = ImmutableList.builder();

    try (ApkGzipDeflater deflater = deflaterSupplier.get()) {
      // matches the {@code ByteStreams} buffer size
      byte[] inputBuffer = new byte[INPUT_BUFFER_SIZE];

      for (ByteSource byteSource : byteSources) {
        try (InputStream is = byteSource.openStream()) {
          while (true) {
            int r = is.read(inputBuffer);
            if (r == -1) {
              gzipSizeIncrements.add(
                  Math.max(0, deflater.entryComplete() - DEFLATER_SYNC_OVERHEAD_BYTES));
              break;
            }
            deflater.handleInput(inputBuffer, r);
          }
        }
      }
    }
    return gzipSizeIncrements.build();
  }

  interface ApkGzipDeflater extends Closeable {

    /** Compresses the next byte of an entry */
    void handleInput(byte[] data, int size);

    /**
     * Called when all of the current entry's data has been passed to the deflater (via {@link
     * #handleInput}).
     *
     * <p>Once called the deflater's size should be reset so that the next entry can be processed.
     *
     * @return the compressed size of this entry.
     */
    long entryComplete();
  }

  static final class JavaUtilZipDeflater implements ApkGzipDeflater {

    private final Deflater deflater;

    // Worse case overestimate for the max size deflation should result it.
    // (most of the time deflation should result in a smaller output, but there are cases
    // where it can be larger).
    private final byte[] outputBuffer = new byte[2 * INPUT_BUFFER_SIZE];

    private long deflatedSize = 0;

    public JavaUtilZipDeflater() {
      this.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, /* noWrap */ true);
    }

    @Override
    public void handleInput(byte[] data, int size) {
      deflater.setInput(data, 0, size);
      while (!deflater.needsInput()) {
        deflatedSize += deflater.deflate(outputBuffer, 0, outputBuffer.length, Deflater.NO_FLUSH);
      }
    }

    @Override
    public long entryComplete() {
      // We need to use syncFlush which is slower but allows us to accurately count GZIP
      // bytes. See {@link Deflater#SYNC_FLUSH}. Sync-flush flushes all deflater's pending
      // output upon calling flush().
      long result =
          deflatedSize
              + deflater.deflate(outputBuffer, 0, outputBuffer.length, Deflater.SYNC_FLUSH);
      deflatedSize = 0;
      return result;
    }

    @Override
    public void close() {
      deflater.end();
    }
  }
}
