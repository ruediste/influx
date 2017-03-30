package ch.ruediste.remoteHz.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.function.Function;

public class SerializationHelper {

    /**
     * Helper class loaded with a custom classloader, causing deserialization to
     * use that class loader too.
     */
    private static class DeserializationHelper implements Function<byte[], Object> {

        @Override
        public Object apply(byte[] bb) {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bb))) {
                return ois.readObject();
            } catch (Throwable t) {
                throw new RuntimeException(
                        "Error during deserialization, class loader was " + getClass().getClassLoader(), t);
            }
        }
    }

    public static Object toObject(byte[] bytes) {
        return toObject(bytes, null);
    }

    private static class HelperClassLoader extends ClassLoader {
        HelperClassLoader(ClassLoader parent) {
            super(parent);
            String name = DeserializationHelper.class.getName();
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                int read;
                byte[] buffer = new byte[128];
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                while ((read = is.read(buffer)) > 0) {
                    os.write(buffer, 0, read);
                }
                byte[] data = os.toByteArray();
                defineClass(name, data, 0, data.length);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "HelperClassLoader(" + getParent() + ")";
        }
    }

    @SuppressWarnings("unchecked")
    public static Function<byte[], Object> objectLoader(ClassLoader cl) {
        try {
            Constructor<?> cst = new HelperClassLoader(cl).loadClass(DeserializationHelper.class.getName())
                    .getDeclaredConstructor();
            cst.setAccessible(true);
            return (Function<byte[], Object>) cst.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Error while creating object loader", e);
        }
    }

    public static Object toObject(byte[] bytes, ClassLoader cl) {
        return objectLoader(cl).apply(bytes);
    }

    public static byte[] toByteArray(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out;
        try {
            out = new ObjectOutputStream(baos);
            out.writeObject(obj);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
