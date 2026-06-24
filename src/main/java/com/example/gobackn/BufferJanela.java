package com.example.gobackn;

/**
 * Buffer circular que guarda os pacotes enviados e ainda nao confirmados.
 *
 * Como a janela do Go-Back-N tem tamanho N, so precisamos guardar os
 * ultimos N pacotes. Usamos seqNum % N como indice do array, o que faz
 * o buffer "circular" automaticamente quando os numeros de sequencia crescem.
 *
 * Assim, em caso de timeout, reenviamos direto da memoria RAM sem reler
 * o arquivo do disco.
 */
public class BufferJanela {

    private final Segmento[] pacotes;
    private final int tamanho;

    /** Cria um buffer com espaco para 'tamanho' pacotes (= tamanho da janela). */
    public BufferJanela(int tamanho) {
        this.tamanho = tamanho;
        this.pacotes = new Segmento[tamanho];
    }

    /** Guarda um segmento na posicao correspondente ao seu numero de sequencia. */
    public void guardar(int seqNum, Segmento segmento) {
        pacotes[indice(seqNum)] = segmento;
    }

    /** Recupera o segmento guardado para um dado numero de sequencia. */
    public Segmento buscar(int seqNum) {
        return pacotes[indice(seqNum)];
    }

    /** Remove um segmento do buffer (usado quando o ACK chega e confirma o pacote). */
    public void remover(int seqNum) {
        pacotes[indice(seqNum)] = null;
    }

    /** Converte um numero de sequencia em indice do array (o "truque" circular). */
    private int indice(int seqNum) {
        return seqNum % tamanho;
    }
}
