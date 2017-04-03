package ch.ruediste.remoteHz.common;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;

public class RemoteHzCallable<T> implements Serializable, Callable<T>, HazelcastInstanceAware {
    private static final long serialVersionUID = 1L;

    private static ThreadLocal<UUID> threadCodeId = new ThreadLocal<>();

    private byte[] codeJar;

    private UUID codeId;

    private byte[] serializedCallable;

    private HazelcastInstance hz;

    private static final class CallableToRunnableWrapper implements Runnable, Serializable, HazelcastInstanceAware {
        private static final long serialVersionUID = 1L;
        private RemoteHzCallable<?> callable;

        public CallableToRunnableWrapper(RemoteHzCallable<?> callable) {
            this.callable = callable;
        }

        @Override
        public void run() {
            try {
                callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
            callable.setHazelcastInstance(hazelcastInstance);
        }
    }

    private static final class RunnableToCallableWrapper
            implements Callable<Object>, Serializable, HazelcastInstanceAware {
        private static final long serialVersionUID = 1L;
        private Runnable runnable;

        public RunnableToCallableWrapper(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public Object call() throws Exception {
            runnable.run();
            return null;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
            if (runnable instanceof HazelcastInstanceAware)
                ((HazelcastInstanceAware) runnable).setHazelcastInstance(hazelcastInstance);

        }
    }

    public static <T extends Runnable & Serializable> Runnable create(T runnable) {

        RemoteHzCallable<Object> callable = create(new RunnableToCallableWrapper(runnable), runnable);
        return new CallableToRunnableWrapper(callable);

    }

    public static <T> Callable<T> create(Callable<T> callable) {
        return create(callable, callable);
    }

    private static <T> RemoteHzCallable<T> create(Callable<T> callable, Object original) {
        ByteArrayOutputStream codeBaos = null;
        ByteArrayOutputStream callableBaos = new ByteArrayOutputStream();
        UUID codeId = threadCodeId.get();
        try {
            if (codeId == null) {
                codeId = UUID.randomUUID();
                codeBaos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(codeBaos)) {
                    String prefix;
                    {
                        int idx = original.getClass().getName().lastIndexOf('.');
                        prefix = original.getClass().getName().substring(0, idx + 1).replace('.', '/');
                    }
                    ClassLoader cl = original.getClass().getClassLoader();
                    for (ResourceInfo info : ClassPath.from(cl).getResources()) {
                        if (info.getResourceName().startsWith(prefix)) {
                            zos.putNextEntry(new ZipEntry(info.getResourceName()));
                            try (InputStream in = cl.getResourceAsStream(info.getResourceName())) {
                                ByteStreams.copy(in, zos);
                            }
                        }
                    }
                }
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(callableBaos)) {
                oos.writeObject(callable);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RemoteHzCallable<T> result = new RemoteHzCallable<>();
        result.codeId = codeId;
        result.codeJar = codeBaos == null ? null : codeBaos.toByteArray();
        result.serializedCallable = callableBaos.toByteArray();
        return result;
    }

    @Override
    public T call() throws Exception {
        UUID oldId = threadCodeId.get();
        try {
            ClassLoader cl;
            if (codeJar != null) {
                cl = RemoteCodeRepository.setCode(codeId, codeJar, hz);
            } else {
                cl = RemoteCodeRepository.getClassLoader(codeId, hz);
            }

            // load callable
            @SuppressWarnings("unchecked")
            Callable<T> callable = (Callable<T>) SerializationHelper.toObject(serializedCallable, cl);

            threadCodeId.set(codeId);
            if (callable instanceof HazelcastInstanceAware) {
                ((HazelcastInstanceAware) callable).setHazelcastInstance(hz);
            }
            return callable.call();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            threadCodeId.set(oldId);
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
    }
}
