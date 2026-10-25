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
 * Configuration of the examples. No credentials are stored in this file.
 *
 * Every value is looked up in this order:
 *   1. environment variable, e.g. RADAR_CLIENT_SECRET for the key clientSecret
 *   2. properties file ../config/radar.properties (git-ignored, copy radar.properties.example),
 *      another location can be set with the environment variable RADAR_CONFIG
 *   3. the default below, if there is one
 */
class Variables {
    private Properties properties = loadProperties()

    def clientId = required("clientId")
    def clientSecret = required("clientSecret")
    def contractAdminUserName = required("contractAdminUserName")
    def contractAdminUserPassword = required("contractAdminUserPassword")
    // only needed for token.getTokens("admin")
    def adminUserName = optional("adminUserName", "")
    def adminUserPassword = optional("adminUserPassword", "")
    def contractId = required("contractId")
    def serverUrl = optional("serverUrl", "https://test.radar-service.eu")
    def contextUrl = optional("contextUrl", "${serverUrl}/radar")
    def ingestUrl = optional("ingestUrl", "${serverUrl}/radar-ingest")
    def redirectUrl = optional("redirectUrl", contextUrl + "/login/token")
    def proxy = optional("proxy", "")
    def proxyPort = optional("proxyPort", "")
    def nonProxyHosts = optional("nonProxyHosts", "")
    def readTimeout = optional("readTimeout", "10000") as Integer

    private static Properties loadProperties() {
        def properties = new Properties()
        def file = new File(System.getenv("RADAR_CONFIG") ?: "./../config/radar.properties")
        if (file.isFile()) {
            file.withReader("UTF-8") { properties.load(it) }
        }
        return properties
    }

    // clientSecret -> RADAR_CLIENT_SECRET
    private static String envName(String key) {
        return "RADAR_" + key.replaceAll(/([a-z])([A-Z])/, '$1_$2').toUpperCase()
    }

    private String optional(String key, String defaultValue) {
        def value = System.getenv(envName(key)) ?: properties.getProperty(key)?.trim()
        return value ?: defaultValue
    }

    private String required(String key) {
        def value = optional(key, null)
        if (!value) {
            throw new IllegalStateException("Missing configuration '${key}': set the environment variable ${envName(key)} " +
                    "or add it to groovy/config/radar.properties (see radar.properties.example)")
        }
        return value
    }
}

new Variables()
