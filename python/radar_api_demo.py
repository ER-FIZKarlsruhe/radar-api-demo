#
# Copyright 2026 FIZ Karlsruhe - Leibniz-Institut fuer Informationsinfrastruktur GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""
RADAR API demo - the same use cases as radar_api_tutorial.ipynb, as a plain script.

    python -m pip install -r requirements.txt
    cp .env.example .env        # and fill in your values
    python radar_api_demo.py                   # use the existing WORKSPACE_ID
    python radar_api_demo.py --new-workspace   # create a fresh workspace first
"""
import argparse
import io
import json
import os
import re
import sys
import tarfile
import time
from pathlib import Path
from xml.sax.saxutils import escape

import requests
from dotenv import load_dotenv

HERE = Path(__file__).resolve().parent
METADATA_XML = HERE.parent / "resources" / "RadarDatasetExample.xml"
SAMPLE_DATA = HERE / "sample_data"

load_dotenv(HERE / ".env")
CONTEXT_URL = os.environ.get("CONTEXT_URL", "https://test.radar-service.eu/radar")
INGEST_URL = os.environ.get("INGEST_URL", "https://test.radar-service.eu/radar-ingest")
TIMEOUT = 60


def check(response):
    """Raise with the server's error message if the request failed."""
    if not response.ok:
        sys.exit(f"{response.request.method} {response.url} -> {response.status_code}\n{response.text}")
    return response


def api(method, url, token, retries=5, **kwargs):
    """
    Send one API request. The API is stateless: every request carries the access token, no session and no
    cookies are kept between requests. GET and DELETE are retried on connection problems, e.g. when a proxy
    closes the connection. POST is not retried, so an action is never executed twice.
    """
    headers = {"Authorization": f"Bearer {token}"}
    for attempt in range(retries + 1):
        try:
            return requests.request(method, url, headers=headers, timeout=TIMEOUT, **kwargs)
        except requests.exceptions.ConnectionError:
            if method not in ("GET", "DELETE") or attempt == retries:
                raise
            print(f"  connection problem, retrying {method} {url}")
            time.sleep(2 ** attempt)


def show(title, data):
    print(f"\n=== {title} ===")
    print(json.dumps(data, indent=2) if isinstance(data, (dict, list)) else data)


def get_tokens():
    """Exchange OAuth client + user credentials for an access and refresh token."""
    body = {
        "clientId": os.environ["OAUTH_CLIENT_ID"],
        "clientSecret": os.environ["OAUTH_CLIENT_SECRET"],
        "redirectUrl": os.environ["OAUTH_CLIENT_REDIRECT_URL"],
        "userName": os.environ["RADAR_USER_USERNAME"],
        "userPassword": os.environ["RADAR_USER_PASSWORD"],
    }
    return check(requests.post(f"{CONTEXT_URL}/api/tokens", json=body, timeout=TIMEOUT)).json()


def refresh_tokens(refresh_token):
    """Get a new access token when the old one has expired."""
    body = {
        "clientId": os.environ["OAUTH_CLIENT_ID"],
        "clientSecret": os.environ["OAUTH_CLIENT_SECRET"],
        "refreshToken": refresh_token,
    }
    return check(requests.post(f"{CONTEXT_URL}/api/tokens/refresh", json=body, timeout=TIMEOUT)).json()


def read_workspace(token, workspace_id):
    return check(api("GET", f"{CONTEXT_URL}/api/workspaces/{workspace_id}", token)).json()


def create_workspace(token, contract_id, title):
    workspace = {
        "id": None,
        "parentId": contract_id,
        "descriptiveMetadata": {"title": title, "description": "Created by radar_api_demo.py"},
        "technicalMetadata": {
            "defaultDatasetRetentionPeriod": 10,
            "defaultDatasetResponsibleEmail": "alice_and_bob@foo.bar",
            "diskQuota": 1_000_000_000,
            "archiveQuota": 1_000_000_000,
            "publicationQuota": 1_000_000_000,
        },
    }
    url = f"{CONTEXT_URL}/api/contracts/{contract_id}/workspaces"
    return check(api("POST", url, token, json=workspace)).json()


def create_dataset(token, workspace_id, title):
    dataset = {
        "id": None,
        "parentId": None,
        "state": "PENDING",
        "technicalMetadata": {
            "retentionPeriod": 10,                 # archiving requires 5, 10 or 15 years
            "responsibleEmail": "alice@bob.de",
            "publicationBacklink": "http://alice-bob.de/publication",
            "access": "PRIVATE",                   # archiving requires an access definition
            "schema": {"key": "RDDM", "version": "9.3"},
        },
        "descriptiveMetadata": {"title": title},
    }
    url = f"{CONTEXT_URL}/api/workspaces/{workspace_id}/datasets"
    return check(api("POST", url, token, json=dataset)).json()


