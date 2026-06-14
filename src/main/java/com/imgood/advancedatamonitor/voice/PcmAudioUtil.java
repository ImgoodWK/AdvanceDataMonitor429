package com.imgood.advancedatamonitor.voice;

public final class PcmAudioUtil {

    private PcmAudioUtil() {}

    public static Stats analyze(byte[] pcm) {
        if (pcm == null || pcm.length < 2) {
            return new Stats(0, 0, 0.0D, -120.0D);
        }
        int samples = pcm.length / 2;
        long squareSum = 0L;
        int peak = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = sampleAt(pcm, i);
            int abs = Math.abs(sample == Short.MIN_VALUE ? Short.MAX_VALUE : sample);
            if (abs > peak) {
                peak = abs;
            }
            squareSum += (long) sample * (long) sample;
        }
        double rms = samples == 0 ? 0.0D : Math.sqrt(squareSum / (double) samples);
        double rmsDb = rms <= 0.0D ? -120.0D : 20.0D * Math.log10(rms / 32768.0D);
        return new Stats(samples, peak, rms, rmsDb);
    }

    public static byte[] normalize(byte[] pcm) {
        Stats stats = analyze(pcm);
        if (pcm == null || pcm.length == 0 || stats.peak <= 0) {
            return pcm;
        }
        int targetPeak = 22000;
        double gain = Math.min(8.0D, targetPeak / (double) stats.peak);
        if (gain <= 1.15D) {
            return pcm;
        }
        byte[] normalized = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = sampleAt(pcm, i);
            int scaled = (int) Math.round(sample * gain);
            if (scaled > Short.MAX_VALUE) {
                scaled = Short.MAX_VALUE;
            } else if (scaled < Short.MIN_VALUE) {
                scaled = Short.MIN_VALUE;
            }
            normalized[i] = (byte) (scaled & 0xFF);
            normalized[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        return normalized;
    }

    private static int sampleAt(byte[] pcm, int offset) {
        return (short) ((pcm[offset] & 0xFF) | (pcm[offset + 1] << 8));
    }

    public static final class Stats {

        public final int samples;
        public final int peak;
        public final double rms;
        public final double rmsDb;

        private Stats(int samples, int peak, double rms, double rmsDb) {
            this.samples = samples;
            this.peak = peak;
            this.rms = rms;
            this.rmsDb = rmsDb;
        }

        public double durationSeconds(int sampleRate) {
            return sampleRate <= 0 ? 0.0D : samples / (double) sampleRate;
        }

        public boolean isProbablySilent() {
            return peak < 500 || rmsDb < -45.0D;
        }

        public String describe() {
            return String.format("duration=%.2fs peak=%d rmsDb=%.1f", durationSeconds(16000), peak, rmsDb);
        }
    }
}
