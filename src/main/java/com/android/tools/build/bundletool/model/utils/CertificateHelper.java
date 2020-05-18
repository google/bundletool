/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.build.bundletool.model.utils;

import com.google.common.io.BaseEncoding;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/** Helpers related to dealing with certificates. */
public final class CertificateHelper {

  private CertificateHelper() {}

  public static String sha256AsHexString(X509Certificate certificate)
      throws CertificateEncodingException {
    try {
      return toHexString(getSha256Bytes(certificate.getEncoded()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not found.", e);
    }
  }

  public static byte[] getSha256Bytes(byte[] input) throws NoSuchAlgorithmException {
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
    messageDigest.update(input);
    return messageDigest.digest();
  }

  /** Obtain hex encoded string from raw bytes. */
  private static String toHexString(byte[] rawBytes) {
    return BaseEncoding.base16().upperCase().encode(rawBytes);
  }
}
