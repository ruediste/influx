package ch.ruediste.remoteHz.common;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ea.agentloader.AgentLoader;

public class RemoteCodeSerializerTest {

    String value;

    @Before
    public void before() {
        value = null;
    }

    @SuppressWarnings("unchecked")
    private <T> T test(T lambda) {
        RemoteCodeSerializer serializer = new RemoteCodeSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.write(baos, lambda);
        return (T) serializer.read(new ByteArrayInputStream(baos.toByteArray()));
    }

    @BeforeClass
    public static void beforeClass() {
        AgentLoader.loadAgentClass(LambdaFactoryAgent.class.getName(), "");
    }

    @Test
    public void supplier() {
        assertEquals("Hello World", this.<Supplier<String>>test(() -> "Hello World").get());
    }

    @Test
    public void supplierWithThis() {
        value = "Hello World";
        assertEquals("Hello World", this.<Supplier<String>>test(() -> value).get());
    }

    @Test
    public void function() {
        assertEquals("Hello World5", this.<Function<Integer, String>>test(i -> "Hello World" + i).apply(5));
    }

    private interface A {
        String get();
    }

    private interface B<T> {
        T get();
    }

    private interface C<T> {
        String apply(T value);
    }

    @Test
    public void privateInterface() {
        assertEquals("Hello World", this.<A>test(() -> "Hello World").get());
        assertEquals("Hello World", this.<B<String>>test(() -> "Hello World").get());
        assertEquals("Hello World1", this.<C<String>>test(value -> value + "1").apply("Hello World"));
    }
}
