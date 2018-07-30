/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.transport.http.netty.pipeline;

import io.netty.handler.codec.http.FullHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contentaware.listeners.PipeliningRequestListener;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.listener.ServerBootstrapConfiguration;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.client.http.HttpClient;

import java.util.HashMap;
import java.util.LinkedList;

import static org.testng.Assert.assertEquals;

/**
 * Tests for http 1.1 pipelining support.
 */
public class HttpPipeliningTestCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPipeliningTestCase.class);

    protected ServerConnector serverConnector;
    protected ListenerConfiguration listenerConfiguration;
    protected HttpWsConnectorFactory httpWsConnectorFactory;
    private PipeliningRequestListener pipeliningRequestListener;

    HttpPipeliningTestCase() {
        this.listenerConfiguration = new ListenerConfiguration();
    }

    @BeforeClass
    private void setUp() {
        listenerConfiguration.setPort(TestUtil.SERVER_CONNECTOR_PORT);
        listenerConfiguration.setServerHeader(TestUtil.TEST_SERVER);

        ServerBootstrapConfiguration serverBootstrapConfig = new ServerBootstrapConfiguration(new HashMap<>());
        httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();

        serverConnector = httpWsConnectorFactory.createServerConnector(serverBootstrapConfig, listenerConfiguration);
        ServerConnectorFuture serverConnectorFuture = serverConnector.start();
        pipeliningRequestListener = new PipeliningRequestListener();
        serverConnectorFuture.setHttpConnectorListener(pipeliningRequestListener);
        try {
            serverConnectorFuture.sync();
        } catch (InterruptedException e) {
            LOGGER.error("Thread Interrupted while sleeping ", e);
        }
    }

    @Test(enabled = false)
    public void testRandomOrderResponses() {
        HttpClient httpClient = new HttpClient(TestUtil.TEST_HOST, TestUtil.SERVER_CONNECTOR_PORT);
        LinkedList<FullHttpResponse> responses = httpClient.finishResponsesRandomly(pipeliningRequestListener);
        assertEquals("/messageid1", TestUtil.getEntityBodyFrom(responses.pop()));
        assertEquals("/messageid2", TestUtil.getEntityBodyFrom(responses.pop()));
        assertEquals("/messageid3", TestUtil.getEntityBodyFrom(responses.pop()));
        assertEquals("/messageid4", TestUtil.getEntityBodyFrom(responses.pop()));
        assertEquals("/messageid5", TestUtil.getEntityBodyFrom(responses.pop()));
    }

    @AfterClass
    public void cleanup() throws InterruptedException {
        serverConnector.stop();
        httpWsConnectorFactory.shutdown();
    }
}
