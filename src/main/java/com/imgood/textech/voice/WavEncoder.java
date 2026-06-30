package com.imgood.textech.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class WavEncoder {

    private WavEncoder() {}

    public static byte[] encodePcm16Mono(byte[] pcm, int sampleRate) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dataLength = pcm == null ? 0 : pcm.length;
        writeAscii(out, "RIFF");
        writeIntLE(out, 36 + dataLength);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, 1);
        writeShortLE(out, 1);
        writeIntLE(out, sampleRate);
        writeIntLE(out, sampleRate * 2);
        writeShortLE(out, 2);
        writeShortLE(out, 16);
        writeAscii(out, "data");
        writeIntLE(out, dataLength);
        if (pcm != null) {
            out.write(pcm);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes("US-ASCII"));
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
