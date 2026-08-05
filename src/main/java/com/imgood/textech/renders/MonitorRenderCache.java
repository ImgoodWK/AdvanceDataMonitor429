package com.imgood.textech.renders;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/** Revision-aware VBO cache shared by native monitor time-series renderers. */
public final class MonitorRenderCache {

    private static final Map<SlotKey, Entry> ENTRIES = new HashMap<SlotKey, Entry>();

    private MonitorRenderCache() {}

    public interface GeometryFactory {

        Geometry build();
    }

    public static Entry getOrBuild(int x, int y, int z, int bindingIndex, int revision, GeometryFactory factory) {
        SlotKey key = new SlotKey(x, y, z, bindingIndex);
        Entry current = ENTRIES.get(key);
        if (current != null && current.revision == revision) return current;
        if (current != null) current.delete();
        Entry replacement = upload(revision, factory.build());
        ENTRIES.put(key, replacement);
        return replacement;
    }

    public static void clear() {
        for (Entry entry : ENTRIES.values()) entry.delete();
        ENTRIES.clear();
    }

    public static int size() {
        return ENTRIES.size();
    }

    private static Entry upload(int revision, Geometry geometry) {
        List<Mesh> meshes = new ArrayList<Mesh>(geometry.meshes.size());
        for (MeshData data : geometry.meshes) {
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(data.vertices.length);
            buffer.put(data.vertices);
            buffer.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
            meshes.add(new Mesh(vbo, data.vertices.length / 3, data.mode));
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new Entry(revision, meshes);
    }

    public static final class Geometry {

        private final List<MeshData> meshes = new ArrayList<MeshData>();

        public Geometry add(int mode, float[] vertices) {
            meshes.add(new MeshData(mode, vertices == null ? new float[0] : vertices));
            return this;
        }
    }

    public static final class Entry {

        private final int revision;
        private final List<Mesh> meshes;

        private Entry(int revision, List<Mesh> meshes) {
            this.revision = revision;
            this.meshes = meshes;
        }

        public void draw(int index) {
            if (index < 0 || index >= meshes.size()) return;
            meshes.get(index)
                .draw();
        }

        private void delete() {
            for (Mesh mesh : meshes) GL15.glDeleteBuffers(mesh.vbo);
        }
    }

    private static final class MeshData {

        final int mode;
        final float[] vertices;

        MeshData(int mode, float[] vertices) {
            this.mode = mode;
            this.vertices = vertices;
        }
    }

    private static final class Mesh {

        final int vbo;
        final int vertexCount;
        final int mode;

        Mesh(int vbo, int vertexCount, int mode) {
            this.vbo = vbo;
            this.vertexCount = vertexCount;
            this.mode = mode;
        }

        void draw() {
            if (vertexCount <= 0) return;
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
            GL11.glDrawArrays(mode, 0, vertexCount);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    static final class SlotKey {

        final int x;
        final int y;
        final int z;
        final int bindingIndex;

        SlotKey(int x, int y, int z, int bindingIndex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bindingIndex = bindingIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SlotKey)) return false;
            SlotKey key = (SlotKey) other;
            return x == key.x && y == key.y && z == key.z && bindingIndex == key.bindingIndex;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + bindingIndex;
            return result;
        }
    }
}
