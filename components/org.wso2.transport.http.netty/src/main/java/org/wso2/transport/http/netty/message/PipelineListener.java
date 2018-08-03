package org.wso2.transport.http.netty.message;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.wso2.transport.http.netty.contractimpl.HttpOutboundRespListener;
import org.wso2.transport.http.netty.listener.PipeliningHandler;

/**
 * Created by rukshani on 8/3/18.
 */
public class PipelineListener implements MessageListener{

    private ChannelHandlerContext sourceContext;
    private HttpCarbonMessage outboundResponseMsg;
    private HttpOutboundRespListener respListener;

    public PipelineListener(ChannelHandlerContext sourceContext, HttpCarbonMessage httpCarbonMessage, HttpOutboundRespListener respListener) {
        this.sourceContext = sourceContext;
        this.outboundResponseMsg = httpCarbonMessage;
        this.respListener = respListener;
    }

    @Override
    public void onMessage(HttpContent httpContent) {
        this.sourceContext.channel().eventLoop().execute(() -> {
            //Handle pipelining
            outboundResponseMsg.addHttpContentBack(httpContent);
            PipeliningHandler.handleQueuedResponses(sourceContext, respListener, outboundResponseMsg);
        });
    }
}