def upload_metadata(token, dataset_id, xml_path, title):
    """
    Upload RADAR XML metadata. The title of the example file is replaced by the given one, because two datasets
    in the same workspace must not have the same title.
    """
    xml = re.sub(r"(<ns2:title>)[^<]*(</ns2:title>)", lambda m: m[1] + escape(title) + m[2], xml_path.read_text("utf-8"), count=1)
    files = {"metadataFile": (xml_path.name, xml.encode("utf-8"), "application/xml")}
    url = f"{CONTEXT_URL}/api/datasets/{dataset_id}/metadata"
    return check(api("POST", url, token, files=files)).json()


def read_dataset(token, dataset_id):
    return check(api("GET", f"{CONTEXT_URL}/api/datasets/{dataset_id}", token)).json()


def upload_file(token, dataset_id, file_path):
    """Upload one single file into the root of the dataset. Returns the id of the new file."""
    with open(file_path, "rb") as f:
        url = f"{INGEST_URL}/upload/{dataset_id}/file"
        return check(api("POST", url, token, files={"file": (file_path.name, f)})).text.strip()


def delete_file(token, file_id):
    """Delete a file (only possible while the dataset is not yet archived or published)."""
    return check(api("DELETE", f"{CONTEXT_URL}/api/files/{file_id}", token))


def ingest_archive(token, dataset_id, archive_name, archive_bytes):
    """Upload a zip / tar / tar.gz which is unpacked on the server (preferred way)."""
    url = f"{INGEST_URL}/upload/{dataset_id}/ingest"
    files = {"file": (archive_name, io.BytesIO(archive_bytes))}
    return check(api("POST", url, token, files=files))


FAILED_JOB_STATES = {"error", "exception", "stopped", "killed"}


def wait_for_job(token, entity_id, poll_seconds=2, max_polls=60):
    """
    Uploads and ingests are processed asynchronously. Poll the progress of the job until it is completed.

    entity_id is the target of the job: the file id for a single file upload, the dataset id for an ingest.
    The response looks like {"status": "queued|active|completed|error|...", "progress": "..."}.
    The server forgets a job once it has finished, so the progress then returns 404 instead of completed.
    """
    url = f"{INGEST_URL}/upload/{entity_id}/progress"
    for _ in range(max_polls):
        response = api("GET", url, token)
        if response.status_code == 404:
            print("  job has finished")
            return {"status": "completed"}
        job = check(response).json()
        print("  progress:", job)
        if job["status"] == "completed":
            return job
        if job["status"] in FAILED_JOB_STATES:
            sys.exit(f"Upload job for {entity_id} failed: {job}")
        time.sleep(poll_seconds)
    sys.exit(f"Upload job for {entity_id} did not finish within {poll_seconds * max_polls} seconds")


def read_children(token, kind, entity_id, rows=10):
    """
    All children of a workspace, dataset or folder (kind = workspaces / datasets / folders), page by page.
    rows is the page size, offset the position of the first entry. Folders have a folderName, files a fileName.
    """
    offset = 0
    while True:
        url = f"{CONTEXT_URL}/api/{kind}/{entity_id}/children"
        page = check(api("GET", url, token, params={"rows": rows, "offset": offset})).json()
        data = page.get("data") or []
        yield from data
        offset += len(data)
        if not data or offset >= page["totalHits"]:
            return


def wait_for_children(token, kind, entity_id, poll_seconds=1, max_polls=30):
    """The children are read from the search index, which is updated a moment after an upload has completed."""
    url = f"{CONTEXT_URL}/api/{kind}/{entity_id}/children"
    for _ in range(max_polls):
        if check(api("GET", url, token, params={"rows": 1})).json()["totalHits"] > 0:
            return
        time.sleep(poll_seconds)
    sys.exit(f"{kind}/{entity_id} has no children in the search index after {poll_seconds * max_polls} seconds")


def print_tree(token, kind, entity_id, indent="  "):
    for child in read_children(token, kind, entity_id, rows=2):
        if child.get("folderName") is not None:
            print(f"{indent}+ {child['folderName']}/  ({child['id']})")
            print_tree(token, "folders", child["id"], indent + "    ")
        else:
            print(f"{indent}- {child['fileName']}  ({child['id']})")


def can_upload(token, dataset_id, filename):
    """
    Check whether a file with this name can be uploaded into the root of the dataset: quota, dataset state,
    forbidden characters and names already used at this level. Returns the response: 200 OK or 422 with the reason.
    """
    return api("GET", f"{CONTEXT_URL}/api/datasets/{dataset_id}/canupload", token, params={"filename": filename})


