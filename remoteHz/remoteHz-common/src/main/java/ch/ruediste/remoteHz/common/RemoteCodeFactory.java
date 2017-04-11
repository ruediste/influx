package ch.ruediste.remoteHz.common;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;

public class RemoteCodeFactory {

    public static void createRemoteCode(Class<?> packageRoot, RemoteCodeSerializer ser) {
        String codeId = UUID.randomUUID().toString();
        ser.clientCodeUuid = codeId;
        try {
            ByteArrayOutputStream codeBaos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(codeBaos)) {
                String prefix;
                {
                    int idx = packageRoot.getName().lastIndexOf('.');
                    prefix = packageRoot.getName().substring(0, idx + 1).replace('.', '/');
                }
                ClassLoader cl = packageRoot.getClassLoader();
                for (ResourceInfo info : ClassPath.from(cl).getResources()) {
                    if (info.getResourceName().startsWith(prefix)) {
                        {
                            String name = info.getResourceName();
                            name = name.substring(0, name.length() - ".class".length());
                            ser.remoteClassNames.add(name.replace('/', '.'));
                        }
                        zos.putNextEntry(new ZipEntry(info.getResourceName()));
                        try (InputStream in = cl.getResourceAsStream(info.getResourceName())) {
                            ByteStreams.copy(in, zos);
                        }
                    }
                }
            }

            byte[] bb = codeBaos.toByteArray();
            RemoteCodeRepository.uploadCode(codeId, bb);
            // hz.getExecutorService("default").execute((IRunnable) () ->
            // RemoteCodeRepository.setCode(codeId, bb));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
