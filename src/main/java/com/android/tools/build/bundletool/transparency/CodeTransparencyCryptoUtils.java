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
package com.android.tools.build.bundletool.transparency;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

/** Helper methods related to code transparency signature parsing and verification. */
public final class CodeTransparencyCryptoUtils {

  /**
   * Parses {@link JsonWebSignature} object from the given {@link ByteSource} containing signed
   * transparency file contents.
   */
  public static JsonWebSignature parseJws(ByteSource signedTransparencyFile) {
    JsonWebSignature jws;
    try {
      jws =
          (JsonWebSignature)
              JsonWebSignature.fromCompactSerialization(
                  signedTransparencyFile.asCharSource(Charset.defaultCharset()).read());
    } catch (JoseException | IOException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Unable to deserialize JWS from code transparency file.")
          .withCause(e)
          .build();
    }
    return jws;
  }

  /**
   * Verifies signature against the public key certificate extracted from the JWS header. Returns
   * {@code true} if signature can be successfully verified.
   */
  public static boolean verifySignature(JsonWebSignature jws) {
    boolean signatureValid;
    try {
      jws.setKey(jws.getLeafCertificateHeaderValue().getPublicKey());
      jws.setAlgorithmConstraints(
          new AlgorithmConstraints(
              ConstraintType.WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256));
      signatureValid = jws.verifySignature();
    } catch (JoseException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Exception while verifying code transparency signature.")
          .withCause(e)
          .build();
    }
    return signatureValid;
  }

  /**
   * Extracts SHA-256 fingerprint of the leaf public key certificate from {@link JsonWebSignature}.
   */
  public static String getCertificateFingerprint(JsonWebSignature jws) {
    X509Certificate certificate;
    try {
      certificate = jws.getLeafCertificateHeaderValue();
    } catch (JoseException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Unable to retrieve certificate header value from JWS.")
          .withCause(e)
          .build();
    }
    return getCertificateFingerprint(certificate);
  }

  /** Returns SHA-256 fingerprint of the given certificate. */
  public static String getCertificateFingerprint(X509Certificate certificate) {
    return Bytes.asList(getCertificateFingerprintBytes(certificate)).stream()
        .map(b -> String.format("%02X", b))
        .collect(joining(" "));
  }

  /** Reads {@link X509Certificate} from the given path. */
  public static X509Certificate getX509Certificate(Path certificatePath) {
    ImmutableList<X509Certificate> certificates = getX509Certificates(certificatePath);
    if (certificates.isEmpty()) {
      throw InvalidCommandException.builder()
          .withInternalMessage("Unable to read public key certificate from the provided path.")
          .build();
    }
    return certificates.get(0);
  }

  /** Reads {@link X509Certificate} chain from the given path. */
  public static ImmutableList<X509Certificate> getX509Certificates(Path certificatePath) {
    try (InputStream inputStream = Files.newInputStream(certificatePath)) {
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
      return certificateFactory.generateCertificates(inputStream).stream()
          .map(certificate -> (X509Certificate) certificate)
          .collect(toImmutableList());
    } catch (IOException e) {
      throw InvalidCommandException.builder()
          .withInternalMessage("Unable to read public key certificate from the provided path.")
          .withCause(e)
          .build();
    } catch (CertificateException e) {
      throw InvalidCommandException.builder()
          .withInternalMessage("Unable to generate X509Certificate.")
          .withCause(e)
          .build();
    }
  }

  private static byte[] getCertificateFingerprintBytes(X509Certificate certificate) {
    byte[] certificateBytes;
    try {
      certificateBytes = ByteSource.wrap(certificate.getEncoded()).hash(Hashing.sha256()).asBytes();
    } catch (CertificateEncodingException | IOException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Unable to get certificate fingerprint value.")
          .withCause(e)
          .build();
    }
    return certificateBytes;
  }

  private CodeTransparencyCryptoUtils() {}
}
