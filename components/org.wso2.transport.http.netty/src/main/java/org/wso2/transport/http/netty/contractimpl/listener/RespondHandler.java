package org.wso2.transport.http.netty.contractimpl.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.message.OutboundMsgContainer;

/**
 * Created by rukshani on 10/12/18.
 */
public class RespondHandler extends ChannelOutboundHandlerAdapter{

    private static final Logger LOG = LoggerFactory.getLogger(RespondHandler.class);

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if (msg instanceof OutboundMsgContainer) {
            OutboundMsgContainer container = (OutboundMsgContainer)msg;
            if (container.getHttpContent() instanceof LastHttpContent) {
                LOG.error(ctx.channel().id() + " - Response Handler Last - " + container.getOutboundMsg().getSequenceId() + " Thread: " + Thread.currentThread().getName());
            } else {
                LOG.error(ctx.channel().id() + " - Response Handler - " + container.getOutboundMsg().getSequenceId() + " Thread: " + Thread.currentThread().getName());
            }

            container.getMessageStateContext().getListenerState().writeOutboundResponseBody(container.getOutboundRespListener(),
                    container.getOutboundMsg(), container.getHttpContent());

           // messageStateContext.getListenerState().writeOutboundResponseBody(this, outboundResponseMsg, httpContent);
        } else {
            ctx.write(msg, promise);
        }
    }
}
