package com.example.gobackn;

import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Receptor do protocolo Go-Back-N.
 *
 * Escuta em uma porta UDP, recebe o handshake do emissor, responde confirmando
 * que esta pronto, recebe os pacotes de dados em ordem, simula perda de pacotes
 * conforme a probabilidade informada, grava o arquivo no destino e envia ACKs
 * cumulativos. Ao receber o FIN, envia um ACK de FIN e aguarda alguns segundos
 * por possiveis retransmissoes do FIN antes de encerrar.
 */
public class Receptor {

    public static void main(String[] args) throws Exception {
        int porta = ConfigProtocolo.PORTA_PADRAO;
        if (args.length >= 1) {
            porta = Integer.parseInt(args[0]);
        }

        byte[] bufferRecepcao = new byte[ConfigProtocolo.CABECALHO_TAMANHO + ConfigProtocolo.TAMANHO_PAYLOAD];
        Random random = new Random();

        try (DatagramSocket socket = new DatagramSocket(porta)) {
            System.out.println("Receptor escutando na porta " + porta);

            // 1. Espera o handshake do emissor
            DatagramPacket pacoteRecebido = new DatagramPacket(bufferRecepcao, bufferRecepcao.length);
            socket.receive(pacoteRecebido);

            Segmento handshake = Segmento.deBytes(pacoteRecebido.getData(), pacoteRecebido.getLength());
            String[] parametros = new String(handshake.getDados(), StandardCharsets.UTF_8).split(";", 3);
            double probPerda = Double.parseDouble(parametros[0]);
            long tamanhoArquivo = Long.parseLong(parametros[1]);
            String caminhoDestino = parametros[2];

            System.out.println("Handshake recebido:");
            System.out.println("  Arquivo destino: " + caminhoDestino);
            System.out.println("  Tamanho esperado: " + tamanhoArquivo + " bytes");
            System.out.println("  Probabilidade de perda: " + probPerda);

            InetAddress enderecoEmissor = pacoteRecebido.getAddress();
            int portaEmissor = pacoteRecebido.getPort();

            // 2. Responde o handshake confirmando que esta pronto
            enviarRespostaHandshake(socket, enderecoEmissor, portaEmissor);

            // 3. Abre o arquivo e fica recebendo dados ate chegar o FIN
            long bytesRecebidos = 0;
            int expectedSeqNum = 0;

            // Contadores de estatisticas
            int pacotesDataVistos = 0;
            int pacotesAceitos = 0;
            int perdasSimuladas = 0;
            int descartesForaDeOrdem = 0;

            try (FileOutputStream arquivo = new FileOutputStream(caminhoDestino)) {
                while (true) {
                    pacoteRecebido = new DatagramPacket(bufferRecepcao, bufferRecepcao.length);
                    socket.receive(pacoteRecebido);
                    Segmento segmento = Segmento.deBytes(pacoteRecebido.getData(), pacoteRecebido.getLength());

                    if (segmento.getTipo() == TipoSegmento.DATA) {
                        pacotesDataVistos++;

                        if (segmento.getNumSeq() == expectedSeqNum) {
                            // Pacote em ordem: decide se simula perda
                            if (random.nextDouble() < probPerda) {
                                // Perda simulada: descarta silenciosamente (sem ACK)
                                perdasSimuladas++;
                            } else {
                                // Nao perdeu: grava apenas os bytes validos e envia ACK
                                arquivo.write(segmento.getDados(), 0, segmento.getTamanhoDados());
                                bytesRecebidos += segmento.getTamanhoDados();
                                expectedSeqNum++;
                                pacotesAceitos++;
                                enviarAck(socket, expectedSeqNum - 1, enderecoEmissor, portaEmissor);
                            }
                        } else {
                            // Pacote fora de ordem: descarta e reenvia o ultimo ACK
                            descartesForaDeOrdem++;
                            enviarAck(socket, expectedSeqNum - 1, enderecoEmissor, portaEmissor);
                        }
                    } else if (segmento.getTipo() == TipoSegmento.FIN) {
                        // Fim da transferencia: envia ACK do FIN
                        enviarAckFin(socket, enderecoEmissor, portaEmissor);
                        break;
                    }
                }
            }

            // 4. Estatisticas finais
            System.out.println("Arquivo recebido com sucesso!");
            System.out.println("  Bytes gravados: " + bytesRecebidos);
            System.out.println("  Ultimo seq recebido: " + (expectedSeqNum - 1));
            System.out.println("  Pacotes DATA vistos: " + pacotesDataVistos);
            System.out.println("  Pacotes aceitos em ordem: " + pacotesAceitos);
            System.out.println("  Perdas simuladas: " + perdasSimuladas);
            System.out.println("  Descartes fora de ordem: " + descartesForaDeOrdem);
            if (pacotesDataVistos > 0) {
                double taxaPerda = (double) perdasSimuladas / pacotesDataVistos * 100;
                System.out.printf("  Taxa de perda efetiva: %.2f%%%n", taxaPerda);
            }
            System.out.println("  Hash MD5 do arquivo recebido: " + HashUtil.md5Arquivo(caminhoDestino));

            // 5. Janela de encerramento: aguarda FIN retransmitido por alguns segundos
            socket.setSoTimeout((int) ConfigProtocolo.TEMPO_ESPERA_FIN_MS);
            try {
                while (true) {
                    pacoteRecebido = new DatagramPacket(bufferRecepcao, bufferRecepcao.length);
                    socket.receive(pacoteRecebido);
                    Segmento segmento = Segmento.deBytes(pacoteRecebido.getData(), pacoteRecebido.getLength());
                    if (segmento.getTipo() == TipoSegmento.FIN) {
                        // FIN retransmitido: o ACK anterior se perdeu, reenvia
                        enviarAckFin(socket, enderecoEmissor, portaEmissor);
                    }
                }
            } catch (SocketTimeoutException e) {
                // Tempo esgotado sem FIN retransmitido: seguro encerrar
            }
        }
    }

    /** Envia um ACK confirmando recebimento cumulativo ate numAck. */
    private static void enviarAck(DatagramSocket socket, int numAck, InetAddress destino, int porta) throws Exception {
        Segmento ack = Segmento.criarAck(numAck);
        byte[] bytes = ack.paraBytes();
        socket.send(new DatagramPacket(bytes, bytes.length, destino, porta));
    }

    /** Envia o ACK especial confirmando o recebimento do FIN. */
    private static void enviarAckFin(DatagramSocket socket, InetAddress destino, int porta) throws Exception {
        Segmento ackFin = Segmento.criarAckFin();
        byte[] bytes = ackFin.paraBytes();
        socket.send(new DatagramPacket(bytes, bytes.length, destino, porta));
    }

    /** Envia a resposta de handshake avisando que o receptor esta pronto. */
    private static void enviarRespostaHandshake(DatagramSocket socket, InetAddress destino, int porta) throws Exception {
        Segmento resposta = Segmento.criarRespostaHandshake();
        byte[] bytes = resposta.paraBytes();
        socket.send(new DatagramPacket(bytes, bytes.length, destino, porta));
    }
}
