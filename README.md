# Protocolo Go-Back-N sobre UDP

Implementação de transferência confiável de arquivos sobre UDP usando o protocolo Go-Back-N, desenvolvida em Java 21 com Maven.

## Pré-requisitos

- **Java 21** (ou superior) instalado
  
## Como funciona

O UDP envia datagramas sem garantias. Este projeto implementa a confiabilidade manualmente:

- **Número de sequência** em cada pacote
- **ACK cumulativo** — o receptor confirma tudo até o pacote recebido em ordem
- **Janela deslizante** de tamanho N — o emissor envia até N pacotes sem esperar ACK
- **Timeout e retransmissão** — se o ACK não chega a tempo, reenvia todos os pacotes pendentes
- **Buffer circular em memória** — pacotes enviados ficam na RAM para retransmissão sem reler o disco
- **Simulação de perda** — o receptor descarta pacotes com probabilidade configurável

## Estrutura do projeto

```
src/main/java/com/example/gobackn/
├── Emissor.java          # Emissor com threads de envio, ACKs e timer
├── Receptor.java         # Receptor com FSM do Go-Back-N
├── Segmento.java         # Serialização/desserialização dos datagramas
├── TipoSegmento.java     # Constantes: DATA, ACK, HANDSHAKE, FIN
├── ConfigProtocolo.java  # Constantes do protocolo
├── BufferJanela.java     # Buffer circular para retransmissão
└── HashUtil.java         # Cálculo de hash MD5 para verificação
```

## Formato do segmento

```
tipo          1 byte    (0=DATA, 1=ACK, 2=HANDSHAKE, 3=FIN)
num_seq       4 bytes   (número de sequência do pacote)
num_ack       4 bytes   (número do último pacote confirmado)
tamanho_dados 2 bytes   (quantidade de bytes válidos no payload)
dados         até 1024 bytes
```

## Compilação

```bash
mvn clean package
```

## Como executar

### 1. Iniciar o Receptor

```bash
java -cp target/go-back-n-0.0.1-SNAPSHOT.jar com.example.gobackn.Receptor 5000
```

O parâmetro `5000` é a porta UDP (opcional, padrão é 5000).

### 2. Iniciar o Emissor

**Windows:**
```bash
java -cp target/go-back-n-0.0.1-SNAPSHOT.jar com.example.gobackn.Emissor arquivo.pdf 127.0.0.1:C:\temp\arquivo_recebido.pdf 8 0.10
```

**Linux:**
```bash
java -cp target/go-back-n-0.0.1-SNAPSHOT.jar com.example.gobackn.Emissor arquivo.pdf 127.0.0.1:/tmp/arquivo_recebido.pdf 8 0.10
```

### Argumentos do Emissor

| Argumento | Descrição | Exemplo |
|-----------|-----------|---------|
| `arquivo_origem` | Caminho do arquivo a enviar | `foto.jpg` |
| `IP:path_destino` | IP do receptor e caminho de destino | `127.0.0.1:C:\temp\recebido.jpg` |
| `tamanho_janela` | Tamanho da janela Go-Back-N | `8` |
| `prob_perda` | Probabilidade de perda simulada (0.0 a 1.0) | `0.10` |

## Concorrência no Emissor

O emissor usa três partes concorrentes:

1. **Thread principal** — lê o arquivo, respeita a janela e envia pacotes
2. **Thread de ACKs** — recebe ACKs cumulativos e avança a base da janela
3. **ScheduledExecutorService** — dispara o timeout do pacote mais antigo não confirmado

A sincronização usa `wait()`/`notifyAll()` para evitar espera ocupada (busy waiting).

## Verificação de integridade

Ao final da transferência, ambos os lados imprimem o hash MD5 do arquivo. Compare os dois valores para confirmar que a transferência foi correta.

## Testes

```bash
mvn test
```

Os testes cobrem serialização de segmentos e comportamento do buffer circular.
