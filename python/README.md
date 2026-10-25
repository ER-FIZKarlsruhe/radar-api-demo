# RADAR API – Python / curl

A language-neutral introduction to the RADAR API, in three forms:

| | |
|---|---|
| [`radar_api_tutorial.ipynb`](radar_api_tutorial.ipynb) | Interactive Jupyter notebook, step by step |
| [`radar_api_publish_tutorial.ipynb`](radar_api_publish_tutorial.ipynb) | Second notebook: reserve a DOI, publish a dataset and correct its metadata |
| [`radar_api_demo.py`](radar_api_demo.py) | The whole flow as a plain Python script |
| [API quick guide](#api-quick-guide) (below) | Reference with `curl` and Python for every use case |

The first notebook, the script and the quick guide cover the same use cases. The metadata is read from the shared [`resources/`](../resources) folder.

## Before you start

* You need a RADAR user **with a password**. Shibboleth users are **not** supported.
* You need an OAuth client (`clientId`, `clientSecret`, `redirectUrl`). Request one at info@radar-service.eu.
* Use the test system `https://test.radar-service.eu` while you are learning.
* If you need an HTTP proxy, set `HTTPS_PROXY` (and `NO_PROXY`), e.g. `export HTTPS_PROXY=http://proxy.example.org:8080`. Both curl and Python `requests` use it. With curl you can also pass it directly: `curl -x http://proxy.example.org:8080 ...`.

## Running the notebook or the script

    cd python
    python -m pip install -r requirements.txt

Notebook – asks for your credentials when run:

    jupyter lab radar_api_tutorial.ipynb
    jupyter lab radar_api_publish_tutorial.ipynb   # publishing and metadata correction

Script – copy [`.env.example`](.env.example) to `.env` (git-ignored), fill in your credentials, then:

    python radar_api_demo.py

---

## API quick guide

The individual API calls, each with `curl` and Python. Copy them into your shell or your own code.

### Use cases

| # | Use case | Method & endpoint |
|---|----------|-------------------|
| 1 | [Get an OAuth token](#1-get-an-oauth-token) | `POST {CONTEXT_URL}/api/tokens` |
| 2 | [Read a workspace](#2-read-a-workspace) | `GET {CONTEXT_URL}/api/workspaces/{workspaceId}` |
| 3 | [Create a workspace](#3-create-a-workspace) | `POST {CONTEXT_URL}/api/contracts/{contractId}/workspaces` |
| 4 | [Create a dataset](#4-create-a-dataset-with-xml-metadata) | `POST {CONTEXT_URL}/api/workspaces/{workspaceId}/datasets` |
| 4 | [Upload XML metadata](#4b-upload-the-metadata-in-radar-xml-format) | `POST {CONTEXT_URL}/api/datasets/{datasetId}/metadata` |
| 5 | [Upload a single file](#5a-single-file) | `POST {INGEST_URL}/upload/{datasetId}/file` |
| 5 | [Upload / ingest progress](#5b-upload--ingest-progress) | `GET {INGEST_URL}/upload/{fileId or datasetId}/progress` |
| 5 | [Delete a file](#5c-delete-a-file) | `DELETE {CONTEXT_URL}/api/files/{fileId}` |
| 5 | [Upload an archive](#5d-archive-zip--tar--targz--preferred) ⭐ | `POST {INGEST_URL}/upload/{datasetId}/ingest` |
| 5 | [Browse the content](#5e-browse-the-content) | `GET {CONTEXT_URL}/api/{workspaces,datasets,folders}/{id}/children` |
| 5 | [Check a file name before uploading](#5f-check-a-file-name-before-uploading) | `GET {CONTEXT_URL}/api/{datasets,folders}/{id}/canupload?filename=...` |
| 6 | [Start / end the review](#6a-start-and-end-the-review) | `POST {CONTEXT_URL}/api/datasets/{datasetId}/startreview` · `/endreview` |
| 6 | [Validate the metadata](#6b-validate-the-metadata) | `GET {CONTEXT_URL}/api/datasets/{datasetId}/metadata/validate` |
| 6 | [Archive the dataset](#6c-archive-the-dataset) | `POST {CONTEXT_URL}/api/datasets/{datasetId}/archive` |
| 7 | [Refresh the token](#7-refresh-the-token) | `POST {CONTEXT_URL}/api/tokens/refresh` |

### Variables used below

```bash
# bash; on Windows PowerShell use $env:NAME = "value"
export OAUTH_CLIENT_ID="<your-client-id>"
export OAUTH_CLIENT_SECRET="<your-client-secret>"
export OAUTH_CLIENT_REDIRECT_URL="<redirect-url-of-your-client>"
export RADAR_USER_USERNAME="<your-username>"
export RADAR_USER_PASSWORD="<your-password>"

export CONTEXT_URL="https://test.radar-service.eu/radar"
export INGEST_URL="https://test.radar-service.eu/radar-ingest"
export CONTRACT_ID="<your-contract-id>"     # e.g. sg7r0qqx30kvt67h
export WORKSPACE_ID="<your-workspace-id>"   # e.g. ajcc82b1tnq5e5p8
```

```python
import requests
CONTEXT_URL = "https://test.radar-service.eu/radar"
INGEST_URL  = "https://test.radar-service.eu/radar-ingest"
```

---

### 1. Get an OAuth token

**curl**
```bash
curl -X POST "${CONTEXT_URL}/api/tokens" \
  -H 'Content-Type: application/json' \
  -d "{
    \"clientId\": \"${OAUTH_CLIENT_ID}\",
    \"clientSecret\": \"${OAUTH_CLIENT_SECRET}\",
    \"redirectUrl\": \"${OAUTH_CLIENT_REDIRECT_URL}\",
    \"userName\": \"${RADAR_USER_USERNAME}\",
    \"userPassword\": \"${RADAR_USER_PASSWORD}\"
  }"
```

**Python**
```python
tokens = requests.post(f"{CONTEXT_URL}/api/tokens", json={
    "clientId": CLIENT_ID, "clientSecret": CLIENT_SECRET, "redirectUrl": REDIRECT_URL,
    "userName": USERNAME, "userPassword": PASSWORD,
}).json()

# The API is stateless: send the token with every request. Don't use a requests.Session -
# it would keep the RADAR_SESSION_ID cookie of the server and send it back with every request.
AUTH = {"Authorization": f"Bearer {tokens['access_token']}"}
```

**Response**
```json
{"access_token": "????????", "refresh_token": "????????????"}
```

Send the access token with every following request:

```bash
export ACCESS_TOKEN="<access_token from the response>"
# header:  Authorization: Bearer ${ACCESS_TOKEN}
```

---

### 2. Read a workspace

**curl**
```bash
curl -X GET "${CONTEXT_URL}/api/workspaces/${WORKSPACE_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
workspace = requests.get(f"{CONTEXT_URL}/api/workspaces/{WORKSPACE_ID}", headers=AUTH).json()
```

**Response**
```json
{
  "id": "ajcc82b1tnq5e5p8",
  "parentId": "x59rpubbtkjvw247",
  "createdDate": "2026-04-21T05:36:38.141553700Z",
  "lastModifiedDate": "2026-07-03T13:36:14.989576589Z",
  "datasetIds": null,
  "descriptiveMetadata": { "title": "1.17.11 Backend", "description": "" },
  "technicalMetadata": {
    "aiSupport": false,
    "archiveQuota": 11000000000,
    "diskQuota": 11000000000,
    "publicationQuota": 11000000000,
    "defaultDatasetResponsibleEmail": "alice_and_bob@foo.bar",
    "defaultDatasetRetentionPeriod": 10,
    "gitSupport": false,
    "webdavSupport": false,
    "categoryAssignments": null,
    "curatorPreAssignments": null,
    "subcuratorPreAssignments": null,
    "ontologies": null,
    "ontologyCollections": null
  }
}
```

---

### 3. Create a workspace

Requires contract admin rights. `parentId` is the contract ID. Quotas are in bytes, the retention period in years.

**curl**
```bash
curl -X POST "${CONTEXT_URL}/api/contracts/${CONTRACT_ID}/workspaces" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "id": null,
    "parentId": "'"${CONTRACT_ID}"'",
    "descriptiveMetadata": {
      "title": "My API workspace",
      "description": "Created via the API"
    },
    "technicalMetadata": {
      "defaultDatasetRetentionPeriod": 10,
      "defaultDatasetResponsibleEmail": "alice_and_bob@foo.bar",
      "diskQuota": 1000000000,
      "archiveQuota": 1000000000,
      "publicationQuota": 1000000000
    }
  }'
```

**Python**
```python
workspace = requests.post(f"{CONTEXT_URL}/api/contracts/{CONTRACT_ID}/workspaces", headers=AUTH, json={
    "id": None,
    "parentId": CONTRACT_ID,
    "descriptiveMetadata": {"title": "My API workspace", "description": "Created via the API"},
    "technicalMetadata": {
        "defaultDatasetRetentionPeriod": 10,
        "defaultDatasetResponsibleEmail": "alice_and_bob@foo.bar",
        "diskQuota": 1_000_000_000, "archiveQuota": 1_000_000_000, "publicationQuota": 1_000_000_000,
    },
}).json()
WORKSPACE_ID = workspace["id"]
```

**Response**
```json
{
  "id": "dn3zpagc4aw7r3ka",
  "parentId": "sg7r0qqx30kvt67h",
  "createdDate": "2026-09-29T06:27:46.296777191Z",
  "descriptiveMetadata": { "title": "My API workspace", "description": "Created via the API" },
  "technicalMetadata": {
    "defaultDatasetRetentionPeriod": 10,
    "defaultDatasetResponsibleEmail": "alice_and_bob@foo.bar",
    "diskQuota": 1000000000, "archiveQuota": 1000000000, "publicationQuota": 1000000000,
    "webdavSupport": false, "gitSupport": false, "aiSupport": false,
    "...": "..."
  }
}
```

---

### 4. Create a dataset with XML metadata

This takes two steps. First create the dataset, then upload its descriptive metadata as a RADAR XML file.

#### 4a. Create the dataset

`schema` selects the metadata format. `RDDM` is RADAR Descriptive Metadata; here version `9.3`.

**curl**
```bash
curl -X POST "${CONTEXT_URL}/api/workspaces/${WORKSPACE_ID}/datasets" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "id": null,
    "parentId": null,
    "state": "PENDING",
    "technicalMetadata": {
      "retentionPeriod": 10,
      "responsibleEmail": "alice@bob.de",
      "publicationBacklink": "http://alice-bob.de/publication",
      "access": "PRIVATE",
      "schema": { "key": "RDDM", "version": "9.3" }
    },
    "descriptiveMetadata": { "title": "Dataset created with the API" }
  }'
```

**Python**
```python
dataset = requests.post(f"{CONTEXT_URL}/api/workspaces/{WORKSPACE_ID}/datasets", headers=AUTH, json={
    "id": None, "parentId": None, "state": "PENDING",
    "technicalMetadata": {
        "retentionPeriod": 10,
        "responsibleEmail": "alice@bob.de",
        "publicationBacklink": "http://alice-bob.de/publication",
        "access": "PRIVATE",
        "schema": {"key": "RDDM", "version": "9.3"},
    },
    "descriptiveMetadata": {"title": "Dataset created with the API"},
}).json()
DATASET_ID = dataset["id"]
```

**Response.** Note `uploadUrl` and `ingestUrl`; step 5 uses them.
```json
{
  "id": "089vm9p9ykcbq036",
  "parentId": "ajcc82b1tnq5e5p8",
  "state": "PENDING",
  "uploadUrl": "https://test.radar-service.eu/radar-ingest/upload/089vm9p9ykcbq036/file",
  "ingestUrl": "https://test.radar-service.eu/radar-ingest/upload/089vm9p9ykcbq036/ingest",
  "webdavUrl": null,
  "technicalMetadata": {
    "retentionPeriod": 10,
    "responsibleEmail": "alice@bob.de",
    "schema": { "key": "RDDM", "version": "9.3" },
    "size": 0,
    "...": "..."
  },
  "descriptiveMetadata": { "title": "Dataset created with the API", "creators": null, "...": "..." }
}
```

#### 4b. Upload the metadata in RADAR XML format

Multipart form field: `metadataFile`. For an example file see
[`../resources/RadarDatasetExample.xml`](../resources/RadarDatasetExample.xml).
The schema is documented at https://www.radar-service.eu/schemas/descriptive/radar/v09/radar-dataset.

The title from the XML replaces the dataset title, and two datasets in the same workspace must not have the
same title. Otherwise the upload is rejected with HTTP 422 ("Es existiert bereits ein Eintrag mit dem gleichen
Namen auf dieser Ebene."). So change `<ns2:title>` before you upload the example file a second time.

**curl**
```bash
export DATASET_ID="<id from the response above>"

sed "s|<ns2:title>[^<]*</ns2:title>|<ns2:title>My dataset $(date +%s)</ns2:title>|" \
  ../resources/RadarDatasetExample.xml > metadata.xml

curl -X POST "${CONTEXT_URL}/api/datasets/${DATASET_ID}/metadata" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "metadataFile=@metadata.xml"
```

**Python**
```python
import re, time
from pathlib import Path
from xml.sax.saxutils import escape

title = f"My dataset {int(time.time())}"
xml = Path("../resources/RadarDatasetExample.xml").read_text(encoding="utf-8")
xml = re.sub(r"(<ns2:title>)[^<]*(</ns2:title>)", lambda m: m[1] + escape(title) + m[2], xml, count=1)

result = requests.post(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/metadata", headers=AUTH,
                       files={"metadataFile": ("RadarDatasetExample.xml", xml.encode("utf-8"), "application/xml")}).json()
```

**Response**
```json
{"status": 200, "warnings": []}
```

To check the result, read the dataset with `GET {CONTEXT_URL}/api/datasets/{DATASET_ID}`.

---

### 5. Upload content to a dataset

Uploads go to the **ingest** service (`/radar-ingest`) and use the multipart form field `file`.

#### 5a. Single file

**curl**
```bash
curl -X POST "${INGEST_URL}/upload/${DATASET_ID}/file" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@hello.txt"
```

**Python**
```python
with open("hello.txt", "rb") as f:
    file_id = requests.post(f"{INGEST_URL}/upload/{DATASET_ID}/file", headers=AUTH, files={"file": ("hello.txt", f)}).text
```

**Response.** The body is the plain-text ID of the new file.

#### 5b. Upload / ingest progress

Uploads and ingests are **asynchronous**. The request returns as soon as the data has arrived, and the server processes it in the background, which can take a few seconds.
Poll the progress until `status` is `completed` before you work with the content (for example, before deleting the file).

`{entityId}` is the **file ID** for a single-file upload and the **dataset ID** for an ingest.

**curl**
```bash
curl "${INGEST_URL}/upload/${FILE_ID}/progress" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
while True:
    response = requests.get(f"{INGEST_URL}/upload/{file_id}/progress", headers=AUTH)
    if response.status_code == 404:   # the job has finished and is no longer tracked
        break
    job = response.json()
    if job["status"] == "completed":
        break
    if job["status"] in ("error", "exception", "stopped", "killed"):
        raise RuntimeError(job)
    time.sleep(2)
```

**Response**
```json
{"status": "active", "progress": "42"}
```

`status` goes `queued` → `active` → `completed`. It is `error`, `exception`, `stopped` or `killed` if the job failed.
`404` means that the job has finished: the server forgets a job once it is done, so you may get `404` instead of `completed`.

#### 5c. Delete a file

Wait until the upload has completed (5b) before deleting the file.
You can delete a file as long as the dataset is not archived or published (otherwise you get `422`). On success the server returns `204`.

**curl**
```bash
curl -X DELETE "${CONTEXT_URL}/api/files/${FILE_ID}" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
requests.delete(f"{CONTEXT_URL}/api/files/{file_id}", headers=AUTH)
```

#### 5d. Archive (zip / tar / tar.gz) ⭐ preferred

> **The ingest endpoint is the preferred way to upload content via the API.**
> The server unpacks zip, tar and tar.gz archives, so one request uploads a whole folder structure.
>
> ⚠️ **An ingest only works in an empty dataset.** Delete any single uploaded files first (see 5c).

**curl**
```bash
curl -X POST "${INGEST_URL}/upload/${DATASET_ID}/ingest" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@my_data.tar.gz"
```

**Python**
```python
with open("my_data.tar.gz", "rb") as f:
    requests.post(f"{INGEST_URL}/upload/{DATASET_ID}/ingest", headers=AUTH, files={"file": ("my_data.tar.gz", f)})
```

Unpacking takes a while. Poll the progress as in 5b, but with the **dataset ID**:

```bash
curl "${INGEST_URL}/upload/${DATASET_ID}/progress" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

#### 5e. Browse the content

The `children` endpoints list what is directly inside a workspace, dataset or folder:

| Endpoint | Returns |
|---|---|
| `GET {CONTEXT_URL}/api/workspaces/{workspaceId}/children` | the datasets of the workspace |
| `GET {CONTEXT_URL}/api/datasets/{datasetId}/children` | the folders and files in the root of the dataset |
| `GET {CONTEXT_URL}/api/folders/{folderId}/children` | the folders and files in the folder |

The result is paged: `rows` is the page size, `offset` the position of the first entry.
The children are read from the search index, which is updated a moment after an upload has completed.

**curl**
```bash
curl "${CONTEXT_URL}/api/datasets/${DATASET_ID}/children?rows=10&offset=0" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
offset = 0
while True:
    page = requests.get(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/children", headers=AUTH,
                        params={"rows": 10, "offset": offset}).json()
    for child in page["data"] or []:
        print(child.get("folderName") or child.get("fileName"), child["id"])   # folders have a folderName, files a fileName
    offset += len(page["data"] or [])
    if not page["data"] or offset >= page["totalHits"]:
        break
```

**Response** (shortened)
```json
{"totalHits": 1, "numRecords": 1, "offset": 0, "data": [{"id": "a5fjypmb39xnp6ac", "folderName": "sample_data", "parentId": "..."}]}
```

#### 5f. Check a file name before uploading

`canupload` tells you whether a file with this name can be uploaded, without uploading it. The server checks the
quota, the state of the dataset, forbidden characters (such as `/` or `:`) and whether the name is already used by a
file or folder at this level. The answer is `200 OK`, or `422` with the reason.
For a folder use `{CONTEXT_URL}/api/folders/{folderId}/canupload`.

**curl**
```bash
curl "${CONTEXT_URL}/api/datasets/${DATASET_ID}/canupload?filename=results.csv" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
response = requests.get(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/canupload", headers=AUTH, params={"filename": "results.csv"})
print(response.status_code, response.text)   # 200 OK
```

---

### 6. Change the dataset state

```
PENDING ──startreview──▶ REVIEW ──endreview──▶ PENDING ──archive──▶ ARCHIVED
```

* Each state change is a `POST` without a body. On success the server answers `OK`.
* You can only change the state once the ingest has **completed** (5d).
* ⚠️ You can't archive a dataset that is in review. End the review first, which puts the dataset back to `PENDING`.
* You need the curator or contract administrator role.

#### 6a. Start and end the review

**curl**
```bash
curl -X POST "${CONTEXT_URL}/api/datasets/${DATASET_ID}/startreview" -H "Authorization: Bearer ${ACCESS_TOKEN}"
curl -X POST "${CONTEXT_URL}/api/datasets/${DATASET_ID}/endreview"   -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
requests.post(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/startreview", headers=AUTH)   # PENDING -> REVIEW
requests.post(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/endreview", headers=AUTH)     # REVIEW  -> PENDING
```

#### 6b. Validate the metadata

Checks the descriptive metadata against the RADAR schema before you archive or publish.

**curl**
```bash
curl "${CONTEXT_URL}/api/datasets/${DATASET_ID}/metadata/validate" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
validation = requests.get(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/metadata/validate", headers=AUTH).json()
```

**Response**
```json
{"timestamp": "...", "success": false, "details": "cvc-complex-type.2.4.a: ... One of '{...}identifier' is expected."}
```

The schema requires an identifier, which a new dataset does not have yet. So **before archiving or publishing
`success` is `false`** with the message that `identifier` is expected. Archiving assigns a RADAR identifier itself.
Every other message in `details` is a real problem that you have to fix first.

For a dataset you want to **publish**, you can reserve a DOI first. Then the validation passes.
This is only possible while the dataset is `PENDING`, and the answer is the DOI. Don't do this for a dataset you
archive: archiving replaces the DOI with a RADAR identifier.

```bash
curl -X POST "${CONTEXT_URL}/api/datasets/${DATASET_ID}/doi" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

#### 6c. Archive the dataset

Before archiving, the server checks that:
* the dataset is not empty
* the metadata is valid
* `access` and `responsibleEmail` are set
* `retentionPeriod` is 5, 10 or 15

It then assigns a RADAR identifier.
⚠️ **You can't change or delete an archived dataset.**

**curl**
```bash
curl -X POST "${CONTEXT_URL}/api/datasets/${DATASET_ID}/archive" -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

**Python**
```python
requests.post(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}/archive", headers=AUTH)
while requests.get(f"{CONTEXT_URL}/api/datasets/{DATASET_ID}", headers=AUTH).json()["state"] != "ARCHIVED":
    time.sleep(5)
```

Archiving runs **asynchronously**. Read the dataset (`GET {CONTEXT_URL}/api/datasets/{DATASET_ID}`) until `state` is `ARCHIVED`.

---

### 7. Refresh the token

When the access token has expired, trade the refresh token for a new token pair. You don't have to send the password again.

**curl**
```bash
curl -X POST "${CONTEXT_URL}/api/tokens/refresh" \
  -H 'Content-Type: application/json' \
  -d "{
    \"clientId\": \"${OAUTH_CLIENT_ID}\",
    \"clientSecret\": \"${OAUTH_CLIENT_SECRET}\",
    \"refreshToken\": \"${REFRESH_TOKEN}\"
  }"
```

**Python**
```python
tokens = requests.post(f"{CONTEXT_URL}/api/tokens/refresh", json={
    "clientId": CLIENT_ID, "clientSecret": CLIENT_SECRET, "refreshToken": tokens["refresh_token"],
}).json()
```

---

## Schemas

See [Schemas](../README.md#schemas) in the top-level README.
