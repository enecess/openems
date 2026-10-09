# Precompiled dev/ecomain deployment files

These branch builds are available for deployment while upstream integration is pending. They use [dev/ecomain source commit 47c814933](https://github.com/enecess/openems/commit/47c8149331db74f05184a00faa2c1e97043a8b9d), synchronized with upstream `3240f7bce`, and include the ecoMain meter driver and installation app plus authentication, UI, and WebSocket fixes. See [manifest.json](manifest.json) for source provenance and image IDs.

## Downloads and verification

| File | Size | Purpose |
| --- | ---: | --- |
| [openems-edge.jar](https://raw.githubusercontent.com/enecess/openems/develop/precompiled/dev-ecomain-2026-10-09/jar/openems-edge.jar) | 63.6 MiB | OpenEMS Edge, including ecoMain |
| [openems-backend.jar](https://raw.githubusercontent.com/enecess/openems/develop/precompiled/dev-ecomain-2026-10-09/jar/openems-backend.jar) | 32.4 MiB | OpenEMS Backend |
| [openems-backend-edge.jar](https://raw.githubusercontent.com/enecess/openems/develop/precompiled/dev-ecomain-2026-10-09/jar/openems-backend-edge.jar) | 15.7 MiB | Backend's Edge WebSocket proxy |
| [Docker bundle part 000](https://raw.githubusercontent.com/enecess/openems/develop/precompiled/dev-ecomain-2026-10-09/docker/openems-images-linux-amd64.tar.gz.part-000) | 95.0 MiB | First part of the five-image offline bundle |
| [Docker bundle part 001](https://raw.githubusercontent.com/enecess/openems/develop/precompiled/dev-ecomain-2026-10-09/docker/openems-images-linux-amd64.tar.gz.part-001) | 94.0 MiB | Second part of the five-image offline bundle |

Alternatively, download this repository and enter `precompiled/dev-ecomain-2026-10-09/`. When downloading files individually, keep the `jar/` and `docker/` directory structure and also download [SHA256SUMS](SHA256SUMS), the [complete Docker bundle checksum](docker/archive.sha256), and the required Compose file.

Run the following commands from this directory. Verify the downloaded files:

```bash
sha256sum -c SHA256SUMS --ignore-missing
```

Docker images support **linux/amd64 only**. JARs require Java 21; check the official platform guidance for ARM compatibility of dependencies such as serial libraries.

## JAR deployment

Follow the official [Getting Started](https://openems.github.io/openems.io/openems/latest/gettingstarted.html), [Edge build](https://openems.github.io/openems.io/openems/latest/edge/build.html), and [Backend build](https://openems.github.io/openems.io/openems/latest/backend/build.html) instructions, using the files above as the download source.

```bash
java -version
java -jar jar/openems-edge.jar
```

If Backend is required, start it in another terminal. In the current source version, Edge connections reach Backend through the Backend Edge proxy:

```bash
java -jar jar/openems-backend.jar
```

```bash
java -jar jar/openems-backend-edge.jar
```

The Apache Felix configuration consoles are `http://<host>:8080/system/console/configMgr`, `:8079/system/console/configMgr`, and `:8078/system/console/configMgr`, respectively. The initial username and password are `admin` / `admin`. Configure devices, controllers, authentication, storage, and connections in these consoles. These builds include no private test-device or server configuration.

For persistent Edge deployment, follow the official [Debian/systemd guide](https://openems.github.io/openems.io/openems/latest/edge/deploy/systemd.html): place the Edge JAR at `/usr/lib/openems/openems-edge.jar`, use `/etc/openems.d/` for configuration, and start the service with:

```bash
/usr/bin/java -Dfelix.cm.dir=/etc/openems.d/ -jar /usr/lib/openems/openems-edge.jar
```

Follow the official guide for the remaining installation steps, service definition, and component configuration.

## Offline Docker deployment

Images use the runtime stages and default configuration from the official Dockerfiles, with this build's precompiled JARs and production UI replacing the compilation stages. Ports, volumes, `EXTRA_JVM_OPTS`, `WEBSOCKET_HOST`, `WEBSOCKET_PORT`, `ENABLE_HTTPS`, and other parameters follow the official conventions. See [build/](build/) for the runtime recipes and [runtime-recipes.json](build/runtime-recipes.json) for upstream recipe paths and checksums. These recipes record the runtime stages used here; downloading them alone is insufficient to rebuild the images.

The five images share one approximately 189 MiB offline bundle, split to meet GitHub's 100 MiB file limit. Download both parts, then reassemble, verify, and import them:

```bash
cat docker/openems-images-linux-amd64.tar.gz.part-* \
  > docker/openems-images-linux-amd64.tar.gz
sha256sum -c docker/archive.sha256
docker load -i docker/openems-images-linux-amd64.tar.gz
```

After importing, no registry pull is required. The bundle contains these local image tags; the tags do not imply publication to Docker Hub:

| Component | Image tag |
| --- | --- |
| Edge | `enecess/openems-edge:dev-ecomain-20261009-47c814933` |
| Backend | `enecess/openems-backend:dev-ecomain-20261009-47c814933` |
| Backend Edge | `enecess/openems-backend-edge:dev-ecomain-20261009-47c814933` |
| UI in Edge mode | `enecess/openems-ui-edge:dev-ecomain-20261009-47c814933` |
| UI in Backend mode | `enecess/openems-ui-backend:dev-ecomain-20261009-47c814933` |

### Edge and UI

Use [compose/edge.yml](compose/edge.yml). Its parameters follow the official [Edge Docker guide](https://openems.github.io/openems.io/openems/latest/edge/deploy/docker.html):

```bash
docker compose -f compose/edge.yml up -d
docker compose -f compose/edge.yml ps
docker compose -f compose/edge.yml logs --tail=100
```

The UI is available at `http://<host>/` and Felix at `http://<host>:8080/system/console/configMgr`. The UI container proxies to Edge using `WEBSOCKET_HOST=openems-edge` and `WEBSOCKET_PORT=8085`. Persistent directories remain `/var/opt/openems/config`, `/var/opt/openems/data`, `/etc/nginx`, and `/var/log/nginx`.

### Backend, Backend Edge, and UI

Use [compose/backend.yml](compose/backend.yml). Its parameters follow the official [Backend Docker guide](https://openems.github.io/openems.io/openems/latest/backend/deploy/docker.html), [Backend Edge image instructions](../../tools/docker/backend-edge/README.md), and [UI Docker guide](https://openems.github.io/openems.io/openems/latest/ui/deploy/docker.html). This example contains the three services supplied in this bundle; deploy and configure InfluxDB separately using the official guide if needed.

```bash
docker compose -f compose/backend.yml up -d
docker compose -f compose/backend.yml ps
docker compose -f compose/backend.yml logs --tail=100
```

The UI is available at `http://<host>/`, Backend Felix at `http://<host>:8079/system/console/configMgr`, and proxy Felix at `http://<host>:8078/system/console/configMgr`.

The current upstream defaults differ from the Compose connection settings. Align these parameters in Felix after the first start:

1. In Backend, set the `Edge.Manager` port to `8083` (the current default configuration file uses `8093`) and `Ui.Websocket` to `8082`.
2. In the proxy's `Backend.Edge.App`, set `Uri=ws://openems-backend:8083` and `Port=8081`. Use the service name between containers; replace the existing default `ws://localhost:8083`.
3. In Edge's `Controller.Api.Backend`, set the URI to `ws://<backend-host>:8081` and match the API key to Backend's Metadata configuration. If Edge is on the same Docker network, the proxy service name `openems-backend-edge` can be used.
4. Configure Metadata, authentication, and Timedata as described in the official documentation. Test environments can use `Metadata.Dummy` / `Timedata.Dummy`; select suitable implementations for production according to the official guide.

The two Compose examples are for Edge mode and Backend mode, respectively. Choose one to start.
