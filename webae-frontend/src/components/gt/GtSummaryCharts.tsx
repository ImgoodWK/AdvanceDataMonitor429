import { Card, Col, Empty, Row, Tag } from 'antd';
import type { GtMachineDto } from '@/types/dto';
import {
  getGtRecipeMapBreakdown,
  getGtStatusBreakdown,
  gtCategoryColor,
  type ChartCategory,
} from '@/utils/gtChartData';

interface GtSummaryChartsProps {
  machines: GtMachineDto[];
  t: (key: string) => string;
  fmtNum: (n: number) => string;
}

function StatusPieChart({
  categories,
  fmtNum,
}: {
  categories: ChartCategory[];
  fmtNum: (n: number) => string;
}) {
  const total = categories.reduce((s, c) => s + c.value, 0) || 1;
  let offset = 0;
  const palette = categories.map((cat, i) => gtCategoryColor(cat, i));

  return (
    <div className="gt-summary-chart">
      <div className="gt-summary-chart__pie-wrap">
        <svg viewBox="0 0 42 42" preserveAspectRatio="xMidYMid meet" className="chart-svg">
          <circle cx="21" cy="21" r="15.9" fill="transparent" stroke="var(--bg-secondary)" strokeWidth="6" />
          <g className="chart-pie-group">
            {categories.map((cat, i) => {
              const dash = (cat.value / total) * 100;
              const el = (
                <circle
                  key={cat.label}
                  cx="21"
                  cy="21"
                  r="15.9"
                  fill="transparent"
                  stroke={palette[i]}
                  strokeWidth="6"
                  strokeDasharray={`${dash} ${100 - dash}`}
                  strokeDashoffset={-offset}
                />
              );
              offset += dash;
              return el;
            })}
          </g>
        </svg>
      </div>
      <div className="gt-summary-chart__legend">
        {categories.map((cat, i) => (
          <Tag key={cat.label} color={palette[i]} style={{ fontSize: '0.7rem', marginBottom: 4 }}>
            {cat.label}: {fmtNum(cat.value)}
          </Tag>
        ))}
      </div>
    </div>
  );
}

function RecipeBarChart({
  categories,
  fmtNum,
}: {
  categories: ChartCategory[];
  fmtNum: (n: number) => string;
}) {
  const maxVal = Math.max(...categories.map((c) => c.value), 1);

  return (
    <div className="gt-summary-chart gt-summary-chart--bars">
      {categories.map((cat, i) => {
        const color = gtCategoryColor(cat, i);
        return (
          <div key={cat.label} className="gt-summary-bar-row">
            <div className="gt-summary-bar-label" title={cat.label}>
              {cat.label}
            </div>
            <div className="gt-summary-bar-track">
              <div
                className="gt-summary-bar-fill chart-bar-segment"
                style={{
                  width: `${(cat.value / maxVal) * 100}%`,
                  background: color,
                }}
              />
            </div>
            <div className="gt-summary-bar-value">{fmtNum(cat.value)}</div>
          </div>
        );
      })}
    </div>
  );
}

export function GtSummaryCharts({ machines, t, fmtNum }: GtSummaryChartsProps) {
  const statusData = getGtStatusBreakdown(machines, t);
  const recipeData = getGtRecipeMapBreakdown(machines, t);
  const hasStatus = statusData.some((c) => c.value > 0);
  const hasRecipe = recipeData.length > 0;

  if (!hasStatus && !hasRecipe) {
    return null;
  }

  return (
    <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
      <Col xs={24} md={12}>
        <Card size="small" title={t('gtSummaryStatusTitle')}>
          {hasStatus ? (
            <StatusPieChart categories={statusData} fmtNum={fmtNum} />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noData')} />
          )}
        </Card>
      </Col>
      <Col xs={24} md={12}>
        <Card size="small" title={t('gtSummaryRecipeMapTitle')}>
          {hasRecipe ? (
            <RecipeBarChart categories={recipeData} fmtNum={fmtNum} />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noData')} />
          )}
        </Card>
      </Col>
    </Row>
  );
}
