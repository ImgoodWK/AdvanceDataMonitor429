export type SparkAnalysisStatus = 'pending' | 'ready' | 'empty' | 'unavailable' | 'legacy';

export interface SparkHotspot {
  className: string;
  methodName: string;
  lineNumber: number;
  category: string;
  dominantThread?: string;
  selfTimeMillis: number;
  totalTimeMillis: number;
  percent: number;
}

export interface SparkCategoryImpact {
  id: string;
  timeMillis: number;
  percent: number;
  topClassName?: string;
  topMethodName?: string;
}

export interface SparkThreadImpact {
  name: string;
  timeMillis: number;
  percent: number;
}

export interface SparkAnalyzedProfile {
  analysisStatus?: SparkAnalysisStatus;
  analysisVersion?: number;
  sampledTimeMillis?: number;
  sampleCount?: number;
  analyzedNodeCount?: number;
  hotspots?: SparkHotspot[];
  categories?: SparkCategoryImpact[];
  threads?: SparkThreadImpact[];
}

export type SparkInsightSeverity = 'critical' | 'warning' | 'info' | 'healthy';

export interface SparkInsight {
  category: string;
  severity: SparkInsightSeverity;
  percent: number;
  evidence: string;
}

export interface SparkCategoryComparison {
  id: string;
  a: number;
  b: number;
  delta: number;
}

export interface SparkHotspotComparison {
  key: string;
  className: string;
  methodName: string;
  a: number;
  b: number;
  delta: number;
}

export function formatSparkMethod(className?: string, methodName?: string): string {
  const owner = className || 'unknown';
  return methodName ? `${owner}.${methodName}` : owner;
}

export function buildSparkInsights(profile?: SparkAnalyzedProfile | null): SparkInsight[] {
  if (!profile || profile.analysisStatus !== 'ready') return [];
  const meaningful = [...(profile.categories || [])]
    .filter((category) => Number.isFinite(category.percent) && category.percent >= 3)
    .sort((left, right) => right.percent - left.percent)
    .slice(0, 5);
  if (!meaningful.length) {
    return [{ category: 'balanced', severity: 'healthy', percent: 0, evidence: '' }];
  }
  return meaningful.map((category) => ({
    category: category.id,
    severity: category.percent >= 35
      ? 'critical'
      : category.percent >= 15
        ? 'warning'
        : 'info',
    percent: category.percent,
    evidence: formatSparkMethod(category.topClassName, category.topMethodName),
  }));
}

export function sparkAnalysisConfidence(sampleCount?: number): 'low' | 'medium' | 'high' {
  if ((sampleCount || 0) >= 1_000) return 'high';
  if ((sampleCount || 0) >= 250) return 'medium';
  return 'low';
}

export function compareSparkCategories(
  left?: SparkAnalyzedProfile | null,
  right?: SparkAnalyzedProfile | null,
): SparkCategoryComparison[] {
  const values = new Map<string, SparkCategoryComparison>();
  for (const category of left?.categories || []) {
    values.set(category.id, { id: category.id, a: category.percent, b: 0, delta: -category.percent });
  }
  for (const category of right?.categories || []) {
    const value = values.get(category.id) || { id: category.id, a: 0, b: 0, delta: 0 };
    value.b = category.percent;
    value.delta = value.b - value.a;
    values.set(category.id, value);
  }
  return [...values.values()].sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta));
}

export function compareSparkHotspots(
  left?: SparkAnalyzedProfile | null,
  right?: SparkAnalyzedProfile | null,
): SparkHotspotComparison[] {
  const values = new Map<string, SparkHotspotComparison>();
  for (const hotspot of left?.hotspots || []) {
    const key = `${hotspot.className}\n${hotspot.methodName}`;
    values.set(key, {
      key,
      className: hotspot.className,
      methodName: hotspot.methodName,
      a: hotspot.percent,
      b: 0,
      delta: -hotspot.percent,
    });
  }
  for (const hotspot of right?.hotspots || []) {
    const key = `${hotspot.className}\n${hotspot.methodName}`;
    const value = values.get(key) || {
      key,
      className: hotspot.className,
      methodName: hotspot.methodName,
      a: 0,
      b: 0,
      delta: 0,
    };
    value.b = hotspot.percent;
    value.delta = value.b - value.a;
    values.set(key, value);
  }
  return [...values.values()]
    .sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta))
    .slice(0, 20);
}
