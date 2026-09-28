import { handleRequest } from '../server/server.mjs';

export default function health(request, response) {
  return handleRequest(request, response, '/health');
}
