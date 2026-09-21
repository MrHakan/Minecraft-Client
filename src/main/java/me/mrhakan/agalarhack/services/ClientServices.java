package me.mrhakan.agalarhack.services;

/** Stable access point for modules; implementations are registered by the composition root. */
public final class ClientServices {
    private static final ServiceRegistry REGISTRY = new ServiceRegistry();
    private ClientServices() { }
    public static ServiceRegistry registry() { return REGISTRY; }
    public static <T> T require(Class<T> type) { return REGISTRY.require(type); }
}
