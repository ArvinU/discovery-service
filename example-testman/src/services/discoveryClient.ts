import { ServicesResponse } from '../types';

/**
 * Discovery base URL only (scheme + host + port). Must NOT include a path like {@code /api}
 * — proxy lives at {@code /proxy/...} and APIs at {@code /api/...} off that base.
 * If {@code VITE_DISCOVERY_URL} is mistakenly set to {@code http://host:8500/api}, we strip {@code /api}
 * so requests don't become {@code /api/proxy/...} (which hits the JSON API and returns 404 Not found).
 */
function normalizeDiscoveryBase(raw: string): string {
  let u = raw.trim();
  if (!u) {
    return 'http://localhost:8500';
  }
  u = u.replace(/\/+$/, '');
  if (u.endsWith('/api')) {
    u = u.slice(0, -4).replace(/\/+$/, '');
  }
  return u || 'http://localhost:8500';
}

const DISCOVERY_URL = normalizeDiscoveryBase(
  import.meta.env.VITE_DISCOVERY_URL || 'http://localhost:8500',
);

export async function fetchTestmanServices(): Promise<ServicesResponse> {
  const res = await fetch(`${DISCOVERY_URL}/api/services?testman=true`);
  if (!res.ok) {
    throw new Error(`Discovery service returned ${res.status}`);
  }
  return res.json();
}

export function getProxyUrl(instanceId: string): string {
  return `${DISCOVERY_URL}/proxy/${instanceId}/`;
}

/** GET a path on the microservice through the discovery reverse proxy (e.g. /api/test-info). */
export function getProxiedServiceUrl(instanceId: string, path: string): string {
  const p = path.startsWith('/') ? path.slice(1) : path;
  return `${DISCOVERY_URL}/proxy/${instanceId}/${p}`;
}
