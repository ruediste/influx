package ch.ruediste.remoteHz;

import java.io.Serializable;

public class SampleTask implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public void run() {
        System.out.println("Hello World");
    }

}
