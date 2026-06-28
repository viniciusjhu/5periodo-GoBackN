package com.example.gobackn;

import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Emissor do protocolo Go-Back-N.
 *
 * Le o arquivo de origem em blocos, envia os pacotes usando uma janela
 * deslizante de tamanho N, recebe ACKs cumulativos em uma thread separada
 * e retransmite todos os pacotes pendentes em caso de timeout.
 *
 * Usa tres partes concorrentes:
 * - Thread principal: le o arquivo e envia pacotes respeitando a janela
 * - Thread de ACKs: fica ouvindo ACKs e movendo a base da janela
 * - ScheduledExecutorService: dispara o timeout do pacote mais antigo
 */
public class Emissor {

    private final DatagramSocket socket;
    private final InetAddress enderecoDestino;
    private final int portaDestino;
    private final int windowSize;
    private final double probPerda;
    private final String caminhoOrigem;
    private final String caminhoDestino;
    private final long tamanhoArquivo;

    /** Lock que protege base, nextSeqNum, buffer e flags compartilhadas. */
    private final Object lock = new Object();

    /** Numero de sequencia do pacote mais antigo ainda nao confirmado. */
    private int base = 0;

    /** Proximo numero de sequencia a ser enviado. */
    private int nextSeqNum = 0;

    /** Guarda os pacotes enviados para retransmissao sem reler o disco. */
    private final BufferJanela buffer;

    private boolean esperandoFinAck = false;
    private boolean finAckRecebido = false;

    /** Scheduler que dispara o temporizador unico do Go-Back-N. */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerFuture;

