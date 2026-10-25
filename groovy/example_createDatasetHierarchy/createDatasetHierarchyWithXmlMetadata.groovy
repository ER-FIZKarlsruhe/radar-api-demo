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

try {
    /* ***************************************************************************************************************** */
    /* Create new workspace */
    /* ***************************************************************************************************************** */
    println "Creating new Workspace"
    String workspaceJson = new File('./../../resources/workspace.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace example4 ${System.currentTimeMillis()}\"")
    def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
    println "New Workspace: " + newWorkspace
    workspaceId = newWorkspace.id

    /* ***************************************************************************************************************** */
    /* Create new dataset */
    /* ***************************************************************************************************************** */
    println "\nCreating new Dataset"
    String datasetJson = new File('./../../resources/dataset.json').text
    datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${workspaceId}\"")
    datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset example4 ${System.currentTimeMillis()}\"")
    def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${workspaceId}/datasets", datasetJson, accessToken)
    println "New Dataset: " + newDataset

    println "\nImporting descriptive metadata as xml"
    def xmlFile = new File('./../../resources/RadarDatasetExample.xml')
    println variables.contextUrl + "/api/datasets/${newDataset.id}/metadata"
    def response = httpRequester.doPostMultipart(variables.contextUrl + "/api/datasets/${newDataset.id}/metadata", new FileInputStream(xmlFile), 'RadarDatasetExample.xml', accessToken, 'metadataFile')
    println "response: " + response

    /* ***************************************************************************************************************** */
    /* Reading dataset descriptive metadata as xml */
    /* ***************************************************************************************************************** */
    println "\nReading dataset descriptive metadata as xml"
    def xml = httpRequester.doGet(variables.contextUrl + "/api/datasets/${newDataset.id}/metadata", accessToken, httpRequester.XML)
    println xml

    /* ***************************************************************************************************************** */
    /* Create new folder */
    /* ***************************************************************************************************************** */
    println "\nCreating new Folder"
    String folderJson = new File('./../../resources/folder.json').text
    folderJson = folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newDataset.id}\"")
    folderJson = folderJson.replace("\"folderName\":\"null\"", "\"folderName\":\"Folder ${System.currentTimeMillis()}\"")
    def newFolder = httpRequester.doPost(variables.contextUrl + "/api/datasets/${newDataset.id}/folders", folderJson, accessToken)
    println "New Folder: " + newFolder

    println "\nImporting descriptive metadata as xml"
    xmlFile = new File('./../../resources/RadarFileExample.xml')
    httpRequester.doPostMultipart(variables.contextUrl + "/api/folders/${newFolder.id}/metadata", new FileInputStream(xmlFile), 'RadarFileExample.xml', accessToken, 'metadataFile')

    /* ***************************************************************************************************************** */
    /* Reading folder descriptive metadata as xml */
    /* ***************************************************************************************************************** */
    println "\nReading folder descriptive metadata as xml"
    def folderxml = httpRequester.doGet(variables.contextUrl + "/api/folders/${newFolder.id}/metadata", accessToken, httpRequester.XML)
    println folderxml
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
