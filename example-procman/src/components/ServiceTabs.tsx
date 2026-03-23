import { useState, useEffect, useCallback } from 'react';
import { ServiceGroup } from '../types';
import { fetchProcmanServices } from '../services/discoveryClient';
import { ServicePanel } from './ServicePanel';

const POLL_INTERVAL = 5000;

export function ServiceTabs() {
  const [groups, setGroups] = useState<ServiceGroup[]>([]);
  const [activeTab, setActiveTab] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const poll = useCallback(async () => {
    try {
      const data = await fetchProcmanServices();
      setGroups(data.groups);
      setError(null);

      if (data.groups.length > 0) {
        setActiveTab((prev) => {
          const names = data.groups.map((g) => g.name);
          return names.includes(prev) ? prev : names[0];
        });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to reach discovery service');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    poll();
    const interval = setInterval(poll, POLL_INTERVAL);
    return () => clearInterval(interval);
  }, [poll]);

  if (loading) {
    return <div className="status-message">Connecting to discovery service...</div>;
  }

  if (error) {
    return (
      <div className="status-message error">
        <span>Discovery service unavailable</span>
        <span className="error-detail">{error}</span>
      </div>
    );
  }

  if (groups.length === 0) {
    return (
      <div className="status-message">
        No procman services registered. Start a microservice with procman=true to see it here.
      </div>
    );
  }

  const activeGroup = groups.find((g) => g.name === activeTab);

  return (
    <div className="tabs-container">
      <nav className="tab-bar">
        {groups.map((group) => (
          <button
            key={group.name}
            className={`tab-button ${group.name === activeTab ? 'active' : ''}`}
            onClick={() => setActiveTab(group.name)}
          >
            <span className="tab-name">{formatName(group.name)}</span>
            <span className="tab-count">{group.instanceCount}</span>
          </button>
        ))}
      </nav>
      <div className="tab-content">
        {activeGroup && <ServicePanel group={activeGroup} />}
      </div>
    </div>
  );
}

function formatName(name: string): string {
  return name
    .replace(/[-_]/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
