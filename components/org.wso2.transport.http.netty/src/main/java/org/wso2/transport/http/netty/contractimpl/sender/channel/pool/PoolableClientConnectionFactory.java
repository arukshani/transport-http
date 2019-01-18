package org.wso2.transport.http.netty.contractimpl.sender.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import org.apache.commons.pool.PoolableObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contract.config.SenderConfiguration;
import org.wso2.transport.http.netty.contractimpl.common.HttpRoute;
import org.wso2.transport.http.netty.contractimpl.sender.ConnectionAvailabilityFuture;
import org.wso2.transport.http.netty.contractimpl.sender.HttpClientChannelInitializer;
import org.wso2.transport.http.netty.contractimpl.sender.channel.BootstrapConfiguration;
import org.wso2.transport.http.netty.contractimpl.sender.channel.TargetChannel;

import java.net.InetSocketAddress;

import static org.wso2.transport.http.netty.contract.Constants.HTTP_SCHEME;

public class PoolableClientConnectionFactory implements PoolableObjectFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PoolableClientConnectionFactory.class);

    private EventLoopGroup eventLoopGroup;
    private Class eventLoopClass;
    private HttpRoute httpRoute;
    private SenderConfiguration senderConfiguration;
    private BootstrapConfiguration bootstrapConfiguration;
    private HttpClientConnectionManager connectionManager;

    public PoolableClientConnectionFactory(EventLoopGroup eventLoopGroup, Class eventLoopClass, HttpRoute httpRoute,
        SenderConfiguration senderConfiguration, BootstrapConfiguration bootstrapConfiguration,
        HttpClientConnectionManager connectionManager) {
        this.eventLoopGroup = eventLoopGroup;
        this.eventLoopClass = eventLoopClass;
        this.httpRoute = httpRoute;
        this.senderConfiguration = senderConfiguration;
        this.bootstrapConfiguration = bootstrapConfiguration;
        this.connectionManager = connectionManager;

    }

    @Override public Object makeObject() throws Exception {
        Bootstrap clientBootstrap = instantiateAndConfigBootStrap(eventLoopGroup, eventLoopClass,
            bootstrapConfiguration);
        ConnectionAvailabilityFuture connectionAvailabilityFuture = new ConnectionAvailabilityFuture();

        HttpClientChannelInitializer httpClientChannelInitializer = instantiateAndConfigClientInitializer(
            senderConfiguration, clientBootstrap, httpRoute, connectionManager, connectionAvailabilityFuture);
        clientBootstrap.handler(httpClientChannelInitializer);

        // Connect to proxy server if proxy is enabled
        ChannelFuture channelFuture;
        if (senderConfiguration.getProxyServerConfiguration() != null && senderConfiguration.getScheme()
            .equals(HTTP_SCHEME)) {
            channelFuture = clientBootstrap.connect(
                new InetSocketAddress(senderConfiguration.getProxyServerConfiguration().getProxyHost(),
                    senderConfiguration.getProxyServerConfiguration().getProxyPort()));
        } else {
            channelFuture = clientBootstrap.connect(new InetSocketAddress(httpRoute.getHost(), httpRoute.getPort()));
        }
        connectionAvailabilityFuture.setSocketAvailabilityFuture(channelFuture);
        connectionAvailabilityFuture.setForceHttp2(senderConfiguration.isForceHttp2());

        TargetChannel targetChannel = new TargetChannel(httpClientChannelInitializer, channelFuture, httpRoute,
            connectionAvailabilityFuture);
        httpClientChannelInitializer.setHttp2ClientChannel(targetChannel.getHttp2ClientChannel());

        LOG.debug("Created channel: {}", httpRoute);

        return targetChannel;
    }

    @Override public void destroyObject(Object o) throws Exception {
        TargetChannel targetChannel = (TargetChannel) o;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Destroying channel: {}", targetChannel.getChannel().id());
        }
        if (targetChannel.getChannel().isOpen()) {
            targetChannel.getChannel().close();
        }
    }

    @Override public boolean validateObject(Object o) {
        TargetChannel targetChannel = (TargetChannel) o;
        if (targetChannel.getChannel() != null) {
            boolean answer = targetChannel.getChannel().isActive();
            LOG.debug("Validating channel: {} -> {}", targetChannel.getChannel().id(), answer);
            return answer;
        }
        return true;
    }

    @Override public void activateObject(Object o) throws Exception {

    }

    @Override public void passivateObject(Object o) throws Exception {

    }

    private Bootstrap instantiateAndConfigBootStrap(EventLoopGroup eventLoopGroup, Class eventLoopClass,
        BootstrapConfiguration bootstrapConfiguration) {
        Bootstrap clientBootstrap = new Bootstrap();
        clientBootstrap.channel(eventLoopClass);
        clientBootstrap.group(eventLoopGroup);
        clientBootstrap.option(ChannelOption.SO_KEEPALIVE, bootstrapConfiguration.isKeepAlive());
        clientBootstrap.option(ChannelOption.TCP_NODELAY, bootstrapConfiguration.isTcpNoDelay());
        clientBootstrap.option(ChannelOption.SO_REUSEADDR, bootstrapConfiguration.isSocketReuse());
        clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, bootstrapConfiguration.getConnectTimeOut());
        return clientBootstrap;
    }

    private HttpClientChannelInitializer instantiateAndConfigClientInitializer(SenderConfiguration senderConfiguration,
        Bootstrap clientBootstrap, HttpRoute httpRoute, HttpClientConnectionManager connectionManager,
        ConnectionAvailabilityFuture connectionAvailabilityFuture) {
        HttpClientChannelInitializer httpClientChannelInitializer = new HttpClientChannelInitializer(
            senderConfiguration, httpRoute, connectionManager, connectionAvailabilityFuture);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new TCP client bootstrap connecting to {}:{} with options: {}", httpRoute.getHost(),
                httpRoute.getPort(), clientBootstrap);
        }
        return httpClientChannelInitializer;
    }
}
