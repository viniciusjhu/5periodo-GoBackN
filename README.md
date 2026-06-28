# 📦 Protocolo Go-Back-N — Transferência Confiável sobre UDP

> Implementação didática do protocolo Go-Back-N em Java puro, com concorrência real (threads), buffer circular em memória, temporizador dedicado e simulação de perda de pacotes.

---

## 🌟 O que é este projeto?

O **Go-Back-N** é um protocolo de transferência confiável de arquivos construído sobre UDP. O UDP não oferece garantias de entrega, ordem ou integridade — esta implementação reconstrói essas garantias manualmente na camada de aplicação.

Se você já estudou Redes de Computadores, sabe que o Go-Back-N é um protocolo de janela deslizante onde o emissor envia vários pacotes sem esperar confirmação, e o receptor só aceita pacotes em ordem. Este projeto materializa exatamente essa teoria em código Java legível e comentado.

---

## ✨ Recursos Principais

* **Janela Deslizante de Tamanho N**: o emissor envia até N pacotes sem esperar por um ACK.
* **ACK Cumulativo**: o receptor confirma todos os pacotes até o último recebido em ordem.
* **Concorrência Real**: o emissor roda 3 threads concorrentes (envio, recepção de ACKs e temporizador).
* **Buffer Circular em Memória**: pacotes enviados ficam na RAM para retransmissão instantânea sem reler o disco.
* **Simulação de Perda**: o receptor descarta pacotes com probabilidade configurável para testar a robustez.
* **Temporizador com Retransmissão**: em caso de timeout, todos os pacotes pendentes são reenviados.
* **Verificação de Integridade**: hash MD5 calculado em ambos os lados para confirmar que o arquivo chegou intacto.

---

## 🛠️ Stack Tecnológica

* **Linguagem**: Java 21
* **Build**: Maven (wrapper incluso)
* **Testes**: JUnit 5
* **Concorrência**: `java.util.concurrent.ScheduledExecutorService`, `wait()`/`notifyAll()`
* **Rede**: `java.net.DatagramSocket` (UDP)

---

## 🚀 Como Executar o Projeto

Subir o projeto na sua máquina é muito simples. Siga o passo a passo abaixo:

### 📋 Pré-requisitos

* **Java 21** (ou superior) instalado
* Variável de ambiente `JAVA_HOME` apontando para a pasta do JDK

Para verificar se está configurado corretamente:

```bash
java -version
echo %JAVA_HOME%   # Windows CMD
echo $JAVA_HOME    # Linux/Mac
```

---

### 1️⃣ Compilando o Projeto

No terminal, na pasta raiz do projeto, execute:

```bash
.\mvnw clean package
```

*Este comando compila todo o código, roda os testes e gera o arquivo JAR em `target/go-back-n-1.0.jar`.*

---

### 2️⃣ Preparando os Arquivos

1. Coloque o arquivo que deseja enviar dentro da pasta `arqsEmissor/`.
2. A pasta `arqsReceptor/` será usada automaticamente pelo Receptor para salvar o arquivo recebido.

> As pastas são criadas automaticamente se não existirem.

---

### 3️⃣ Iniciando o Receptor

Abra um terminal e execute:

```bash
java -cp target/go-back-n-1.0.jar com.example.gobackn.Receptor 5000
```

O parâmetro `5000` é a porta UDP (opcional, padrão é 5000). O Receptor vai ficar escutando, aguardando o handshake do Emissor.

---

### 4️⃣ Iniciando o Emissor

Abra **outro terminal** e execute:

```bash
java -cp target/go-back-n-1.0.jar com.example.gobackn.Emissor [arquivo_origem] [IP:arquivo_destino] [tamanho_janela] [prob_perda]
```

#### Argumentos do Emissor

| Argumento | Descrição | Exemplo |
|-----------|-----------|---------|
| `arquivo_origem` | Nome do arquivo dentro de `arqsEmissor/` | `foto.jpg` |
| `IP:arquivo_destino` | IP do receptor e nome do arquivo de destino (salvo em `arqsReceptor/`) | `127.0.0.1:recebido.jpg` |
| `tamanho_janela` | Tamanho da janela Go-Back-N | `8` |
| `prob_perda` | Probabilidade de perda simulada (0.0 a 1.0) | `0.10` |

---

## 📂 Estrutura do Projeto

```
5periodo-GoBackN/
├── arqsEmissor/                 # Coloque aqui o arquivo a ser enviado
├── arqsReceptor/                # O arquivo recebido aparece aqui
├── src/main/java/com/example/gobackn/
│   ├── Emissor.java             # Emissor com threads (envio, ACKs, timer)
│   ├── Receptor.java            # Receptor com FSM do Go-Back-N
│   ├── Segmento.java            # Serialização dos datagramas UDP
│   ├── TipoSegmento.java        # Constantes: DATA, ACK, HANDSHAKE, FIN
│   ├── ConfigProtocolo.java     # Constantes do protocolo
│   ├── BufferJanela.java        # Buffer circular para retransmissão
│   └── HashUtil.java            # Hash MD5 para verificação de integridade
├── src/test/java/               # Testes unitários
├── pom.xml                      # Configuração do Maven
└── mvnw / mvnw.cmd              # Maven Wrapper
```

---

## 🧩 Formato do Segmento

Cada datagrama UDP carrega um cabeçalho de 11 bytes seguido do payload:

```
tipo           1 byte    (0=DATA, 1=ACK, 2=HANDSHAKE, 3=FIN)
num_seq        4 bytes   (número de sequência do pacote)
num_ack        4 bytes   (número do último pacote confirmado)
tamanho_dados  2 bytes   (quantidade de bytes válidos no payload)
dados          até 1024 bytes
```

---

## ⚙️ Concorrência no Emissor

O emissor usa três partes concorrentes para implementar o Go-Back-N de forma assíncrona:

1. **Thread Principal** — lê o arquivo, respeita a janela e envia pacotes. Quando a janela enche, dorme com `wait()`.
2. **Thread de ACKs** — fica ouvindo ACKs cumulativos e avança a base da janela com `notifyAll()`.
3. **ScheduledExecutorService** — dispara o timeout do pacote mais antigo não confirmado e retransmite todos os pendentes.

A sincronização usa `wait()`/`notifyAll()` para evitar espera ocupada (busy waiting).

---

## 🧪 Testes

```bash
.\mvnw test
```

Os testes cobrem serialização/desserialização de segmentos e comportamento do buffer circular.

---

## 🔐 Verificação de Integridade

Ao final da transferência, ambos os lados imprimem o hash MD5 do arquivo. Compare os dois valores para confirmar que a transferência foi correta:

```
Emissor:   Hash MD5 do arquivo original: E7252C3D21741C0A130E059EB2D96CAA
Receptor:  Hash MD5 do arquivo recebido: E7252C3D21741C0A130E059EB2D96CAA
```

Se os hashes forem idênticos, o arquivo foi transferido sem corrupção. 🎉
