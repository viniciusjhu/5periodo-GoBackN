# go-back-n

This file provides context about the project for AI assistants.

## Project Overview

- **Ecosystem**: Java

## Tech Stack

- Java Version: 21
- Scaffold: plain-java
- Build Tool: maven
- Testing: junit5

## Project Structure

```
go-back-n/
├── .mvn/                # Maven Wrapper metadata
├── mvnw                 # Maven Wrapper launcher
├── pom.xml              # Maven build definition
├── README.md            # Instrucoes de uso
├── src/main/java/com/example/gobackn/
│   ├── Emissor.java     # Emissor com threads (envio, ACKs, timer)
│   ├── Receptor.java    # Receptor com FSM do Go-Back-N
│   ├── Segmento.java    # Serializacao dos datagramas UDP
│   ├── TipoSegmento.java # Constantes: DATA, ACK, HANDSHAKE, FIN
│   ├── ConfigProtocolo.java # Constantes do protocolo
│   ├── BufferJanela.java    # Buffer circular para retransmissao
│   └── HashUtil.java   # Hash MD5 para verificacao de integridade
├── src/test/java/       # Test suite
```

## Common Commands

- `./mvnw test` - Run tests
- `./mvnw exec:java` - Start the app
- `./mvnw package` - Build the jar

## Maintenance

Keep AGENTS.md updated when:

- Adding/removing dependencies
- Changing project structure
- Adding new features or services
- Modifying build/dev workflows

AI assistants should suggest updates to this file when they notice relevant changes.
