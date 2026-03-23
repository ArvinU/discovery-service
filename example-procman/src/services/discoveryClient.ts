import { ServicesResponse } from '../types';

const DISCOVERY_URL = import.meta.env.VITE_DISCOVERY_URL || 'http://localhost:8500';

export async function fetchProcmanServices(): Promise<ServicesResponse> {
  const res = await fetch(`${DISCOVERY_URL}/api/services?procman=true`);
  if (!res.ok) {
    throw new Error(`Discovery service returned ${res.status}`);
  }
  return res.json();
}

export function getProxyUrl(instanceId: string): string {
  return `${DISCOVERY_URL}/proxy/${instanceId}/`;
}

/** GET a path on the microservice through the discovery reverse proxy (e.g. /api/procman-info). */
export function getProxiedServiceUrl(instanceId: string, path: string): string {
  const p = path.startsWith('/') ? path.slice(1) : path;
  return `${DISCOVERY_URL}/proxy/${instanceId}/${p}`;
}