    private int pacotesEnviados = 0;
    private int retransmissoes = 0;
    private int totalPacotes;
    private long inicioTransferenciaMs;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Uso: java Emissor <arquivo_origem> <IP_destino>:<path_destino> <tamanho_janela> <prob_perda>");
            System.exit(1);
        }

        // Garante que a pasta arqsEmissor/ existe e le o arquivo de la
        new File("arqsEmissor").mkdirs();
        String caminhoOrigem = new File("arqsEmissor", args[0]).getPath();
        String destino = args[1];
        int sep = destino.indexOf(':');
        String ip = destino.substring(0, sep);
        String caminhoDestino = destino.substring(sep + 1);
        // Envia ao receptor apenas o nome do arquivo (sem o caminho completo)
        String arquivoDestino = new File(caminhoDestino).getName();
        int windowSize = Integer.parseInt(args[2]);
        double probPerda = Double.parseDouble(args[3]);

        long tamanhoArquivo = new File(caminhoOrigem).length();

        try (DatagramSocket socket = new DatagramSocket()) {
            Emissor emissor = new Emissor(socket, InetAddress.getByName(ip),
                    ConfigProtocolo.PORTA_PADRAO, windowSize, probPerda,
                    caminhoOrigem, arquivoDestino, tamanhoArquivo);
            emissor.executar();
        }
    }

    public Emissor(DatagramSocket socket, InetAddress enderecoDestino, int portaDestino,
                   int windowSize, double probPerda, String caminhoOrigem,
                   String caminhoDestino, long tamanhoArquivo) {
        this.socket = socket;
        this.enderecoDestino = enderecoDestino;
        this.portaDestino = portaDestino;
        this.windowSize = windowSize;
        this.probPerda = probPerda;
        this.caminhoOrigem = caminhoOrigem;
        this.caminhoDestino = caminhoDestino;
        this.tamanhoArquivo = tamanhoArquivo;
        this.buffer = new BufferJanela(windowSize);
    }

    /** Executa todo o fluxo: handshake, envio de dados, FIN e encerramento. */
    public void executar() throws Exception {
        System.out.println("Enviando arquivo: " + caminhoOrigem);
        System.out.println("  Tamanho: " + tamanhoArquivo + " bytes");
        System.out.println("  Janela: " + windowSize);
        System.out.println("  Probabilidade de perda: " + probPerda);

        // 1. Handshake com retransmissao
        enviarHandshake();
        esperarRespostaHandshake();
        System.out.println("Handshake confirmado pelo receptor.");

        // Calcula o total de pacotes para mostrar progresso
        totalPacotes = (int) Math.ceil((double) tamanhoArquivo / ConfigProtocolo.TAMANHO_PAYLOAD);
        inicioTransferenciaMs = System.currentTimeMillis();

        // 2. Inicia thread de recepcao de ACKs
        Thread threadAcks = new Thread(this::receberAcks, "Thread-ACKs");
        threadAcks.start();

        // 3. Le o arquivo e envia pacotes respeitando a janela
        try (FileInputStream fis = new FileInputStream(caminhoOrigem)) {
            byte[] bloco = new byte[ConfigProtocolo.TAMANHO_PAYLOAD];
            int lidos;
            while ((lidos = fis.read(bloco)) != -1) {
                // Copia os bytes lidos (o array bloco e reutilizado na proxima iteracao)
                byte[] dados = Arrays.copyOf(bloco, lidos);
                synchronized (lock) {
                    // Espera ate haver espaco na janela (sem busy waiting)
                    while (nextSeqNum >= base + windowSize) {
                        lock.wait();
                    }
                    // Cria, guarda no buffer e envia o pacote
                    Segmento segmento = Segmento.criarData(nextSeqNum, dados, dados.length);
                    buffer.guardar(nextSeqNum, segmento);
                    enviarSegmento(segmento);
                    pacotesEnviados++;
                    // Inicia o timer se este e o pacote mais antigo sem ACK
                    if (base == nextSeqNum) {
                        reiniciarTimer();
                    }
                    nextSeqNum++;
                    // Mostra progresso a cada 10 pacotes enviados
                    if (nextSeqNum % 10 == 0 || nextSeqNum == totalPacotes) {
                        System.out.printf("Progresso: %d/%d pacotes enviados, %d confirmados%n",
                                nextSeqNum, totalPacotes, base);
                    }
                }
            }
        }

        // 4. Aguarda todos os ACKs dos dados
        synchronized (lock) {
            while (base < nextSeqNum) {
                lock.wait();
            }
        }
        System.out.println("Todos os dados confirmados.");

        // 5. Envia FIN e aguarda ACK do FIN (com retransmissao em timeout)
        synchronized (lock) {
            esperandoFinAck = true;
            enviarFin();
            reiniciarTimer();
            while (!finAckRecebido) {
                lock.wait();
            }
        }
        System.out.println("ACK do FIN recebido.");

        // 6. Encerra threads e socket
        cancelarTimer();
        scheduler.shutdown();
        socket.close();
        threadAcks.join();

        // 7. Estatisticas finais
        long tempoTotalMs = System.currentTimeMillis() - inicioTransferenciaMs;
        double tempoSeg = tempoTotalMs / 1000.0;
        double throughput = tamanhoArquivo / tempoSeg / 1024.0;
        System.out.println("Transferencia concluida!");
        System.out.println("  Pacotes enviados: " + pacotesEnviados);
        System.out.println("  Retransmissoes: " + retransmissoes);
        System.out.printf("  Tempo total: %.3f s%n", tempoSeg);
        System.out.printf("  Throughput: %.2f KB/s%n", throughput);
        System.out.println("  Hash MD5 do arquivo original: " + HashUtil.md5Arquivo(caminhoOrigem));
    }

    /** Loop da thread de ACKs: recebe ACKs e avanca a base da janela. */
    private void receberAcks() {
        byte[] bufferRecepcao = new byte[ConfigProtocolo.CABECALHO_TAMANHO + ConfigProtocolo.TAMANHO_PAYLOAD];
        try {
            while (true) {
                DatagramPacket pacote = new DatagramPacket(bufferRecepcao, bufferRecepcao.length);
                socket.receive(pacote);
                Segmento ack = Segmento.deBytes(pacote.getData(), pacote.getLength());

                if (ack.getTipo() != TipoSegmento.ACK) {
                    continue;
                }

                synchronized (lock) {
                    int numAck = ack.getNumAck();

                    if (numAck == -1 && esperandoFinAck) {
                        // ACK do FIN: transferencia concluida
                        finAckRecebido = true;
                        cancelarTimer();
                        lock.notifyAll();
                    } else if (numAck >= 0 && numAck >= base) {
                        // ACK de dados: remove pacotes confirmados e avanca base
                        for (int i = base; i <= numAck; i++) {
                            buffer.remover(i);
                        }
                        base = numAck + 1;
                        if (base == nextSeqNum) {
                            cancelarTimer();
                        } else {
                            reiniciarTimer();
                        }
                        lock.notifyAll();
                    }
                }
            }
        } catch (Exception e) {
            // Socket fechado: encerra a thread
        }
    }

    /** Trata o timeout: retransmite os pacotes pendentes (ou o FIN). */
    private void tratarTimeout() {
        try {
            synchronized (lock) {
                if (esperandoFinAck && !finAckRecebido) {
                    // Timeout do FIN: reenvia
                    enviarFin();
                    reiniciarTimer();
                } else if (base < nextSeqNum) {
                    // Timeout de dados: reenvia todos os pacotes da base ate nextSeqNum-1
                    for (int i = base; i < nextSeqNum; i++) {
                        Segmento segmento = buffer.buscar(i);
                        if (segmento != null) {
                            enviarSegmento(segmento);
                            retransmissoes++;
                        }
                    }
                    reiniciarTimer();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Inicia (ou reinicia) o temporizador unico do pacote mais antigo sem ACK. */
    private void reiniciarTimer() {
        cancelarTimer();
        timerFuture = scheduler.schedule(this::tratarTimeout,
                ConfigProtocolo.TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /** Cancela o temporizador atual. */
    private void cancelarTimer() {
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    /** Envia um segmento via UDP para o receptor. */
    private void enviarSegmento(Segmento segmento) {
        try {
            byte[] bytes = segmento.paraBytes();
            socket.send(new DatagramPacket(bytes, bytes.length, enderecoDestino, portaDestino));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar segmento", e);
        }
    }

    /** Envia o segmento FIN. */
    private void enviarFin() {
        enviarSegmento(Segmento.criarFin());
    }

    /** Envia o handshake com os parametros da sessao. */
    private void enviarHandshake() {
        String payload = probPerda + ";" + tamanhoArquivo + ";" + caminhoDestino;
        Segmento handshake = Segmento.criarHandshake(payload.getBytes(StandardCharsets.UTF_8));
        enviarSegmento(handshake);
    }

    /** Espera a resposta de handshake do receptor, retransmitindo em caso de timeout. */
    private void esperarRespostaHandshake() throws Exception {
        byte[] bufferRecepcao = new byte[ConfigProtocolo.CABECALHO_TAMANHO + ConfigProtocolo.TAMANHO_PAYLOAD];
        socket.setSoTimeout((int) ConfigProtocolo.TIMEOUT_MS);
        while (true) {
            try {
                DatagramPacket pacote = new DatagramPacket(bufferRecepcao, bufferRecepcao.length);
                socket.receive(pacote);
                Segmento segmento = Segmento.deBytes(pacote.getData(), pacote.getLength());
                if (segmento.isRespostaHandshake()) {
                    break;
                }
            } catch (SocketTimeoutException e) {
                // Timeout: reenvia o handshake
                enviarHandshake();
            }
        }
        socket.setSoTimeout(0);
    }
}
