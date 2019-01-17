package org.wso2.transport.http.netty.contractimpl.sender.channel.pool;

import org.apache.commons.pool.impl.GenericObjectPool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpClientConnectionManager {

    private PoolConfiguration poolConfiguration;
    private final Map<String, GenericObjectPool> connGlobalPool;

    private HttpClientConnectionManager(){
        connGlobalPool = new ConcurrentHashMap<>();
    }

    private static class SingletonHelper{
        private static final HttpClientConnectionManager INSTANCE = new HttpClientConnectionManager();
    }

    public static HttpClientConnectionManager getInstance(){
        return SingletonHelper.INSTANCE;
    }

    public PoolConfiguration getPoolConfiguration() {
        return poolConfiguration;
    }

    public void setPoolConfiguration(PoolConfiguration poolConfiguration) {
        this.poolConfiguration = poolConfiguration;
    }
}
