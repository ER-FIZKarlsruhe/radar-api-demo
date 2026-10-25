/*
 * Copyright 2026 FIZ Karlsruhe - Leibniz-Institut fuer Informationsinfrastruktur GmbH
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

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)

String accessToken = token.getTokens().access_token
def workspaceId
def datasetId

File createRandomTestFile() {
    String fileName = "webdav-test-" + System.currentTimeMillis() + ".bin"
    File file = new File(fileName)

    byte[] data = new byte[16 * 1024]
    new Random().nextBytes(data)
    file.bytes = data

    println "Created random test file: ${file.absolutePath} (${file.length()} bytes)"
    return file
}

String readResponseBody(HttpURLConnection conn) {
    InputStream is = null
    try {
        is = (conn.responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream()
        return is != null ? is.getText("UTF-8") : ""
    } catch (Exception ignored) {
        return ""
    } finally {
        if (is != null) {
            try {
                is.close()
            } catch (Exception ignored2) {
            }
        }
    }
}

String basicAuthHeader(String username, String password) {
    String credentials = "${username}:${password}"
    String encoded = Base64.encoder.encodeToString(credentials.getBytes("UTF-8"))
    return "Basic ${encoded}"
}

int uploadViaWebDav(String targetUrl, File file, String username, String password) {
    HttpURLConnection conn = null
    OutputStream os = null
    InputStream fis = null

    try {
        println "Uploading file to WebDAV: ${targetUrl}"
        println "Using BasicAuth username: ${username}"

        conn = (HttpURLConnection) new URL(targetUrl).openConnection()
        conn.setRequestMethod("PUT")
        conn.setDoOutput(true)
        conn.setConnectTimeout(30000)
        conn.setReadTimeout(30000)
        conn.setInstanceFollowRedirects(false)

        conn.setRequestProperty("Authorization", basicAuthHeader(username, password))
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setFixedLengthStreamingMode(file.length())

        os = conn.getOutputStream()
        fis = new FileInputStream(file)

        byte[] buffer = new byte[8192]
        int len
        while ((len = fis.read(buffer)) != -1) {
            os.write(buffer, 0, len)
        }
        os.flush()

        int status = conn.getResponseCode()
        String responseBody = readResponseBody(conn)

        println "WebDAV response code: ${status}"
        if (responseBody) {
            println "WebDAV response body: ${responseBody}"
        }

        return status
    } finally {
        if (fis != null) {
            try {
                fis.close()
            } catch (Exception ignored) {
            }
        }
        if (os != null) {
            try {
                os.close()
            } catch (Exception ignored) {
            }
        }
        if (conn != null) {
            conn.disconnect()
        }
    }
}

/* ***************************************************************************************************************** */
/* Create new workspace */
/* ***************************************************************************************************************** */
println "Creating new Workspace: " + "/api/contracts/${variables.contractId}/workspaces"
String workspaceJson = new File('./../../resources/workspace-webdav.json').text
workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace example4 ${System.currentTimeMillis()}\"")
def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
println "New Workspace: " + newWorkspace
workspaceId = newWorkspace.id

/* ***************************************************************************************************************** */
/* Create new WebDAV dataset */
/* ***************************************************************************************************************** */
String createUrl = "/api/workspaces/${workspaceId}/datasets?webdav=true"
println "\nCreating new Webdav Dataset: " + createUrl
String datasetJson = new File('./../../resources/dataset.json').text
datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${workspaceId}\"")
datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset example4 ${System.currentTimeMillis()}\"")

def newDataset = httpRequester.doPost(variables.contextUrl + createUrl, datasetJson, accessToken)
datasetId = newDataset.id
println "New Dataset: " + newDataset

/* ***************************************************************************************************************** */
/* Upload random test file via WebDAV */
/* ***************************************************************************************************************** */
String webdavUrl = newDataset.webdavUrl
String uploadToken = newDataset.technicalMetadata?.uploadToken
String webdavUsername = newDataset.id

if (!webdavUrl) {
    throw new IOException("Dataset does not contain a webdavUrl.")
}
if (!uploadToken) {
    throw new IOException("Dataset does not contain technicalMetadata.uploadToken.")
}
if (!webdavUsername) {
    throw new IOException("Dataset does not contain an id for BasicAuth username.")
}

File testFile = createRandomTestFile()
String targetUrl = webdavUrl + testFile.name

println "\nUploading random test file via WebDAV ..."
int status
try {
    status = uploadViaWebDav(targetUrl, testFile, webdavUsername, uploadToken)
} finally {
    testFile.delete()
}

if (status >= 200 && status < 300) {
    println "WebDAV upload successful: ${targetUrl}"
} else {
    throw new IOException("WebDAV upload failed with HTTP status ${status}")
}

/* ***************************************************************************************************************** */
/* Trigger WebDAV import */
/* ***************************************************************************************************************** */
println "\nTriggering importWebdav for dataset ${datasetId} ..."

String importUrl = variables.contextUrl + "/api/datasets/${datasetId}/importWebdav"
println "\nImport Webdav Dataset: " + importUrl
def importResponse = httpRequester.doPost(importUrl, "", accessToken, httpRequester.TEXT)
println "importWebdav response: " + importResponse
