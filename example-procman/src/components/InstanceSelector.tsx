import { ServiceInstance } from '../types';

interface Props {
  instances: ServiceInstance[];
  selectedId: string;
  onSelect: (instanceId: string) => void;
}

export function InstanceSelector({ instances, selectedId, onSelect }: Props) {
  if (instances.length <= 1) return null;

  return (
    <div className="instance-selector">
      <label htmlFor="instance-select">Instance:</label>
      <select
        id="instance-select"
        value={selectedId}
        onChange={(e) => onSelect(e.target.value)}
      >
        {instances.map((inst) => (
          <option key={inst.instanceId} value={inst.instanceId}>
            {inst.instanceId} ({inst.host}:{inst.port})
          </option>
        ))}
      </select>
      <span className="instance-count">{instances.length} instances</span>
    </div>
  );
}
