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

def jsonOutput = new JsonOutput()

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
    println "Creating new Workspace"
    String workspaceJson = new File('./../../resources/workspace-with-categories.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace example9 ${System.currentTimeMillis()}\"")
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
    def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${workspaceId}/datasets", datasetJson, accessToken)
    println "New Dataset: " + newDataset
    datasetId = newDataset.id

    println "\nSearching Facet title for all workspaces belonging to contract"
    StringBuilder builder = new StringBuilder()
    builder.append("facetName=descriptiveMetadata.title")
    builder.append("&searchQuery=parentId:").append(variables.contractId)
    def response = httpRequester.doGet(variables.contextUrl + "/api/workspaces/facets?" + builder.toString(), accessToken)
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))

    println "\nSearching Facet description for all workspaces belonging to contract"
    builder = new StringBuilder()
    builder.append("facetName=descriptiveMetadata.description")
    builder.append("&searchQuery=parentId:").append(variables.contractId)
    response = httpRequester.doGet(variables.contextUrl + "/api/workspaces/facets?" + builder.toString(), accessToken)
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))

    println "\nSearching Facet title for all workspaces belonging to contract, restricting to distinct description"
    builder = new StringBuilder()
    builder.append("facetName=descriptiveMetadata.title")
    builder.append("&searchQuery=parentId:").append(variables.contractId)
    builder.append("&facetValues=descriptiveMetadata.description=to+be+used+only+by+Alice+and+Bob")
    //builder.append("&facetValues=descriptiveMetadata.description=description")
    response = httpRequester.doGet(variables.contextUrl + "/api/workspaces/facets?" + builder.toString(), accessToken)
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))

    println "\nSearching Workspaces, restricting to FacetValue"
    builder = new StringBuilder()
    builder.append("query=parentId:").append(variables.contractId)
    builder.append("&facetValues=descriptiveMetadata.description=to+be+used+only+by+Alice+and+Bob")
    //builder.append("&facetValues=descriptiveMetadata.description=description")
    response = httpRequester.doGet(variables.contextUrl + "/api/workspaces?" + builder.toString(), accessToken)
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))
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
