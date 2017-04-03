package ch.ruediste.remoteHz;

import java.io.Serializable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IExecutorService;

import ch.ruediste.remoteHz.common.RemoteHzCallable;

public class SampleTask implements Runnable, Serializable, HazelcastInstanceAware {

    private static class Task2 implements Runnable, Serializable {

        @Override
        public void run() {
            System.out.println("Hello World from the inner task");
        }

    }

    private static final long serialVersionUID = 1L;
    private HazelcastInstance hz;

    @Override
    public void run() {
        System.out.println("Hello World from the Sample Task");
        IExecutorService executor = hz.getExecutorService("default");
        executor.executeOnAllMembers(RemoteHzCallable.create(new Task2()));
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;

    }

}
