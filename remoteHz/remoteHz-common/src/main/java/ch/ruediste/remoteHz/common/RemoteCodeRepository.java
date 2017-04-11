package ch.ruediste.remoteHz.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
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
    private static Cache<String, ClassLoader> classLoaders = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES).build();

    private static IMap<String, byte[]> map() {
        return HzHolder.hz.getMap("remoteCodeLoader");
    }

    static class InMemoryClassLoader extends ClassLoader {
        public String codeUuid;

        HashMap<String, byte[]> resources = new HashMap<>();

        public InMemoryClassLoader(ClassLoader parent, byte[] codeJar, String codeId) {
            super(parent);
            this.codeUuid = codeId;
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

    static void uploadCode(String uuid, byte[] code) {
        System.out.println("Uploading remote code: " + uuid);
        map().put(uuid, code);
        try {
            for (Future<Object> future : HzHolder.hz.getExecutorService("default")
                    .submitToAllMembers((Callable<Object> & Serializable) () -> {
                        classLoaders.put(uuid,
                                new InMemoryClassLoader(RemoteCodeRepository.class.getClassLoader(), code, uuid));
                        return null;
                    }).values()) {
                future.get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void startup(HazelcastInstance hz) {
        for (Entry<String, byte[]> entry : map().entrySet()) {
            classLoaders.put(entry.getKey(), new InMemoryClassLoader(RemoteCodeRepository.class.getClassLoader(),
                    entry.getValue(), entry.getKey()));
        }
    }

    public static ClassLoader getClassLoader(String uuid) {
        return classLoaders.asMap().get(uuid);
    }
}
