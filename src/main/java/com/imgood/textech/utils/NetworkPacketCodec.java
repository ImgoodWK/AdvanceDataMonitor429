package com.imgood.textech.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.ByteBuf;

/**
 * Length-prefixed readers for packets crossing the client/server trust boundary.
 *
 * <p>The packet classes in this project use a four-byte length prefix for their
 * own byte arrays and strings.  Forge's legacy helpers do not validate that
 * prefix against the remaining buffer before allocating, so packet decoders
 * must use these methods before any untrusted allocation.</p>
 */
public final class NetworkPacketCodec {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private NetworkPacketCodec() {}

    public static byte[] readBytes(ByteBuf buf, int maxBytes) {
        requireBuffer(buf);
        requireLimit(maxBytes);
        requireReadable(buf, 4);
        int length = buf.readInt();
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid packet byte-array length: " + length);
        }
        byte[] value = new byte[length];
        if (length > 0) {
            buf.readBytes(value);
        }
        return value;
    }

    public static String readUtf8(ByteBuf buf, int maxBytes) {
        byte[] bytes = readBytes(buf, maxBytes);
        return decodeUtf8(bytes);
    }

    /** Decode a bounded packet field that has already been separated from its framing. */
    public static String decodeUtf8(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Packet UTF-8 field is null");
        }
        return decodeUtf8Internal(bytes);
    }

    /**
     * Reads the VarInt-length UTF-8 framing used by
     * {@code ByteBufUtils.writeUTF8String}.
     */
    public static String readVarUtf8(ByteBuf buf, int maxBytes) {
        requireBuffer(buf);
        requireLimit(maxBytes);
        int length = readVarInt(buf, 5);
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid VarInt UTF-8 length: " + length);
        }
        byte[] bytes = new byte[length];
        if (length > 0) {
            buf.readBytes(bytes);
        }
        return decodeUtf8(bytes);
    }

    /**
     * Reads the unsigned-short byte length used by a few legacy packets.
     * The writer historically used {@link ByteBuf#writeShort(int)}, so the
     * reader must treat the two bytes as an unsigned length before applying
     * the packet-specific limit.
     */
    public static String readUnsignedShortUtf8(ByteBuf buf, int maxBytes) {
        requireBuffer(buf);
        requireLimit(maxBytes);
        requireReadable(buf, 2);
        int length = buf.readUnsignedShort();
        if (length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid unsigned-short UTF-8 length: " + length);
        }
        byte[] bytes = new byte[length];
        if (length > 0) {
            buf.readBytes(bytes);
        }
        return decodeUtf8(bytes);
    }

    /** Write the bounded VarInt-length UTF-8 framing used by {@code readVarUtf8}. */
    public static void writeVarUtf8(ByteBuf buf, String value, int maxBytes) {
        requireBuffer(buf);
        requireLimit(maxBytes);
        byte[] bytes = (value == null ? "" : value).getBytes(UTF8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Packet UTF-8 field exceeds packet limit");
        }
        writeVarInt(buf, bytes.length);
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
    }

    /**
     * Reads Forge 1.7.10's NBT framing: a signed-short compressed length,
     * {@code -1} for null, followed by zlib-compressed NBT bytes.
     */
    public static NBTTagCompound readTag(ByteBuf buf, int maxCompressedBytes, long maxNbtBytes) {
        requireBuffer(buf);
        requireLimit(maxCompressedBytes);
        if (maxNbtBytes <= 0L) {
            throw new IllegalArgumentException("NBT size limit must be positive");
        }
        requireReadable(buf, 2);
        short signedLength = buf.readShort();
        if (signedLength == -1) {
            return null;
        }
        if (signedLength < 0) {
            throw new IllegalArgumentException("Invalid compressed NBT length: " + signedLength);
        }
        int length = signedLength;
        if (length > maxCompressedBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid compressed NBT length: " + length);
        }
        byte[] compressed = new byte[length];
        if (length > 0) {
            buf.readBytes(compressed);
        }
        try {
            return CompressedStreamTools.func_152457_a(compressed, new NBTSizeTracker(maxNbtBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid NBT payload", e);
        }
    }

    /** Write Forge 1.7.10's signed-short NBT framing after enforcing the same limits. */
    public static void writeTag(ByteBuf buf, NBTTagCompound tag, int maxCompressedBytes) {
        if (tag == null) {
            buf.writeShort(-1);
            return;
        }
        if (maxCompressedBytes < 0 || maxCompressedBytes > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Compressed NBT limit must fit Forge's signed-short framing");
        }
        byte[] compressed;
        try {
            compressed = CompressedStreamTools.compress(tag);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not compress NBT payload", e);
        }
        if (compressed.length > maxCompressedBytes) {
            throw new IllegalArgumentException("Compressed NBT payload exceeds packet limit");
        }
        buf.writeShort(compressed.length);
        if (compressed.length > 0) {
            buf.writeBytes(compressed);
        }
    }

    private static int readVarInt(ByteBuf buf, int maxBytes) {
        int result = 0;
        int shift = 0;
        for (int i = 0; i < maxBytes; i++) {
            requireReadable(buf, 1);
            int value = buf.readUnsignedByte();
            if (shift == 28 && (value & 0xf8) != 0) {
                throw new IllegalArgumentException("Packet VarInt overflows a non-negative Java int");
            }
            result |= (value & 0x7f) << shift;
            if ((value & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IllegalArgumentException("Packet VarInt is too long");
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Packet VarInt value must not be negative");
        }
        int remaining = value;
        do {
            int next = remaining & 0x7f;
            remaining >>>= 7;
            if (remaining != 0) {
                next |= 0x80;
            }
            buf.writeByte(next);
        } while (remaining != 0);
    }

    private static String decodeUtf8Internal(byte[] bytes) {
        CharsetDecoder decoder = UTF8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8 packet field", e);
        }
    }

    private static void requireBuffer(ByteBuf buf) {
        if (buf == null) {
            throw new IllegalArgumentException("Packet buffer is null");
        }
    }

    private static void requireLimit(int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Packet size limit must not be negative");
        }
    }

    private static void requireReadable(ByteBuf buf, int bytes) {
        if (bytes < 0 || buf.readableBytes() < bytes) {
            throw new IllegalArgumentException("Packet ended before a field was complete");
        }
    }
}
