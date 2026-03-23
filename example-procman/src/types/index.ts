export interface ServiceInstance {
  instanceId: string;
  name: string;
  host: string;
  port: number;
  protocol: string;
  metadata: Record<string, string>;
  lastHeartbeat: number;
  registeredAt: number;
}

export interface ServiceGroup {
  name: string;
  instanceCount: number;
  instances: ServiceInstance[];
}

export interface ServicesResponse {
  groups: ServiceGroup[];
}
