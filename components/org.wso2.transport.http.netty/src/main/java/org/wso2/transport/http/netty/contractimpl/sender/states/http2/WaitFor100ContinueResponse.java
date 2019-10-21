package org.wso2.transport.http.netty.contractimpl.sender.states.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http2.Http2Exception;
import org.wso2.transport.http.netty.contractimpl.common.states.Http2MessageStateContext;
import org.wso2.transport.http.netty.contractimpl.sender.http2.OutboundMsgHolder;
import org.wso2.transport.http.netty.message.Http2DataFrame;
import org.wso2.transport.http.netty.message.Http2HeadersFrame;
import org.wso2.transport.http.netty.message.Http2PushPromise;

public class WaitFor100ContinueResponse implements SenderState {


    @Override
    public void writeOutboundRequestHeaders(ChannelHandlerContext ctx, HttpContent httpContent) throws Http2Exception {

    }

    @Override
    public void writeOutboundRequestBody(ChannelHandlerContext ctx, HttpContent httpContent,
                                         Http2MessageStateContext http2MessageStateContext) throws Http2Exception {

    }

    @Override
    public void readInboundResponseHeaders(ChannelHandlerContext ctx, Http2HeadersFrame http2HeadersFrame,
                                           OutboundMsgHolder outboundMsgHolder, boolean serverPush,
                                           Http2MessageStateContext http2MessageStateContext) {

    }

    @Override
    public void readInboundResponseBody(ChannelHandlerContext ctx, Http2DataFrame http2DataFrame,
                                        OutboundMsgHolder outboundMsgHolder, boolean serverPush,
                                        Http2MessageStateContext http2MessageStateContext) {

    }

    @Override
    public void readInboundPromise(ChannelHandlerContext ctx, Http2PushPromise http2PushPromise,
                                   OutboundMsgHolder outboundMsgHolder) {

    }

    @Override
    public void handleStreamTimeout(OutboundMsgHolder outboundMsgHolder, boolean serverPush) {

    }

    @Override
    public void handleConnectionClose(OutboundMsgHolder outboundMsgHolder) {

    }
}
