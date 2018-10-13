package org.wso2.transport.http.netty.contractimpl.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.wso2.transport.http.netty.message.OutboundMsgContainer;

/**
 * Created by rukshani on 10/12/18.
 */
public class RespondHandler extends ChannelOutboundHandlerAdapter{
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if (msg instanceof OutboundMsgContainer) {
            OutboundMsgContainer container = (OutboundMsgContainer)msg;
            container.getMessageStateContext().getListenerState().writeOutboundResponseBody(container.getOutboundRespListener(),
                    container.getOutboundMsg(), container.getHttpContent());
           // messageStateContext.getListenerState().writeOutboundResponseBody(this, outboundResponseMsg, httpContent);
        } else {
            ctx.write(msg, promise);
        }
    }
}
