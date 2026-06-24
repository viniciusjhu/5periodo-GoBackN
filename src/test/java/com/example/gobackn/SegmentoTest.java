package com.example.gobackn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Testes de serializacao e desserializacao da classe Segmento.
 * Garante que todo segmento vira bytes e volta corretamente.
 */
class SegmentoTest {

    @Test
    void serializaEDesserializaDataComPayload() {
        byte[] payload = "conteudo do arquivo".getBytes();
        Segmento original = Segmento.criarData(5, payload, payload.length);

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(TipoSegmento.DATA, reconstruido.getTipo());
        assertEquals(5, reconstruido.getNumSeq());
        assertEquals(payload.length, reconstruido.getTamanhoDados());
        assertArrayEquals(payload, reconstruido.getDados());
    }

    @Test
    void serializaEDesserializaAck() {
        Segmento original = Segmento.criarAck(10);

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(TipoSegmento.ACK, reconstruido.getTipo());
        assertEquals(10, reconstruido.getNumAck());
        assertEquals(0, reconstruido.getTamanhoDados());
    }

    @Test
    void serializaEDesserializaHandshake() {
        byte[] params = "0.10;1048576;/tmp/saida.pdf".getBytes();
        Segmento original = Segmento.criarHandshake(params);

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(TipoSegmento.HANDSHAKE, reconstruido.getTipo());
        assertArrayEquals(params, reconstruido.getDados());
    }

    @Test
    void serializaEDesserializaFin() {
        Segmento original = Segmento.criarFin();

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(TipoSegmento.FIN, reconstruido.getTipo());
        assertEquals(0, reconstruido.getTamanhoDados());
    }

    @Test
    void ackFinTemNumAckNegativo() {
        Segmento original = Segmento.criarAckFin();

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(TipoSegmento.ACK, reconstruido.getTipo());
        assertEquals(-1, reconstruido.getNumAck());
    }

    @Test
    void dataComPayloadMenorQue1024PreservaTamanho() {
        byte[] payload = new byte[317];
        Segmento original = Segmento.criarData(0, payload, 317);

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(317, reconstruido.getTamanhoDados());
    }

    @Test
    void dataComPayloadMaximoFunciona() {
        byte[] payload = new byte[ConfigProtocolo.TAMANHO_PAYLOAD];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }
        Segmento original = Segmento.criarData(42, payload, payload.length);

        byte[] bytes = original.paraBytes();
        Segmento reconstruido = Segmento.deBytes(bytes, bytes.length);

        assertEquals(42, reconstruido.getNumSeq());
        assertEquals(1024, reconstruido.getTamanhoDados());
        assertArrayEquals(payload, reconstruido.getDados());
    }
}
