/*
 * Copyright 2018 FIZ Karlsruhe - Leibniz-Institut fuer Informationsinfrastruktur GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Small HTTP helper based on the JDK HttpClient (Java 11+), no external libraries needed.
 *
 * The content type argument selects the Accept/Content-Type header and how the response is returned:
 *   JSON (default) - parsed with JsonSlurper (Map / List)
 *   TEXT, XML      - response body as String
 */

import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest as JdkHttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.regex.Pattern

class HttpRequest {

    static final String JSON = "application/json"
    static final String TEXT = "text/plain"
    static final String XML = "application/xml"

    private static final String USER_AGENT = "GroovyRadarApiDemo/2.0"

    def binding = new Binding()
    def engine = new GroovyScriptEngine("./../common")
    def variables = engine.run('CommonVariables.groovy', binding)

    private HttpClient client = createClient()

    Object doGet(String url, String token, String contentType = JSON) throws IOException {
        def request = newRequest(url, token, contentType).GET()
        return send(request, contentType)
    }

    Object doPost(String url, String postBody, String token, String contentType = JSON) throws IOException {
        def request = newRequest(url, token, contentType)
                .header("Content-Type", contentType + "; charset=UTF-8")
                .POST(bodyOf(postBody))
        return send(request, contentType)
    }

    // The upload and ingest endpoints read the first part of the request whatever its name; "file" is the name
    // the documentation uses. The metadata endpoints need the part name "metadataFile".
    Object doPostMultipart(String url, InputStream ins, String filename, String token, String partname = "file") throws IOException {
        String boundary = "----RadarApiDemo" + UUID.randomUUID().toString().replace("-", "")
        byte[] head = ("--${boundary}\r\n" +
                "Content-Disposition: form-data; name=\"${partname}\"; filename=\"${filename}\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8)
        byte[] tail = "\r\n--${boundary}--\r\n".getBytes(StandardCharsets.UTF_8)
        def parts = [new ByteArrayInputStream(head), ins, new ByteArrayInputStream(tail)]
        def body = new SequenceInputStream(Collections.enumeration(parts))

        // no request timeout: uploading large files takes longer than readTimeout
        def request = JdkHttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "multipart/form-data; boundary=${boundary}")
                .POST(JdkHttpRequest.BodyPublishers.ofInputStream { body })
        addToken(request, token)
        return send(request, TEXT)
    }

    Object doPut(String url, String putBody, String token, String contentType = JSON) throws IOException {
        def request = newRequest(url, token, contentType)
                .header("Content-Type", contentType + "; charset=UTF-8")
                .PUT(bodyOf(putBody))
        return send(request, contentType)
    }

    Object doDelete(String url, String token) throws IOException {
        def request = newRequest(url, token, JSON).DELETE()
        return send(request, JSON)
    }

    private JdkHttpRequest.Builder newRequest(String url, String token, String contentType) {
        def request = JdkHttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(variables.readTimeout))
                .header("User-Agent", USER_AGENT)
                .header("Accept", contentType)
        addToken(request, token)
        return request
    }

    private static void addToken(JdkHttpRequest.Builder request, String token) {
        if (token != null) {
            request.header("Authorization", "Bearer " + token)
        }
    }

    private static JdkHttpRequest.BodyPublisher bodyOf(String body) {
        return body == null ? JdkHttpRequest.BodyPublishers.noBody() : JdkHttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
    }

    private Object send(JdkHttpRequest.Builder request, String contentType) throws IOException {
        HttpResponse<String> response
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new IOException("Request interrupted: " + request.build().uri(), e)
        }

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP ${response.statusCode()} ${response.request().method()} ${response.uri()}:" + response.body())
        }
        String body = response.body()
        if (contentType == JSON) {
            return body ? new JsonSlurper().parseText(body) : null
        }
        return body
    }

    private HttpClient createClient() {
        def builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(variables.readTimeout))
        if (variables.proxy) {
            builder.proxy(new RadarProxySelector(variables.proxy, (variables.proxyPort ?: "80") as int, variables.nonProxyHosts))
        }
        return builder.build()
    }
}

/**
 * Uses the configured proxy for every URL, except those matching nonProxyHosts.
 */
class RadarProxySelector extends ProxySelector {

    private final Proxy proxy
    private final Pattern nonProxyHostsPattern

    RadarProxySelector(String host, int port, String nonProxyHosts) {
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))
        if (nonProxyHosts) {
            nonProxyHosts = nonProxyHosts.replaceAll("\\.", "\\\\.")
            nonProxyHosts = nonProxyHosts.replaceAll("\\*", "")
            nonProxyHosts = nonProxyHosts.replaceAll("\\?", "\\\\?")
            nonProxyHostsPattern = Pattern.compile(nonProxyHosts)
        }
    }

    @Override
    List<Proxy> select(URI uri) {
        if (nonProxyHostsPattern && nonProxyHostsPattern.matcher(uri.toString()).find()) {
            return [Proxy.NO_PROXY]
        }
        return [proxy]
    }

    @Override
    void connectFailed(URI uri, SocketAddress address, IOException e) {
    }
}

new HttpRequest()
