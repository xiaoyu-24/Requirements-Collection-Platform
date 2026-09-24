package com.xqfx.requirements.aiconfig;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class AiHttpClientFactory {

    private AiHttpClientFactory() {
    }

    static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler,
                                    int timeoutSeconds) throws IOException, InterruptedException {
        try {
            return createDefaultClient(timeoutSeconds).send(request, bodyHandler);
        } catch (SSLHandshakeException handshakeFailure) {
            try {
                return createTls12Client(timeoutSeconds).send(request, bodyHandler);
            } catch (SSLHandshakeException compatibilityFailure) {
                compatibilityFailure.addSuppressed(handshakeFailure);
                throw compatibilityFailure;
            }
        }
    }

    private static HttpClient createDefaultClient(int timeoutSeconds) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    private static HttpClient createTls12Client(int timeoutSeconds) {
        var sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[]{"TLSv1.2"});
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .sslParameters(sslParameters)
                .build();
    }
}
