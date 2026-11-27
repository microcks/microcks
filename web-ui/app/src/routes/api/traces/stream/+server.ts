import { error } from '@sveltejs/kit';
import { streamCall, TRACE_TICK_MS } from '#lib/server/observe.js';

/** Server-sent events: one `call` event every 3.2 s (about 19 calls per minute). Placeholder traffic. */
export const GET = ({ request }) => {
  if (!streamCall(0)) error(404, 'No REST service in the catalogue to trace.');
  const encoder = new TextEncoder();
  let timer: ReturnType<typeof setInterval> | undefined;
  const stream = new ReadableStream({
    start(controller) {
      let tick = 0;
      controller.enqueue(encoder.encode('retry: 3000\n\n'));
      timer = setInterval(() => {
        const call = streamCall(tick++);
        if (call) controller.enqueue(encoder.encode(`event: call\ndata: ${JSON.stringify(call)}\n\n`));
      }, TRACE_TICK_MS);
      request.signal.addEventListener('abort', () => {
        clearInterval(timer);
        try {
          controller.close();
        } catch {
          // already closed
        }
      });
    },
    cancel() {
      clearInterval(timer);
    }
  });
  return new Response(stream, {
    headers: { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' }
  });
};
