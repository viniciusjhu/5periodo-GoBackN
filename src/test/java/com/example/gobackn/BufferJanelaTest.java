package com.example.gobackn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Testes do buffer circular usado para retransmissao.
 */
class BufferJanelaTest {

    @Test
    void guardaEBuscaPacotePorNumeroDeSequencia() {
        BufferJanela buffer = new BufferJanela(4);
        Segmento pacote = Segmento.criarData(0, "dados".getBytes(), 5);

        buffer.guardar(0, pacote);

        assertEquals(pacote, buffer.buscar(0));
    }

    @Test
    void removePacoteDoBuffer() {
        BufferJanela buffer = new BufferJanela(4);
        buffer.guardar(2, Segmento.criarData(2, "dados".getBytes(), 5));

        buffer.remover(2);

        assertNull(buffer.buscar(2));
    }

    @Test
    void comportaComportamentoCircular() {
        BufferJanela buffer = new BufferJanela(4);

        Segmento pacote0 = Segmento.criarData(0, "a".getBytes(), 1);
        Segmento pacote4 = Segmento.criarData(4, "b".getBytes(), 1);

        buffer.guardar(0, pacote0);
        buffer.guardar(4, pacote4);

        assertEquals(pacote4, buffer.buscar(4));
        assertEquals(pacote4, buffer.buscar(0));
    }

    @Test
    void funcionaComJanelaTamanho1() {
        BufferJanela buffer = new BufferJanela(1);
        Segmento pacote = Segmento.criarData(5, "x".getBytes(), 1);

        buffer.guardar(5, pacote);

        assertEquals(pacote, buffer.buscar(5));
    }
}
