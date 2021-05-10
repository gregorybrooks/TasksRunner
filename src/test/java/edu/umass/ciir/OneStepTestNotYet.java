package edu.umass.ciir;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OneStepTestNotYet {

    @Before
    public void setUp()  {
    }

    @After
    public void tearDown() throws IOException {
    }

    @Test
    public void testOneStep() throws Exception {
    //    setEnv();
        updateEnv("scratchFileLocation", "/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_DRY_RUN/scratch/clear_ir/");

        assertEquals("AUTO", Pathnames.mode);
    }

    @SuppressWarnings({ "unchecked" })
    public static void updateEnv(String name, String val) throws ReflectiveOperationException {
        Map<String, String> env = System.getenv();
        Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        ((Map<String, String>) field.get(env)).put(name, val);
    }

    protected static void setEnv() throws Exception {
        Map<String, String> newenv = new HashMap<>();
        newenv.putAll(System.getenv());
        newenv.put("scratchFileLocation", "/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_DRY_RUN/scratch/clear_ir/");

        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> oldenv = (Map<String, String>) theEnvironmentField.get(null);
            oldenv.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>)     theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for(Class cl : classes) {
                if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }

    public static void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if(files != null) {
                for (File sub : files) {
                    if (sub.isDirectory()) {
                        deleteDirectory(sub);
                    } else {
                        sub.delete();
                    }
                }
            }
        }
        directory.delete();
    }
}
