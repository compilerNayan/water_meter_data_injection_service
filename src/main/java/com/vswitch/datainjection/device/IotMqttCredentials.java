package com.vswitch.datainjection.device;

/**
 * Fleet-provisioning IoT credentials (temporary hardcode; move to Secrets Manager later).
 * Matches {@code ConnectionDetailsProvider} fleet provisioning defaults on device.
 */
public final class IotMqttCredentials {

    public static final String MQTT_ENDPOINT =
            "mqtts://a2hlcpmplecdfa-ats.iot.us-east-1.amazonaws.com";

    public static final String CA_CERTIFICATE_PEM =
            """
            -----BEGIN CERTIFICATE-----
            MIIDQTCCAimgAwIBAgITBmyfz5m/jAo54vB4ikPmljZbyjANBgkqhkiG9w0BAQsF
            ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6
            b24gUm9vdCBDQSAxMB4XDTE1MDUyNjAwMDAwMFoXDTM4MDExNzAwMDAwMFowOTEL
            MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEZMBcGA1UEAxMQQW1hem9uIFJv
            b3QgQ0EgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALJ4gHHKeNXj
            ca9HgFB0fW7Y14h29Jlo91ghYPl0hAEvrAIthtOgQ3pOsqTQNroBvo3bSMgHFzZM
            9O6II8c+6zf1tRn4SWiw3te5djgdYZ6k/oI2peVKVuRF4fn9tBb6dNqcmzU5L/qw
            IFAGbHrQgLKm+a/sRxmPUDgH3KKHOVj4utWp+UhnMJbulHheb4mjUcAwhmahRWa6
            VOujw5H5SNz/0egwLX0tdHA114gk957EWW67c4cX8jJGKLhD+rcdqsq08p8kDi1L
            93FcXmn/6pUCyziKrlA4b9v7LWIbxcceVOF34GfID5yHI9Y/QCB/IIDEgEw+OyQm
            jgSubJrIqg0CAwEAAaNCMEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
            AYYwHQYDVR0OBBYEFIQYzIU07LwMlJQuCFmcx7IQTgoIMA0GCSqGSIb3DQEBCwUA
            A4IBAQCY8jdaQZChGsV2USggNiMOruYou6r4lK5IpDB/G/wkjUu0yKGX9rbxenDI
            U5PMCCjjmCXPI6T53iHTfIUJrU6adTrCC2qJeHZERxhlbI1Bjjt/msv0tadQ1wUs
            N+gDS63pYaACbvXy8MWy7Vu33PqUXHeeE6V/Uq2V8viTO96LXFvKWlJbYK8U90vv
            o/ufQJVtMVT8QtPHRh8jrdkPSHCa2XV4cdFyQzR1bldZwgJcJmApzyMZFo6IQ6XU
            5MsI+yMRQ+hDKXJioaldXgjUkK642M4UwtBV8ob2xJNDd2ZhwLnoQdeXeGADbkpy
            rqXRfboQnoZsG4q5WTP468SQvvG5
            -----END CERTIFICATE-----""";

    public static final String CLIENT_CERTIFICATE_PEM =
            """
            -----BEGIN CERTIFICATE-----
            MIIDWTCCAkGgAwIBAgIUbe9HHYeW7k5DUp3y085n01KGZ98wDQYJKoZIhvcNAQEL
            BQAwTTFLMEkGA1UECwxCQW1hem9uIFdlYiBTZXJ2aWNlcyBPPUFtYXpvbi5jb20g
            SW5jLiBMPVNlYXR0bGUgU1Q9V2FzaGluZ3RvbiBDPVVTMB4XDTI2MDUzMDEwNTI0
            MVoXDTQ5MTIzMTIzNTk1OVowHjEcMBoGA1UEAwwTQVdTIElvVCBDZXJ0aWZpY2F0
            ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAN2q4rfW3EyPnv1Myk5o
            RRu5PoYoYY4gCQMogdbTPqE0pioC4/hnlmz+PXmE2rZgfgGNTJ4wrpPcHrTOP7qN
            CrqhedVKH+wM5NKIN1PsusYBg9R99BKj61eQyNeqjAaaqg2cQAO4P4Pf2lHQEpKY
            bzrU9je2gZo6tqA4JDO1M5a32n3Zs/yJfnM3pz0W7Nl7m0hMkZ9o8WMPT7BQJCHL
            fsywvc5/vWtQvKNA5pibIhpofydfMuswWYWixpGEYIlNFqWsX/HKWsOfp1gZ3dOj
            PgDPzBNoV7l/pesFCSejUZrJOYQRDyNyjc8NT00JrYHerVFWMka8tyeYMCiZfERo
            5LsCAwEAAaNgMF4wHwYDVR0jBBgwFoAUcE5mKDC71gZMJzxjByxFwPKFmdUwHQYD
            VR0OBBYEFBe85GRyVeDflPn56VrNWr3M9aeBMAwGA1UdEwEB/wQCMAAwDgYDVR0P
            AQH/BAQDAgeAMA0GCSqGSIb3DQEBCwUAA4IBAQDeqLjNYrSV+u30bPp8EWNSgsVU
            vdPvaJ0CajklrSAhLS/T9khdETxAfn7nvQw/4N8Prlh60kaiwKyDGPrhl///wVxS
            pjYquVI3eBjQ6ni2qnjBTH5SfiCrgEmgYNxq6Nn/1BkHpGWO913SqZIZGd+MO3oa
            Obvh+tnFqFyvc9qkwqPgCDpFmj8bCBscoEhl4vt4EPZN9LQ6/f55aPvAKx0DXNRG
            BOpohb2vdaaiYOyHHtPcu01RG+WkKO5+9/0mk220jAG5MtqBdaQpRz2dQCp1Q1ks
            vEv8w06C05mSYIBwtjQMQu93GYeIOYsOuB50F2bY3estrhBXg4Naaojygsqf
            -----END CERTIFICATE-----""";

