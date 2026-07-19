import { describe, expect, it } from 'vitest';
import type { ChatMessageDto } from '@/types/dto';
import { hasScreenshotAttachment, screenshotSizeKiB } from './chatAttachments';

function message(overrides: Partial<ChatMessageDto> = {}): ChatMessageDto {
  return {
    id: 1,
    senderUuid: 'player',
    senderName: 'Player',
    content: '',
    timestamp: 1,
    source: 'game',
    ...overrides,
  };
}

describe('chat screenshot attachments', () => {
  it('accepts only bounded server attachment ids', () => {
    expect(hasScreenshotAttachment(message({ attachmentId: 'a'.repeat(32) }))).toBe(true);
    expect(hasScreenshotAttachment(message({ attachmentId: '../secret' }))).toBe(false);
    expect(hasScreenshotAttachment(message())).toBe(false);
  });

  it('rounds bytes to a readable KiB value', () => {
    expect(screenshotSizeKiB(message({ attachmentBytes: 1536 }))).toBe(2);
    expect(screenshotSizeKiB(message({ attachmentBytes: 0 }))).toBe(1);
  });
});
