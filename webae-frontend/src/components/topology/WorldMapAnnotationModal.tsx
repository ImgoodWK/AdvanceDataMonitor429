import { useEffect } from 'react';

import { Form, Input, InputNumber, Modal } from 'antd';

import { ColorField } from '@/components/dashboard/ColorField';
import { useI18n } from '@/i18n';
import type { WorldMapAnnotationDto, WorldMapAnnotationInput } from '@/types/dto';

const { TextArea } = Input;

export interface WorldMapAnnotationPosition {
  dimension: number;
  x: number;
  /** The 2D map cannot infer elevation; creation callers may leave this unset. */
  y?: number;
  z: number;
}

interface AnnotationFormValues {
  dimension: number;
  x: number;
  y: number;
  z: number;
  label: string;
  note: string;
  color: string;
  fromVersion: number;
  toVersion: number;
}

function AnnotationColorField({
  label,
  value = '',
  onChange,
}: {
  label: string;
  value?: string;
  onChange?: (value: string) => void;
}) {
  return <ColorField label={label} value={value} onChange={(next) => onChange?.(next)} />;
}

export interface WorldMapAnnotationModalProps {
  open: boolean;
  networkId: number;
  annotation?: WorldMapAnnotationDto | null;
  initialPosition?: WorldMapAnnotationPosition | null;
  saving?: boolean;
  onCancel: () => void;
  onSave: (input: WorldMapAnnotationInput) => Promise<void> | void;
}

function integerOrZero(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : 0;
}

export function WorldMapAnnotationModal({
  open,
  networkId,
  annotation = null,
  initialPosition = null,
  saving = false,
  onCancel,
  onSave,
}: WorldMapAnnotationModalProps) {
  const { t } = useI18n();
  const [form] = Form.useForm<AnnotationFormValues>();

  useEffect(() => {
    if (!open) return;
    const source = annotation ?? initialPosition;
    form.setFieldsValue({
      dimension: source?.dimension ?? 0,
      x: source?.x ?? 0,
      // A flat map cannot infer elevation. Use a visible, editable overworld
      // baseline so background-created annotations pass the required Y field;
      // marker-created annotations still preserve their exact server Y.
      y: source?.y ?? 64,
      z: source?.z ?? 0,
      label: annotation?.label ?? '',
      note: annotation?.note ?? '',
      color: annotation?.color ?? '#faad14',
      fromVersion: integerOrZero(annotation?.fromVersion),
      toVersion: integerOrZero(annotation?.toVersion),
    });
  }, [annotation, form, initialPosition, open]);

  const handleFinish = async (values: AnnotationFormValues) => {
    const fromVersion = Math.max(0, integerOrZero(values.fromVersion));
    const toVersion = Math.max(0, integerOrZero(values.toVersion));
    if (fromVersion > 0 && toVersion > 0 && fromVersion > toVersion) {
      form.setFields([
        {
          name: 'toVersion',
          errors: [t('worldMapAnnotationVersionRangeInvalid')],
        },
      ]);
      return;
    }
    await onSave({
      networkId,
      dimension: integerOrZero(values.dimension),
      x: integerOrZero(values.x),
      y: integerOrZero(values.y),
      z: integerOrZero(values.z),
      label: values.label.trim(),
      note: values.note.trim(),
      color: values.color.trim(),
      fromVersion,
      toVersion,
    });
  };

  return (
    <Modal
      open={open}
      title={annotation ? t('worldMapAnnotationEdit') : t('worldMapAnnotationAdd')}
      okText={t('worldMapAnnotationSave')}
      cancelText={t('worldMapAnnotationCancel')}
      confirmLoading={saving}
      destroyOnClose
      onCancel={onCancel}
      onOk={() => form.submit()}
    >
      <Form<AnnotationFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => void handleFinish(values)}
      >
        <Form.Item
          name="label"
          label={t('worldMapAnnotationLabel')}
          rules={[
            { required: true, whitespace: true, message: t('worldMapAnnotationLabelRequired') },
            { max: 64 },
          ]}
        >
          <Input maxLength={64} showCount autoFocus />
        </Form.Item>

        <Form.Item name="note" label={t('worldMapAnnotationNote')} rules={[{ max: 512 }]}>
          <TextArea maxLength={512} showCount autoSize={{ minRows: 2, maxRows: 6 }} />
        </Form.Item>

        <div className="worldmap-annotation-coordinate-grid">
          <Form.Item name="dimension" label="D" rules={[{ required: true }]}>
            <InputNumber precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="x" label={t('worldMapAnnotationCoordinateX')} rules={[{ required: true }]}>
            <InputNumber precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="y" label={t('worldMapAnnotationCoordinateY')} rules={[{ required: true }]}>
            <InputNumber precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="z" label={t('worldMapAnnotationCoordinateZ')} rules={[{ required: true }]}>
            <InputNumber precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </div>

        <Form.Item
          name="color"
          label={t('worldMapAnnotationColor')}
          rules={[
            { required: true },
            { pattern: /^#[0-9a-fA-F]{6}$/, message: t('worldMapAnnotationColorInvalid') },
          ]}
        >
          <AnnotationColorField label={t('worldMapAnnotationColor')} />
        </Form.Item>

        <div className="worldmap-annotation-version-grid">
          <Form.Item
            name="fromVersion"
            label={t('worldMapAnnotationFromVersion')}
            extra={t('worldMapAnnotationRangeHint')}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="toVersion" label={t('worldMapAnnotationToVersion')}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );
}
