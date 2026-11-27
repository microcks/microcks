import { error, json } from '@sveltejs/kit';
import { replayCall } from '#lib/server/observe.js';

export const POST = ({ params }) => {
  const call = replayCall(params.callId);
  if (!call) error(404, `No call ${params.callId} to replay.`);
  return json(call, { status: 201 });
};
