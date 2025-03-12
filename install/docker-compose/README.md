# Getting started with Docker Compose

[Docker Compose](https://docs.docker.com/compose/) is a tool for easily test and run multi-container applications. [Microcks](https://microcks.io/) offers a simple way to set up the minimal required containers to have a functional environment in your local computer.

## Usage

To get started, make sure you have [Docker installed](https://docs.docker.com/get-docker/) on your system.

In your terminal issue the following commands:

1. Clone this repository.

   ```bash
   git clone https://github.com/microcks/microcks.git
   ```

2. Change to the install folder

   ```bash
   cd microcks/install/docker-compose
   ```

3. Spin up the containers

   ```bash
   docker-compose up -d
   ```

This will start the required containers and setup a simple environment for your usage.

Open a new browser tab and point to the `http://localhost:8080` endpoint. This will redirect you to the [Keycloak](https://www.keycloak.org/) Single Sign On page for login. Use the following default credentials:

* **Username:** admin
* **Password:** microcks123

You will be redirected to the main dashboard page. You can now start [using Microcks](https://microcks.io/documentation/tutorials/getting-started/#using-microcks).

