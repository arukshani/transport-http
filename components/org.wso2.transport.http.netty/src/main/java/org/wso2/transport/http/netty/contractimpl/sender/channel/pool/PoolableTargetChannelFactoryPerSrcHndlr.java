package org.wso2.transport.http.netty.contractimpl.sender.channel.pool;

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contractimpl.sender.channel.TargetChannel;

public class PoolableTargetChannelFactoryPerSrcHndlr implements PoolableObjectFactory {
    private static final Logger LOG = LoggerFactory.getLogger(PoolableTargetChannelFactoryPerSrcHndlr.class);

    private final GenericObjectPool genericObjectPool;

    PoolableTargetChannelFactoryPerSrcHndlr(GenericObjectPool genericObjectPool) {
        this.genericObjectPool = genericObjectPool;
    }

    @Override
    public Object makeObject() throws Exception {
        TargetChannel targetChannel = (TargetChannel) this.genericObjectPool.borrowObject();
//        LOG.debug("Created channel: {}", targetChannel);
        LOG.warn("Wrapper makeObject {}");
        return targetChannel;
    }

    @Override
    public void destroyObject(Object o) throws Exception {
        if (((TargetChannel) o).getChannel().isActive()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Original Channel {} is returned to the pool. ", ((TargetChannel) o).getChannel().id());
            }
            LOG.warn("Wrapper destroy return channel {}");
            this.genericObjectPool.returnObject(o);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Original Channel is destroyed. ");
            }
            LOG.warn("Wrapper destroy destroy channel {}");
            this.genericObjectPool.invalidateObject(o);
            LOG.warn("Source handler destroy channel");
        }
    }

    @Override
    public boolean validateObject(Object o) {
        if (((TargetChannel) o).getChannel() != null) {
            boolean answer = ((TargetChannel) o).getChannel().isActive();

            LOG.debug("Validating channel: {} -> {}", o, answer);
            return answer;
        }
        return true;
    }

    @Override
    public void activateObject(Object o) {
    }

    @Override
    public void passivateObject(Object o) {
    }

}
