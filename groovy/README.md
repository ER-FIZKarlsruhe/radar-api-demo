# Groovy examples

One folder `example_*` per use case, with shared helpers in [`common/`](common).
The metadata is read from the shared [`resources/`](../resources) folder,
the archives to upload from [`sample_data/`](sample_data).

## Prerequisites

- Groovy 5.x (Java 11 or newer) or Groovy 6.x (Java 17 or newer), tested with Groovy 5.1.3 and 6.0.0 on Java 21
- No further libraries: HTTP calls use the JDK `java.net.http.HttpClient`
- A RADAR user with a password and an OAuth client, see [Before you start](../README.md#before-you-start)

## Configuration

Copy [`config/radar.properties.example`](config/radar.properties.example) to `config/radar.properties`
(git-ignored) and fill in your values:

    clientId=<your-client-id>
    clientSecret=<your-client-secret>
    contractAdminUserName=<your-username>
    contractAdminUserPassword=<your-password>
    contractId=<your-contract-id>
    serverUrl=https://test.radar-service.eu

Alternatively set them as environment variables, which take precedence over the file:
`RADAR_` + key in upper snake case, e.g. `RADAR_CLIENT_SECRET`. `RADAR_CONFIG` can point to a properties file elsewhere.
The keys are read by [`common/CommonVariables.groovy`](common/CommonVariables.groovy);
a missing required value stops the example with a message naming it.

## Running an example

The examples load the helpers from `./../common`, so start them from within their example folder:

    cd groovy/example_crudWorkspace
    groovy crudWorkspace.groovy

With the Gradle wrapper (Java 17 or newer, used by CI), from the `groovy` folder:

    ./gradlew build                          # compile all scripts
    ./gradlew integrationTest --continue     # run the examples against the configured server
    ./gradlew runCrudWorkspace               # run a single example

`integrationTest` runs the examples that clean up after themselves, plus `setStates`, which archives a dataset that
can't be deleted again - so use a contract meant for testing. [`build.gradle`](build.gradle) lists them.
`--continue` runs all of them even if one fails. Each example writes a JUnit XML report to
`build/test-results/integrationTest/`, so a CI server can show the result per example.

If a request fails, the example stops with the server's error message. Workspaces an example creates are deleted
again at the end, also after an error, except where noted below.

## Examples

| Script | What it shows |
|--------|---------------|
| [`example_getAccessToken/getAccessToken.groovy`](example_getAccessToken/getAccessToken.groovy) | Get an access token and trade the refresh token for a new one |
| [`example_crudWorkspace/crudWorkspace.groovy`](example_crudWorkspace/crudWorkspace.groovy) | Create, read, update and delete a workspace |
| [`example_crudSchema/crudSchema.groovy`](example_crudSchema/crudSchema.groovy) | Create, read, update and delete a metadata schema of the contract |
| [`example_createDatasetHierarchy/createDatasetHierarchy.groovy`](example_createDatasetHierarchy/createDatasetHierarchy.groovy) | Create a workspace, a dataset and folders with and without metadata; requests the server rejects |
| [`example_createDatasetHierarchy/createDatasetHierarchyWithXmlMetadata.groovy`](example_createDatasetHierarchy/createDatasetHierarchyWithXmlMetadata.groovy) | Upload and read RADAR XML metadata of a dataset and a folder |
| [`example_ingestHierarchy/ingestHierarchy.groovy`](example_ingestHierarchy/ingestHierarchy.groovy) | Ingest a zip archive, upload single files into a dataset and a folder, wait for the upload jobs |
| [`example_browseHierarchy/browseHierarchy.groovy`](example_browseHierarchy/browseHierarchy.groovy) | Build a dataset with nested folders and files, then walk it with the `children` endpoints page by page; read a single folder and file |
| [`example_validateMetadata/validateMetadata.groovy`](example_validateMetadata/validateMetadata.groovy) | Check with `canupload` whether a file name can be uploaded, and validate the dataset metadata before publishing: it fails until a DOI is reserved, then passes |
| [`example_ingestHierarchyDisciplineSpecificMetadata/ingestHierarchyDisciplineSpecificMetadata.groovy`](example_ingestHierarchyDisciplineSpecificMetadata/ingestHierarchyDisciplineSpecificMetadata.groovy) | The same with discipline-specific metadata (schema `PNG` version `7`, which must exist in your contract) |
| [`example_createDatasetWebdav/createDatasetWebdav.groovy`](example_createDatasetWebdav/createDatasetWebdav.groovy) | Create a WebDAV dataset, upload a file via WebDAV and import it. Keeps the workspace |
| [`example_setRights/setRights.groovy`](example_setRights/setRights.groovy) | Assign curators to a workspace (the user `user` must exist); an unknown user is rejected |
| [`example_setStates/setStates.groovy`](example_setStates/setStates.groovy) | Start and end the review, archive a dataset and wait until it is `ARCHIVED`. Keeps the workspace |
| [`example_metadataCorrection/publishDataset.groovy`](example_metadataCorrection/publishDataset.groovy) | Create and publish a dataset. Keeps the workspace |
| [`example_metadataCorrection/correctDataset.groovy`](example_metadataCorrection/correctDataset.groovy) | Correct and release the metadata of a published dataset: `groovy correctDataset.groovy <datasetId>` |
| [`example_facets/search.groovy`](example_facets/search.groovy) | Search workspaces: query, offset and rows, sorting |
| [`example_facets/facets.groovy`](example_facets/facets.groovy) | Facets of workspaces, and search restricted to a facet value |
| [`example_stresstest/stresstest.groovy`](example_stresstest/stresstest.groovy) | Load test: 20 datasets ingesting an archive in parallel. Keeps the workspace |
