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
  const [procmanInfo, setProcmanInfo] = useState<string | null>(null);
  const [procmanInfoLoading, setProcmanInfoLoading] = useState(false);
  const [procmanInfoError, setProcmanInfoError] = useState<string | null>(null);

  useEffect(() => {
    const stillExists = group.instances.some(
      (i) => i.instanceId === selectedInstanceId
    );
    if (!stillExists && group.instances.length > 0) {
      setSelectedInstanceId(group.instances[0].instanceId);
    }
  }, [group.instances, selectedInstanceId]);

  useEffect(() => {
    setProcmanInfo(null);
    setProcmanInfoError(null);
  }, [selectedInstanceId]);

  async function fetchProcmanInfo() {
    if (!selectedInstanceId) return;
    setProcmanInfoLoading(true);
    setProcmanInfoError(null);
    setProcmanInfo(null);
    try {
      const url = getProxiedServiceUrl(selectedInstanceId, '/api/procman-info');
      const res = await fetch(url);
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      setProcmanInfo(JSON.stringify(data, null, 2));
    } catch (e) {
      setProcmanInfoError(e instanceof Error ? e.message : 'Request failed');
    } finally {
      setProcmanInfoLoading(false);
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
      <div className="procman-service-toolbar">
        <button
          type="button"
          className="procman-info-btn"
          onClick={() => void fetchProcmanInfo()}
          disabled={procmanInfoLoading}
        >
          {procmanInfoLoading ? 'Loading…' : 'Get info from service'}
        </button>
        <span className="procman-info-hint">GET /api/procman-info via discovery proxy</span>
      </div>
      {procmanInfoError && (
        <div className="procman-info-error">{procmanInfoError}</div>
      )}
      {procmanInfo && (
        <pre className="procman-info-json">{procmanInfo}</pre>
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
