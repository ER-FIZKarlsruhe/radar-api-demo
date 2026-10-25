/*
 * Copyright 2022 FIZ Karlsruhe - Leibniz-Institut fuer Informationsinfrastruktur GmbH
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
// schema keys must be unique, so every run creates its own
String uniqueSchemaKey = "APITEST${variables.contractId}${System.currentTimeMillis()}"

/* ***************************************************************************************************************** */
/* Create Schema */
/* ***************************************************************************************************************** */
println "\n=========================================================\n"
println "CREATE " + "/api/schemas/${variables.contractId}"
String schemaJson = new File('./../../resources/schema.json').text
schemaJson = schemaJson.replace("SCHEMA_NAME", "APITEST${variables.contractId}")
schemaJson = schemaJson.replace("CONTRACT_ID", "${variables.contractId}")
schemaJson = schemaJson.replace("SCHEMA_KEY", uniqueSchemaKey)
schemaJson = schemaJson.replace("SCHEMA_VERSION", "v1")
println "\n=========================================================\n"
println schemaJson
def newSchema = httpRequester.doPost(variables.contextUrl + "/api/schemas/${variables.contractId}", schemaJson, accessToken)
println "\n=========================================================\n"
println "New Schema: " + newSchema
schemaKey = newSchema.key
schemaVersion = newSchema.version

try {
    /* ***************************************************************************************************************** */
    /* Read Schema */
    /* ***************************************************************************************************************** */
    println "\n=========================================================\n"
    println "READ"
    def response = httpRequester.doGet(variables.contextUrl + "/api/schemas/${variables.contractId}/${schemaKey}/${schemaVersion}", accessToken)
    println "\n=========================================================\n"
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))

    /* ***************************************************************************************************************** */
    /* Read ALL Schema Versions */
    /* ***************************************************************************************************************** */
    println "\n=========================================================\n"
    println "READ ALL Schema versions"
    response = httpRequester.doGet(variables.contextUrl + "/api/schemas/${variables.contractId}/${schemaKey}", accessToken)
    println "\n=========================================================\n"
    println JsonOutput.prettyPrint(JsonOutput.toJson(response))

    /* ***************************************************************************************************************** */
    /* Update Schema */
    /* ***************************************************************************************************************** */
    println "\n=========================================================\n"
    println "\nUPDATE"
    schemaJson = new File('./../../resources/schema.json').text
    schemaJson = schemaJson.replace("SCHEMA_NAME", "APITEST${variables.contractId}EDITED")
    schemaJson = schemaJson.replace("CONTRACT_ID", "${variables.contractId}")
    schemaJson = schemaJson.replace("SCHEMA_KEY", uniqueSchemaKey)
    schemaJson = schemaJson.replace("SCHEMA_VERSION", "v1")
    schemaJson = schemaJson.replace("<xs:complexType>", "<!--EDITED--><xs:complexType>")
    schemaJson = schemaJson.replace("<re:creators>", "<!--EDITED--><re:creators>")
    def updatedSchema = httpRequester.doPut(variables.contextUrl + "/api/schemas/${variables.contractId}", schemaJson, accessToken)
    println "\n=========================================================\n"
    println updatedSchema
} catch (Exception e) {
    // don't leave the schema behind when a step fails, then report the original error
    println "\nCleanup... Delete Schema ..."
    try {
        httpRequester.doDelete(variables.contextUrl + "/api/schemas/${variables.contractId}/${schemaKey}/${schemaVersion}", accessToken)
    } catch (IOException cleanupError) {
        println "Cleanup failed: " + cleanupError.getMessage()
    }
    throw e
}

/* ***************************************************************************************************************** */
/* Delete Schema */
/* ***************************************************************************************************************** */
println "\n=========================================================\n"
println "DELETE"
httpRequester.doDelete(variables.contextUrl + "/api/schemas/${variables.contractId}/${schemaKey}/${schemaVersion}", accessToken)
println "\n=========================================================\n"
println "Schema deleted"

// Try to read schema again -> 404
println "\n=========================================================\n"
println "RE-READ"
try {
    httpRequester.doGet(variables.contextUrl + "/api/schemas/${variables.contractId}/${schemaKey}/${schemaVersion}", accessToken)
    println "WARNING: the deleted schema can still be read"
} catch (IOException e) {
    println "Not found as expected: " + e.getMessage()
}
