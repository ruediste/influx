package dataloader;

import java.io.File;
import java.io.PrintStream;

import org.zeroturnaround.exec.ProcessExecutor;

import com.ea.agentloader.AgentLoader;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.GlobalSerializerConfig;
import com.hazelcast.core.HazelcastInstance;

import ch.ruediste.remoteHz.common.HzHolder;
import ch.ruediste.remoteHz.common.LambdaFactoryAgent;
import ch.ruediste.remoteHz.common.RemoteCodeFactory;
import ch.ruediste.remoteHz.common.RemoteCodeSerializer;
import dataloader.Trader.AlgoSpeciem;
import dataloader.ga.GeneticAlgorithm.OptimizationResult;

public class RemoteTrader {
    private static HazelcastInstance hz;

    public static void main(String... args) throws Exception {
        AgentLoader.loadAgentClass(LambdaFactoryAgent.class.getName(), "");
        {
            ClientConfig clientConfig = new ClientConfig();
            GlobalSerializerConfig serConfig = new GlobalSerializerConfig();
            serConfig.setOverrideJavaSerialization(true);
            RemoteCodeSerializer ser = new RemoteCodeSerializer();
            serConfig.setImplementation(ser);
            clientConfig.getSerializationConfig().setGlobalSerializerConfig(serConfig);
            clientConfig.getNetworkConfig().addAddress("localhost");
            hz = HazelcastClient.newHazelcastClient(clientConfig);
            HzHolder.hz = hz;
            RemoteCodeFactory.createRemoteCode(RemoteTrader.class, ser);
        }
        System.out.println("executing hello world");
        hz.getExecutorService("default").submit(() -> {
            System.out.println("Hello from the trader");
            HzHolder.hz.getExecutorService("default").execute(() -> {
                System.out.println("Hello again from the trader");
            });
        }).get();
        System.out.println("executed hello world");
        try {
            // if (true)
            // return;
            OptimizationResult<AlgoSpeciem> result = hz.getExecutorService("default").submit(new Trader()).get();

            // write data
            // File dataFile = File.createTempFile("plot", "data");
            // dataFile.deleteOnExit();
            File dataFile = new File("data.dat");
            try (PrintStream out = new PrintStream(dataFile)) {
                for (int i = 0; i < result.fitnesses.size(); i++) {
                    double[] gen = result.fitnesses.get(i);
                    for (double f : gen) {
                        out.println(i + " " + f);
                    }
                }
            }

            // invoke gnuplot
            new ProcessExecutor("gnuplot", "-e", "plot \"" + dataFile.getAbsolutePath() + "\"; pause -1")
                    .redirectOutput(System.out).redirectInput(System.in).execute();
        } finally {
            hz.shutdown();
        }
    }
}
