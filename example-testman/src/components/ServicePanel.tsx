import { useState, useEffect } from 'react';
import { ServiceGroup } from '../types';
import { getProxyUrl, getProxiedServiceUrl } from '../services/discoveryClient';
import { InstanceSelector } from './InstanceSelector';

interface Props {
  group: ServiceGroup;
}

export function ServicePanel({ group }: Props) {
  const [selectedInstanceId, setSelectedInstanceId] = useState(
    group.instances[0]?.instanceId || ''
  );
  const [testInfo, setTestInfo] = useState<string | null>(null);
  const [testInfoLoading, setTestInfoLoading] = useState(false);
  const [testInfoError, setTestInfoError] = useState<string | null>(null);

  useEffect(() => {
    const stillExists = group.instances.some(
      (i) => i.instanceId === selectedInstanceId
    );
    if (!stillExists && group.instances.length > 0) {
      setSelectedInstanceId(group.instances[0].instanceId);
    }
  }, [group.instances, selectedInstanceId]);

  useEffect(() => {
    setTestInfo(null);
    setTestInfoError(null);
  }, [selectedInstanceId]);

  async function fetchTestInfo() {
    if (!selectedInstanceId) return;
    setTestInfoLoading(true);
    setTestInfoError(null);
    setTestInfo(null);
    try {
      const url = getProxiedServiceUrl(selectedInstanceId, '/api/test-info');
      const res = await fetch(url);
      if (!res.ok) {
        const body = (await res.text()).trim();
        const hint =
          body.length > 0
            ? ` — ${body.length > 240 ? `${body.slice(0, 240)}…` : body}`
            : '';
        throw new Error(`HTTP ${res.status}${hint}`);
      }
      const data = await res.json();
      setTestInfo(JSON.stringify(data, null, 2));
    } catch (e) {
      setTestInfoError(e instanceof Error ? e.message : 'Request failed');
    } finally {
      setTestInfoLoading(false);
    }
  }

  if (group.instances.length === 0) {
    return <div className="panel-empty">No instances available</div>;
  }

  const proxyUrl = getProxyUrl(selectedInstanceId);

  return (
    <div className="service-panel">
      <InstanceSelector
        instances={group.instances}
        selectedId={selectedInstanceId}
        onSelect={setSelectedInstanceId}
      />
      <div className="test-service-toolbar">
        <button
          type="button"
          className="test-info-btn"
          onClick={() => void fetchTestInfo()}
          disabled={testInfoLoading}
        >
          {testInfoLoading ? 'Loading…' : 'Get info from service'}
        </button>
        <span className="test-info-hint">GET /api/test-info via discovery proxy</span>
      </div>
      {testInfoError && (
        <div className="test-info-error">{testInfoError}</div>
      )}
      {testInfo && (
        <pre className="test-info-json">{testInfo}</pre>
      )}
      <iframe
        key={selectedInstanceId}
        className="service-iframe"
        src={proxyUrl}
        title={`${group.name} - ${selectedInstanceId}`}
      />
    </div>
  );
}
