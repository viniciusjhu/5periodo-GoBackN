package com.example.gobackn;

/**
 * Centraliza as constantes do protocolo Go-Back-N em um so lugar.
 * Assim fica facil ajustar parametros sem procurar pelo codigo inteiro.
 */
public final class ConfigProtocolo {

    private ConfigProtocolo() {
    }

    /** Tamanho maximo do payload em cada pacote DATA (em bytes). */
    public static final int TAMANHO_PAYLOAD = 1024;

    /** Porta UDP padrao usada pelo receptor. */
    public static final int PORTA_PADRAO = 5000;

    /** Tempo de espera por ACK antes de retransmitir (em milissegundos). */
    public static final long TIMEOUT_MS = 1000;

    /** Tamanho do cabecalho: tipo(1) + numSeq(4) + numAck(4) + tamanhoDados(2) = 11 bytes. */
    public static final int CABECALHO_TAMANHO = 11;

    /** Tempo que o receptor espera por FIN retransmitido antes de encerrar (em ms). */
    public static final long TEMPO_ESPERA_FIN_MS = 5000;
}
