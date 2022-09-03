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
package com.android.tools.build.bundletool.testing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Factory to build X.509 certificates from a key pair. */
public final class CertificateFactory {

  private static final String BASIC_CONSTRAINTS_EXTENSION = "2.5.29.19";

  /** Inflates an encoded X.509 certificate into a {@link X509Certificate} object. */
  public static X509Certificate inflateCertificate(byte[] encodedCertificate) {
    try {
      java.security.cert.CertificateFactory certFactory =
          java.security.cert.CertificateFactory.getInstance("X.509");
      return (X509Certificate)
          certFactory.generateCertificate(new ByteArrayInputStream(encodedCertificate));
    } catch (CertificateException e) {
      throw new RuntimeException(
          "Cannot parse the certificates as X.509 certificates. cert: "
              + Arrays.toString(encodedCertificate),
          e);
    }
  }

  /** Builds a self-signed certificate using RSA signature algorithm. */
  public static X509Certificate buildSelfSignedCertificate(
      KeyPair keyPair, String distinguishedName) {
    return inflateCertificate(
        buildSelfSignedCertificateDerEncoded(keyPair, distinguishedName, "SHA256withRSA"));
  }

  /** Builds a self-signed certificate using DSA signature algorithm. */
  public static X509Certificate buildSelfSignedDSACertificate(
      KeyPair keyPair, String distinguishedName) {
    return inflateCertificate(
        buildSelfSignedCertificateDerEncoded(keyPair, distinguishedName, "SHA256withDSA"));
  }

  /**
   * Builds a self-signed certificate using RSA signature algorithm.
   *
   * @return the DER-encoded certificate.
   */
  public static byte[] buildSelfSignedCertificateDerEncoded(
      KeyPair keyPair, String distinguishedName) {
    return buildSelfSignedCertificateDerEncoded(keyPair, distinguishedName, "SHA256withRSA");
  }

  /**
   * Builds a self-signed certificate.
   *
   * @return the DER-encoded certificate.
   */
  private static byte[] buildSelfSignedCertificateDerEncoded(
      KeyPair keyPair, String distinguishedName, String signatureAlgorithm) {
    X500Principal principal = new X500Principal(distinguishedName);

    // Default is 30 years. Fields are ignored by Android framework anyway (as of Jan 2017).
    Instant notBefore = Instant.now();
    Instant notAfter = notBefore.atOffset(ZoneOffset.UTC).plusYears(30).toInstant();

    SecureRandom rng = new SecureRandom();
    try {
      return new JcaX509v3CertificateBuilder(
              /* issuer= */ principal,
              generateRandomSerialNumber(rng),
              new Date(notBefore.toEpochMilli()),
              new Date(notAfter.toEpochMilli()),
              /* subject= */ principal,
              keyPair.getPublic())
          // Basic constraints: subject type = CA
          .addExtension(
              new ASN1ObjectIdentifier(BASIC_CONSTRAINTS_EXTENSION),
              false,
              new DERSequence(ASN1Boolean.TRUE))
          .build(new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate()))
          .getEncoded();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (OperatorCreationException e) {
      throw new RuntimeException(e);
    }
  }

  private static BigInteger generateRandomSerialNumber(SecureRandom rng) {
    // Serial number of conforming CAs must be positive and no larger than 20 bytes long.
    byte[] serialBytes = new byte[20];
    while (true) {
      rng.nextBytes(serialBytes);
      BigInteger serial = new BigInteger(1, serialBytes);
      if (serial.compareTo(BigInteger.ONE) >= 0) {
        return serial;
      }
    }
  }

  private CertificateFactory() {}
}