    public static final String CLIENT_PRIVATE_KEY_PEM =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEogIBAAKCAQEA3arit9bcTI+e/UzKTmhFG7k+hihhjiAJAyiB1tM+oTSmKgLj
            +GeWbP49eYTatmB+AY1MnjCuk9wetM4/uo0KuqF51Uof7Azk0og3U+y6xgGD1H30
            EqPrV5DI16qMBpqqDZxAA7g/g9/aUdASkphvOtT2N7aBmjq2oDgkM7Uzlrfafdmz
            /Il+czenPRbs2XubSEyRn2jxYw9PsFAkIct+zLC9zn+9a1C8o0DmmJsiGmh/J18y
            6zBZhaLGkYRgiU0Wpaxf8cpaw5+nWBnd06M+AM/ME2hXuX+l6wUJJ6NRmsk5hBEP
            I3KNzw1PTQmtgd6tUVYyRry3J5gwKJl8RGjkuwIDAQABAoIBAB1sQFm8mFNFQQpI
            NhZAOuQaK5VtKL3PvMKBjvJv6cFGFsQ+y/m97jabbJeDrfBFUJRuJ/xbY+DWd/Dx
            632cmQ76vgw0oZYYhAr577YhFw2PR+tdqJcM0QE3g6E0zw9VWjsiQVD9FNkxTm5L
            mxuARktd1yy/+eX54yHTMeL3K1jW4VU/3PxprEgaFgEJ9ZBMeidMl0kB3WNTi4ys
            MyzoZc8MAUZXVYlkYe6fsS7Cn9t2BeJzxIYGtzTs4xjs0v/bb6lxmi96dP1oWwpZ
            vI9b+ZEOnAp70s++W+SPHby0gayipMxReIlhUVhWXoymoDk98VnXhdOyiDw0+4oT
            86501yECgYEA84YptgAMzYXTMqLF4lyYbO9tFSniDijBtxIT5TLEbj8/kWn4zhvr
            Yx4uWaPEM6UQ7hex93JcwElxRHFPSIGyG8PnA6LlhzqtrPwoCDdM+DNVu4tq42ec
            9OlcUp/SUmW8oxaZbW0VcD1UU1OS0SwLDiSjIhKUUYjyOKTIRgvHbGsCgYEA6QYS
            gywc2db8gqL4PKf4RKGZSBZ/UBQqX6rPCl71F5KoDGS+nhDNsbekWI9gDQhA5Xpl
            MQ9vebK6tD+UpqaB5DmyLxp2ZWq2OX4Hlbfd0sPZNF9h6khgAmiZqo7uU3ycPuDg
            7oaL4sBgHO8Zqe2Gy6XNWlP9Mpkb+7ivpAl4fPECgYB/0DHCOWJ+2DdSA0azGQBT
            ZJKvIe1omxGZIV7Z0/xvFLkrfCA/JT41JpkTKTYIGSG6pSseAaMWtTVCw+nl11SA
            6CAus2eewzh2a14jecrnFiJwLatrMW2ayYRQRVvhLU6Flo3udetjnnzMwzdym5gt
            0yLf9jpsVOE0w5/ty67egwKBgFcNSK8uNJ0A3pZjEX9/dJUXFa9DkE43KllQ80W5
            kbA7voHaxQdB2cYRh9j5vvU/ZxcTcWgxjwCUz4D027CiNZYwI6vLI/3hLrAtr+Gz
            ra/GMIeLNoYSgaOEthtsiAYyYCBxXDZflzSfj4hfnmPH83pyt1OOWuGjJzwTk7Ih
            Q5zRAoGAdfizZDOSiK28g/6MjgNiIJlUANd61WjnO+xRly0e53vzRB02Ynrq/Vwu
            kf2ro8Wle2TBVBCR0GpyKLFRl/V/axjXhkSCk0q4+RVEqLunw77PUspR7+cGLxVb
            ok5rte626z1PeQc30Rtf45RMIiKla3iGOTsIX02gipx9a7vSyQg=
            -----END RSA PRIVATE KEY-----""";

    private IotMqttCredentials() {}
}
