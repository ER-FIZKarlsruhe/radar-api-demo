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

import groovy.json.JsonSlurper

def jsonSlurper = new JsonSlurper()

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)
String accessToken = token.getTokens().access_token
def workspaceId
def datasetId

try {
    /* ***************************************************************************************************************** */
    /* Create new workspace */
    /* ***************************************************************************************************************** */
    println "Creating new Workspace: " + "/api/contracts/${variables.contractId}/workspaces"
    String workspaceJson = new File('./../../resources/workspace.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace example4 ${System.currentTimeMillis()}\"")
    def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
    println "New Workspace: " + newWorkspace
    workspaceId = newWorkspace.id

    /* ***************************************************************************************************************** */
    /* Create new dataset */
    /* ***************************************************************************************************************** */
    println "\nCreating new Dataset: " + "/api/workspaces/${workspaceId}/datasets"
    String datasetJson = new File('./../../resources/dataset.json').text
    datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${workspaceId}\"")
    datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset example4 ${System.currentTimeMillis()}\"")
    def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${workspaceId}/datasets", datasetJson, accessToken)
    datasetId = newDataset.id
    println "New Dataset: " + newDataset

    /* ***************************************************************************************************************** */
    /* Create new folders */
    /* ***************************************************************************************************************** */
    println "\nCreating new Folder WITH Metadata: " + "/api/datasets/${newDataset.id}/folders"
    String folderJson = new File('./../../resources/folder-with-metadata.json').text
    folderJson = folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${datasetId}\"")
    folderJson = folderJson.replace("\"folderName\":\"null\"", "\"folderName\":\"folderWithMetadata ${System.currentTimeMillis()}\"")
    def newFolder = httpRequester.doPost(variables.contextUrl + "/api/datasets/${datasetId}/folders", folderJson, accessToken)
    println "New Folder: " + newFolder

    println "\nCreating new Folder NO Metadata: " + "/api/datasets/${datasetId}/folders"
    folderJson = new File('./../../resources/folder.json').text
    folderJson = folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${datasetId}\"")
    folderJson = folderJson.replace("\"folderName\":\"null\"", "\"folderName\":\"folderNoMetadata ${System.currentTimeMillis()}\"")
    newFolder = httpRequester.doPost(variables.contextUrl + "/api/datasets/${datasetId}/folders", folderJson, accessToken)
    println "New Folder: " + newFolder

    /* ***************************************************************************************************************** */
    /* Create new file via API is DEPRECATED */
    /* See ingestHierarchy.groovy on how to create a file */
    /* ***************************************************************************************************************** */

    /* ***************************************************************************************************************** */
    /* Create new dataset wrong parentId - the server rejects it */
    /* ***************************************************************************************************************** */
    try {
        println "\nCreating new Dataset with wrong parent: " + "/api/workspaces/blablub/datasets"
        datasetJson = new File('./../../resources/dataset.json').text
        datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"blablub\"")
        datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset example4 ${System.currentTimeMillis()}\"")
        httpRequester.doPost(variables.contextUrl + "/api/workspaces/blablub/datasets", datasetJson, accessToken)
        println "WARNING: the server accepted a dataset with a wrong parent"
    } catch (IOException e) {
        println "Rejected as expected: " + e.getMessage()
    }

    /* ***************************************************************************************************************** */
    /* Create new folder no folderName - the server rejects it */
    /* ***************************************************************************************************************** */
    try {
        println "\nCreating new Folder with no folderName: " + "/api/datasets/${datasetId}/folders"
        folderJson = new File('./../../resources/folder.json').text
        folderJson = folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${datasetId}\"")
        folderJson = folderJson.replace("\"folderName\":\"null\"", "\"folderName\":\"\"")
        httpRequester.doPost(variables.contextUrl + "/api/datasets/${datasetId}/folders", folderJson, accessToken)
        println "WARNING: the server accepted a folder without a name"
    } catch (IOException e) {
        println "Rejected as expected: " + e.getMessage()
    }
} finally {
    /* ***************************************************************************************************************** */
    /* Cleanup */
    /* ***************************************************************************************************************** */
    if (workspaceId) {
        println ""
        println "Cleanup... Delete Workspace ..."
        try {
            httpRequester.doDelete(variables.contextUrl + "/api/workspaces/" + workspaceId, accessToken)
        } catch (IOException e) {
            println "Cleanup failed: " + e.getMessage()
        }
    }
}
