package org.wso2.transport.http.netty.contractimpl.sender.http2;

import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2NoMoreStreamIdsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contractimpl.common.states.Http2MessageStateContext;
import org.wso2.transport.http.netty.contractimpl.sender.states.http2.SendingHeaders;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;

public class Http2RequestWriter {
    private static final Logger LOG = LoggerFactory.getLogger(Http2RequestWriter.class);

    HttpCarbonMessage httpOutboundRequest;
    OutboundMsgHolder outboundMsgHolder;
    Http2MessageStateContext http2MessageStateContext;
    int streamId;
    private final Http2ClientChannel http2ClientChannel;

    public Http2RequestWriter(OutboundMsgHolder outboundMsgHolder, Http2ClientChannel http2ClientChannel) {
        this.outboundMsgHolder = outboundMsgHolder;
        this.httpOutboundRequest = outboundMsgHolder.getRequest();
        http2MessageStateContext = httpOutboundRequest.getHttp2MessageStateContext();
        if (http2MessageStateContext == null) {
            http2MessageStateContext = new Http2MessageStateContext();
            httpOutboundRequest.setHttp2MessageStateContext(http2MessageStateContext);
        }
        this.http2ClientChannel = http2ClientChannel;
    }

   /* public void startWritingContent(ChannelHandlerContext ctx, Http2ClientChannel http2ClientChannel) {
        httpOutboundRequest.getHttp2MessageStateContext()
            .setSenderState(new SendingHeaders(Http2TargetHandler.this, this));
        // Write Content
        httpOutboundRequest.getHttpContentAsync().setMessageListener((httpContent -> {
            LOG.warn("Thread: {}", Thread.currentThread());
            http2ClientChannel.getChannel().eventLoop().execute(() -> {
                try {
                    writeOutboundRequest(ctx, httpContent);
                } catch (Http2NoMoreStreamIdsException ex) {
                    //Remove connection from the pool
                    http2ClientChannel.removeFromConnectionPool();
                    LOG.warn("Channel is removed from the connection pool : {}", ex.getMessage(), ex);
                    outboundMsgHolder.getResponseFuture().notifyHttpListener(ex);
                } catch (Http2Exception ex) {
                    LOG.error("Failed to send the request : " + ex.getMessage(), ex);
                    outboundMsgHolder.getResponseFuture().notifyHttpListener(ex);
                }
            });
        }));
    }*/

    public void startWritingContent() {
        httpOutboundRequest.getHttp2MessageStateContext()
            .setSenderState(new SendingHeaders(this));
        // Write Content
        httpOutboundRequest.getHttpContentAsync().setMessageListener((httpContent -> {
            LOG.warn("Thread: {}", Thread.currentThread());
            http2ClientChannel.getChannel().eventLoop().execute(() -> {
//                try {
//                    writeOutboundRequest(ctx, httpContent);
                //Write to channel
                Http2Content http2Content = new Http2Content(httpContent, http2MessageStateContext,
                                                             outboundMsgHolder);
                http2ClientChannel.getChannel().write(http2Content);
                /*} catch (Http2NoMoreStreamIdsException ex) {
                    //Remove connection from the pool
                    http2ClientChannel.removeFromConnectionPool();
                    LOG.warn("Channel is removed from the connection pool : {}", ex.getMessage(), ex);
                    outboundMsgHolder.getResponseFuture().notifyHttpListener(ex);
                } catch (Http2Exception ex) {
                    LOG.error("Failed to send the request : " + ex.getMessage(), ex);
                    outboundMsgHolder.getResponseFuture().notifyHttpListener(ex);
                }*/
            });
        }));
    }

   /* private void writeOutboundRequest(ChannelHandlerContext ctx, HttpContent msg) throws Http2Exception {
        try {
            http2MessageStateContext.getSenderState().writeOutboundRequestBody(ctx, msg, http2MessageStateContext);
        } catch (RuntimeException ex) {
            httpOutboundRequest.getHttp2MessageStateContext()
                .setSenderState(new SendingEntityBody(this));
            httpOutboundRequest.getHttp2MessageStateContext()
                .getSenderState().writeOutboundRequestBody(ctx, new DefaultLastHttpContent(),
                                                           http2MessageStateContext);
        }
    }*/

    public Http2MessageStateContext getHttp2MessageStateContext() {
        return http2MessageStateContext;
    }

    public HttpCarbonMessage getHttpOutboundRequest() {
        return httpOutboundRequest;
    }

    public OutboundMsgHolder getOutboundMsgHolder() {
        return outboundMsgHolder;
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public Http2ClientChannel getHttp2ClientChannel() {
        return http2ClientChannel;
    }
}
