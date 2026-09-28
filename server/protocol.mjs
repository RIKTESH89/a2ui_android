export const UIAGENT_CATALOG = 'https://uiagent.example/a2ui/catalog/v1/catalog.json';
export const BASIC_CATALOG = 'https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json';
export const SUPPORTED_CATALOGS = Object.freeze([UIAGENT_CATALOG, BASIC_CATALOG]);

const allowed = new Set([
  'Text',
  'Column',
  'Row',
  'List',
  'Card',
  'Button',
  'CheckBox',
  'Divider',
  'Image',
  'Icon',
  'Video',
]);
const actions = new Set(['ask', 'save']);
const imageIds = new Set([
  'uiagent://image/travel',
  'uiagent://image/weather',
  'uiagent://image/media',
  'uiagent://image/generic',
]);
const videoIds = new Set(['uiagent://video/reference']);
const textVariants = new Set(['h1', 'h2', 'h3', 'h4', 'h5', 'caption', 'body']);
const layoutAlignments = new Set(['start', 'center', 'end', 'stretch']);
const layoutJustifications = new Set([
  'start',
  'center',
  'end',
  'spaceAround',
  'spaceBetween',
  'spaceEvenly',
  'stretch',
]);
const listDirections = new Set(['vertical', 'horizontal']);
const buttonVariants = new Set(['default', 'primary', 'borderless']);
const imageFits = new Set(['contain', 'cover', 'fill', 'none', 'scaleDown']);
const imageVariants = new Set([
  'icon',
  'avatar',
  'smallFeature',
  'mediumFeature',
  'largeFeature',
  'header',
]);
const iconNames = new Set([
  'accountCircle', 'add', 'arrowBack', 'arrowForward', 'attachFile', 'calendarToday',
  'call', 'camera', 'check', 'close', 'delete', 'download', 'edit', 'error', 'event',
  'fastForward',
  'favorite', 'favoriteOff', 'folder', 'help', 'home', 'info', 'locationOn', 'lock',
  'lockOpen', 'mail', 'menu', 'moreHoriz', 'moreVert', 'notifications',
  'notificationsOff', 'pause',
  'payment', 'person', 'phone', 'photo', 'play', 'print', 'refresh', 'search', 'send',
  'rewind', 'settings', 'share', 'shoppingCart', 'skipNext', 'skipPrevious', 'star',
  'starHalf', 'starOff', 'stop', 'upload',
  'visibility', 'visibilityOff', 'volumeDown', 'volumeMute', 'volumeOff', 'volumeUp',
  'warning',
]);

export function negotiateCatalog(metadata) {
  const advertised = metadata?.a2uiClientCapabilities?.supportedCatalogIds;
  if (!Array.isArray(advertised) || advertised.length === 0) {
    throw Error('Client did not advertise an A2UI catalog');
  }
  if (
    advertised.length > 8 ||
    advertised.some(id => typeof id !== 'string' || id.length > 300)
  ) throw Error('Invalid catalog capabilities');
  const selected = advertised.find(id => SUPPORTED_CATALOGS.includes(id));
  if (!selected) throw Error('No compatible A2UI catalog');
  return selected;
}

function validateAction(action) {
  const event = action?.event;
  if (!event || Object.keys(action).length !== 1 || !actions.has(event.name)) {
    throw Error('Unauthorized action');
  }
  const context = event.context;
  if (!context || Array.isArray(context) || typeof context !== 'object') {
    throw Error('Invalid action context');
  }
  if (event.name === 'ask') {
    if (
      Object.keys(context).some(key => key !== 'prompt') ||
      typeof context.prompt !== 'string' ||
      !context.prompt.trim() ||
      context.prompt.length > 240 ||
      /https?:\/\//i.test(context.prompt)
    ) throw Error('Invalid follow-up action');
  }
  if (event.name === 'save') {
    if (
      Object.keys(context).some(key => !['itemId', 'title'].includes(key)) ||
      typeof context.itemId !== 'string' ||
      !/^[a-zA-Z0-9_-]{1,60}$/.test(context.itemId) ||
      typeof context.title !== 'string' ||
      !context.title.trim() ||
      context.title.length > 120
    ) throw Error('Invalid save action');
  }
}

export function validateClientAction(action) {
  if (!action || !['ask', 'save'].includes(action.name)) throw Error('Invalid action');
  if (
    typeof action.surfaceId !== 'string' ||
    typeof action.componentId !== 'string' ||
    !action.context ||
    typeof action.context !== 'object'
  ) throw Error('Invalid action');
  validateAction({ event: { name: action.name, context: action.context } });
}

