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

import groovy.json.JsonOutput

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)
String accessToken = token.getTokens().access_token

/* ***************************************************************************************************************** */
/* Create Workspace */
/* ***************************************************************************************************************** */
println "\nCREATE " + "/api/contracts/${variables.contractId}/workspaces"
String workspaceJson = new File('./../../resources/workspace.json').text
workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace example3 ${System.currentTimeMillis()}\"")
println workspaceJson
def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
println "New Workspace: " + newWorkspace

try {
    /* ***************************************************************************************************************** */
    /* Read Workspace */
    /* ***************************************************************************************************************** */
    println "\nREAD"
    def response = httpRequester.doGet(variables.contextUrl + "/api/workspaces/${newWorkspace.id}", accessToken)
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))

    /* ***************************************************************************************************************** */
    /* Update Workspace */
    /* ***************************************************************************************************************** */
    println "\nUPDATE"
    workspaceJson = new File('./../../resources/workspace.json').text
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Renamed Workspace example3 ${System.currentTimeMillis()}\"")
    workspaceJson = workspaceJson.replace("\"id\":null", "\"id\":\"${newWorkspace.id}\"")
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    def updatedWorkspace = httpRequester.doPut(variables.contextUrl + "/api/workspaces/${newWorkspace.id}", workspaceJson, accessToken)
    println updatedWorkspace
} catch (Exception e) {
    // don't leave the workspace behind when a step fails, then report the original error
    println "\nCleanup... Delete Workspace ..."
    try {
        httpRequester.doDelete(variables.contextUrl + "/api/workspaces/${newWorkspace.id}", accessToken)
    } catch (IOException cleanupError) {
        println "Cleanup failed: " + cleanupError.getMessage()
    }
    throw e
}

/* ***************************************************************************************************************** */
/* Delete Workspace */
/* ***************************************************************************************************************** */
println "\nDELETE"
httpRequester.doDelete(variables.contextUrl + "/api/workspaces/${newWorkspace.id}", accessToken)
println "Workspace deleted"

// Try to read workspace again -> 404
println "\nRE-READ"
try {
    httpRequester.doGet(variables.contextUrl + "/api/workspaces/${newWorkspace.id}", accessToken)
    println "WARNING: the deleted workspace can still be read"
} catch (IOException e) {
    println "Not found as expected: " + e.getMessage()
}
