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
 * Checks before uploading and publishing, so problems show up before the real request fails:
 *
 *   GET /api/datasets/{id}/canupload?filename=...   can a file with this name be uploaded into the dataset?
 *   GET /api/folders/{id}/canupload?filename=...    the same for a folder
 *   GET /api/datasets/{id}/metadata/validate        is the descriptive metadata of the dataset valid?
 *
 * canupload checks the quota, the dataset state, forbidden characters and whether the name is already used.
 * It answers 200 "OK" or 422 with the reason.
 */

import java.io.FileInputStream

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)
def commonIngest = engine.run('CommonIngest.groovy', binding)
String accessToken = token.getTokens().access_token
def newWorkspace

def canUploadUrl = { String type, String id, String filename ->
    variables.contextUrl + "/api/${type}/${id}/canupload?filename=" + URLEncoder.encode(filename, "UTF-8")
}

def expectRejected = { String description, String url ->
    try {
        println "\n${description}: " + url
        httpRequester.doGet(url, accessToken, httpRequester.TEXT)
        println "WARNING: the server accepted it"
    } catch (IOException e) {
        println "Rejected as expected: " + e.getMessage()
    }
}

try {
    /* ***************************************************************************************************************** */
    /* Create workspace, dataset and folder */
    /* ***************************************************************************************************************** */
    println "Creating new Workspace: " + "/api/contracts/${variables.contractId}/workspaces"
    String workspaceJson = new File('./../../resources/workspace.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace validateMetadata ${System.currentTimeMillis()}\"")
    newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
    println "New Workspace: " + newWorkspace.id

    println "\nCreating new Dataset: " + "/api/workspaces/${newWorkspace.id}/datasets"
    String datasetJson = new File('./../../resources/dataset.json').text
    datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newWorkspace.id}\"")
    datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset validateMetadata ${System.currentTimeMillis()}\"")
    def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${newWorkspace.id}/datasets", datasetJson, accessToken)
    println "New Dataset: " + newDataset.id

    println "\nCreating new Folder: " + "/api/datasets/${newDataset.id}/folders"
    String folderJson = new File('./../../resources/folder.json').text
    folderJson = folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newDataset.id}\"")
    folderJson = folderJson.replace("\"folderName\":\"null\"", "\"folderName\":\"results\"")
    def newFolder = httpRequester.doPost(variables.contextUrl + "/api/datasets/${newDataset.id}/folders", folderJson, accessToken)
    println "New Folder: " + newFolder.id

    /* ***************************************************************************************************************** */
    /* Check before uploading */
    /* ***************************************************************************************************************** */
    String url = canUploadUrl("datasets", newDataset.id, "measurements.csv")
    println "\nCan measurements.csv be uploaded into the dataset? " + url
    println httpRequester.doGet(url, accessToken, httpRequester.TEXT)

    url = canUploadUrl("folders", newFolder.id, "measurements.csv")
    println "\nCan measurements.csv be uploaded into the folder? " + url
    println httpRequester.doGet(url, accessToken, httpRequester.TEXT)

    expectRejected("Checking a file name with a forbidden character", canUploadUrl("datasets", newDataset.id, "a:b.csv"))

    expectRejected("Checking a folder id on the datasets URL", canUploadUrl("datasets", newFolder.id, "measurements.csv"))

    println "\nUploading measurements.csv after the check succeeded: " + newDataset.uploadUrl
    def fileId = httpRequester.doPostMultipart(newDataset.uploadUrl, new FileInputStream(new File('./../../resources/file.json')), 'measurements.csv', accessToken).trim()
    commonIngest.showIngestProgress(fileId, accessToken)

    // names are checked against the search index, which is updated shortly after the upload has completed
    Thread.sleep(3000)
    expectRejected("Checking the same name again", canUploadUrl("datasets", newDataset.id, "measurements.csv"))
    expectRejected("Checking the name of the folder", canUploadUrl("datasets", newDataset.id, "results"))

    /* ***************************************************************************************************************** */
    /* Validate the metadata before publishing */
    /* ***************************************************************************************************************** */
    // The metadata is validated against the full RADAR schema, in which the identifier (DOI) is mandatory.
    // A new dataset has no identifier yet, so the first validation fails with a message that identifier is expected.
    url = variables.contextUrl + "/api/datasets/${newDataset.id}/metadata/validate"
    println "\nValidating the metadata: " + url
    def validation = httpRequester.doGet(url, accessToken)
    println "success: " + validation?.success
    println "details: " + validation?.details

    // Reserving a DOI writes the identifier into the metadata. Only possible while the dataset is PENDING.
    String doiUrl = variables.contextUrl + "/api/datasets/${newDataset.id}/doi"
    println "\nReserving a DOI: " + doiUrl
    println "DOI: " + httpRequester.doPost(doiUrl, null, accessToken, httpRequester.TEXT)

    println "\nValidating the metadata again: " + url
    validation = httpRequester.doGet(url, accessToken)
    println "success: " + validation?.success
    println "details: " + validation?.details
} finally {
    /* ***************************************************************************************************************** */
    /* Cleanup */
    /* ***************************************************************************************************************** */
    if (newWorkspace) {
        println ""
        println "Cleanup... Delete Workspace ..."
        try {
            httpRequester.doDelete(variables.contextUrl + "/api/workspaces/" + newWorkspace.id, accessToken)
        } catch (IOException e) {
            println "Cleanup failed: " + e.getMessage()
        }
    }
}