export function validateSurface(
  reply,
  surfaceId,
  catalogId = UIAGENT_CATALOG,
  previousChecklist = [],
  includeCreate = true,
  trustedAssetIds = new Set(),
) {
  if (!SUPPORTED_CATALOGS.includes(catalogId)) throw Error('Unsupported catalog');
  if (!reply || typeof reply.text !== 'string' || reply.text.length > 2000) {
    throw Error('Invalid assistant text');
  }
  if (
    !Array.isArray(reply.components) ||
    !reply.components.length ||
    reply.components.length > 70
  ) throw Error('Invalid component count');

  const ids = new Set();
  for (const component of reply.components) {
    if (
      !component ||
      typeof component.id !== 'string' ||
      !/^[a-zA-Z0-9_-]{1,60}$/.test(component.id) ||
      ids.has(component.id) ||
      !allowed.has(component.component)
    ) throw Error('Invalid component');
    ids.add(component.id);
    const props = {
      Text: ['text', 'variant'],
      Column: ['children', 'align', 'justify'],
      Row: ['children', 'align', 'justify'],
      List: ['children', 'direction', 'align'],
      Card: ['child'],
      Button: ['child', 'variant', 'action'],
      CheckBox: ['label', 'value'],
      Divider: [],
      Image: ['url', 'description', 'fit', 'variant'],
      Icon: ['name'],
      Video: ['url'],
    }[component.component];
    const unexpected = Object.keys(component)
      .find(key => !['id', 'component', ...props].includes(key));
    if (unexpected) throw Error(`Unexpected property ${unexpected} on ${component.component}`);

    if (
      component.component === 'Text' &&
      (typeof component.text !== 'string' || component.text.length > 2500)
    ) throw Error('Invalid text');
    if (
      component.component === 'Text' &&
      !isOptionalEnum(component.variant, textVariants)
    ) throw Error('Invalid text variant');
    if (
      ['Column', 'Row', 'List'].includes(component.component) &&
      (!Array.isArray(component.children) ||
        component.children.length > 24 ||
        component.children.some(child => typeof child !== 'string'))
    ) throw Error('Invalid children');
    if (['Card', 'Button'].includes(component.component) && typeof component.child !== 'string') {
      throw Error('Missing child');
    }
    if (
      ['Column', 'Row', 'List'].includes(component.component) &&
      !isOptionalEnum(component.align, layoutAlignments)
    ) throw Error('Invalid layout alignment');
    if (
      ['Column', 'Row'].includes(component.component) &&
      !isOptionalEnum(component.justify, layoutJustifications)
    ) throw Error('Invalid layout justification');
    if (
      component.component === 'List' &&
      !isOptionalEnum(component.direction, listDirections)
    ) throw Error('Invalid list direction');
    if (component.component === 'Button') {
      if (!isOptionalEnum(component.variant, buttonVariants)) {
        throw Error('Invalid button variant');
      }
      validateAction(component.action);
    }
    if (
      component.component === 'CheckBox' &&
      (typeof component.label !== 'string' ||
        component.label.length > 160 ||
        !/^\/choice[0-9]{1,2}$/.test(component.value?.path) ||
        Object.keys(component.value).length !== 1)
    ) throw Error('Invalid checkbox binding');
    if (
      component.component === 'Image' &&
      (!(imageIds.has(component.url) || trustedAssetIds.has(component.url)) ||
        typeof component.description !== 'string' ||
        component.description.length > 240 ||
        !isOptionalEnum(component.fit, imageFits) ||
        !isOptionalEnum(component.variant, imageVariants))
    ) throw Error('Invalid image');
    if (component.component === 'Icon' && !iconNames.has(component.name)) {
      throw Error('Invalid icon');
    }
    if (component.component === 'Video' && !videoIds.has(component.url)) {
      throw Error('Invalid video');
    }
  }

  if (!ids.has('root')) throw Error('Missing root');
  const byId = new Map(reply.components.map(component => [component.id, component]));
  const seen = new Set();
  function walk(id, parents = new Set()) {
    if (!ids.has(id) || parents.has(id) || parents.size > 12) {
      throw Error('Invalid component graph');
    }
    seen.add(id);
    const component = byId.get(id);
    const next = new Set([...parents, id]);
    for (const child of component.children ?? (component.child ? [component.child] : [])) {
      walk(child, next);
    }
  }
  walk('root');
  if (seen.size !== ids.size) throw Error('Unreachable component');

  const prior = new Map(previousChecklist.map(item => [item.label, item.checked]));
  const value = {};
  for (const component of reply.components) {
    if (component.component === 'CheckBox') {
      value[component.value.path.slice(1)] = prior.get(component.label) === true;
    }
  }
  const messages = finalMessages(surfaceId, catalogId, value, reply.components, includeCreate);
  return { text: reply.text, messages };
}

function isOptionalEnum(value, allowedValues) {
  return value === undefined || (typeof value === 'string' && allowedValues.has(value));
}

export function loadingMessages(surfaceId, catalogId) {
  return [
    message('createSurface', {
      surfaceId,
      catalogId,
      sendDataModel: true,
    }),
    message('updateDataModel', { surfaceId, path: '/', value: {} }),
    message('updateComponents', {
      surfaceId,
      components: [
        { id: 'loadingTitle', component: 'Text', text: 'Composing your interface…', variant: 'h4' },
        { id: 'loadingBody', component: 'Text', text: 'UIAgent is arranging trusted native components.' },
        { id: 'loadingColumn', component: 'Column', children: ['loadingTitle', 'loadingBody'], align: 'stretch' },
        { id: 'root', component: 'Card', child: 'loadingColumn' },
      ],
    }),
  ];
}

function finalMessages(surfaceId, catalogId, value, components, includeCreate) {
  const messages = [];
  if (includeCreate) {
    messages.push(message('createSurface', { surfaceId, catalogId, sendDataModel: true }));
  }
  messages.push(message('updateDataModel', { surfaceId, path: '/', value }));
  messages.push(message('updateComponents', { surfaceId, components }));
  return messages;
}

function message(type, body) {
  return { version: 'v0.9', [type]: body };
}
