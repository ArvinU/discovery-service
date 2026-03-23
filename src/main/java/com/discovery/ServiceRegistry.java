package com.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServiceRegistry {

    private static final Logger LOG = Logger.getLogger(ServiceRegistry.class.getName());
    private static final String REGISTRY_FILE = "registry.json";

    private final ConcurrentHashMap<String, ServiceInstance> instances = new ConcurrentHashMap<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private final List<RegistryChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final String dataDir;
    private final String nodeId;
    private final Gson gson;
    private volatile boolean dirty = false;
    private final ScheduledExecutorService flushScheduler;

    public interface RegistryChangeListener {
        void onChange(ChangeEvent event);
    }

    public ServiceRegistry(String dataDir, String nodeId) {
        this.dataDir = dataDir;
        this.nodeId = nodeId;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        new File(dataDir).mkdirs();
        loadFromFile();

        flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "registry-flush");
            t.setDaemon(true);
            return t;
        });
        flushScheduler.scheduleAtFixedRate(() -> {
            if (dirty) {
                persistToFile();
                dirty = false;
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public String getNodeId() {
        return nodeId;
    }

    public String register(ServiceInstance instance) {
        String instanceId = ServiceInstance.generateInstanceId(instance.getName());
        instance.setInstanceId(instanceId);
        instance.setRegisteredAt(System.currentTimeMillis());
        instance.setLastHeartbeat(System.currentTimeMillis());
        instance.setSourceNodeId(nodeId);
        instances.put(instanceId, instance);
        persistToFile();
        fireEvent(ChangeEvent.EventType.REGISTER, instance, false);
        LOG.info("Registered: " + instance);
        return instanceId;
    }

    public boolean deregister(String instanceId) {
        ServiceInstance removed = instances.remove(instanceId);
        if (removed != null) {
            persistToFile();
            fireEvent(ChangeEvent.EventType.DEREGISTER, removed, false);
            LOG.info("Deregistered: " + removed);
            return true;
        }
        return false;
    }

    public boolean heartbeat(String instanceId) {
        ServiceInstance inst = instances.get(instanceId);
        if (inst != null) {
            inst.setLastHeartbeat(System.currentTimeMillis());
            dirty = true;
            return true;
        }
        return false;
    }

    public void evict(String instanceId) {
        ServiceInstance removed = instances.remove(instanceId);
        if (removed != null) {
            dirty = true;
            fireEvent(ChangeEvent.EventType.EVICT, removed, false);
            LOG.info("Evicted (heartbeat timeout): " + removed);
        }
    }

    /**
     * Apply a single event received from a peer node.
     * Fires a SYNC event so listeners can distinguish peer-originated changes.
     */
    public void applyPeerEvent(ChangeEvent.EventType originalType, ServiceInstance instance) {
        switch (originalType) {
            case REGISTER: {
                ServiceInstance existing = instances.get(instance.getInstanceId());
                if (existing != null && existing.getLastHeartbeat() >= instance.getLastHeartbeat()) {
                    return;
                }
                instances.put(instance.getInstanceId(), instance);
                dirty = true;
                fireEvent(ChangeEvent.EventType.SYNC, instance, true);
                LOG.fine("Sync applied REGISTER for " + instance.getInstanceId());
                break;
            }
            case DEREGISTER:
            case EVICT: {
                ServiceInstance removed = instances.remove(instance.getInstanceId());
                if (removed != null) {
                    dirty = true;
                    fireEvent(ChangeEvent.EventType.SYNC, removed, true);
                    LOG.fine("Sync applied " + originalType + " for " + instance.getInstanceId());
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Bulk merge from a peer's full state during periodic pull sync.
     * Adds missing instances and updates stale ones based on heartbeat timestamp.
     */
    public void mergeFromPeer(Collection<ServiceInstance> peerInstances) {
        boolean changed = false;
        for (ServiceInstance peerInst : peerInstances) {
            ServiceInstance local = instances.get(peerInst.getInstanceId());
            if (local == null) {
                instances.put(peerInst.getInstanceId(), peerInst);
                changed = true;
            } else if (peerInst.getLastHeartbeat() > local.getLastHeartbeat()) {
                instances.put(peerInst.getInstanceId(), peerInst);
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
        }
    }

    public ServiceInstance getInstance(String instanceId) {
        return instances.get(instanceId);
    }

    public Collection<ServiceInstance> getAllInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public List<ServiceInstance> getByName(String name) {
        return instances.values().stream()
                .filter(i -> i.getName().equals(name))
                .collect(Collectors.toList());
    }

    public List<ServiceInstance> getByMetadata(String key, String value) {
        return instances.values().stream()
                .filter(i -> value.equals(i.getMetadata().get(key)))
                .collect(Collectors.toList());
    }

    public Map<String, List<ServiceInstance>> getGrouped() {
        return instances.values().stream()
                .collect(Collectors.groupingBy(ServiceInstance::getName));
    }

    public Map<String, List<ServiceInstance>> getGroupedByMetadata(String key, String value) {
        return instances.values().stream()
                .filter(i -> value.equals(i.getMetadata().get(key)))
                .collect(Collectors.groupingBy(ServiceInstance::getName));
    }

    public Set<String> getInstanceIds() {
        return Collections.unmodifiableSet(instances.keySet());
    }

    public int size() {
        return instances.size();
    }

    public void addChangeListener(RegistryChangeListener listener) {
        listeners.add(listener);
    }

    public void shutdown() {
        flushScheduler.shutdown();
        if (dirty) {
            persistToFile();
        }
    }

    private void fireEvent(ChangeEvent.EventType type, ServiceInstance instance, boolean fromSync) {
        List<ServiceInstance> allForService = getByName(instance.getName());
        ChangeEvent event = new ChangeEvent(type, instance, allForService, fromSync);
        for (RegistryChangeListener listener : listeners) {
            try {
                listener.onChange(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener error", e);
            }
        }
    }

    private void persistToFile() {
        fileLock.writeLock().lock();
        try {
            File target = new File(dataDir, REGISTRY_FILE);
            File tmp = new File(dataDir, REGISTRY_FILE + ".tmp");
            List<ServiceInstance> list = new ArrayList<>(instances.values());
            String json = gson.toJson(list);
            try (Writer writer = new BufferedWriter(new FileWriter(tmp))) {
                writer.write(json);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to persist registry", e);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private void loadFromFile() {
        fileLock.readLock().lock();
        try {
            File file = new File(dataDir, REGISTRY_FILE);
            if (!file.exists()) {
                LOG.info("No existing registry file found, starting fresh");
                return;
            }
            try (Reader reader = new BufferedReader(new FileReader(file))) {
                Type listType = new TypeToken<List<ServiceInstance>>() {}.getType();
                List<ServiceInstance> loaded = gson.fromJson(reader, listType);
                if (loaded != null) {
                    int count = 0;
                    for (ServiceInstance inst : loaded) {
                        instances.put(inst.getInstanceId(), inst);
                        count++;
                    }
                    LOG.info("Loaded " + count + " instances from disk");
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load registry from file", e);
        } finally {
            fileLock.readLock().unlock();
        }
    }
}
