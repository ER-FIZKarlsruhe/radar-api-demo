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

if (!args || args?.size() == 0) {
    println "You have to provide the id of a published dataset as parameter. E.g"
    println "groovy correctDataset.groovy tDsgWlYQbyJWAeSv"
    System.exit(1)
}

println "args[0]: " + args[0]
String datasetId = args[0]

/* ***************************************************************************************************************** */
/* Correct dataset */
/* ***************************************************************************************************************** */
println "\nCorrecting Dataset metadata"

def xmlFile = new File('./../../resources/RadarDatasetExample-correction.xml')
String xml = xmlFile.text
xml = xml.replace("@@TITLE@@", "CORRECTION " + new Date().toString())
println variables.contextUrl + "/api/datasets/${datasetId}/metadata-correction"
def response = httpRequester.doPostMultipart(variables.contextUrl + "/api/datasets/${datasetId}/metadata-correction", new ByteArrayInputStream(xml.getBytes("UTF-8")), 'RadarDatasetExample.xml', accessToken, 'metadataFile')
println "response: " + response

/* ***************************************************************************************************************** */
/* Release Correction */
/* ***************************************************************************************************************** */
println "\nRelease Correction"
httpRequester.doPost(variables.contextUrl + "/api/datasets/${datasetId}/metadata-correction/release", null, accessToken, httpRequester.TEXT)
