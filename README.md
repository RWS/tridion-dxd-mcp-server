# Tridion DXD MCP Server

This repository contains an example Model Context Protocol (MCP) Server for Tridion DXD.


## Building

This project requires Java 21 and a recent version of Maven. To build:
```shell
mvn clean install -U
```


## Running

To run this application, you need a running Tridion DXD Content Service and Token Service.

1. Set the following Environment Variables:
   DXD_CLIENT_SECRET
   DXD_CONTENT_URL
   DXD_TOKEN_URL
2. Use the Spring Boot Maven Plugin:
   ```shell
   mvn spring-boot:run
   ```

## Debugging

The MCP Inspector is a useful tool for debugging MCP Servers. To run it:
```shell
npx @modelcontextprotocol/inspector
```
See https://modelcontextprotocol.io/docs/tools/inspector

