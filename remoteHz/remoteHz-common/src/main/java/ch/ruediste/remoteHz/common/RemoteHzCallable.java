package ch.ruediste.remoteHz.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;

public class RemoteHzCallable<T> implements Serializable, Callable<T> {
    private static final long serialVersionUID = 1L;

    byte[] codeJar;

    byte[] serializedCallable;

    private static final class CallableToRunnableWrapper implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private Callable<?> callable;

        public CallableToRunnableWrapper(Callable<?> callable) {
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
    }

    private static final class RunnableToCallableWrapper implements Callable<Object>, Serializable {
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

    public static <T> Runnable create(Runnable runnable) {

        Callable<Object> callable = create(new RunnableToCallableWrapper(runnable), runnable);
        return new CallableToRunnableWrapper(callable);

    }

    public static <T> Callable<T> create(Callable<T> callable) {
        return create(callable, callable);
    }

    private static <T> Callable<T> create(Callable<T> callable, Object original) {
        ByteArrayOutputStream codeBaos = new ByteArrayOutputStream();
        ByteArrayOutputStream callableBaos = new ByteArrayOutputStream();
        try {
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
            try (ObjectOutputStream oos = new ObjectOutputStream(callableBaos)) {
                oos.writeObject(callable);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        RemoteHzCallable<T> result = new RemoteHzCallable<>();
        result.codeJar = codeBaos.toByteArray();
        result.serializedCallable = callableBaos.toByteArray();
        return result;
    }

    @Override
    public T call() throws Exception {
        InMemoryClassLoader cl = new InMemoryClassLoader(getClass().getClassLoader(), codeJar);
        @SuppressWarnings("unchecked")
        Callable<T> callable = (Callable<T>) SerializationHelper.toObject(serializedCallable, cl);
        return callable.call();
    }
}
