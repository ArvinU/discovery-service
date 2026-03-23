package com.discovery;

import java.util.List;

public class ChangeEvent {

    public enum EventType {
        REGISTER, DEREGISTER, EVICT, SYNC
    }

    private final EventType event;
    private final long timestamp;
    private final String instanceId;
    private final String serviceName;
    private final ServiceInstance instance;
    private final List<ServiceInstance> allInstancesForService;
    private final boolean fromSync;

    public ChangeEvent(EventType event, ServiceInstance instance, List<ServiceInstance> allInstancesForService) {
        this(event, instance, allInstancesForService, false);
    }

    public ChangeEvent(EventType event, ServiceInstance instance, List<ServiceInstance> allInstancesForService,
                       boolean fromSync) {
        this.event = event;
        this.timestamp = System.currentTimeMillis();
        this.instanceId = instance.getInstanceId();
        this.serviceName = instance.getName();
        this.instance = instance;
        this.allInstancesForService = allInstancesForService;
        this.fromSync = fromSync;
    }

    public EventType getEvent() {
        return event;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ServiceInstance getInstance() {
        return instance;
    }

    public List<ServiceInstance> getAllInstancesForService() {
        return allInstancesForService;
    }

    public boolean isFromSync() {
        return fromSync;
    }
}
