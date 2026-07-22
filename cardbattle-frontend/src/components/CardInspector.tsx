import type { BoardUnit, CardDef } from '../api/client';
import { THEME_ZH, type ThemeId } from '../lib/themeTokens';
import { CardView } from './CardView';

const KIND_ZH: Record<string, string> = {
  unit: '单位',
  spell: '法术',
  structure: '结构',
  equipment: '装备',
};

const SPEED_ZH: Record<string, string> = {
  slow: '慢速 · 仅主行动空栈',
  fast: '快速 · 可加入响应栈',
  burst: '爆发 · 立即结算且保留行动权',
};

const KEYWORDS: Record<string, { name: string; detail: string }> = {
  lifesteal: { name: '吸血', detail: '造成战斗伤害时，使己方 Nexus 恢复等量生命。' },
  aoe: { name: '溅射', detail: '攻击被格挡时，对格挡者相邻槽位造成攻击力一半的伤害，最低 1。' },
  stealth: { name: '隐秘', detail: '只能被同样具有隐秘的单位格挡。' },
  untargetable: { name: '不可选中', detail: '不能成为敌方定向效果的目标。' },
  machine: { name: '机器', detail: '可被 GT 拆卸、储能与过载效果识别。' },
  capacitor: { name: '电容', detail: '轮次结束时允许把剩余普通法力存入 GT 储能。' },
  beehive: { name: '蜂箱', detail: '按冷却周期生成或强化蜜蜂，并且不可被敌方效果选中。' },
  bee: { name: '蜜蜂', detail: '可被蜂箱和林业插件强化的单位类型。' },
  aspect: { name: '源质', detail: '可携带神秘源质；秩序与风组合会解锁格挡后换位。' },
  singularity: { name: '奇点', detail: '打出后为永恒奇点累计 1 点进度。' },
  eternal_singularity: { name: '永恒奇点', detail: '需要 3 点奇点进度，激活本局斩杀规则。' },
  accelerator: { name: '加速器', detail: '减少己方结构的生产冷却。' },
  reflect: { name: '反射', detail: '把受到的部分 Nexus 伤害返还给对手。' },
  reduce_damage: { name: '减伤', detail: '按百分比降低 Nexus 最终受到的伤害。' },
};

const ASPECT_ZH: Record<string, string> = {
  ordo: '秩序',
  aer: '风',
  ignis: '火',
  aqua: '水',
  terra: '地',
  perditio: '混沌',
};

export function CardInspector(props: {
  def?: CardDef;
  unit?: BoardUnit;
  pinned?: boolean;
  onTogglePin?: () => void;
}) {
  const { def, unit } = props;
  if (!def) {
    return (
      <section className="panel card-inspector empty">
        <span className="eyebrow">卡牌档案</span>
        <h3>悬停或聚焦一张卡牌</h3>
        <p className="muted">这里会显示完整效果、速度、目标限制、关键词解释和场上实时数值。</p>
      </section>
    );
  }

  const keywords = unit?.keywords ?? def.keywords ?? [];
  const aspects = unit?.aspects ?? def.aspects ?? [];

  return (
    <section className="panel card-inspector" aria-live="polite">
      <header className="inspector-heading">
        <div>
          <span className="eyebrow">卡牌档案 · {THEME_ZH[def.theme as ThemeId] ?? def.theme}</span>
          <h3>{def.nameZh}</h3>
          <span className="muted">{def.name}</span>
        </div>
        {props.onTogglePin && (
          <button
            type="button"
            className={props.pinned ? 'inspector-pin active' : 'inspector-pin secondary'}
            onClick={props.onTogglePin}
            aria-pressed={props.pinned}
          >
            {props.pinned ? '已锁定' : '锁定'}
          </button>
        )}
      </header>

      <div className="inspector-card-preview">
        <CardView def={def} unit={unit} className="preview-card" />
      </div>

      <div className="inspector-meta">
        <span>{KIND_ZH[def.kind] ?? def.kind}</span>
        <span>费用 {def.cost}</span>
        {def.spellSpeed && <span>{SPEED_ZH[def.spellSpeed] ?? def.spellSpeed}</span>}
      </div>

      <div className="rules-box">
        <span className="eyebrow">精确效果</span>
        <p>{def.rulesZh ?? def.textZh ?? '无额外效果。'}</p>
      </div>

      {unit && (
        <div className="live-stats">
          <span className="eyebrow">场上实时状态</span>
          <div>
            攻击 {unit.attack} · 生命 {unit.health}/{unit.maxHealth} · 护甲 {unit.armor}
          </div>
          {unit.hiveTurnsLeft != null && <div>生产冷却：{unit.hiveTurnsLeft}</div>}
        </div>
      )}

      {(keywords.length > 0 || aspects.length > 0) && (
        <div className="keyword-list">
          {keywords.map((keyword) => {
            const entry = KEYWORDS[keyword] ?? { name: keyword, detail: '该关键词由服务端规则定义。' };
            return (
              <div className="keyword-entry" key={keyword}>
                <strong>{entry.name}</strong>
                <span>{entry.detail}</span>
              </div>
            );
          })}
          {aspects.map((aspect) => (
            <div className="keyword-entry aspect" key={aspect}>
              <strong>源质 · {ASPECT_ZH[aspect] ?? aspect}</strong>
              <span>源质永久保留；秩序 + 风会在防守时解锁一次槽位换位。</span>
            </div>
          ))}
        </div>
      )}

      <p className="inspector-footnote">显示内容来自独立卡牌后端的权威卡表。</p>
    </section>
  );
}
