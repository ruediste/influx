package ch.ruediste.remoteHz.member;

import com.ea.agentloader.AgentLoader;
import com.hazelcast.config.Config;
import com.hazelcast.config.GlobalSerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import ch.ruediste.remoteHz.common.HzHolder;
import ch.ruediste.remoteHz.common.LambdaFactoryAgent;
import ch.ruediste.remoteHz.common.RemoteCodeRepository;
import ch.ruediste.remoteHz.common.RemoteCodeSerializer;

public class HzMember {
    public static void main(String... args) {
        AgentLoader.loadAgentClass(LambdaFactoryAgent.class.getName(), "");
        Config cfg = new Config();
        RemoteCodeSerializer serializer = new RemoteCodeSerializer();
        GlobalSerializerConfig globalSerializerConfig = new GlobalSerializerConfig();
        globalSerializerConfig.setOverrideJavaSerialization(true);
        globalSerializerConfig.setImplementation(serializer);
        cfg.getSerializationConfig().setGlobalSerializerConfig(globalSerializerConfig);
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(cfg);
        HzHolder.hz = hz;
        RemoteCodeRepository.startup(hz);
    }
}
