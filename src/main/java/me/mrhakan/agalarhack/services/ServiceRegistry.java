package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Client-thread registry. Explicit contracts, no reflection and no silent replacement. */
public final class ServiceRegistry {
    private final Map<Class<?>, Object> services = new LinkedHashMap<>();
    public <T> T register(Class<T> contract, T service) {
        Objects.requireNonNull(contract);
        Objects.requireNonNull(service);
        if (!contract.isInstance(service)) throw new IllegalArgumentException("Invalid service: " + contract.getName());
        if (services.putIfAbsent(contract, service) != null) {
            throw new IllegalStateException("Service already registered: " + contract.getName());
        }
        return service;
    }
    public <T> Optional<T> find(Class<T> contract) {
        return Optional.ofNullable(contract.cast(services.get(contract)));
    }
    /**
     * Render and tick code calls this per frame, so a present service is a plain lookup: going
     * through {@link #find} cost an {@code Optional} and a capturing lambda on every call.
     */
    public <T> T require(Class<T> contract) {
        Object service = services.get(contract);
        if (service == null) throw new IllegalStateException("Missing service: " + contract.getName());
        return contract.cast(service);
    }
}
