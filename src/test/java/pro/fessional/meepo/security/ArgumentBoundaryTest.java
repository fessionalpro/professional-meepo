package pro.fessional.meepo.security;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.util.Eval;

import java.util.Collections;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArgumentBoundaryTest {
    @Test
    void quotedDataCannotCreateExtraCommandArguments() {
        Random random = new Random(20261106L);
        char[] alphabet = "a '\"\\\t\r\n|;$()中文😀".toCharArray();
        for (int trial = 0; trial < 500; trial++) {
            StringBuilder text = new StringBuilder();
            for (int count = random.nextInt(100); count > 0; count--)
                text.append(alphabet[random.nextInt(alphabet.length)]);
            String value = text.toString();
            String quoted = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            assertEquals(Collections.singletonList(value), Eval.parseArgs(quoted, Eval.ArgType.Str), "trial " + trial);
        }
    }
}
