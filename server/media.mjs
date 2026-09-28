import { randomUUID } from 'node:crypto';

const MAX_ASSET_BYTES = 8_000_000;
const MAX_ASSETS = 32;
const ASSET_TTL_MS = 24 * 60 * 60 * 1000;
const ASSET_PATH = /^\/media\/([a-f0-9]{32})$/;

export function createMediaService({ env = process.env, fetchImpl = fetch, now = Date.now } = {}) {
  const assets = new Map();
  const configuredProviders = providerOrder(env).filter(name => providerKey(name, env));

  async function resolveSurface(reply, signal) {
    const trustedAssetIds = new Set();
    if (configuredProviders.length === 0) return { reply, trustedAssetIds };

    const components = [];
    for (const component of reply.components) {
      if (component.component !== 'Image' || !component.url.startsWith('uiagent://image/')) {
        components.push(component);
        continue;
      }
      const query = imageQuery(component);
      const asset = await resolveAsset(query, signal);
      if (!asset) {
        components.push(component);
        continue;
      }
      const reference = assetReference(asset);
      trustedAssetIds.add(reference);
      components.push({ ...component, url: reference });
    }
    return { reply: { ...reply, components }, trustedAssetIds };
  }

  async function resolveAsset(query, signal) {
    for (const name of configuredProviders) {
      try {
        const candidate =
          name === 'pexels'
            ? await searchPexels(query, env.PEXELS_API_KEY, fetchImpl, signal)
            : await searchPixabay(query, env.PIXABAY_API_KEY, fetchImpl, signal);
        if (!candidate) continue;
        const image = await downloadImage(candidate.imageUrl, name, fetchImpl, signal);
        evictExpired(assets, now());
        while (assets.size >= MAX_ASSETS) assets.delete(assets.keys().next().value);
        const token = randomUUID().replaceAll('-', '');
        const asset = {
          token,
          provider: name,
          credit: candidate.credit,
          sourceUrl: candidate.sourceUrl,
          bytes: image.bytes,
          contentType: image.contentType,
          expiresAt: now() + ASSET_TTL_MS,
        };
        assets.set(token, asset);
        return asset;
      } catch (error) {
        const detail = error instanceof Error ? error.message : 'unknown failure';
        console.warn(`[media] ${name} image unavailable: ${detail}`);
      }
    }
    return null;
  }

  function handleRequest(request, response, requestId) {
    if (request.method !== 'GET') return false;
    const match = new URL(request.url, 'http://localhost').pathname.match(ASSET_PATH);
    if (!match) return false;
    evictExpired(assets, now());
    const asset = assets.get(match[1]);
    if (!asset) {
      const body = Buffer.from(JSON.stringify({ error: 'Media not found or expired' }));
      response.writeHead(404, mediaHeaders(requestId, 'application/json', body.length));
      response.end(body);
      return true;
    }
    response.writeHead(
      200,
      mediaHeaders(requestId, asset.contentType, asset.bytes.length, asset.token),
    );
    response.end(asset.bytes);
    return true;
  }

  return {
    configuredProviders: Object.freeze([...configuredProviders]),
    resolveSurface,
    handleRequest,
  };
}

function providerOrder(env) {
  const requested = (env.IMAGE_PROVIDERS ?? 'pexels,pixabay')
    .split(',')
    .map(value => value.trim().toLowerCase())
    .filter(Boolean);
  if (requested.some(value => !['pexels', 'pixabay'].includes(value))) {
    throw Error('IMAGE_PROVIDERS accepts only pexels and pixabay');
  }
  return [...new Set(requested)];
}

function providerKey(provider, env) {
  return provider === 'pexels' ? env.PEXELS_API_KEY : env.PIXABAY_API_KEY;
}

