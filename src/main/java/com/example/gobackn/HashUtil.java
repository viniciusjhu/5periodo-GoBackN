package com.example.gobackn;

import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * Utilitario para calcular hash de arquivos usando MD5.
 * Serve para verificar se o arquivo recebido e identico ao enviado.
 */
public final class HashUtil {

    private HashUtil() {
    }

    /** Calcula o hash MD5 de um arquivo e retorna como string hexadecimal. */
    public static String md5Arquivo(String caminho) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(caminho)) {
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = fis.read(buffer)) != -1) {
                md.update(buffer, 0, lidos);
            }
        }
        return bytesParaHex(md.digest());
    }

    /** Converte um array de bytes em uma string hexadecimal legivel. */
    private static String bytesParaHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
