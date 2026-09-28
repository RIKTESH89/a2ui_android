import test from 'node:test';
import assert from 'node:assert/strict';
import { createMediaService } from './media.mjs';

const surface = () => ({
  text: 'A travel card',
  components: [
    {
      id: 'root',
      component: 'Image',
      url: 'uiagent://image/travel',
      description: 'Tokyo skyline at sunset',
    },
  ],
});

test('keeps semantic artwork when no stock provider is configured', async () => {
  const service = createMediaService({ env: {} });
  const result = await service.resolveSurface(surface());
  assert.equal(result.reply.components[0].url, 'uiagent://image/travel');
  assert.equal(result.trustedAssetIds.size, 0);
});

test('resolves, caches and serves a Pexels image behind an opaque asset id', async () => {
  const bytes = Buffer.from('safe-image-bytes');
  const fetchImpl = async url => {
    const parsed = new URL(url);
    if (parsed.hostname === 'api.pexels.com') {
      return Response.json({
        photos: [
          {
            photographer: 'Ana Example',
            url: 'https://www.pexels.com/photo/tokyo-123/',
            src: { large: 'https://images.pexels.com/photos/123/pexels-photo-123.jpeg' },
          },
        ],
      });
    }
    return new Response(bytes, {
      status: 200,
      headers: { 'Content-Type': 'image/jpeg', 'Content-Length': String(bytes.length) },
    });
  };
  const service = createMediaService({ env: { PEXELS_API_KEY: 'test-key' }, fetchImpl });
  const result = await service.resolveSurface(surface());
  const reference = result.reply.components[0].url;
  assert.match(reference, /^uiagent:\/\/asset\/[a-f0-9]{32}\?/);
  assert.match(reference, /provider=pexels/);
  assert.match(reference, /credit=Ana\+Example/);
  assert.equal(result.trustedAssetIds.has(reference), true);

  let status;
  let headers;
  let body;
  const response = {
    writeHead(value, valueHeaders) {
      status = value;
      headers = valueHeaders;
    },
    end(value) {
      body = value;
    },
  };
  const token = new URL(reference).pathname.slice(1);
  assert.equal(
    service.handleRequest({ method: 'GET', url: `/media/${token}` }, response, 'request-1'),
    true,
  );
  assert.equal(status, 200);
  assert.equal(headers['Content-Type'], 'image/jpeg');
  assert.deepEqual(body, bytes);
});

test('rejects a provider result that points outside its trusted domains', async () => {
  const fetchImpl = async () =>
    Response.json({
      photos: [
        {
          photographer: 'Mallory',
          url: 'https://www.pexels.com/photo/1/',
          src: { large: 'https://evil.example/tracker.jpg' },
        },
      ],
    });
  const service = createMediaService({ env: { PEXELS_API_KEY: 'test-key' }, fetchImpl });
  const result = await service.resolveSurface(surface());
  assert.equal(result.reply.components[0].url, 'uiagent://image/travel');
  assert.equal(result.trustedAssetIds.size, 0);
});

test('signed media works across stateless service instances', async () => {
  const bytes = Buffer.from('serverless-image-bytes');
  const fetchImpl = async url => {
    const parsed = new URL(url);
    if (parsed.hostname === 'api.pexels.com') {
      return Response.json({
        photos: [
          {
            photographer: 'Ana Example',
            url: 'https://www.pexels.com/photo/tokyo-123/',
            src: { large: 'https://images.pexels.com/photos/123/pexels-photo-123.jpeg' },
          },
        ],
      });
    }
    return new Response(bytes, {
      status: 200,
      headers: { 'Content-Type': 'image/jpeg', 'Content-Length': String(bytes.length) },
    });
  };
  const env = {
    PEXELS_API_KEY: 'test-key',
    MEDIA_SIGNING_KEY: 'test-signing-key-with-at-least-32-characters',
  };
  const issuer = createMediaService({ env, fetchImpl, now: () => 1_000 });
  const result = await issuer.resolveSurface(surface());
  const reference = result.reply.components[0].url;
  const token = new URL(reference).pathname.slice(1);
  assert.match(token, /^[A-Za-z0-9_-]+\.[a-f0-9]{64}$/);

  const servingInstance = createMediaService({ env, fetchImpl, now: () => 2_000 });
  let status;
  let body;
  const completed = new Promise(resolve => {
    const response = {
      headersSent: false,
      writableEnded: false,
      writeHead(value) {
        status = value;
        this.headersSent = true;
      },
      end(value) {
        body = value;
        this.writableEnded = true;
        resolve();
      },
    };
    assert.equal(
      servingInstance.handleRequest(
        { method: 'GET', url: `/media/${token}` },
        response,
        'request-2',
      ),
      true,
    );
  });
  await completed;
  assert.equal(status, 200);
  assert.deepEqual(body, bytes);
});

test('rejects a tampered signed media token', async () => {
  const env = {
    PEXELS_API_KEY: 'test-key',
    MEDIA_SIGNING_KEY: 'test-signing-key-with-at-least-32-characters',
  };
  const fetchImpl = async url => {
    if (new URL(url).hostname === 'api.pexels.com') {
      return Response.json({
        photos: [
          {
            photographer: 'Ana Example',
            url: 'https://www.pexels.com/photo/tokyo-123/',
            src: { large: 'https://images.pexels.com/photos/123/pexels-photo-123.jpeg' },
          },
        ],
      });
    }
    throw Error('image download must not run');
  };
  const service = createMediaService({ env, fetchImpl, now: () => 1_000 });
  const result = await service.resolveSurface(surface());
  const token = new URL(result.reply.components[0].url).pathname.slice(1);
  const tampered = `${token.slice(0, -1)}${token.endsWith('0') ? '1' : '0'}`;
  let status;
  const completed = new Promise(resolve => {
    const response = {
      headersSent: false,
      writableEnded: false,
      writeHead(value) {
        status = value;
        this.headersSent = true;
      },
      end() {
        this.writableEnded = true;
        resolve();
      },
    };
    service.handleRequest({ method: 'GET', url: `/media/${tampered}` }, response, 'request-3');
  });
  await completed;
  assert.equal(status, 404);
});
