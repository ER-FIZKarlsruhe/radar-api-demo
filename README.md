# RADAR API examples

Examples for working with the [RADAR](https://radar-service.eu) API.

| Folder | Content |
|--------|---------|
| [`python/`](python/README.md) | Language-neutral introduction: Jupyter notebook, `curl` / Python quick guide and a Python script – **start here** |
| [`groovy/`](groovy/README.md) | Groovy scripts, one folder per use case |
| [`resources/`](resources) | Shared JSON and RADAR XML metadata used by both the Python and the Groovy examples |

## Before you start

- Register a user at the target RADAR environment. The user needs a password – Shibboleth users are **not** supported.
- Request an OAuth client from RADAR staff at info@radar-service.eu.
- Use the test system `https://test.radar-service.eu` while you are learning.

Credentials are configured outside of the code, so none end up in the repository.
See the [Python](python/README.md) and [Groovy](groovy/README.md) READMEs for how to set them up.

## Schemas

To send JSON / XML via the API you need to know its structure. These are the latest schema definitions:

Technical metadata
- https://radar-service.eu/schemas/dataset-technical-metadata/latest
- https://radar-service.eu/schemas/workspace-technical-metadata/latest
- https://radar-service.eu/schemas/contract-technical-metadata/latest

Descriptive metadata
- https://www.radar-service.eu/schemas/descriptive/radar/v09/radar-dataset
- https://www.radar-service.eu/schemas/descriptive/radar/v09/radar-file

## License

Copyright FIZ Karlsruhe - Leibniz-Institut fuer Informationsinfrastruktur GmbH.
Licensed under the [Apache License, Version 2.0](LICENSE).
