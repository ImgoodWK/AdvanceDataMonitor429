import { Button, Divider, Select, Space, Tag, Typography } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { InterfaceDto } from '@/types/dto';

const { Text } = Typography;

interface PatternInjectPanelProps {
  interfaces: InterfaceDto[];
  selectedInterface: string;
  onSelectedInterfaceChange: (v: string) => void;
  selectedIface: InterfaceDto | undefined;
  injectSlot: number;
  onInjectSlotChange: (v: number) => void;
  encodedNbt: string;
  onInject: () => void;
}

export function PatternInjectPanel({
  interfaces,
  selectedInterface,
  onSelectedInterfaceChange,
  selectedIface,
  injectSlot,
  onInjectSlotChange,
  encodedNbt,
  onInject,
}: PatternInjectPanelProps) {
  const { t } = useI18n();

  return (
    <>
      <Divider>{t('selectInterface')}</Divider>
      <Space style={{ marginBottom: 16 }} wrap direction="vertical" size={8}>
        <Select
          placeholder={t('selectMEInterface')}
          value={selectedInterface || undefined}
          onChange={onSelectedInterfaceChange}
          style={{ width: '100%', maxWidth: 480 }}
          options={interfaces.map((iface) => {
            const patternCount =
              iface.existingPatterns?.length ?? iface.slots?.filter((s) => s.occupied).length ?? 0;
            const recipeType = iface.machineRecipeType || iface.targetRecipePool || iface.targetMachineName || '';
            const coord = `(${iface.x},${iface.y},${iface.z})`;
            const suffix = [coord, t('patternInterfacePatternCount').replace('{n}', String(patternCount)), recipeType]
              .filter(Boolean)
              .join(' · ');
            return {
              label: `${iface.name} — ${suffix}`,
              value: `${iface.x}_${iface.y}_${iface.z}_${iface.dim}`,
            };
          })}
        />
        {selectedIface && (
          <div style={{ width: '100%', maxWidth: 480 }}>
            {selectedIface.machineRecipeType || selectedIface.targetRecipePool ? (
              <Text type="secondary" style={{ fontSize: '0.8rem', display: 'block', marginBottom: 4 }}>
                {t('patternInterfaceRecipeType')}:{' '}
                {selectedIface.machineRecipeType ||
                  `${selectedIface.targetMachineName} / ${selectedIface.targetRecipePool}`}
              </Text>
            ) : null}
            {(selectedIface.existingPatterns?.length ?? 0) > 0 && (
              <>
                <Text strong style={{ fontSize: '0.8rem' }}>
                  {t('patternInterfacePatterns')}
                </Text>
                <div className="webae-section-card" style={{ maxHeight: 160, overflow: 'auto', marginTop: 4, padding: 4 }}>
                  {selectedIface.existingPatterns!.map((pat) => {
                    const out = pat.outputs[0];
                    return (
                      <div
                        key={pat.patternId}
                        className="webae-list-row"
                        style={{ border: 'none', padding: '4px 6px', fontSize: '0.75rem', cursor: 'default' }}
                      >
                        <Tag style={{ margin: 0 }}>{t('patternSlot')} {pat.slotIndex}</Tag>
                        {out && <Icon item={out} size={20} alt={out.displayName || out.registryName} />}
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {out?.displayName || out?.registryName || pat.patternId}
                          {out && out.stackSize > 1 ? ` ×${out.stackSize}` : ''}
                        </span>
                        {pat.crafting ? (
                          <Tag color="blue" style={{ margin: 0, fontSize: '0.65rem' }}>
                            {t('crafting')}
                          </Tag>
                        ) : (
                          <Tag style={{ margin: 0, fontSize: '0.65rem' }}>{t('processing')}</Tag>
                        )}
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        )}
        <Space wrap>
          <Select
            value={injectSlot}
            onChange={onInjectSlotChange}
            style={{ width: 120 }}
            options={Array.from({ length: 36 }, (_, i) => ({ label: `${t('selectedSlot')} ${i}`, value: i }))}
          />
          <Button icon={<DownloadOutlined />} onClick={onInject} disabled={!encodedNbt || !selectedInterface}>
            {t('injectPattern')}
          </Button>
        </Space>
      </Space>
    </>
  );
}
