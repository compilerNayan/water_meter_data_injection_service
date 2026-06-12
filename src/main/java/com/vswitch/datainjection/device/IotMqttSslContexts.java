package com.vswitch.datainjection.device;

import java.io.StringReader;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

final class IotMqttSslContexts {

    private IotMqttSslContexts() {}

    static SSLContext fromPem(String caPem, String clientCertPem, String clientKeyPem)
            throws Exception {
        Certificate caCertificate = parseCertificate(caPem);
        Certificate clientCertificate = parseCertificate(clientCertPem);
        PrivateKey privateKey = parsePrivateKey(clientKeyPem);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("amazon-root-ca", caCertificate);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry(
                "client",
                privateKey,
                new char[0],
                new Certificate[] {clientCertificate});

        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                new SecureRandom());
        return sslContext;
    }

    private static Certificate parseCertificate(String pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(
                new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (object instanceof PEMKeyPair keyPair) {
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            if (object instanceof X509CertificateHolder) {
                throw new IllegalArgumentException("Expected private key PEM, got certificate");
            }
            throw new IllegalArgumentException("Unsupported private key PEM format");
        }
    }
}
