package pro.fessional.meepo.sack;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cmap (context map): one merge's mutable bindings over shared function defaults.
 * <p>Caller bindings are shallow-copied and override the immutable default snapshot,
 * including an explicit null. get checks overrides before defaults, and put
 * writes only the overlay. Operations requiring a full Map view materialize it
 * once. Removing a default
 * must stay removed rather than reveal the underlying value again.</p>
 * <p>Owned by one {@link Acid} and used in one thread. This is runtime context,
 * so it stays in sack with Acid.</p>
 */
public final class Cmap extends AbstractMap<String, Object> {
    // Distinguish an explicit null override from a missing overlay binding.
    private static final Object NULL = new Object();
    private Map<String, Object> defaults;
    private final Map<String, Object> values;
    private boolean flat;

    /**
     * Share immutable defaults and shallow-copy input, retaining explicit null overrides.
     */
    Cmap(Map<String, Object> defaults, Map<String, Object> input) {
        this.defaults = defaults;
        int size = input.size();
        this.values = new HashMap<>(Math.max(16, size + (size + 2) / 3));
        putAll(input);
    }

    @Override
    public Object get(Object key) {
        Object value = values.get(key);
        return value == NULL ? null : value != null ? value : defaults.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return values.containsKey(key) || defaults.containsKey(key);
    }

    @Override
    public Object put(String key, Object value) {
        if (flat) return values.put(key, value);
        Object old = values.put(key, value == null ? NULL : value);
        return old == NULL ? null : old != null ? old : defaults.get(key);
    }

    @Override
    public Object remove(Object key) {
        return materialize().remove(key);
    }

    @Override
    public void clear() {
        defaults = Collections.emptyMap();
        values.clear();
        flat = true;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return materialize().entrySet();
    }

    @Override
    public int size() {
        return materialize().size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty() && defaults.isEmpty();
    }

    private Map<String, Object> materialize() {
        if (!flat) {
            // From this point on, views and removals operate on one ordinary map.
            // The overlay is already ours. Explicit nulls still use NULL here,
            // so putIfAbsent cannot replace a caller's null with a default.
            for (Entry<String, Object> entry : defaults.entrySet()) {
                values.putIfAbsent(entry.getKey(), entry.getValue());
            }
            for (Entry<String, Object> entry : values.entrySet()) {
                if (entry.getValue() == NULL) entry.setValue(null);
            }
            defaults = Collections.emptyMap();
            flat = true;
        }
        return values;
    }
}
