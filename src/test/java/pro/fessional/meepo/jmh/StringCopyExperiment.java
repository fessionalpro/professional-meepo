package pro.fessional.meepo.jmh;

import org.openjdk.jmh.annotations.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Isolates the cost of safe copying versus transferring an immutable, exact-sized array.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StringCopyExperiment {
    @Param({ "64", "4096" })
    public int length;
    @Param({ "ascii", "unicode" })
    public String alphabet;
    private char[] characters;
    private MethodHandle sharing;

    @Setup
    public void setup() throws Throwable {
        characters = new char[length];
        Arrays.fill(characters, "ascii".equals(alphabet) ? 'a' : '中');
        try {
            Constructor<String> constructor = String.class.getDeclaredConstructor(char[].class, boolean.class);
            constructor.setAccessible(true);
            sharing = MethodHandles.lookup().unreflectConstructor(constructor);
        }
        catch (ReflectiveOperationException | RuntimeException unavailable) {
            sharing = null;
        }
        if (!safeCopy().equals(ownedArray())) throw new IllegalStateException("copy mismatch");
        System.err.println("JDK 8 sharing constructor available: " + (sharing != null));
    }

    @Benchmark
    public String safeCopy() {
        return new String(characters);
    }

    // The fixture array never changes after setup. This assumption is required for sharing.
    @Benchmark
    public String ownedArray() throws Throwable {
        return sharing == null ? safeCopy() : (String) sharing.invokeExact(characters, true);
    }
}
