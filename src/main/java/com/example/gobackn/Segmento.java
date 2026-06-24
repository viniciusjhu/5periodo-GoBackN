package com.example.gobackn;

import java.nio.ByteBuffer;

/**
 * Representa um datagrama do protocolo Go-Back-N.
 *
 * Layout do cabecalho (11 bytes):
 *   tipo          1 byte
 *   numSeq        4 bytes
 *   numAck        4 bytes
 *   tamanhoDados  2 bytes
 * Apos o cabecalho vem o payload (ate 1024 bytes).
 */
public class Segmento {

    private final TipoSegmento tipo;
    private final int numSeq;
    private final int numAck;
    private final byte[] dados;
    private final int tamanhoDados;

    /** Construtor privado - use os metodos de fabrica para criar segmentos. */
    private Segmento(TipoSegmento tipo, int numSeq, int numAck, byte[] dados, int tamanhoDados) {
        this.tipo = tipo;
        this.numSeq = numSeq;
        this.numAck = numAck;
        this.dados = dados;
        this.tamanhoDados = tamanhoDados;
    }

    /** Cria um segmento DATA carregando um bloco do arquivo. */
    public static Segmento criarData(int numSeq, byte[] payload, int tamanho) {
        return new Segmento(TipoSegmento.DATA, numSeq, 0, payload, tamanho);
    }

    /** Cria um segmento ACK confirmando recebimento cumulativo ate numAck. */
    public static Segmento criarAck(int numAck) {
        return new Segmento(TipoSegmento.ACK, 0, numAck, null, 0);
    }

    /** Cria um segmento HANDSHAKE com os parametros da sessao no payload. */
    public static Segmento criarHandshake(byte[] parametros) {
        return new Segmento(TipoSegmento.HANDSHAKE, 0, 0, parametros, parametros.length);
    }

    /** Cria a resposta de HANDSHAKE (sem payload) confirmando que o receptor esta pronto. */
    public static Segmento criarRespostaHandshake() {
        return new Segmento(TipoSegmento.HANDSHAKE, 0, 0, null, 0);
    }

    /** Verifica se este segmento e uma resposta de handshake (tipo HANDSHAKE sem payload). */
    public boolean isRespostaHandshake() {
        return tipo == TipoSegmento.HANDSHAKE && tamanhoDados == 0;
    }

    /** Cria um segmento FIN sinalizando fim da transferencia. */
    public static Segmento criarFin() {
        return new Segmento(TipoSegmento.FIN, 0, 0, null, 0);
    }

    /** Cria um ACK especial confirmando o recebimento do FIN (numAck = -1). */
    public static Segmento criarAckFin() {
        return new Segmento(TipoSegmento.ACK, 0, -1, null, 0);
    }

    /** Serializa este segmento em bytes para envio via DatagramSocket. */
    public byte[] paraBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(ConfigProtocolo.CABECALHO_TAMANHO + tamanhoDados);
        buffer.put((byte) tipo.getCodigo());
        buffer.putInt(numSeq);
        buffer.putInt(numAck);
        buffer.putShort((short) tamanhoDados);
        if (tamanhoDados > 0) {
            buffer.put(dados, 0, tamanhoDados);
        }
        return buffer.array();
    }

    /** Desserializa bytes recebidos do socket de volta para um Segmento. */
    public static Segmento deBytes(byte[] bytes, int comprimento) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, comprimento);
        TipoSegmento tipo = TipoSegmento.deCodigo(buffer.get());
        int numSeq = buffer.getInt();
        int numAck = buffer.getInt();
        int tamanhoDados = buffer.getShort() & 0xFFFF;
        byte[] dados = null;
        if (tamanhoDados > 0) {
            dados = new byte[tamanhoDados];
            buffer.get(dados, 0, tamanhoDados);
        }
        return new Segmento(tipo, numSeq, numAck, dados, tamanhoDados);
    }

    public TipoSegmento getTipo() {
        return tipo;
    }

    public int getNumSeq() {
        return numSeq;
    }

    public int getNumAck() {
        return numAck;
    }

    public byte[] getDados() {
        return dados;
    }

    public int getTamanhoDados() {
        return tamanhoDados;
    }
}
