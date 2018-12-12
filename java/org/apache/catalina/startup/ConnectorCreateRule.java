/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import java.lang.reflect.Method;

import org.apache.catalina.Executor;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;


/**
 * Rule implementation that creates a connector.
 */

public class ConnectorCreateRule extends Rule {

    private static final Log log = LogFactory.getLog(ConnectorCreateRule.class);
    protected static final StringManager sm = StringManager.getManager(ConnectorCreateRule.class);
    // --------------------------------------------------------- Public Methods


    /**
     * Process the beginning of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     * @param attributes The attribute list for this element
     *
     * 所以在碰到server.xml文件中的Server/Service/Connector节点时将会触发ConnectorCreateRule类的begin方法的调用
     */

    // 方法中首先取出此时栈顶元素StandardService(Connector尚未创建)，再从包含所有<Connector>标签属
    // 性的attributes中查找是否存在exector属性，存在最终会调用_setExecutor(Connector, Executor)方
    // 法，该方法的主要作用是设置处理端到端连接的线程池，默认情况下server.xml中并不会事先设置该线程池，
    // 但即便不设置，之后在Tomcat启动时也会默认创建一个，在后面分析启动流程时会详细分析，这里暂先按默认
    // 未设置线程池流程走。之后会根据protocol属性创建Connector对象，基于Tomcat架构中各个组件及组件间
    // 关系中给出的server.xml可知，<Connector>标签共有两种协议，一种是HTTP/1.1，另一种是AJP/1.3。
    // 前者大家很清楚是HTTP协议的1.1版本，后者一般用于web容器之间通信，比HTTP协议在web容器间拥有更高的
    // 吞吐量。因为存在两种协议，那就会存在两个Connector实体，为了突出重点，我们只分析最常用的HTTP协议
    // 对应的Connector初始化流程
    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        Service svc = (Service)digester.peek();
        Executor ex = null;
        if ( attributes.getValue("executor")!=null ) {
            ex = svc.getExecutor(attributes.getValue("executor"));
        }
        //会根据配置文件中Server/Service/Connector节点的protocol属性调用
        //先看第一个Connector节点，调用Connector的构造方法时会传入字符串HTTP/1.1
        Connector con = new Connector(attributes.getValue("protocol"));
        if (ex != null) {
            setExecutor(con, ex);
        }
        String sslImplementationName = attributes.getValue("sslImplementationName");
        if (sslImplementationName != null) {
            setSSLImplementationName(con, sslImplementationName);
        }
        digester.push(con);
    }

    private static void setExecutor(Connector con, Executor ex) throws Exception {
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(),"setExecutor",new Class[] {java.util.concurrent.Executor.class});
        if (m!=null) {
            m.invoke(con.getProtocolHandler(), new Object[] {ex});
        }else {
            log.warn(sm.getString("connector.noSetExecutor", con));
        }
    }

    private static void setSSLImplementationName(Connector con, String sslImplementationName) throws Exception {
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(),"setSslImplementationName",new Class[] {String.class});
        if (m != null) {
            m.invoke(con.getProtocolHandler(), new Object[] {sslImplementationName});
        } else {
            log.warn(sm.getString("connector.noSetSSLImplementationName", con));
        }
    }

    /**
     * Process the end of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     */
    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
    }


}
