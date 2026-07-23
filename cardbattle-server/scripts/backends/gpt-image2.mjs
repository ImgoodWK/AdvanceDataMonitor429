import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { writeFinalOutputsManifest, ensureCardArtSize1024 } from '../lib/card-art-common.mjs';
import { STYLE_SUFFIX } from '../lib/card-art-style.mjs';

/**
 * OpenAI-compatible GPT Image 2 adapter.
 * Env (never log values):
 *   TEXTECH_IMAGE_API_KEY   required
 *   TEXTECH_IMAGE_BASE_URL  default https://api.openai.com/v1
 *   TEXTECH_IMAGE_MODEL     default gpt-image-2
 *   TEXTECH_IMAGE_SIZE      default 1024x1024
 *   TEXTECH_IMAGE_QUALITY   optional (provider-specific; default omitted)
 */

function envConfig() {
  const apiKey = process.env.TEXTECH_IMAGE_API_KEY;
  if (!apiKey) {
    throw new Error('TEXTECH_IMAGE_API_KEY is not set (configure locally; never commit)');
  }
  return {
    apiKey,
    baseUrl: (process.env.TEXTECH_IMAGE_BASE_URL || 'https://api.openai.com/v1').replace(/\/$/, ''),
    model: process.env.TEXTECH_IMAGE_MODEL || 'gpt-image-2',
    size: process.env.TEXTECH_IMAGE_SIZE || '1024x1024',
    quality: process.env.TEXTECH_IMAGE_QUALITY || '',
  };
}

function blobFromFile(filePath) {
  const buffer = fs.readFileSync(filePath);
  const name = path.basename(filePath);
  return new Blob([buffer], { type: 'image/png' });
}

async function postJson(url, apiKey, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`DIY image API non-JSON response (${response.status}): ${text.slice(0, 240)}`);
  }
  if (!response.ok) {
    const message = json.error?.message || json.message || text.slice(0, 240);
    throw new Error(`DIY image API ${response.status}: ${message}`);
  }
  return json;
}

async function postMultipart(url, apiKey, form) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
    },
    body: form,
  });
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`DIY image API non-JSON response (${response.status}): ${text.slice(0, 240)}`);
  }
  if (!response.ok) {
    const message = json.error?.message || json.message || text.slice(0, 240);
    throw new Error(`DIY image API ${response.status}: ${message}`);
  }
  return json;
}

async function decodeImagePayload(json, outputRoot) {
  const first = json.data?.[0];
  if (!first) throw new Error('DIY image API returned no data entries');
  const pngName = 'card.png';
  const pngPath = path.join(outputRoot, pngName);
  if (first.b64_json) {
    fs.writeFileSync(pngPath, Buffer.from(first.b64_json, 'base64'));
  } else if (first.url) {
    const imageResponse = await fetch(first.url);
    if (!imageResponse.ok) {
      throw new Error(`failed to download DIY image URL (${imageResponse.status})`);
    }
    const bytes = Buffer.from(await imageResponse.arrayBuffer());
    fs.writeFileSync(pngPath, bytes);
  } else {
    throw new Error('DIY image API entry missing b64_json/url');
  }
  const size = ensureCardArtSize1024(pngPath);
  if (size.width !== 1024 || size.height !== 1024) {
    throw new Error(`DIY output must be 1024x1024, received ${size.width}x${size.height}`);
  }
  return pngName;
}

/**
 * @param {{ prompt: string, referencePaths: string[], outputRoot: string, quality?: string, onProgress?: (msg: string) => void }} args
 */
export async function runDiyImage2(args) {
  const config = envConfig();
  const { prompt, referencePaths, outputRoot, onProgress } = args;
  fs.mkdirSync(outputRoot, { recursive: true });
  const fullPrompt = `${prompt}, ${STYLE_SUFFIX}`;
  const jobId = `diy-${randomUUID()}`;
  onProgress?.(`[INFO] diy starting model=${config.model} refs=${referencePaths.length}`);

  let json;
  if (referencePaths.length > 0) {
    // Prefer edits endpoint when references exist (OpenAI-compatible multimodal image edit).
    const form = new FormData();
    form.append('model', config.model);
    form.append('prompt', fullPrompt);
    form.append('size', config.size);
    form.append('n', '1');
    if (config.quality) form.append('quality', config.quality);
    // Ask for base64 when the provider honors it via multipart (some ignore; generations fallback handles JSON).
    form.append('response_format', 'b64_json');
    for (const ref of referencePaths) {
      form.append('image[]', blobFromFile(ref), path.basename(ref));
      // Also append singular `image` for providers that only accept one field name.
    }
    // Ensure at least one `image` field for single-image providers.
    form.append('image', blobFromFile(referencePaths[0]), path.basename(referencePaths[0]));
    try {
      json = await postMultipart(`${config.baseUrl}/images/edits`, config.apiKey, form);
    } catch (error) {
      onProgress?.(`[WARN] edits endpoint failed (${error.message}); falling back to generations without binary refs`);
      const promptWithRefNote = [
        fullPrompt,
        `style locked by ${referencePaths.length} local reference image(s) that could not be uploaded; keep cubic volumetric forms and centered cinematic still`,
      ].join(', ');
      const body = {
        model: config.model,
        prompt: promptWithRefNote,
        size: config.size,
        n: 1,
        response_format: 'b64_json',
      };
      if (config.quality) body.quality = config.quality;
      json = await postJson(`${config.baseUrl}/images/generations`, config.apiKey, body);
    }
  } else {
    const body = {
      model: config.model,
      prompt: fullPrompt,
      size: config.size,
      n: 1,
      response_format: 'b64_json',
    };
    if (config.quality) body.quality = config.quality;
    json = await postJson(`${config.baseUrl}/images/generations`, config.apiKey, body);
  }

  onProgress?.(`[INFO] diy status=success job_id=${jobId}`);
  const pngName = await decodeImagePayload(json, outputRoot);
  writeFinalOutputsManifest(outputRoot, pngName, jobId);
  return { jobId, pngName };
}

export function diyConfigured() {
  return Boolean(process.env.TEXTECH_IMAGE_API_KEY);
}
