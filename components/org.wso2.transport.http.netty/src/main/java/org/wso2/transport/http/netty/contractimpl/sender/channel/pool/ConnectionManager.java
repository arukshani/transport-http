/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.wso2.transport.http.netty.contractimpl.sender.channel.pool;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contractimpl.common.HttpRoute;
import org.wso2.transport.http.netty.contractimpl.listener.SourceHandler;
import org.wso2.transport.http.netty.contractimpl.sender.channel.BootstrapConfiguration;
import org.wso2.transport.http.netty.contractimpl.sender.channel.TargetChannel;
import org.wso2.transport.http.netty.contractimpl.sender.http2.Http2ConnectionManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class which handles connection pool management.
 */
public class ConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionManager.class);

    private PoolConfiguration poolConfiguration;
    private final Map<String, GenericObjectPool> connGlobalPool;
    private Map<String, GenericObjectPool> poolablePool;
    private Http2ConnectionManager http2ConnectionManager;
    private PoolManagementPolicy poolManagementPolicy;

    public ConnectionManager(PoolConfiguration poolConfiguration) {
        this.poolConfiguration = poolConfiguration;
        connGlobalPool = new ConcurrentHashMap<>();
        poolablePool = new ConcurrentHashMap<>();
        this.http2ConnectionManager = new Http2ConnectionManager(poolConfiguration);
        if (poolConfiguration.getNumberOfPools() == 1) {
            this.poolManagementPolicy = PoolManagementPolicy.LOCK_DEFAULT_POOLING;
        }
    }

    private GenericObjectPool createPoolForRoute(PoolableTargetChannelFactory poolableTargetChannelFactory) {
        return new GenericObjectPool(poolableTargetChannelFactory, instantiateAndConfigureConfig());
    }

    public void returnChannel(TargetChannel targetChannel) throws Exception {
        releaseChannelToPool(targetChannel, this.connGlobalPool.get(targetChannel.getHttpRoute().toString()));


       /* if (targetChannel.getCorrelatedSource() != null) {
            Map<String, GenericObjectPool> objectPoolMap = targetChannel.getCorrelatedSource().getTargetChannelPool();
            releaseChannelToPool(targetChannel, objectPoolMap.get(targetChannel.getHttpRoute().toString()));
        } else {
            releaseChannelToPool(targetChannel, this.connGlobalPool.get(targetChannel.getHttpRoute().toString()));
        }*/

//        releaseChannelToPool(targetChannel, poolablePool.get(targetChannel.getHttpRoute().toString()));
    }

    private void releaseChannelToPool(TargetChannel targetChannel, GenericObjectPool pool) throws Exception {
        try {
            String channelID = targetChannel.getChannel().id().asShortText();
            if (targetChannel.getChannel().isActive() && pool != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Returning connection {} to the pool", channelID);
                }
                LOG.warn("Channel returned to pool {}", targetChannel.getChannel().id());
                pool.returnObject(targetChannel);
            } else {
                LOG.debug("Channel {} is inactive hence not returning to connection pool", channelID);
            }
        } catch (Exception e) {
            throw new Exception("Couldn't return channel to pool", e);
        }
    }

    public void invalidateTargetChannel(TargetChannel targetChannel) throws Exception {
        LOG.warn("Invalidate channel {}", targetChannel.getChannel().id());
        this.connGlobalPool.get(targetChannel.getHttpRoute().toString()).invalidateObject(targetChannel);

        /*if (targetChannel.getCorrelatedSource() != null) {
            Map<String, GenericObjectPool> objectPoolMap = targetChannel.getCorrelatedSource().getTargetChannelPool();
            try {
                // Need a null check because SourceHandler side could timeout before TargetHandler side.
                String httpRoute = targetChannel.getHttpRoute().toString();
                if (objectPoolMap.get(httpRoute) != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Invalidating connection {} to the pool",
                                  targetChannel.getChannel().id().asShortText());
                    }
                    objectPoolMap.get(httpRoute).invalidateObject(targetChannel);
                }
            } catch (Exception e) {
                throw new Exception("Cannot invalidate channel from pool", e);
            }
        } else {
            this.connGlobalPool.get(targetChannel.getHttpRoute().toString()).invalidateObject(targetChannel);
        }*/

//        poolablePool.get(targetChannel.getHttpRoute().toString()).invalidateObject(targetChannel);

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

    public Http2ConnectionManager getHttp2ConnectionManager() {
        return http2ConnectionManager;
    }

    /**
     * Gets the client connection pool.
     *
     * @param httpRoute        Represents the endpoint address
     * @param sourceHandler    Represents the source channel
     * @param senderConfig     Represents the client configurations
     * @param bootstrapConfig  Represents the bootstrap info related to client connection creation
     * @param clientEventGroup Represents the eventloop group that the client channel should be bound to
     * @return the client connection pool
     */
    public GenericObjectPool getClientConnectionPool(HttpRoute httpRoute, SourceHandler sourceHandler,
                                                     SenderConfiguration senderConfig,
                                                     BootstrapConfiguration bootstrapConfig,
                                                     EventLoopGroup clientEventGroup) {
        GenericObjectPool clientPool;
        if (sourceHandler != null) {
            ChannelHandlerContext ctx = sourceHandler.getInboundChannelContext();
           /* clientPool = this.connGlobalPool.get(httpRoute.toString());
            if(clientPool == null) {
                clientPool = getGenericObjectPool(httpRoute, senderConfig, bootstrapConfig, ctx.channel().getClass(),
                                                  ctx.channel().eventLoop());
            }*/
            clientPool = getGenericObjectPool(httpRoute, senderConfig, bootstrapConfig, ctx.channel().getClass(),
                                              ctx.channel().eventLoop());
        } else {
            clientPool = getGenericObjectPool(httpRoute, senderConfig, bootstrapConfig, NioSocketChannel.class,
                                              clientEventGroup);
        }
        return clientPool;
    }

    /**
     * Creates the client pool if it doesn't exist in the global pool map.
     *
     * @param httpRoute       Represents the endpoint address
     * @param senderConfig    Represents the client configurations
     * @param bootstrapConfig Represents the bootstrap info related to client connection creation
     * @param eventLoopClass  Represents the eventloop class of the client channel
     * @param group           Represents the eventloop group that the client channel should be bound to
     * @return the client connection pool
     */
    private GenericObjectPool getGenericObjectPool(HttpRoute httpRoute, SenderConfiguration senderConfig,
        BootstrapConfiguration bootstrapConfig, Class eventLoopClass, EventLoopGroup group) {
        GenericObjectPool clientPool;
        synchronized (this) {
            if (!this.connGlobalPool.containsKey(httpRoute.toString())) {
                PoolableTargetChannelFactory poolableTargetChannelFactory = new PoolableTargetChannelFactory(group,
                    eventLoopClass, httpRoute, senderConfig, bootstrapConfig, this);
                clientPool = createPoolForRoute(poolableTargetChannelFactory);
                this.connGlobalPool.put(httpRoute.toString(), clientPool);
            } else {
                clientPool = this.connGlobalPool.get(httpRoute.toString());
            }
        }
        return clientPool;
    }

    private GenericObjectPool createPoolForRoutePerSrcHndlr(GenericObjectPool genericObjectPool) {
        return new GenericObjectPool(new PoolableTargetChannelFactoryPerSrcHndlr(genericObjectPool),
                                     instantiateAndConfigureConfig());
    }

    /**
     * @param httpRoute BE address
     * @param sourceHandler Incoming channel
     * @param senderConfig The sender configuration instance
     * @return the target channel which is requested for given parameters.
     * @throws Exception to notify any errors occur during retrieving the target channel
     */
    public TargetChannel borrowTargetChannel(HttpRoute httpRoute, SourceHandler sourceHandler,
                                             SenderConfiguration senderConfig, BootstrapConfiguration bootstrapConfig,
                                             EventLoopGroup clientGroup) throws Exception {
        GenericObjectPool trgHlrConnPool;

        if (sourceHandler != null) {
            EventLoopGroup group;
            ChannelHandlerContext ctx = sourceHandler.getInboundChannelContext();
            group = ctx.channel().eventLoop();
            Class eventLoopClass = ctx.channel().getClass();

            if (poolManagementPolicy == PoolManagementPolicy.LOCK_DEFAULT_POOLING) {
                // This is faster than the above one (about 2k difference)
                Map<String, GenericObjectPool> srcHlrConnPool = sourceHandler.getTargetChannelPool();
                trgHlrConnPool = srcHlrConnPool.get(httpRoute.toString());
                if (trgHlrConnPool == null) {
                    PoolableTargetChannelFactory poolableTargetChannelFactory =
                        new PoolableTargetChannelFactory(group, eventLoopClass, httpRoute, senderConfig,
                                                         bootstrapConfig, this);
                    trgHlrConnPool = createPoolForRoute(poolableTargetChannelFactory);
                    srcHlrConnPool.put(httpRoute.toString(), trgHlrConnPool);
                }
            } else {
                Map<String, GenericObjectPool> srcHlrConnPool = sourceHandler.getTargetChannelPool();
                trgHlrConnPool = srcHlrConnPool.get(httpRoute.toString());
                if (trgHlrConnPool == null) {
                    synchronized (this) {
                        if (!this.connGlobalPool.containsKey(httpRoute.toString())) {
                            PoolableTargetChannelFactory poolableTargetChannelFactory =
                                new PoolableTargetChannelFactory(group,
                                                                 eventLoopClass, httpRoute, senderConfig, bootstrapConfig, this);
                            trgHlrConnPool = createPoolForRoute(poolableTargetChannelFactory);
                            this.connGlobalPool.put(httpRoute.toString(), trgHlrConnPool);
                        }
                        trgHlrConnPool = this.connGlobalPool.get(httpRoute.toString());
                        trgHlrConnPool = createPoolForRoutePerSrcHndlr(trgHlrConnPool);
                    }
                    srcHlrConnPool.put(httpRoute.toString(), trgHlrConnPool);
                }
            }
        } else {
            Class cl = NioSocketChannel.class;
            EventLoopGroup group = clientGroup;
            synchronized (this) {
                if (!this.connGlobalPool.containsKey(httpRoute.toString())) {
                    PoolableTargetChannelFactory poolableTargetChannelFactory =
                        new PoolableTargetChannelFactory(group, cl,
                                                         httpRoute, senderConfig, bootstrapConfig, this);
                    trgHlrConnPool = createPoolForRoute(poolableTargetChannelFactory);
                    this.connGlobalPool.put(httpRoute.toString(), trgHlrConnPool);
                }
                trgHlrConnPool = this.connGlobalPool.get(httpRoute.toString());
            }
        }

        TargetChannel targetChannel = (TargetChannel) trgHlrConnPool.borrowObject();
        targetChannel.setCorrelatedSource(sourceHandler);
        targetChannel.setConnectionManager(this);
        return targetChannel;
    }

    /**
     * Connection pool management policies for  target channels.
     */
    public enum PoolManagementPolicy {
        LOCK_DEFAULT_POOLING,
    }

    public TargetChannel borrowObjectFromPoolablePool(HttpRoute httpRoute, SourceHandler sourceHandler,
                                         SenderConfiguration senderConfig, BootstrapConfiguration bootstrapConfig,
                                         EventLoopGroup clientGroup) throws Exception {
        GenericObjectPool actualPool = null;

        if (sourceHandler != null) {
            EventLoopGroup group;
            ChannelHandlerContext ctx = sourceHandler.getInboundChannelContext();
            group = ctx.channel().eventLoop();
            Class eventLoopClass = ctx.channel().getClass();

//            Map<String, GenericObjectPool> tempConn = sourceHandler.getTargetChannelPool();
            actualPool = poolablePool.get(httpRoute.toString());
            if (actualPool == null) {
                synchronized (this) {
                    if (!this.connGlobalPool.containsKey(httpRoute.toString())) {
                        PoolableTargetChannelFactory poolableTargetChannelFactory =
                            new PoolableTargetChannelFactory(group,
                                                             eventLoopClass, httpRoute, senderConfig, bootstrapConfig,
                                                             this);
                        actualPool = createPoolForRoute(poolableTargetChannelFactory);
                        this.connGlobalPool.put(httpRoute.toString(), actualPool);
                    }
                    actualPool = this.connGlobalPool.get(httpRoute.toString());
                    actualPool = createPoolForRoutePerSrcHndlr(actualPool);
                }
                poolablePool.put(httpRoute.toString(), actualPool);
            }
        } else {
            Class cl = NioSocketChannel.class;
            EventLoopGroup group = clientGroup;
            synchronized (this) {
                if (!this.connGlobalPool.containsKey(httpRoute.toString())) {
                    PoolableTargetChannelFactory poolableTargetChannelFactory =
                        new PoolableTargetChannelFactory(group, cl,
                                                         httpRoute, senderConfig, bootstrapConfig, this);
                    actualPool = createPoolForRoute(poolableTargetChannelFactory);
                    this.connGlobalPool.put(httpRoute.toString(), actualPool);
                }
                actualPool = this.connGlobalPool.get(httpRoute.toString());
            }
        }

        TargetChannel targetChannel = (TargetChannel) actualPool.borrowObject();
        targetChannel.setCorrelatedSource(sourceHandler);
        targetChannel.setConnectionManager(this);
        return targetChannel;
    }

    GenericObjectPool getConnGlobalPool(HttpRoute route) {
        return this.connGlobalPool.get(route.toString());
    }

}
