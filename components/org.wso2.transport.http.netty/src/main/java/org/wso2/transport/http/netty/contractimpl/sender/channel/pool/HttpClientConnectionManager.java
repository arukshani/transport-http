package org.wso2.transport.http.netty.contractimpl.sender.channel.pool;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contractimpl.common.HttpRoute;
import org.wso2.transport.http.netty.contractimpl.listener.SourceHandler;
import org.wso2.transport.http.netty.contractimpl.sender.channel.BootstrapConfiguration;
import org.wso2.transport.http.netty.contractimpl.sender.channel.TargetChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpClientConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientConnectionManager.class);

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

    public TargetChannel borrowTargetChannel(HttpRoute httpRoute, SourceHandler sourceHandler,
        SenderConfiguration senderConfig, BootstrapConfiguration bootstrapConfig) throws Exception {
        GenericObjectPool trgHlrConnPool = null;
        if (sourceHandler != null) {
            EventLoopGroup group;
            ChannelHandlerContext ctx = sourceHandler.getInboundChannelContext();
            group = ctx.channel().eventLoop();
            Class eventLoopClass = ctx.channel().getClass();
            synchronized (this) {
                if (!this.connGlobalPool.containsKey(httpRoute.toString())) {
                    PoolableClientConnectionFactory poolableTargetChannelFactory =
                        new PoolableClientConnectionFactory(group,
                            eventLoopClass, httpRoute, senderConfig, bootstrapConfig, this);
                    trgHlrConnPool = createPoolForRoute(poolableTargetChannelFactory);
                    this.connGlobalPool.put(httpRoute.toString(), trgHlrConnPool);
                } else {
                    trgHlrConnPool = this.connGlobalPool.get(httpRoute.toString());
                }
            }
        }
        TargetChannel targetChannel = (TargetChannel) trgHlrConnPool.borrowObject();
        targetChannel.setCorrelatedSource(sourceHandler);
        targetChannel.setConnectionManager(this);
        return targetChannel;
    }

    private GenericObjectPool createPoolForRoute(PoolableClientConnectionFactory poolableClientConnectionFactory) {
        return new GenericObjectPool(poolableClientConnectionFactory, instantiateAndConfigureConfig());
    }

    private GenericObjectPool.Config instantiateAndConfigureConfig() {
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.maxActive = poolConfiguration.getMaxActivePerPool();
        config.maxIdle = poolConfiguration.getMaxIdlePerPool();
        config.minIdle = poolConfiguration.getMinIdlePerPool();
        config.testOnBorrow = poolConfiguration.isTestOnBorrow();
        config.testWhileIdle = poolConfiguration.isTestWhileIdle();
        config.timeBetweenEvictionRunsMillis = poolConfiguration.getTimeBetweenEvictionRuns();
        config.minEvictableIdleTimeMillis = poolConfiguration.getMinEvictableIdleTime();
        config.whenExhaustedAction = poolConfiguration.getExhaustedAction();
        config.maxWait = poolConfiguration.getMaxWaitTime();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating a pool with {}", config);
        }
        return config;
    }
}
