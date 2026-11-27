import { error, json } from '@sveltejs/kit';
import { conformanceForService } from '#lib/server/testing.js';

/** Microcks: GET /api/metrics/conformance/service/{serviceId} -> TestConformanceMetric. */
export const GET = ({ params }) => {
  const metric = conformanceForService(params.serviceId);
  if (!metric) error(404, `Service "${params.serviceId}" does not exist.`);
  return json(metric);
};
