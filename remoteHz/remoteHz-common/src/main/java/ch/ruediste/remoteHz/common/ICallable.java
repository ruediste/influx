package ch.ruediste.remoteHz.common;

import java.io.Serializable;
import java.util.concurrent.Callable;

public interface ICallable<V> extends Callable<V>, Serializable {

}
