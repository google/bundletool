/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;

import com.android.bundle.CodeTransparencyOuterClass.CodeTransparency;
import com.google.protobuf.util.JsonFormat;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import org.jose4j.jws.JsonWebSignature;

/** Helper class for writing code transparency related tests. */
public final class CodeTransparencyTestUtils {

  public static String createJwsToken(
      CodeTransparency codeTransparency, X509Certificate certificate, PrivateKey privateKey)
      throws Exception {
    return createJwsToken(codeTransparency, certificate, privateKey, RSA_USING_SHA256);
  }

  public static String createJwsToken(
      CodeTransparency codeTransparency,
      X509Certificate certificate,
      PrivateKey privateKey,
      String algorithmIdentifier)
      throws Exception {
    return createJwsToken(
        codeTransparency, new X509Certificate[] {certificate}, privateKey, algorithmIdentifier);
  }

  public static String createJwsToken(
      CodeTransparency codeTransparency,
      X509Certificate[] certificates,
      PrivateKey privateKey,
      String algorithmIdentifier)
      throws Exception {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(algorithmIdentifier);
    jws.setCertificateChainHeaderValue(certificates);
    jws.setPayload(JsonFormat.printer().print(codeTransparency));
    jws.setKey(privateKey);
    return jws.getCompactSerialization();
  }

  private CodeTransparencyTestUtils() {}
}
