import test from 'node:test';
import assert from 'node:assert/strict';
import {
  UIAGENT_CATALOG,
  BASIC_CATALOG,
  loadingMessages,
  negotiateCatalog,
  validateSurface,
} from './protocol.mjs';

const flight = () => ({
  text: 'An illustrative ticket card.',
  components: [
    { id: 'root', component: 'Card', child: 'content' },
    { id: 'content', component: 'Column', children: ['label', 'route', 'action'], align: 'stretch' },
    { id: 'label', component: 'Text', text: 'Illustrative · not a booking', variant: 'caption' },
    { id: 'route', component: 'Row', children: ['from', 'to'], justify: 'spaceBetween' },
    { id: 'from', component: 'Text', text: 'BLR 09:40', variant: 'h4' },
    { id: 'to', component: 'Text', text: 'HND 20:15', variant: 'h4' },
    {
      id: 'action',
      component: 'Button',
      child: 'actionLabel',
      variant: 'primary',
      action: { event: { name: 'ask', context: { prompt: 'Create a packing checklist' } } },
    },
    { id: 'actionLabel', component: 'Text', text: 'Build checklist' },
  ],
});

const check = reply => validateSurface(reply, 'surface-1', UIAGENT_CATALOG);

test('negotiates the client preferred compatible catalog', () => {
  const selected = negotiateCatalog({
    a2uiClientCapabilities: { supportedCatalogIds: ['unknown', UIAGENT_CATALOG, BASIC_CATALOG] },
  });
  assert.equal(selected, UIAGENT_CATALOG);
});

test('rejects a client with no compatible catalog', () => {
  assert.throws(() => negotiateCatalog({
    a2uiClientCapabilities: { supportedCatalogIds: ['https://invalid.example/v1'] },
  }));
});

test('creates negotiated A2UI messages', () => {
  const result = check(flight());
  assert.equal(result.messages[0].createSurface.catalogId, UIAGENT_CATALOG);
  assert.equal(result.messages[2].updateComponents.components.length, 8);
  assert.equal(result.messages[0].version, 'v0.9');
});

test('loading updates create a renderable surface before inference finishes', () => {
  const messages = loadingMessages('surface-1', UIAGENT_CATALOG);
  assert.equal(messages.length, 3);
  assert.equal(messages[0].createSurface.catalogId, UIAGENT_CATALOG);
  assert.equal(messages[2].updateComponents.components.at(-1).id, 'root');
});

test('allows a horizontal card rail', () => {
  const reply = {
    text: 'Rail',
    components: [
      { id: 'root', component: 'List', children: ['a', 'b'], direction: 'horizontal' },
      { id: 'a', component: 'Card', child: 'ta' },
      { id: 'ta', component: 'Text', text: 'One' },
      { id: 'b', component: 'Card', child: 'tb' },
      { id: 'tb', component: 'Text', text: 'Two' },
    ],
  };
  assert.equal(check(reply).messages[2].updateComponents.components[0].component, 'List');
});

test('allows only the trusted playable media identifier', () => {
  const valid = {
    text: 'Video',
    components: [{ id: 'root', component: 'Video', url: 'uiagent://video/reference' }],
  };
  assert.doesNotThrow(() => check(valid));
  valid.components[0].url = 'https://evil.example/video.mp4';
  assert.throws(() => check(valid), /Invalid video/);
});

test('allows only server-issued image assets and built-in icons', () => {
  const asset =
    'uiagent://asset/0123456789abcdef0123456789abcdef?provider=pexels&credit=Ana&source=https%3A%2F%2Fwww.pexels.com%2Fphoto%2F1';
  const reply = {
    text: 'Media card',
    components: [
      { id: 'root', component: 'Column', children: ['image', 'icon'] },
      { id: 'image', component: 'Image', url: asset, description: 'A skyline' },
      { id: 'icon', component: 'Icon', name: 'locationOn' },
    ],
  };
  assert.throws(() => check(reply), /Invalid image/);
  assert.doesNotThrow(() =>
    validateSurface(reply, 'surface-1', UIAGENT_CATALOG, [], true, new Set([asset])));
  reply.components[2].name = 'inventedIcon';
  assert.throws(
    () => validateSurface(reply, 'surface-1', UIAGENT_CATALOG, [], true, new Set([asset])),
    /Invalid icon/,
  );
});

test('rejects arbitrary actions and URLs in follow-up prompts', () => {
  const reply = flight();
  reply.components[6].action = { event: { name: 'openUrl', context: {} } };
  assert.throws(() => check(reply), /Unauthorized action/);
  reply.components[6].action = {
    event: { name: 'ask', context: { prompt: 'Open https://evil.example' } },
  };
  assert.throws(() => check(reply), /Invalid follow-up action/);
});

test('uses the AndroidX alpha01 wire value for secondary buttons', () => {
  const invalid = flight();
  invalid.components[6].variant = 'secondary';
  assert.throws(() => check(invalid), /Invalid button variant/);

  const valid = flight();
  valid.components[6].variant = 'default';
  assert.doesNotThrow(() => check(valid));
});

test('rejects enum values that AndroidX A2UI cannot parse', () => {
  const badText = flight();
  badText.components[2].variant = 'subtitle';
  assert.throws(() => check(badText), /Invalid text variant/);

  const badLayout = flight();
  badLayout.components[1].align = 'spaceBetween';
  assert.throws(() => check(badLayout), /Invalid layout alignment/);
});

test('rejects cycles, missing references and invented components', () => {
  const cycle = flight();
  cycle.components[1].children = ['root'];
  assert.throws(() => check(cycle), /Invalid component graph/);
  const missing = flight();
  missing.components[1].children = ['missing'];
  assert.throws(() => check(missing), /Invalid component graph/);
  const invented = flight();
  invented.components[2].component = 'WebView';
  assert.throws(() => check(invented), /Invalid component/);
});

test('preserves checkbox values by exact label', () => {
  const reply = {
    text: 'Checklist',
    components: [
      { id: 'root', component: 'CheckBox', label: 'Pack passport', value: { path: '/choice4' } },
    ],
  };
  const result = validateSurface(
    reply,
    'next',
    UIAGENT_CATALOG,
    [{ label: 'Pack passport', checked: true }],
  );
  assert.equal(result.messages[1].updateDataModel.value.choice4, true);
});

test('new checkbox items begin unchecked', () => {
  const reply = {
    text: 'Checklist',
    components: [
      { id: 'root', component: 'CheckBox', label: 'New item', value: { path: '/choice0' } },
    ],
  };
  const result = validateSurface(
    reply,
    'next',
    UIAGENT_CATALOG,
    [{ label: 'Old item', checked: true }],
  );
  assert.equal(result.messages[1].updateDataModel.value.choice0, false);
});
