import { describe, expect, it } from 'vitest';
import {
  buildSparkInsights,
  compareSparkCategories,
  compareSparkHotspots,
  sparkAnalysisConfidence,
} from './sparkAnalysis';

describe('sparkAnalysis', () => {
  it('orders evidence-backed insights by impact and assigns severity', () => {
    const insights = buildSparkInsights({
      analysisStatus: 'ready',
      categories: [
        { id: 'ae2', timeMillis: 20, percent: 20, topClassName: 'appeng.Tick', topMethodName: 'run' },
        { id: 'chunks', timeMillis: 40, percent: 40, topClassName: 'ChunkProvider', topMethodName: 'load' },
        { id: 'jvm', timeMillis: 2, percent: 2 },
      ],
    });

    expect(insights.map((value) => value.category)).toEqual(['chunks', 'ae2']);
    expect(insights.map((value) => value.severity)).toEqual(['critical', 'warning']);
    expect(insights[0].evidence).toBe('ChunkProvider.load');
  });

  it('compares category and hotspot percentages as B minus A', () => {
    const left = {
      categories: [{ id: 'ae2', timeMillis: 10, percent: 10 }],
      hotspots: [{
        className: 'appeng.Tick', methodName: 'run', lineNumber: 1, category: 'ae2',
        selfTimeMillis: 10, totalTimeMillis: 20, percent: 10,
      }],
    };
    const right = {
      categories: [{ id: 'ae2', timeMillis: 22, percent: 22 }],
      hotspots: [{
        className: 'appeng.Tick', methodName: 'run', lineNumber: 1, category: 'ae2',
        selfTimeMillis: 22, totalTimeMillis: 30, percent: 22,
      }],
    };

    expect(compareSparkCategories(left, right)[0].delta).toBe(12);
    expect(compareSparkHotspots(left, right)[0].delta).toBe(12);
  });

  it('derives confidence from the weighted sample count', () => {
    expect(sparkAnalysisConfidence(80)).toBe('low');
    expect(sparkAnalysisConfidence(500)).toBe('medium');
    expect(sparkAnalysisConfidence(1_500)).toBe('high');
  });
});
