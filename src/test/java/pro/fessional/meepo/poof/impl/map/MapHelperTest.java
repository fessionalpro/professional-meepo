package pro.fessional.meepo.poof.impl.map;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pro.fessional.meepo.bind.Const.ARR$EMPTY_STRING;

/**
 * @author trydofor
 * @since 2020-11-10
 */
public class MapHelperTest {

    @Test
    public void beanPropertiesMatchPublicGetterMetadata() throws Exception {
        Method getter = Bean.class.getMethod("getCode");
        assertEquals(0, getter.getParameterCount());
        assertEquals(String.class, getter.getReturnType());
        Bean bean = new Bean();
        assertEquals(getter.invoke(bean), MapHelper.get(bean, "code", ARR$EMPTY_STRING));
    }

    @Test
    public void cachedMembersReadUpdatedValuesFromEachReceiver() {
        Bean first = new Bean();
        Bean second = new Bean();
        first.code = "first";
        second.code = "second";
        assertEquals("first", MapHelper.get(first, "code", ARR$EMPTY_STRING));
        assertEquals("second", MapHelper.get(second, "code", ARR$EMPTY_STRING));
        first.code = "changed";
        assertEquals("changed", MapHelper.get(first, "code", ARR$EMPTY_STRING));
        assertEquals("second", MapHelper.get(second, "code", ARR$EMPTY_STRING));
        assertNull(MapHelper.get(first, "absent", ARR$EMPTY_STRING));
        assertNull(MapHelper.get(second, "absent", ARR$EMPTY_STRING));
    }

    @Test
    public void beanGetter() {
        Bean bean = new Bean();
        Map<String, Object> map = new HashMap<>();
        map.put("name", "name");
        map.put("code", "code");
        map.put("male", true);
        map.put("bean", bean);

        assertEquals("name", MapHelper.get(map, "name", ARR$EMPTY_STRING));
        assertEquals("code", MapHelper.get(map, "code", ARR$EMPTY_STRING));
        assertEquals(Boolean.TRUE, MapHelper.get(map, "male", ARR$EMPTY_STRING));

        assertEquals("name", MapHelper.get(bean, "name", ARR$EMPTY_STRING));
        assertEquals("code", MapHelper.get(bean, "code", ARR$EMPTY_STRING));
        assertEquals(Boolean.TRUE, MapHelper.get(bean, "male", ARR$EMPTY_STRING));

        assertEquals("name", MapHelper.get(map, "bean.name", new String[]{ "bean", "name" }));
        assertEquals("code", MapHelper.get(map, "bean.code", new String[]{ "bean", "code" }));
        assertEquals(Boolean.TRUE, MapHelper.get(map, "bean.male", new String[]{ "bean", "male" }));
    }

    public static class Bean {
        public final String name = "name";
        public String code = "code";
        public final boolean male = true;

        public String getCode() {
            return code;
        }

        public boolean isMale() {
            return male;
        }
    }
}
