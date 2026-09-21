package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceRegistryTest {
    @Test void rejectsReplacementAndSupportsOptionalDependencies() {
        ServiceRegistry registry = new ServiceRegistry();
        assertTrue(registry.find(Runnable.class).isEmpty());
        assertThrows(IllegalStateException.class, () -> registry.require(Runnable.class));
        Runnable implementation = () -> { };
        registry.register(Runnable.class, implementation);
        assertSame(implementation, registry.require(Runnable.class));
        assertThrows(IllegalStateException.class, () -> registry.register(Runnable.class, () -> { }));
    }
}
