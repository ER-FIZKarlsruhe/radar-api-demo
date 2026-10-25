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

class Ingest {
    static final Set<String> FAILED_STATES = ["error", "exception", "stopped", "killed"] as Set

    def binding = new Binding()
    def engine = new GroovyScriptEngine("./../common")
    def variables = engine.run('CommonVariables.groovy', binding)
    def httpRequester = engine.run('HttpRequester.groovy', binding)

    /**
     * Uploads and ingests are processed asynchronously. Polls the progress of the job until it has completed.
     *
     * id is the file id for a single file upload and the dataset id for an ingest.
     * The status goes queued -> active -> completed, or error / exception / stopped / killed if the job failed.
     * The server forgets a job once it has finished, so the progress then returns HTTP 404 instead of completed.
     * Throws an IOException if the job failed or did not complete within the time limit.
     */
    Map showIngestProgress(String id, String accessToken, int pollSeconds = 2, int maxPolls = 150) throws IOException {
        def progressUrl = variables.ingestUrl + "/upload/${id}/progress"
        println "\nProgress of upload: " + progressUrl
        for (int poll = 0; poll < maxPolls; poll++) {
            def progress
            try {
                progress = httpRequester.doGet(progressUrl, accessToken)
            } catch (IOException e) {
                if (e.message?.startsWith("HTTP 404 ")) {
                    println "Upload job for ${id} has finished"
                    return [status: "completed"]
                }
                throw e
            }
            println "Upload Progress " + progress
            String status = progress?.status?.toString()?.toLowerCase()
            if (status == "completed") {
                return progress
            }
            if (status in FAILED_STATES) {
                throw new IOException("Upload job for ${id} failed: ${progress}")
            }
            Thread.sleep(pollSeconds * 1000L)
        }
        throw new IOException("Upload job for ${id} did not complete within ${pollSeconds * maxPolls} seconds")
    }
}

new Ingest()
