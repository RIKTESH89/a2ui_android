import { handleRequest } from '../server/server.mjs';

export const maxDuration = 300;

export default function chat(request, response) {
  return handleRequest(request, response, '/chat');
}
