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
    String workspaceJson = new File('./../../resources/workspace-with-categories.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace example8 ${System.currentTimeMillis()}\"")
    def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
    println "New Workspace: " + newWorkspace
    workspaceId = newWorkspace.id

    /* ***************************************************************************************************************** */
    /* Create new dataset */
    /* ***************************************************************************************************************** */
    println "\nCreating new Dataset"
    String datasetJson = new File('./../../resources/dataset.json').text
    datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newWorkspace.id}\"")
    datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset example8 ${System.currentTimeMillis()}\"")
    def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${newWorkspace.id}/datasets", datasetJson, accessToken)
    println "New Dataset: " + newDataset

    /* ***************************************************************************************************************** */
    /* Set Rights */
    /* ***************************************************************************************************************** */
    println "\nSetting Rights"
    httpRequester.doPost(variables.contextUrl + "/api/workspaces/" + workspaceId + "/curators", "[\"user\"]", accessToken, httpRequester.TEXT)
    //httpRequester.doPost(variables.contextUrl + "/api/workspaces/" + workspaceId + "/subcurators", "[\"subcurator1\"]", accessToken, httpRequester.TEXT)
    println "Curators for Workspace " + workspaceId + ":" + httpRequester.doGet(variables.contextUrl + "/api/workspaces/" + workspaceId + "/curators", accessToken)
    println "Subcurators for Workspace " + workspaceId + ":" + httpRequester.doGet(variables.contextUrl + "/api/workspaces/" + workspaceId + "/subcurators", accessToken)

    /* ***************************************************************************************************************** */
    /* Set Wrong Rights - the server rejects an unknown user */
    /* ***************************************************************************************************************** */
    try {
        println "\nSetting Wrong Rights"
        httpRequester.doPost(variables.contextUrl + "/api/workspaces/" + workspaceId + "/curators", "[\"blablub\"]", accessToken, httpRequester.TEXT)
        println "WARNING: the server accepted an unknown curator"
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
