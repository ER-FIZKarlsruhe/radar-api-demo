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

/*
 * Builds a small hierarchy and walks it with the children endpoints:
 *
 *   Workspace
 *   └── Dataset
 *       ├── dataset.json
 *       └── Folder A
 *           ├── folder.json
 *           └── Folder B
 *               └── file.json
 *
 * The children are read page by page with rows and offset. PAGE_SIZE is deliberately small to show the paging.
 */

import java.io.FileInputStream

final int PAGE_SIZE = 2

def binding = new Binding()
def engine = new GroovyScriptEngine("./../common")
def httpRequester = engine.run('HttpRequester.groovy', binding)
def variables = engine.run('CommonVariables.groovy', binding)
def token = engine.run('TokenAccess.groovy', binding)
def commonIngest = engine.run('CommonIngest.groovy', binding)
String accessToken = token.getTokens().access_token
def newWorkspace

/**
 * Reads all children of a workspace, dataset or folder, page by page.
 * type is the path segment of the parent: workspaces, datasets or folders.
 */
def readAllChildren = { String type, String id ->
    def children = []
    int offset = 0
    while (true) {
        def page = httpRequester.doGet(variables.contextUrl + "/api/${type}/${id}/children?rows=${PAGE_SIZE}&offset=${offset}", accessToken)
        println "  page offset=${page.offset} numRecords=${page.numRecords} totalHits=${page.totalHits}"
        children.addAll(page.data ?: [])
        offset += (page.data ?: []).size()
        if (!page.data || offset >= page.totalHits) {
            return children
        }
    }
}

/**
 * Children are found via the search index, which is updated shortly after an upload has completed.
 * Waits until the parent has the expected number of children.
 */
def waitForChildren = { String type, String id, int expected ->
    for (int poll = 0; poll < 30; poll++) {
        def page = httpRequester.doGet(variables.contextUrl + "/api/${type}/${id}/children?rows=1", accessToken)
        if (page.totalHits >= expected) {
            return
        }
        Thread.sleep(1000)
    }
    throw new IOException("${type}/${id} has not got ${expected} children in the search index after 30 seconds")
}

def uploadFile = { def parent, String path, String filename ->
    println "\nUploading ${filename} to: " + parent.uploadUrl
    def fileId = httpRequester.doPostMultipart(parent.uploadUrl, new FileInputStream(new File(path)), filename, accessToken).trim()
    commonIngest.showIngestProgress(fileId, accessToken)
    return fileId
}

/** Prints the tree below a dataset or folder. Folders have a folderName, files a fileName. */
def printTree
printTree = { String type, String id, String indent ->
    println "${indent}Reading children of ${type}/${id}"
    readAllChildren(type, id).each { child ->
        if (child.folderName != null) {
            println "${indent}+ Folder ${child.folderName} (${child.id})"
            printTree("folders", child.id, indent + "    ")
        } else {
            println "${indent}- File ${child.fileName} (${child.id})"
        }
    }
}

try {
    /* ***************************************************************************************************************** */
    /* Create the hierarchy */
    /* ***************************************************************************************************************** */
    println "Creating new Workspace: " + "/api/contracts/${variables.contractId}/workspaces"
    String workspaceJson = new File('./../../resources/workspace.json').text
    workspaceJson = workspaceJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${variables.contractId}\"")
    workspaceJson = workspaceJson.replace("\"title\":\"null\"", "\"title\":\"Workspace browseHierarchy ${System.currentTimeMillis()}\"")
    newWorkspace = httpRequester.doPost(variables.contextUrl + "/api/contracts/${variables.contractId}/workspaces", workspaceJson, accessToken)
    println "New Workspace: " + newWorkspace.id

    println "\nCreating new Dataset: " + "/api/workspaces/${newWorkspace.id}/datasets"
    String datasetJson = new File('./../../resources/dataset.json').text
    datasetJson = datasetJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newWorkspace.id}\"")
    datasetJson = datasetJson.replace("\"title\":\"null\"", "\"title\":\"Dataset browseHierarchy ${System.currentTimeMillis()}\"")
    def newDataset = httpRequester.doPost(variables.contextUrl + "/api/workspaces/${newWorkspace.id}/datasets", datasetJson, accessToken)
    println "New Dataset: " + newDataset.id

    println "\nCreating Folder A: " + "/api/datasets/${newDataset.id}/folders"
    String folderJson = new File('./../../resources/folder.json').text
    def folderA = httpRequester.doPost(variables.contextUrl + "/api/datasets/${newDataset.id}/folders",
            folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${newDataset.id}\"").replace("\"folderName\":\"null\"", "\"folderName\":\"Folder A\""),
            accessToken)
    println "Folder A: " + folderA.id

    // a folder inside a folder is created below /api/folders
    println "\nCreating Folder B in Folder A: " + "/api/folders/${folderA.id}/folders"
    def folderB = httpRequester.doPost(variables.contextUrl + "/api/folders/${folderA.id}/folders",
            folderJson.replace("\"parentId\":\"null\"", "\"parentId\":\"${folderA.id}\"").replace("\"folderName\":\"null\"", "\"folderName\":\"Folder B\""),
            accessToken)
    println "Folder B: " + folderB.id

    uploadFile(newDataset, './../../resources/dataset.json', 'dataset.json')
    uploadFile(folderA, './../../resources/folder.json', 'folder.json')
    def fileId = uploadFile(folderB, './../../resources/file.json', 'file.json')

    waitForChildren("datasets", newDataset.id, 2)
    waitForChildren("folders", folderA.id, 2)
    waitForChildren("folders", folderB.id, 1)

    /* ***************************************************************************************************************** */
    /* Walk the hierarchy */
    /* ***************************************************************************************************************** */
    println "\nDatasets of the workspace: " + "/api/workspaces/${newWorkspace.id}/children"
    readAllChildren("workspaces", newWorkspace.id).each { dataset ->
        println "Dataset ${dataset.descriptiveMetadata?.title} (${dataset.id}), state ${dataset.state}"
        printTree("datasets", dataset.id, "    ")
    }

    /* ***************************************************************************************************************** */
    /* Read a single folder and file */
    /* ***************************************************************************************************************** */
    println "\nReading Folder B: " + "/api/folders/${folderB.id}"
    def folder = httpRequester.doGet(variables.contextUrl + "/api/folders/${folderB.id}", accessToken)
    println "Folder: " + folder

    println "\nReading file.json: " + "/api/files/${fileId}"
    def file = httpRequester.doGet(variables.contextUrl + "/api/files/${fileId}", accessToken)
    println "File: " + file
    println "Parent of the file: " + file.parentId
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