def validate_metadata(token, dataset_id):
    """
    Validate the descriptive metadata against the RADAR schema: {"success": true|false, "details": "..."}.
    The schema requires an identifier, which a dataset only gets when it is archived or published (or when a DOI
    is reserved with POST /api/datasets/{id}/doi), so before that success is false with "identifier is expected".
    """
    return check(api("GET", f"{CONTEXT_URL}/api/datasets/{dataset_id}/metadata/validate", token)).json()


def change_state(token, dataset_id, action):
    """
    Change the state of a dataset. action is one of
      startreview  PENDING -> REVIEW
      endreview    REVIEW  -> PENDING
      archive      PENDING -> ARCHIVED (asynchronous, see wait_for_state)
    """
    return check(api("POST", f"{CONTEXT_URL}/api/datasets/{dataset_id}/{action}", token))


def wait_for_state(token, dataset_id, state, poll_seconds=10, max_polls=90):
    """Poll the dataset until it has reached the given state. Archiving is finished by a server job (default: every 2 minutes)."""
    for _ in range(max_polls):
        current = read_dataset(token, dataset_id)["state"]
        print("  state:", current)
        if current == state:
            return current
        time.sleep(poll_seconds)
    sys.exit(f"Dataset {dataset_id} did not reach state {state} within {poll_seconds * max_polls} seconds")


def build_tar_gz(folder):
    """Pack the text files of a local folder into an in-memory tar.gz (the XML files are metadata, not content)."""
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        for path in sorted(folder.glob("*.txt")):
            tar.add(path, arcname=f"sample_data/{path.name}")
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--new-workspace", action="store_true", help="create a new workspace in CONTRACT_ID")
    args = parser.parse_args()

    # requests uses HTTP_PROXY / HTTPS_PROXY / NO_PROXY from .env or the environment, otherwise the system settings
    proxy = requests.utils.get_environ_proxies(CONTEXT_URL).get("https")
    print("Proxy:", proxy or "none (direct connection)")

    tokens = get_tokens()
    print("Got access token", tokens["access_token"][:8] + "...")

    token = tokens["access_token"]

    if args.new_workspace:
        workspace = create_workspace(token, os.environ["CONTRACT_ID"], f"API demo {int(time.time() * 1000)}")
        show("Created workspace", workspace)
        workspace_id = workspace["id"]
    else:
        workspace_id = os.environ["WORKSPACE_ID"]
        show("Workspace", read_workspace(token, workspace_id))

    # titles must be unique within a workspace, so every run gets its own
    title = f"Dataset created with Python {int(time.time() * 1000)}"
    dataset = create_dataset(token, workspace_id, title)
    show("Created dataset", dataset)
    dataset_id = dataset["id"]

    show("Upload RADAR XML metadata", upload_metadata(token, dataset_id, METADATA_XML, title))
    show("Dataset title after metadata upload", read_dataset(token, dataset_id)["descriptiveMetadata"]["title"])

    file_id = upload_file(token, dataset_id, SAMPLE_DATA / "hello.txt")
    show("Upload single file", f"file id {file_id}")
    show("Upload progress", wait_for_job(token, file_id))

    # An ingest only works in an empty dataset, so remove the single file again
    response = delete_file(token, file_id)
    show("Delete single file", f"HTTP {response.status_code}")

    response = ingest_archive(token, dataset_id, "sample_data.tar.gz", build_tar_gz(SAMPLE_DATA))
    show("Ingest tar.gz", f"HTTP {response.status_code} {response.text}")
    show("Ingest progress", wait_for_job(token, dataset_id))

    wait_for_children(token, "datasets", dataset_id)
    print("\n=== Content of the dataset ===")
    print_tree(token, "datasets", dataset_id)

    print("\n=== Check file names before uploading ===")
    for name in ["results.csv", "sample_data", "a:b.txt"]:
        response = can_upload(token, dataset_id, name)
        print(f"  {name:12} -> {response.status_code} {response.text}")

    # State changes are only possible when the ingest has completed
    change_state(token, dataset_id, "startreview")
    show("Start review", read_dataset(token, dataset_id)["state"])

    # A dataset in review cannot be archived directly, it has to go back to PENDING first
    change_state(token, dataset_id, "endreview")
    show("End review", read_dataset(token, dataset_id)["state"])

    # Before archiving only the missing identifier is expected here, archiving assigns a RADAR identifier
    show("Validate metadata", validate_metadata(token, dataset_id))

    print("\n=== Archive (takes a few minutes) ===")
    change_state(token, dataset_id, "archive")
    print("  final state:", wait_for_state(token, dataset_id, "ARCHIVED"))

    print(f"\nDone. Open the dataset in RADAR: {CONTEXT_URL}  (dataset id {dataset_id})")


if __name__ == "__main__":
    main()
