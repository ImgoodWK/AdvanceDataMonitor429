import type { ChatMessageDto } from '@/types/dto';

export function hasScreenshotAttachment(message: ChatMessageDto): boolean {
  return Boolean(message.attachmentId && /^[0-9a-f]{32}$/.test(message.attachmentId));
}

export function screenshotSizeKiB(message: ChatMessageDto): number {
  return Math.max(1, Math.round(Math.max(0, message.attachmentBytes || 0) / 1024));
}
