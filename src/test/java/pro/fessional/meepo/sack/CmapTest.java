package pro.fessional.meepo.sack;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.poof.RnaManager;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CmapTest {
    @Test
    void defaultsAloneMakeTheContextNonempty() {
        Cmap map = new Cmap(Collections.singletonMap("builtin", "value"), Collections.emptyMap());
        assertFalse(map.isEmpty());
        assertEquals("value", map.get("builtin"));
    }

    @Test
    void lazyReadsWritesAndNullsHaveHashMapSemantics() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("builtin", "original");
        defaults.put("shadow", "hidden");
        defaults.put("changed", "old");
        Map<String, Object> input = new HashMap<>();
        input.put("shadow", null);
        input.put(null, "null-key");
        Cmap map = new Cmap(Collections.unmodifiableMap(defaults), input);
        assertEquals("original", map.get("builtin"));
        assertEquals("null-key", map.get(null));
        assertNull(map.get("shadow"));
        assertNull(map.get("missing"));
        assertTrue(map.containsKey("shadow"));
        assertTrue(map.containsKey("builtin"));
        assertFalse(map.containsKey("missing"));
        assertFalse(map.isEmpty());
        assertNull(map.put("shadow", "visible"));
        assertEquals("visible", map.put("shadow", null));
        assertNull(map.put("shadow", "again"));
        assertEquals("old", map.put("changed", null));
        assertNull(map.put("changed", "new"));
        assertNull(map.put("new", null));
        assertNull(map.put("new", "v"));
        assertEquals("original", map.remove("builtin"));
        assertFalse(map.containsKey("builtin"));
        assertEquals("new", map.put("changed", "newer"));
        assertEquals("v", map.get("new"));
        assertEquals("hidden", defaults.get("shadow"));
        assertNull(input.get("shadow"));
    }

    @Test
    void liveViewsAndDefaultMapOperationsRemainMutable() {
        Cmap map = new Cmap(Collections.singletonMap("builtin", "b"), Collections.singletonMap("value", null));
        Set<Map.Entry<String, Object>> entries = map.entrySet();
        assertEquals(2, map.size());
        map.put("later", 1);
        assertEquals(3, entries.size());
        assertTrue(map.values().remove(null));
        for (Map.Entry<String, Object> entry : entries) {
            if (entry.getKey().equals("later")) entry.setValue(2);
        }
        assertEquals(2, map.get("later"));
        assertEquals(3, map.compute("later", (k, v) -> ((Integer) v) + 1));
        assertEquals(5, map.merge("later", 2, (a, b) -> (Integer) a + (Integer) b));
        map.replaceAll((k, v) -> String.valueOf(v));
        assertEquals("5", map.get("later"));
        map.clear();
        assertTrue(entries.isEmpty());
        assertTrue(map.isEmpty());
        assertNull(map.get("builtin"));
        map.put("after-clear", null);
        assertTrue(map.containsKey("after-clear"));
        assertNull(map.remove("after-clear"));
        assertTrue(map.isEmpty());
        assertTrue(new Cmap(Collections.emptyMap(), Collections.emptyMap()).isEmpty());
    }

    @Test
    void operationsMatchAnEagerHashMap() {
        Random random = new Random(20261105L);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("a", "default-a");
        defaults.put("b", "default-b");
        for (int round = 0; round < 200; round++) {
            Map<String, Object> input = new HashMap<>();
            input.put("b", null);
            Cmap actual = new Cmap(Collections.unmodifiableMap(defaults), input);
            Map<String, Object> expected = new HashMap<>(defaults);
            expected.putAll(input);
            for (int i = 0; i < 30; i++) {
                String key = random.nextBoolean() ? "a" : random.nextBoolean() ? "b" : "c";
                Object value = random.nextBoolean() ? null : random.nextInt(10);
                switch (random.nextInt(6)) {
                    case 0:
                        assertEquals(expected.get(key), actual.get(key));
                        break;
                    case 1:
                        assertEquals(expected.put(key, value), actual.put(key, value));
                        break;
                    case 2:
                        assertEquals(expected.containsKey(key), actual.containsKey(key));
                        break;
                    case 3:
                        assertEquals(expected.putIfAbsent(key, value), actual.putIfAbsent(key, value));
                        break;
                    case 4:
                        assertEquals(expected.compute(key, (k, v) -> value), actual.compute(key, (k, v) -> value));
                        break;
                    default:
                        assertEquals(expected.remove(key), actual.remove(key));
                }
            }
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
        }
    }

    @Test
    void registrationSnapshotsAndCallerMapsAreIsolated() {
        String key = "snapshot-fixture";
        Supplier<String> first = () -> "first";
        Supplier<String> second = () -> "second";
        RnaManager.register(key, first);
        Map<String, Object> before = RnaManager.getFunctionSnapshot();
        RnaManager.register(key, first);
        assertSame(before, RnaManager.getFunctionSnapshot());
        Map<String, Object> copied = new HashMap<>();
        RnaManager.putFunction(copied);
        assertEquals(before, copied);
        assertEquals(before.size(), RnaManager.getFunctionCount());
        Acid acid = new Acid(Collections.emptyMap(), Collections.emptySet());
        RnaManager.register(key, second);
        assertSame(first, before.get(key));
        assertSame(first, acid.context.get(key));
        assertSame(second, RnaManager.getFunctionSnapshot().get(key));
        assertThrows(UnsupportedOperationException.class, () -> before.put("x", "y"));
        acid.clear();
        assertTrue(acid.context.isEmpty());
    }

    @Test
    void materializingDefaultsPreservesNullKeysNullValuesAndExplicitOverrides() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put(null, "default-null-key");
        defaults.put("null-default", null);
        defaults.put("shadow", "default");
        Map<String, Object> input = new HashMap<>();
        input.put(null, null);
        input.put("shadow", null);
        input.put("value", "caller");
        Cmap actual = new Cmap(Collections.unmodifiableMap(defaults), input);
        Map<String, Object> expected = new HashMap<>(defaults);
        expected.putAll(input);
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);
        assertTrue(actual.containsKey(null));
        assertTrue(actual.containsKey("null-default"));
        assertNull(actual.get("shadow"));
        actual.put("null-default", "changed");
        actual.remove(null);
        assertTrue(input.containsKey(null));
        assertNull(defaults.get("null-default"));
        assertEquals("default", defaults.get("shadow"));
    }

    @Test
    void largerCallerContextsRemainIndependent() {
        Map<String, Object> input = new HashMap<>();
        for (int i = 0; i < 100; i++) input.put("key" + i, i);
        Cmap map = new Cmap(Collections.emptyMap(), input);
        input.clear();
        assertEquals(100, map.size());
        assertEquals(99, map.get("key99"));
    }

    @Test
    void growthAndCollisionsMatchHashMap() {
        Cmap actual = new Cmap(Collections.singletonMap("builtin", "b"), Collections.emptyMap());
        Map<String, Object> expected = new HashMap<>();
        expected.put("builtin", "b");
        actual.put(null, null);
        expected.put(null, null);
        for (int i = 0; i < 60; i++) {
            String key = "grown" + i;
            assertEquals(expected.put(key, i), actual.put(key, i));
            for (String probe : expected.keySet()) assertEquals(expected.get(probe), actual.get(probe));
        }
        assertEquals(expected, actual);
        actual.clear();
        assertTrue(actual.isEmpty());
        actual = new Cmap(Collections.emptyMap(), Collections.emptyMap());
        expected.clear();
        // Every combination of Aa/BB has the same String hash.
        for (int i = 0; i < 16; i++) {
            StringBuilder key = new StringBuilder();
            for (int bit = 0; bit < 4; bit++) key.append((i & (1 << bit)) == 0 ? "Aa" : "BB");
            String text = key.toString();
            assertEquals(expected.put(text, i), actual.put(text, i));
            assertEquals(i, actual.get(new String(text)));
        }
        assertEquals(expected, actual);
        Cmap small = new Cmap(Collections.emptyMap(), Collections.singletonMap("k", "v"));
        small.clear();
        assertTrue(small.isEmpty());
        assertNull(small.get("k"));
    }
}
