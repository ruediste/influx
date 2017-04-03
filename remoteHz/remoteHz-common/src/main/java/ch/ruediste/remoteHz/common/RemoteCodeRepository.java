package ch.ruediste.remoteHz.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteStreams;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

public class RemoteCodeRepository {
    private static Cache<UUID, ClassLoader> classLoaders = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS).build();

    private static IMap<UUID, byte[]> map(HazelcastInstance hz) {
        return hz.getMap("remoteCodeLoader");
    }

    private static class InMemoryClassLoader extends ClassLoader {

        HashMap<String, byte[]> resources = new HashMap<>();

        public InMemoryClassLoader(ClassLoader parent, byte[] codeJar) {
            super(parent);
            try (ZipInputStream zip = new JarInputStream(new ByteArrayInputStream(codeJar))) {
                while (true) {
                    ZipEntry entry = zip.getNextEntry();
                    if (entry == null)
                        break;
                    resources.put(entry.getName(), ByteStreams.toByteArray(zip));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bb = resources.get(name.replace('.', '/') + ".class");
            if (bb != null)
                return defineClass(name, bb, 0, bb.length);
            else
                return null;
        }
    }

    public static ClassLoader setCode(UUID uuid, byte[] code, HazelcastInstance hz) {
        ClassLoader cl = new InMemoryClassLoader(RemoteCodeRepository.class.getClassLoader(), code);
        map(hz).put(uuid, code);
        classLoaders.put(uuid, cl);
        return cl;
    }

    public static ClassLoader getClassLoader(UUID uuid, HazelcastInstance hz) {
        try {
            return classLoaders.get(uuid, () -> {
                byte[] code = map(hz).get(uuid);
                if (code == null)
                    throw new RuntimeException("Code not found for " + uuid);
                return new InMemoryClassLoader(RemoteCodeRepository.class.getClassLoader(), code);
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
