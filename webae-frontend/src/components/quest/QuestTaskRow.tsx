import { Button, Space, Tag, Typography } from 'antd';
import { McFormattedText } from '@/components/McFormattedText';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { QuestTaskDto } from '@/types/dto';
import { stripMcFormatting } from '@/utils/mcFormatting';
import { questIconProps, questMaterialLabel } from './questUtils';

const { Text } = Typography;

interface QuestTaskRowProps {
  task: QuestTaskDto;
  onFindRecipe?: () => void;
  canClickSubmit?: boolean;
  onSubmitStep?: () => void;
  busy?: boolean;
}

function formatNeedAmount(template: string, amount: number | string, name: string): string {
  return template.replace('{amount}', String(amount)).replace('{name}', name);
}

export function QuestTaskRow({
  task,
  onFindRecipe,
  canClickSubmit,
  onSubmitStep,
  busy,
}: QuestTaskRowProps) {
  const { t } = useI18n();
  const materialLabel = questMaterialLabel(task);
  const displayName = materialLabel || task.name || task.registryName || `#${task.index + 1}`;
  const plainName = stripMcFormatting(displayName);
  const webTag =
    task.webAction === 'SUBMIT' || task.webAction === 'DETECT' ? (
      <Tag color="green">{t('quest.webCapable')}</Tag>
    ) : (
      <Tag>{t('quest.inGameOnly')}</Tag>
    );

  const iconProps = questIconProps(task);
  const icon = iconProps ? <Icon {...iconProps} size={40} alt={task.fluidName || plainName} /> : null;

  const iconSlot = icon ?? (
    <span
      style={{
        width: 40,
        height: 40,
        flexShrink: 0,
        borderRadius: 6,
        background: 'rgba(148,163,184,0.15)',
        display: 'inline-block',
      }}
    />
  );

  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 14 }}>
      {canClickSubmit && onSubmitStep ? (
        <button
          type="button"
          onClick={onSubmitStep}
          disabled={busy}
          title={t('quest.submitStep')}
          style={{
            padding: 0,
            border: '1px solid #fbbf24',
            borderRadius: 6,
            background: 'transparent',
            cursor: busy ? 'wait' : 'pointer',
          }}
        >
          {iconSlot}
        </button>
      ) : (
        iconSlot
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <Space wrap size={4}>
          <Text strong>
            <McFormattedText text={displayName} />
          </Text>
          {task.complete ? <Tag color="success">{t('quest.taskComplete')}</Tag> : webTag}
        </Space>
        {task.description && task.description !== task.name && task.description !== displayName ? (
          <Text type="secondary" style={{ display: 'block', marginTop: 2 }}>
            <McFormattedText text={task.description} preWrap />
          </Text>
        ) : null}
        {task.registryName ? (
          <div style={{ marginTop: 4 }}>
            <Text>{formatNeedAmount(t('quest.needAmount'), task.required, plainName)}</Text>
            <div>
              <Text type="secondary">
                {task.progress}/{task.required}
              </Text>
            </div>
          </div>
        ) : null}
        {!task.registryName && !task.fluidName && (task.required > 0 || task.progress > 0) ? (
          <div style={{ marginTop: 4 }}>
            <Text type="secondary">
              {task.progress}/{task.required}
            </Text>
          </div>
        ) : null}
        {task.extraItemCount != null && task.extraItemCount > 0 ? (
          <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
            {t('quest.extraItems').replace('{n}', String(task.extraItemCount))}
          </Text>
        ) : null}
        {task.fluidName ? (
          <div style={{ marginTop: 4 }}>
            <Text>
              {formatNeedAmount(
                t('quest.needAmount'),
                task.fluidRequired ?? 0,
                materialLabel || task.fluidName
              )}
            </Text>
            <div>
              <Text type="secondary">
                {task.fluidProgress ?? 0}/{task.fluidRequired ?? 0} mB
              </Text>
            </div>
          </div>
        ) : null}
        {task.webAction === 'IN_GAME_ONLY' && task.reasonKey ? (
          <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
            {t(task.reasonKey.replace('adm.', 'quest.'))}
          </Text>
        ) : null}
        <Space size={4} wrap>
          {onFindRecipe && task.registryName ? (
            <Button size="small" type="link" onClick={onFindRecipe} style={{ paddingLeft: 0 }}>
              {t('quest.findRecipe')}
            </Button>
          ) : null}
          {canClickSubmit && onSubmitStep ? (
            <Button size="small" type="link" loading={busy} onClick={onSubmitStep}>
              {t('quest.submitStep')}
            </Button>
          ) : null}
        </Space>
      </div>
    </div>
  );
}
