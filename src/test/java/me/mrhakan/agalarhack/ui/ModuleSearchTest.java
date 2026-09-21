package me.mrhakan.agalarhack.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleSearchTest {
    @Test void combinesWordsAndSupportsAbbreviations() {
        assertTrue(ModuleSearch.matches("food hunger","AutoEat chooses food at hunger threshold"));
        assertTrue(ModuleSearch.matches("ateat","AutoEat"));
        assertFalse(ModuleSearch.matches("food teleport","AutoEat food hunger"));
        assertTrue(ModuleSearch.matches("","anything"));
    }

    @Test void preparedSearchIsCaseInsensitiveAndWhitespaceTolerant() {
        String prepared = ModuleSearch.prepare("AutoWalk Movement pauseWhenHungry hunger threshold");
        assertTrue(ModuleSearch.matchesPrepared("  AWALK   HUNGRY ", prepared));
        assertTrue(ModuleSearch.matchesPrepared("movement threshold", prepared));
        assertFalse(ModuleSearch.matchesPrepared("movement elytra", prepared));
    }
}
