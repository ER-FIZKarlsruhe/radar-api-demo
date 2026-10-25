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
def commonIngest = engine.run('CommonIngest.groovy', binding)
String accessToken = token.getTokens().access_token
String timestamp = System.currentTimeMillis().toString()

/* ***************************************************************************************************************** */
/* Create new workspace */
/* ***************************************************************************************************************** */
println "Creating new Workspace"
String workspaceJson = new File('./../../resources/workspace-with-categories.json').text
workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace setStates ${timestamp}\"" )
def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
println "New Workspace: " + newWorkspace
def workspaceId = newWorkspace.id

/* ***************************************************************************************************************** */
/* Create new dataset */
/* ***************************************************************************************************************** */
println "\nCreating new Dataset"
String datasetJson = new File('./../../resources/dataset.json').text
datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${workspaceId}\"")
datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset setStates ${timestamp}\"")
def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${workspaceId}/datasets", datasetJson, accessToken)
println "New Dataset: " + newDataset
def datasetId = newDataset.id

/* ***************************************************************************************************************** */
/* Set States */
/* ***************************************************************************************************************** */
println "\nSet dataset state to Review"
httpRequester.doPost(variables.contextUrl + "/api/datasets/" + datasetId + "/startreview", null, accessToken, httpRequester.TEXT)

println "\nSet dataset state to Pending"
httpRequester.doPost(variables.contextUrl + "/api/datasets/" + datasetId + "/endreview", null, accessToken, httpRequester.TEXT)

/* ***************************************************************************************************************** */
/* Empty datasets cannot be archived. So upload a file to the dataset */
/* ***************************************************************************************************************** */
println "\nUploading File to dataset: " + newDataset.uploadUrl
def inputFile = new File('./../../resources/file.json')
def newFileId = httpRequester.doPostMultipart(newDataset.uploadUrl, new FileInputStream(inputFile), 'file.json', accessToken).trim()

// archiving is only possible once the upload has completed
commonIngest.showIngestProgress(newFileId, accessToken)

/* ***************************************************************************************************************** */
/* Archive. This runs asynchronously: a server job (by default every 2 minutes) sets the state to ARCHIVED */
/* ***************************************************************************************************************** */
println "\nSet dataset state to Archive"
httpRequester.doPost(variables.contextUrl + "/api/datasets/" + datasetId + "/archive", null, accessToken, httpRequester.TEXT)

String state = null
for (int poll = 0; poll < 90 && state != "ARCHIVED"; poll++) {
    if (poll > 0) {
        Thread.sleep(10000)
    }
    state = httpRequester.doGet(variables.contextUrl + "/api/datasets/" + datasetId, accessToken).state
    println "Dataset state: " + state
}
if (state != "ARCHIVED") {
    throw new IOException("Dataset ${datasetId} was not archived within 15 minutes")
}
println "\nDataset ${datasetId} is archived"
