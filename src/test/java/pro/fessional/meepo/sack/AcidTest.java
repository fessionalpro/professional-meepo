package pro.fessional.meepo.sack;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.poof.RnaEngine;
import pro.fessional.meepo.poof.RnaManager;
import pro.fessional.meepo.poof.RnaWarmed;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AcidTest {
    @Test
    void mutableEnginesAndBindingsBelongToEachExecution() {
        String type = "acid-" + UUID.randomUUID();
        RnaEngine prototype = new CounterEngine(type);
        RnaManager.register(prototype);
        Map<String, Object> input = Collections.singletonMap("value", "caller");
        Acid first = new Acid(input, Collections.singleton(type));
        Acid second = new Acid(input, Collections.singleton(type));
        try {
            RnaEngine one = first.getEngine(type);
            RnaEngine two = second.getEngine(type);
            assertNotSame(prototype, one);
            assertNotSame(one, two);
            assertSame(one, first.getEngine(type));
            RnaWarmed expression = new RnaWarmed(type, "counter");
            assertEquals(1, one.eval(first.context, expression, false));
            assertEquals(2, one.eval(first.context, expression, false));
            assertEquals(1, two.eval(second.context, expression, false));
            first.context.put("value", "first");
            first.execute.put("choice", null);
            assertEquals("caller", input.get("value"));
            assertEquals("caller", second.context.get("value"));
            assertTrue(second.execute.isEmpty());
            first.clear();
            assertTrue(first.context.isEmpty());
            assertTrue(first.execute.isEmpty());
            assertEquals("caller", second.context.get("value"));
            assertSame(two, second.getEngine(type));
        }
        finally {
            first.clear();
            second.clear();
        }
    }

    private static final class CounterEngine implements RnaEngine {
        private final String type;
        private int counter;

        private CounterEngine(String type) {
            this.type = type;
        }

        public String[] type() {
            return new String[]{ type };
        }

        public Object eval(Map<String, Object> context, RnaWarmed expression, boolean mute) {
            return ++counter;
        }

        public RnaEngine fork() {
            return new CounterEngine(type);
        }
    }
}
