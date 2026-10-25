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
import java.io.FileInputStream;

def jsonSlurper = new JsonSlurper()

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)
def commonIngest = engine.run('CommonIngest.groovy', binding)
String accessToken = token.getTokens().access_token
def newWorkspace
def newDataset

try {
    /* ***************************************************************************************************************** */
    /* Create new workspace */
    /* ***************************************************************************************************************** */
    println "Creating new Workspace: " + "/api/contracts/${variables.contractId}/workspaces"
    String workspaceJson = new File('./../../resources/workspace-with-categories.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace ingestHierarchy ${System.currentTimeMillis()}\"")
    newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
    println "New Workspace: " + newWorkspace

    /* ***************************************************************************************************************** */
    /* Create new dataset metadata */
    /* ***************************************************************************************************************** */
    println "\nCreating new Dataset metadata: " + "/api/workspaces/${newWorkspace.id}/datasets"
    String datasetJson = new File('./../../resources/dataset.json').text
    datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newWorkspace.id}\"")
    datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset ingestHierarchy ${System.currentTimeMillis()}\"")
    newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${newWorkspace.id}/datasets", datasetJson, accessToken)
    println "New Dataset: " + newDataset

    /* ***************************************************************************************************************** */
    /* Ingest Zip to dataset */
    /* ***************************************************************************************************************** */
    println "\nIngesting zip to dataset to " + newDataset.ingestUrl
    File inputFile = new File('./../sample_data/apache-maven-3.3.9-bin.zip')
    httpRequester.doPostMultipart(newDataset.ingestUrl, new FileInputStream(inputFile), 'apache-maven-3.3.9-bin.zip', accessToken)
    println "newDataset.id: " + newDataset.id
    commonIngest.showIngestProgress(newDataset.id, accessToken)

    /* ***************************************************************************************************************** */
    /* Upload file to dataset */
    /* ***************************************************************************************************************** */
    println "\nUploading File to dataset: " + newDataset.uploadUrl
    inputFile = new File('./../../resources/file-with-metadata.json')
    def newFileId = httpRequester.doPostMultipart(newDataset.uploadUrl, new FileInputStream(inputFile), 'file.json', accessToken).trim()
    commonIngest.showIngestProgress(newFileId, accessToken)

    /* ***************************************************************************************************************** */
    /* Upload file to folder */
    /* ***************************************************************************************************************** */
    println "\nCreating new Folder: " + "/api/datasets/${newDataset.id}/folders"
    String folderJson = new File('./../../resources/folder.json').text
    folderJson = folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newDataset.id}\"")
    folderJson = folderJson.replace("\"folderName\":\"null\"", "\"folderName\":\"Folder ${System.currentTimeMillis()}\"")
    def newFolder = httpRequester.doPost(variables.contextUrl + "/api/datasets/${newDataset.id}/folders", folderJson, accessToken)
    println "New Folder: " + newFolder

    println "\nUploading File to folder: " + newFolder.uploadUrl
    inputFile = new File('./../../resources/dataset.json')
    newFileId = httpRequester.doPostMultipart(newFolder.uploadUrl, new FileInputStream(inputFile), 'dataset.json', accessToken).trim()
    println "newFileId " + newFileId
    commonIngest.showIngestProgress(newFileId, accessToken)
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
