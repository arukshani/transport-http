package org.wso2.transport.http.netty.util.client.http2.nettyclient;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2FrameListener;
import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contractimpl.common.FrameLogger;

import static io.netty.handler.logging.LogLevel.TRACE;

public class Http2PriorKnowledgeClientInitializer extends ChannelInitializer<SocketChannel> {
    private Http2Connection connection;
    private Http2ClientFrameListener clientFrameListener;
    private Http2ConnectionHandler http2ConnectionHandler;

    public Http2PriorKnowledgeClientInitializer() {
        connection = new DefaultHttp2Connection(false);
        clientFrameListener = new Http2ClientFrameListener();
        Http2FrameListener frameListener = new DelegatingDecompressorFrameListener(connection, clientFrameListener);
        Http2ConnectionHandlerBuilder connectionHandlerBuilder = new Http2ConnectionHandlerBuilder();
        connectionHandlerBuilder.frameLogger(new FrameLogger(TRACE, Constants.TRACE_LOG_UPSTREAM));
        http2ConnectionHandler = connectionHandlerBuilder.connection(connection).frameListener(frameListener).build();
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline clientPipeline = socketChannel.pipeline();
        clientPipeline.addLast(Constants.CONNECTION_HANDLER, http2ConnectionHandler);
    }
}
