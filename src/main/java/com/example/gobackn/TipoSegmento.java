package com.example.gobackn;

/**
 * Define os tipos de segmento do protocolo Go-Back-N.
 * Cada tipo tem um codigo numerico que vai no primeiro byte do datagrama.
 */
public enum TipoSegmento {

    DATA(0),
    ACK(1),
    HANDSHAKE(2),
    FIN(3);

    private final int codigo;

    TipoSegmento(int codigo) {
        this.codigo = codigo;
    }

    /** Retorna o codigo numerico do tipo, usado na serializacao. */
    public int getCodigo() {
        return codigo;
    }

    /** Converte um codigo numerico de volta para o tipo correspondente. */
    public static TipoSegmento deCodigo(int codigo) {
        for (TipoSegmento t : values()) {
            if (t.codigo == codigo) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de segmento desconhecido: " + codigo);
    }
}
