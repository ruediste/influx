package ch.ruediste.remoteHz;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;

import ch.ruediste.remoteHz.common.RemoteHzCallable;

public class RemoteHzClient {

    public static void main(String... args) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost");
        HazelcastInstance hz = HazelcastClient.newHazelcastClient(clientConfig);
        hz.getExecutorService("default").execute(new SampleTask());
        hz.shutdown();
    }
}
