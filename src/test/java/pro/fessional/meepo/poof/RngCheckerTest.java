package pro.fessional.meepo.poof;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RngCheckerTest {
    @Test
    void privateCacheKeyHonorsTheObjectEqualityContract() throws Exception {
        // Test the private value object's contract without changing any parser/cache state.
        Class<?> key = Class.forName(RngChecker.class.getName() + "$Key");
        java.lang.reflect.Constructor<?> create = key.getDeclaredConstructor(String.class, String.class);
        create.setAccessible(true);
        Object a = create.newInstance("map", "value");
        Object b = create.newInstance("map", "value");
        Object c = create.newInstance("map", "value");
        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals("map:value"));
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(b, c);
        assertEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
        Object empty = create.newInstance(null, null);
        assertEquals(empty, create.newInstance(null, null));
        assertNotEquals(empty, a);
    }

    @Test
    void equalHashCodesDoNotAliasDifferentTypesOrExpressions() {
        String a = "branch-Aa", b = "branch-BB";
        assertEquals(a.hashCode(), b.hashCode());
        RnaManager.register(new RnaEngine() {
            public String[] type() {
                return new String[]{ a, b };
            }

            public Object eval(Map<String, Object> context, RnaWarmed expression, boolean mute) {
                return expression.expr;
            }

            public RnaEngine fork() {
                return this;
            }
        });
        RngChecker checker = new RngChecker();
        StringBuilder errors = new StringBuilder();
        RnaWarmed first = checker.check(errors, a, "Aa");
        RnaWarmed otherExpression = checker.check(errors, a, "BB");
        RnaWarmed otherType = checker.check(errors, b, "Aa");
        assertNotSame(first, otherExpression);
        assertNotSame(first, otherType);
        assertSame(first, checker.check(errors, a, "Aa"));
        assertEquals(0, errors.length());
    }
}
