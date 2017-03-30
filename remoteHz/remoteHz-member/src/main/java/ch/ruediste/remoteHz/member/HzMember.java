package ch.ruediste.remoteHz.member;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

public class HzMember {
    public static void main(String... args) {
        Config cfg = new Config();
        Hazelcast.newHazelcastInstance(cfg);
    }
}
