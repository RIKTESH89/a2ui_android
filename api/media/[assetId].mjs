import { handleRequest } from '../../server/server.mjs';

export const maxDuration = 30;

export default function media(request, response) {
  const assetId = Array.isArray(request.query?.assetId)
    ? request.query.assetId[0]
    : request.query?.assetId;
  if (typeof assetId !== 'string') {
    response.statusCode = 404;
    response.end('Not found');
    return;
  }
  return handleRequest(request, response, `/media/${assetId}`);
}
