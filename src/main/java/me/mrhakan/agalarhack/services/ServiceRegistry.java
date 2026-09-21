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
    public <T> T require(Class<T> contract) {
        return find(contract).orElseThrow(() -> new IllegalStateException("Missing service: " + contract.getName()));
    }
}
