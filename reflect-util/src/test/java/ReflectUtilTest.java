import com.bringholm.reflectutil.v1_1_1.ReflectUtil;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Bukkit.class)
public class ReflectUtilTest {
    @Test
    public void testVersionHigherThan() {
        PowerMockito.mockStatic(Bukkit.class);
        // This is just to satisfy the package initializers
        when(Bukkit.getServer()).thenReturn(mock(Server.class));
        when(Bukkit.getVersion()).thenReturn("blablabla (MC: 1.9.4)");
        assertEquals("major version", 1, ReflectUtil.MAJOR_VERSION);
        assertEquals("minor version", 9, ReflectUtil.MINOR_VERSION);
        assertEquals("build", 4, ReflectUtil.BUILD);
        assertTrue(ReflectUtil.isVersionHigherThan(1, 9, 3));
        assertTrue(ReflectUtil.isVersionHigherThan(1, 9));
        assertFalse(ReflectUtil.isVersionHigherThan(1, 9, 4));
        assertFalse(ReflectUtil.isVersionHigherThan(1, 9, 5));
        assertFalse(ReflectUtil.isVersionHigherThan(1, 10));
        assertFalse(ReflectUtil.isVersionHigherThan(1, 10, 1));
        assertFalse(ReflectUtil.isVersionHigherThan(2, 1));
        assertFalse(ReflectUtil.isVersionHigherThan(2, 9, 1));
        assertFalse(ReflectUtil.isVersionHigherThan(2, 9));
    }
}
