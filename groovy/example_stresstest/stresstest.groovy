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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import java.security.DigestInputStream;
import java.security.MessageDigest;

def jsonSlurper = new JsonSlurper()

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)
def commonIngest = engine.run('CommonIngest.groovy', binding)
String accessToken = token.getTokens().access_token
//File zipFile = new File('./../sample_data/apache-maven-3.3.9-bin.zip')
File zipFile = new File('./../sample_data/many_files.zip')
File datasetFile = new File('./../../resources/dataset.json')
AtomicInteger failed = new AtomicInteger(0)
AtomicInteger started = new AtomicInteger(0)
AtomicInteger exception = new AtomicInteger(0)

long startTime = System.currentTimeMillis()

/* ***************************************************************************************************************** */
/* Create new workspace */
/* ***************************************************************************************************************** */
println "Creating new Workspace: " + "/api/contracts/${variables.contractId}/workspaces"
String workspaceJson = new File('./../../resources/workspace-with-categories.json').text
workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Lasttest ${System.currentTimeMillis()}\"")
def newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
println "New Workspace: " + newWorkspace

/* ***************************************************************************************************************** */
/* Create new datasets */
/* ***************************************************************************************************************** */
def list = 1..20

def pool = Executors.newFixedThreadPool(Runtime.runtime.availableProcessors() + 1)
list.each { int datasetNo ->
    pool.execute {
    try {
        int existingValue = started.incrementAndGet()

        println "Started ${started.intValue()} of ${list.last()}"
        println "------------------------"
        println "\nCreating new Dataset metadata: " + "/api/workspaces/${newWorkspace.id}/datasets"
        
        String datasetJson = datasetFile.text
        datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newWorkspace.id}\"")
        datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset ${datasetNo}\"")
        def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${newWorkspace.id}/datasets", datasetJson, accessToken)
        println "New Dataset: " + newDataset.id
        FileInputStream fis = new FileInputStream(zipFile)
        MessageDigest md = MessageDigest.getInstance("MD5");
        try {
            DigestInputStream dis = new DigestInputStream(fis, md) 
            httpRequester.doPostMultipart(newDataset.ingestUrl, dis, zipFile.getName(), accessToken)
            
            println "${newDataset.id} upload md5sum: " + String.format("%032X", new BigInteger(1, md.digest()))
        } catch (IOException e) {
            println("Failed to upload zip file: " + e.getMessage())
            throw e
        } finally {
            fis.close()
        }
        println "newDataset.id: " + newDataset.id

        // parallel ingests queue up on the server, so allow up to 30 minutes
        commonIngest.showIngestProgress(newDataset.id, accessToken, 2, 900)
    } catch (Exception e) {
        println "INGEST FAILED dataset ${datasetNo}: " + e.getMessage()
        failed.incrementAndGet()
    }
    }
}
pool.shutdown()
pool.awaitTermination(1, TimeUnit.DAYS)

println "Test end. ${failed.intValue()} of ${started.intValue()} ingest failed."

long endTime   = System.currentTimeMillis()
long totalTime = (endTime - startTime)/1000
println "The test runs " + totalTime + " s"