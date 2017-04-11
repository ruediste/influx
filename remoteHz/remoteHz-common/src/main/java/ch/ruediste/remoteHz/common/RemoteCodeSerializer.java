package ch.ruediste.remoteHz.common;

import static com.esotericsoftware.kryo.util.Util.className;
import static com.esotericsoftware.minlog.Log.TRACE;
import static com.esotericsoftware.minlog.Log.WARN;
import static com.esotericsoftware.minlog.Log.trace;
import static com.esotericsoftware.minlog.Log.warn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.instantiator.sun.UnsafeFactoryInstantiator;
import org.objenesis.strategy.InstantiatorStrategy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import com.esotericsoftware.kryo.util.DefaultStreamFactory;
import com.esotericsoftware.kryo.util.IdentityObjectIntMap;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import ch.ruediste.remoteHz.common.RemoteCodeRepository.InMemoryClassLoader;

public class RemoteCodeSerializer implements StreamSerializer<Object> {

    public String clientCodeUuid;
    public Set<String> remoteClassNames = new HashSet<>();

    @Override
    public int getTypeId() {
        return 1;
    }

    @Override
    public void destroy() {

    }

    private final ThreadLocal<Kryo> kryoThreadLocal = new ThreadLocal<Kryo>() {
        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo(new DefaultClassResolver() {
                protected IdentityObjectIntMap<String> codeUuidToCodeId = new IdentityObjectIntMap<>();
                protected IntMap<String> codeIdToCodeUuid = new IntMap<String>();
                protected int nextCodeId = 1;

                @SuppressWarnings("rawtypes")
                @Override
                protected void writeName(Output output, Class type,
                        com.esotericsoftware.kryo.Registration registration) {
                    output.writeVarInt(NAME + 2, true);
                    if (classToNameId != null) {
                        int nameId = classToNameId.get(type, -1);
                        if (nameId != -1) {
                            if (TRACE)
                                trace("kryo", "Write class name reference " + nameId + ": " + className(type));
                            output.writeVarInt(nameId, true);
                            return;
                        }
                    }
                    // Only write the class name the first time encountered in
                    // object graph.
                    if (TRACE)
                        trace("kryo", "Write class name: " + className(type));
                    int nameId = nextNameId++;
                    if (classToNameId == null)
                        classToNameId = new IdentityObjectIntMap<>();
                    classToNameId.put(type, nameId);
                    output.writeVarInt(nameId, true);
                    String codeUuid = null;
                    if (clientCodeUuid != null) {
                        // executed on client
                        if (remoteClassNames.contains(type.getName()))
                            codeUuid = clientCodeUuid;
                    } else {
                        // executed on HZ member
                        if (type.getClassLoader() instanceof InMemoryClassLoader)
                            codeUuid = ((InMemoryClassLoader) type.getClassLoader()).codeUuid;
                    }
                    if (codeUuid == null) {
                        // no remote code
                        output.writeVarInt(0, true);
                    } else {
                        int codeId = codeUuidToCodeId.get(codeUuid, -1);
                        if (codeId != -1)
                            output.writeVarInt(codeId, true);
                        else {
                            codeId = nextCodeId++;
                            codeUuidToCodeId.put(codeUuid, codeId);
                            output.writeVarInt(codeId, true);
                            output.writeString(codeUuid);
                        }
                    }
                    output.writeString(type.getName());
                };

                @Override
                protected Registration readName(Input input) {
                    int nameId = input.readVarInt(true);
                    if (nameIdToClass == null)
                        nameIdToClass = new IntMap<>();
                    Class<?> type = nameIdToClass.get(nameId);
                    if (type == null) {
                        // Only read the class name the first time encountered
                        // in object graph.
                        String codeUuid;
                        {
                            int codeId = input.readVarInt(true);
                            if (codeId == 0)
                                codeUuid = "";
                            else {
                                codeUuid = codeIdToCodeUuid.get(codeId);
                                if (codeUuid == null) {
                                    codeUuid = input.readString();
                                    codeIdToCodeUuid.put(codeId, codeUuid);
                                }
                            }

                        }
                        String className = input.readString();
                        String classKey = codeUuid + ":" + className;
                        type = nameToClass != null ? nameToClass.get(classKey) : null;
                        if (type == null) {
                            if ("".equals(codeUuid) || clientCodeUuid != null)
                                try {
                                    type = Class.forName(className, false, kryo.getClassLoader());
                                } catch (ClassNotFoundException ex) {
                                    if (WARN)
                                        warn("kryo", "Unable to load class " + className
                                                + " with kryo's ClassLoader. Retrying with current..");
                                    try {
                                        type = Class.forName(className);
                                    } catch (ClassNotFoundException e) {
                                        throw new KryoException("Unable to find class: " + className, ex);
                                    }
                                }
                            else
                                try {
                                    type = RemoteCodeRepository.getClassLoader(codeUuid).loadClass(className);
                                } catch (Throwable e) {
                                    throw new RuntimeException(
                                            "Could not load class " + className + " from code jar " + codeUuid, e);
                                }

                            if (nameToClass == null)
                                nameToClass = new ObjectMap<>();
                            nameToClass.put(classKey, type);
                        }
                        nameIdToClass.put(nameId, type);
                        if (TRACE)
                            trace("kryo", "Read class name: " + className);
                    } else {
                        if (TRACE)
                            trace("kryo", "Read class name reference " + nameId + ": " + className(type));
                    }
                    return kryo.getRegistration(type);
                }

                @Override
                public void reset() {
                    super.reset();
                    codeIdToCodeUuid = new IntMap<>();
                    codeUuidToCodeId = new IdentityObjectIntMap<>();
                    nextCodeId = 1;
                };
            }, new MapReferenceResolver(), new DefaultStreamFactory());
            kryo.register(java.lang.invoke.SerializedLambda.class);
            kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());
            kryo.setInstantiatorStrategy(new InstantiatorStrategy() {

                @Override
                public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> arg0) {
                    return new UnsafeFactoryInstantiator<>(arg0);
                }
            });
            return kryo;
        }

    };

    boolean compress;

    @Override
    public void write(ObjectDataOutput out, Object object) throws IOException {
        Kryo kryo = kryoThreadLocal.get();
        if (compress) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(16384);
            DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream);
            Output output = new Output(deflaterOutputStream);
            kryo.writeClassAndObject(output, object);
            output.close();
            byte[] bytes = byteArrayOutputStream.toByteArray();
            out.write(bytes);
        } else {
            Output output = new Output((OutputStream) out);
            kryo.writeClassAndObject(output, object);
            output.flush();
        }

    }

    @Override
    public Object read(ObjectDataInput objectDataInput) throws IOException {
        InputStream in = (InputStream) objectDataInput;
        if (compress) {
            in = new InflaterInputStream(in);
        }
        Input input = new Input(in);
        Kryo kryo = kryoThreadLocal.get();
        return kryo.readClassAndObject(input);
    }

}
