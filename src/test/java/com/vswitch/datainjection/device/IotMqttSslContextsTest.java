package com.vswitch.datainjection.device;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IotMqttSslContextsTest {

    @Test
    void buildsSslContextFromHardcodedFleetCredentials() throws Exception {
        SSLContext sslContext =
                IotMqttSslContexts.fromPem(
                        IotMqttCredentials.CA_CERTIFICATE_PEM,
                        IotMqttCredentials.CLIENT_CERTIFICATE_PEM,
                        IotMqttCredentials.CLIENT_PRIVATE_KEY_PEM);

        assertNotNull(sslContext.getSocketFactory());
    }
}
