import { THEME_ZH, type ThemeId } from '../lib/themeTokens';
import { listSkinsForUi, type BoardSkin } from '../lib/skins';

export function LobbyScreen(props: {
  meta: {
    themes: string[];
    voltages: string[];
    themeSlotsByVoltage: Record<string, number>;
    equipment: { id: string; nameZh: string; attack: number; health: number; armor: number }[];
    cardCount: number;
  };
  themes: string[];
  voltage: string;
  equipmentIds: string[];
  skinId: string;
  victories: number;
  busy: boolean;
  onVoltage: (v: string) => void;
  onToggleTheme: (t: string) => void;
  onToggleEquipment: (id: string) => void;
  onSelectSkin: (id: string) => void;
  onStart: () => void;
}) {
  const skins = listSkinsForUi(props.victories);

  return (
    <>
      <div className="panel">
        <h2>开局配置</h2>
        <p className="muted">
          电压决定可带主题数。卡表 {props.meta.cardCount} 张 · 像素扁平 GTNH 风格 · 拖拽出牌。
        </p>
        <div className="row" style={{ margin: '0.75rem 0' }}>
          <label>
            电压{' '}
            <select value={props.voltage} onChange={(e) => props.onVoltage(e.target.value)}>
              {props.meta.voltages.map((v) => (
                <option key={v} value={v}>
                  {v}（最多 {props.meta.themeSlotsByVoltage[v]} 主题）
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="grid-themes">
          {props.meta.themes.map((t) => (
            <button
              key={t}
              type="button"
              className={`chip${props.themes.includes(t) ? ' on' : ''}`}
              onClick={() => props.onToggleTheme(t)}
            >
              {THEME_ZH[t as ThemeId] ?? t}
            </button>
          ))}
        </div>
        <h3 style={{ marginTop: '1rem' }}>开局装备</h3>
        <div className="row">
          {props.meta.equipment.map((eq) => {
            const on = props.equipmentIds.includes(eq.id);
            return (
              <button
                key={eq.id}
                type="button"
                className={on ? undefined : 'secondary'}
                onClick={() => props.onToggleEquipment(eq.id)}
              >
                {eq.nameZh} (+{eq.attack}/{eq.health}/{eq.armor})
              </button>
            );
          })}
        </div>
        <div className="row" style={{ marginTop: '1rem' }}>
          <button disabled={props.busy || props.themes.length === 0} onClick={props.onStart}>
            开始 PvE 冒险
          </button>
        </div>
      </div>

      <div className="panel">
        <h3>棋盘皮肤</h3>
        <p className="muted">累计胜利 {props.victories} 场 · 与 LoR 类似，可解锁不同战场背景。</p>
        <div className="skin-grid" style={{ marginTop: '0.65rem' }}>
          {skins.map((s) => (
            <SkinButton
              key={s.id}
              skin={s}
              unlocked={s.unlocked}
              selected={props.skinId === s.id}
              onSelect={() => props.onSelectSkin(s.id)}
            />
          ))}
        </div>
      </div>
    </>
  );
}

function SkinButton(props: {
  skin: BoardSkin & { unlocked: boolean };
  unlocked: boolean;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      className={`skin-card${props.selected ? ' on' : ''}${props.unlocked ? '' : ' locked'}`}
      disabled={!props.unlocked}
      onClick={props.onSelect}
    >
      <div
        className="skin-preview"
        style={{
          background: `${props.skin.boardBgAlt}, ${props.skin.boardBg}`,
          borderColor: props.skin.frameTint,
        }}
      />
      <div style={{ fontWeight: 700 }}>{props.skin.nameZh}</div>
      <div className="muted" style={{ fontSize: '0.75rem' }}>
        {props.unlocked ? props.skin.unlockHint : `🔒 ${props.skin.unlockHint}`}
      </div>
    </button>
  );
}
