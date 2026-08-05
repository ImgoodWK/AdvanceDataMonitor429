package com.imgood.textech.webae.network;

import java.io.ByteArrayOutputStream;

/**
 * Shared bounds for WebAE binary packet transfers.
 *
 * <p>Forge 1.7.10 custom payloads have a 32767 byte ceiling.  Keeping the
 * binary portion at 24 KiB leaves room for packet metadata and length
 * framing, including the discriminator added by the network wrapper.</p>
 */
public final class WebAeBinaryTransfer {

    public static final int MAX_PACKET_CHUNK_BYTES = 24 * 1024;
    public static final long SESSION_TTL_MS = 120000L;

    private WebAeBinaryTransfer() {}

    public static int chunkCount(int totalBytes, int maxTotalBytes) {
        if (totalBytes <= 0 || maxTotalBytes <= 0 || totalBytes > maxTotalBytes) {
            return 0;
        }
        return (totalBytes + MAX_PACKET_CHUNK_BYTES - 1) / MAX_PACKET_CHUNK_BYTES;
    }

    public static byte[] copyChunk(byte[] bytes, int chunkIndex) {
        if (bytes == null || chunkIndex < 0) {
            return null;
        }
        long offsetLong = (long) chunkIndex * MAX_PACKET_CHUNK_BYTES;
        if (offsetLong >= bytes.length) {
            return null;
        }
        int offset = (int) offsetLong;
        int length = Math.min(MAX_PACKET_CHUNK_BYTES, bytes.length - offset);
        byte[] chunk = new byte[length];
        System.arraycopy(bytes, offset, chunk, 0, length);
        return chunk;
    }

    /**
     * A single-use, strictly ordered accumulator.  Callers must discard the
     * instance after {@link #accept(int, int, byte[])} returns the final
     * payload or throws.
     */
    public static final class SequentialAssembler {

        private final int maxTotalBytes;
        private final int maxChunks;
        private ByteArrayOutputStream output;
        private int totalChunks;
        private int nextIndex;
        private int receivedBytes;
        private long lastTouchedMs;

        public SequentialAssembler(int maxTotalBytes, int maxChunks) {
            if (maxTotalBytes <= 0 || maxChunks <= 0) {
                throw new IllegalArgumentException("Invalid binary transfer limits");
            }
            this.maxTotalBytes = maxTotalBytes;
            this.maxChunks = maxChunks;
            this.lastTouchedMs = System.currentTimeMillis();
        }

        public synchronized byte[] accept(int index, int total, byte[] chunk) {
            touch();
            if (total < 1 || total > maxChunks || index < 0 || index >= total
                || chunk == null || chunk.length == 0 || chunk.length > MAX_PACKET_CHUNK_BYTES) {
                throw new IllegalArgumentException("Invalid binary transfer chunk");
            }
            if (index == 0) {
                if (output != null) {
                    throw new IllegalArgumentException("Binary transfer restarted");
                }
                totalChunks = total;
                nextIndex = 0;
                receivedBytes = 0;
                // Do not reserve the peer-declared worst case up front.  The stream
                // grows only as validated chunks arrive, keeping many concurrent
                // first chunks from consuming the full session budget immediately.
                output = new ByteArrayOutputStream(Math.min(maxTotalBytes, chunk.length));
            } else if (output == null || total != totalChunks) {
                throw new IllegalArgumentException("Binary transfer has no matching start");
            }
            if (index != nextIndex) {
                throw new IllegalArgumentException("Binary transfer chunk is out of order");
            }
            if (receivedBytes > maxTotalBytes - chunk.length) {
                throw new IllegalArgumentException("Binary transfer exceeds total limit");
            }
            output.write(chunk, 0, chunk.length);
            receivedBytes += chunk.length;
            nextIndex++;
            if (nextIndex == totalChunks) {
                return output.toByteArray();
            }
            return null;
        }

        public synchronized long lastTouchedMs() {
            return lastTouchedMs;
        }

        private void touch() {
            lastTouchedMs = System.currentTimeMillis();
        }
    }
}