function imageQuery(component) {
  const category = component.url.slice('uiagent://image/'.length);
  const description = typeof component.description === 'string' ? component.description : '';
  return `${description} ${category}`
    .replace(/[^\p{L}\p{N}\s,'-]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 100) || category;
}

async function searchPexels(query, key, fetchImpl, parentSignal) {
  const url = new URL('https://api.pexels.com/v1/search');
  url.searchParams.set('query', query);
  url.searchParams.set('orientation', 'landscape');
  url.searchParams.set('size', 'medium');
  url.searchParams.set('per_page', '5');
  const response = await fetchImpl(url, {
    headers: { Authorization: key },
    redirect: 'error',
    signal: combinedSignal(parentSignal, 10_000),
  });
  if (!response.ok) throw Error(`search returned ${response.status}`);
  const photo = (await response.json()).photos?.[0];
  if (
    !photo ||
    !isAllowedHttps(photo.src?.large, ['pexels.com']) ||
    !isAllowedHttps(photo.url, ['pexels.com'])
  ) return null;
  return {
    imageUrl: photo.src.large,
    sourceUrl: photo.url,
    credit: safeCredit(photo.photographer),
  };
}

async function searchPixabay(query, key, fetchImpl, parentSignal) {
  const url = new URL('https://pixabay.com/api/');
  url.searchParams.set('key', key);
  url.searchParams.set('q', query);
  url.searchParams.set('image_type', 'photo');
  url.searchParams.set('orientation', 'horizontal');
  url.searchParams.set('safesearch', 'true');
  url.searchParams.set('per_page', '5');
  const response = await fetchImpl(url, {
    redirect: 'error',
    signal: combinedSignal(parentSignal, 10_000),
  });
  if (!response.ok) throw Error(`search returned ${response.status}`);
  const photo = (await response.json()).hits?.[0];
  const imageUrl = photo?.largeImageURL ?? photo?.webformatURL;
  if (
    !photo ||
    !isAllowedHttps(imageUrl, ['pixabay.com']) ||
    !isAllowedHttps(photo.pageURL, ['pixabay.com'])
  ) return null;
  return {
    imageUrl,
    sourceUrl: photo.pageURL,
    credit: safeCredit(photo.user),
  };
}

async function downloadImage(url, provider, fetchImpl, parentSignal) {
  const domains = provider === 'pexels' ? ['pexels.com'] : ['pixabay.com'];
  const response = await fetchAllowed(url, domains, fetchImpl, parentSignal);
  if (!response.ok) throw Error(`download returned ${response.status}`);
  const contentType = response.headers.get('content-type')?.split(';')[0]?.toLowerCase();
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(contentType)) {
    throw Error('download returned an unsupported media type');
  }
  const declaredLength = Number(response.headers.get('content-length') ?? 0);
  if (declaredLength > MAX_ASSET_BYTES) throw Error('image exceeds the size limit');
  const reader = response.body?.getReader();
  if (!reader) throw Error('image response has no body');
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_ASSET_BYTES) {
      await reader.cancel();
      throw Error('image exceeds the size limit');
    }
    chunks.push(Buffer.from(value));
  }
  return { bytes: Buffer.concat(chunks), contentType };
}

async function fetchAllowed(url, domains, fetchImpl, parentSignal) {
  let current = new URL(url);
  for (let redirect = 0; redirect <= 3; redirect++) {
    if (!isAllowedHttps(current, domains)) throw Error('image host is not trusted');
    const response = await fetchImpl(current, {
      redirect: 'manual',
      signal: combinedSignal(parentSignal, 15_000),
    });
    if (![301, 302, 303, 307, 308].includes(response.status)) return response;
    const location = response.headers.get('location');
    if (!location) throw Error('invalid image redirect');
    current = new URL(location, current);
  }
  throw Error('too many image redirects');
}

function combinedSignal(parentSignal, timeout) {
  return parentSignal
    ? AbortSignal.any([parentSignal, AbortSignal.timeout(timeout)])
    : AbortSignal.timeout(timeout);
}

function isAllowedHttps(value, domains) {
  try {
    const url = value instanceof URL ? value : new URL(value);
    return (
      url.protocol === 'https:' &&
      url.username === '' &&
      url.password === '' &&
      domains.some(domain => url.hostname === domain || url.hostname.endsWith(`.${domain}`))
    );
  } catch {
    return false;
  }
}

function safeCredit(value) {
  return String(value ?? 'Contributor')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 80) || 'Contributor';
}

function assetReference(asset) {
  const params = new URLSearchParams({
    provider: asset.provider,
    credit: asset.credit,
    source: asset.sourceUrl,
  });
  return `uiagent://asset/${asset.token}?${params}`;
}

function evictExpired(assets, timestamp) {
  for (const [token, asset] of assets) {
    if (asset.expiresAt <= timestamp) assets.delete(token);
  }
}

function mediaHeaders(requestId, contentType, length, etag) {
  return {
    'Content-Type': contentType,
    'Content-Length': String(length),
    'Cache-Control': 'public, max-age=86400, immutable',
    'X-Content-Type-Options': 'nosniff',
    'X-Request-Id': requestId,
    ...(etag ? { ETag: `"${etag}"` } : {}),
  };
}
