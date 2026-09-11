package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PacketRatesTest {

    private static PacketRates counting() {
        PacketRates rates = new PacketRates();
        rates.install();
        rates.requestCounting();
        return rates;
    }

    @Test
    void nothingIsCountedBeforeSomethingAsks() {
        PacketRates rates = new PacketRates();
        rates.install();
        assertFalse(rates.isCounting());
        for (int index = 0; index < 100; index++) PacketRates.countInbound();
        rates.tick(0);
        rates.tick(2000);
        assertFalse(rates.hasSamples());
    }

    @Test
    void ratesAreCountedPerSecond() {
        PacketRates rates = counting();
        rates.tick(0);
        for (int index = 0; index < 40; index++) PacketRates.countInbound();
        for (int index = 0; index < 10; index++) PacketRates.countOutbound();
        rates.tick(1000);
        assertTrue(rates.hasSamples());
        assertEquals(40.0, rates.inboundPerSecond(), 1e-9);
        assertEquals(10.0, rates.outboundPerSecond(), 1e-9);
    }

    @Test
    void aStalledClientTickDoesNotInflateTheRate() {
        PacketRates rates = counting();
        rates.tick(0);
        for (int index = 0; index < 40; index++) PacketRates.countInbound();
        // Four seconds passed, not one: 40 packets is 10 a second, not 40.
        rates.tick(4000);
        assertEquals(10.0, rates.inboundPerSecond(), 1e-9);
    }

    @Test
    void nothingIsSampledUntilASecondHasPassed() {
        PacketRates rates = counting();
        rates.tick(0);
        PacketRates.countInbound();
        rates.tick(500);
        assertFalse(rates.hasSamples(), "half a second is not a rate");
        rates.tick(1000);
        assertTrue(rates.hasSamples());
    }

    @Test
    void countingExpiresOnItsOwnAndClearsTheFigures() {
        PacketRates rates = counting();
        rates.tick(0);
        for (int index = 0; index < 10; index++) PacketRates.countInbound();
        rates.tick(1000);
        assertTrue(rates.hasSamples());
        for (int tick = 0; tick <= PacketRates.IDLE_TICKS + 1; tick++) rates.tick(1000 + tick);
        assertFalse(rates.isCounting());
        assertFalse(rates.hasSamples(), "a stale rate must not read as current");
    }

    @Test
    void askingAgainKeepsItAlive() {
        PacketRates rates = counting();
        for (int tick = 0; tick < PacketRates.IDLE_TICKS * 3; tick++) {
            rates.requestCounting();
            rates.tick(tick);
            assertTrue(rates.isCounting(), "tick " + tick);
        }
    }

    @Test
    void theWindowSmoothsAcrossSeconds() {
        PacketRates rates = counting();
        rates.tick(0);
        for (int index = 0; index < 100; index++) PacketRates.countInbound();
        rates.tick(1000);
        rates.tick(2000);
        // One busy second and one quiet one average to half, not to either end.
        assertEquals(50.0, rates.inboundPerSecond(), 1e-9);
    }

    @Test
    void resetCountsFromZeroAgain() {
        PacketRates rates = counting();
        rates.tick(0);
        for (int index = 0; index < 10; index++) PacketRates.countInbound();
        rates.tick(1000);
        rates.reset();
        assertFalse(rates.hasSamples());
        assertFalse(rates.isCounting());

        rates.requestCounting();
        rates.tick(2000);
        for (int index = 0; index < 5; index++) PacketRates.countInbound();
        rates.tick(3000);
        assertEquals(5.0, rates.inboundPerSecond(), 1e-9, "the previous connection must not carry over");
    }

    @Test
    void countingWithNoInstalledInstanceDoesNotThrow() {
        // The network thread can outlive the client's own state; a lost race must be harmless.
        new PacketRates().install();
        PacketRates.countInbound();
        PacketRates.countOutbound();
    }
}
