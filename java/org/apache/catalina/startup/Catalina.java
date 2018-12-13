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


import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.security.SecurityConfig;
import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;


/**
 * Startup/Shutdown shell program for Catalina.  The following command line
 * options are recognized:
 * <ul>
 * <li><b>-config {pathname}</b> - Set the pathname of the configuration file
 *     to be processed.  If a relative path is specified, it will be
 *     interpreted as relative to the directory pathname specified by the
 *     "catalina.base" system property.   [conf/server.xml]</li>
 * <li><b>-help</b>      - Display usage information.</li>
 * <li><b>-nonaming</b>  - Disable naming support.</li>
 * <li><b>configtest</b> - Try to test the config</li>
 * <li><b>start</b>      - Start an instance of Catalina.</li>
 * <li><b>stop</b>       - Stop the currently running instance of Catalina.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class Catalina {


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    public static final String SERVER_XML = "conf/server.xml";

    // ----------------------------------------------------- Instance Variables

    /**
     * Use await.
     */
    protected boolean await = false;

    /**
     * Pathname to the server configuration file.
     */
    protected String configFile = SERVER_XML;

    // XXX Should be moved to embedded
    /**
     * The shared extensions class loader for this server.
     */
    protected ClassLoader parentClassLoader =
        Catalina.class.getClassLoader();


    /**
     * The server component we are starting or stopping.
     */
    protected Server server = null;


    /**
     * Use shutdown hook flag.
     */
    protected boolean useShutdownHook = true;


    /**
     * Shutdown hook.
     */
    protected Thread shutdownHook = null;


    /**
     * Is naming enabled ?
     */
    protected boolean useNaming = true;


    /**
     * Prevent duplicate loads.
     */
    protected boolean loaded = false;


    // ----------------------------------------------------------- Constructors

    public Catalina() {
        setSecurityProtection();
        ExceptionUtils.preload();
    }


    // ------------------------------------------------------------- Properties

    public void setConfigFile(String file) {
        configFile = file;
    }


    public String getConfigFile() {
        return configFile;
    }


    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }


    public boolean getUseShutdownHook() {
        return useShutdownHook;
    }


    /**
     * Set the shared extensions class loader.
     *
     * @param parentClassLoader The shared extensions class loader.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    public void setServer(Server server) {
        this.server = server;
    }


    public Server getServer() {
        return server;
    }


    /**
     * @return <code>true</code> if naming is enabled.
     */
    public boolean isUseNaming() {
        return this.useNaming;
    }


    /**
     * Enables or disables naming support.
     *
     * @param useNaming The new use naming value
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }

    //启动的时候反射调用到这里
    public void setAwait(boolean b) {
        await = b;
    }

    public boolean isAwait() {
        return await;
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * Process the specified command line arguments.
     *
     * @param args Command line arguments to process
     * @return <code>true</code> if we should continue processing
     */
    protected boolean arguments(String args[]) {

        boolean isConfig = false;

        if (args.length < 1) {
            usage();
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            if (isConfig) {
                configFile = args[i];
                isConfig = false;
            } else if (args[i].equals("-config")) {
                isConfig = true;
            } else if (args[i].equals("-nonaming")) {
                setUseNaming(false);
            } else if (args[i].equals("-help")) {
                usage();
                return false;
            } else if (args[i].equals("start")) {
                // NOOP
            } else if (args[i].equals("configtest")) {
                // NOOP
            } else if (args[i].equals("stop")) {
                // NOOP
            } else {
                usage();
                return false;
            }
        }

        return true;
    }


    /**
     * Return a File object representing our configuration file.
     * @return the main configuration file
     */
    protected File configFile() {

        File file = new File(configFile);
        if (!file.isAbsolute()) {
            file = new File(Bootstrap.getCatalinaBase(), configFile);
        }
        return file;

    }


    /**
     * Create and configure the Digester we will be using for startup.
     * @return the main digester to parse server.xml
     *
     * 这里很重要===》要考的
     *
     * --------------Rule-------------- 对应方法------------------用途------------
     * --------ObjectCreateRule-----addObjectCreate----根据匹配解析模式创建对应标签的实体类
     * -------SetPropertiesRule-----addSetProperties----根据匹配解析模式为对应标签实体类设置相关属性
     * ----------SetNextRule-----------addSetNext--------建立标签对应实体之间子父类关系
     *
     */
    protected Digester createStartDigester() {
        long t1=System.currentTimeMillis();
        // Initialize the digester
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setRulesValidation(true);
        Map<Class<?>, List<String>> fakeAttributes = new HashMap<>();
        // Ignore className on all elements
        List<String> objectAttrs = new ArrayList<>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);
        // Ignore attribute added by Eclipse for its internal tracking
        List<String> contextAttrs = new ArrayList<>();
        contextAttrs.add("source");
        fakeAttributes.put(StandardContext.class, contextAttrs);
        // Ignore Connector attribute used internally but set on Server
        List<String> connectorAttrs = new ArrayList<>();
        connectorAttrs.add("portOffset");
        fakeAttributes.put(Connector.class, connectorAttrs);
        digester.setFakeAttributes(fakeAttributes);
        digester.setUseContextClassLoader(true);

        // Configure the actions we will be using

        // 为解析<Server>标签创建了三个规则ObjectCreateRule、SetPropertiesRule和SetNextRule，
        // 并指明了<Server>对应对象的实例为org.apache.catalina.core.StandardServer，这三个规
        // 则最终会被放在规则父类RuleBase类的缓存HashMap<String,List<Rule>> cache中，而Digester
        // 又持有该类的实例，也就是说Digester最终会装载解析xml文件所需的所有规则
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");



        digester.addObjectCreate("Server/GlobalNamingResources",
                                 "org.apache.catalina.deploy.NamingResourcesImpl");
        digester.addSetProperties("Server/GlobalNamingResources");
        digester.addSetNext("Server/GlobalNamingResources",
                            "setGlobalNamingResources",
                            "org.apache.catalina.deploy.NamingResourcesImpl");

        digester.addObjectCreate("Server/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Listener");
        digester.addSetNext("Server/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        // addObjectCreate(String pattern, String className, String attributeName)底层使
        // 用的规则为ObjectCreateRule，方法的第一个参数是pattern，表明了解析到什么标签才会使用配
        // 置的规则对标签的内容进行解析，和正则表达式匹配的作用类似。比如上图中的pattern为Server/Service
        // 表示解析到<Server>下的<Service>标签时运用规则进行解析，这里用/表示一种父子关系。第二个参数
        // className很明显表示标签对应的java实体类，<Service>标签对应的实体实际上就是
        // StandardService，其实该参数是一个可选参数，可以传null，用第三个参数attributeName在运行
        // 时指定该标签对应的类，以图中举例就是说<Service>标签可以存在一个属性，属性名为className，
        // <Service className = ""> 这么配置
        // 当第二个参数没有指定时，Digester会自动解析该属性，并通过反射生成该类的实例再压入Digester
        // 内部的栈顶。

        digester.addObjectCreate("Server/Service",
                                 "org.apache.catalina.core.StandardService",
                                 "className");
        // addSetProperties(String pattern)底层使用的规则为SetPropertiesRule，方法唯一的参数也
        // 是pattern，同样表示遇到何种标签才进行解析，SetPropertiesRule规则用于解析标签对应的属性。
        // <Service name = "catalina">
        // 其属性只有name一个,那我们猜想在StandardService中可能存在一个该属性对应的set方法，
        // 看下StandardService的代码发现确实如此

        // 这里有一个小坑需要说明一下，实际上标签对应的实体类并不一定存在标签属性对应的set方法，
        // 并且也不是存在对应属性的set方法就会调用，理解这个细节我们需要进入到SetPropertiesRule类
        // 的begin()方法中

        digester.addSetProperties("Server/Service");

        // 解析规则addSetNext(String pattern, String methodName, String paramType)，底层
        // 使用的规则为SetNextRule，方法的第一个参数指明了触发该规则的具体模式，第二个参数表明调
        // 用父标签对应实体的方法名称，第三个参数就是方法参数的类型。在这里因为当前栈顶元素为StandardService，
        // addSetNext会调用StandardServer的addService(Service service)方法，将当前StandardService
        // 与其父元素StandardServer建立关联
        digester.addSetNext("Server/Service",
                            "addService",
                            "org.apache.catalina.Service");

        // 将调用org.apache.catalina.core.StandardServer类的addLifecycleListener方法，
        // 将根据server.xml中配置的Server节点下的Listener节点所定义的className属性构造对
        // 象实例，并作为addLifecycleListener方法的入参。所有的监听器都会实现上面提到的
        // org.apache.catalina.LifecycleListener接口。Server节点下的Listener节点有好几个，
        // 这里以org.apache.catalina.core.JasperListener举例。
        digester.addObjectCreate("Server/Service/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Listener");
        digester.addSetNext("Server/Service/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        //Executor
        digester.addObjectCreate("Server/Service/Executor",
                         "org.apache.catalina.core.StandardThreadExecutor",
                         "className");
        digester.addSetProperties("Server/Service/Executor");

        digester.addSetNext("Server/Service/Executor",
                            "addExecutor",
                            "org.apache.catalina.Executor");

        // server.xml中的connector节点时是这么处理的：
        // 创建<Connector>对象的规则和<Service>规则不太一样，并没有使用Digester內建的ObjectCreateRule，
        // 而是自己继承Rule创建了ConnectorCreateRule，解析到对应标签的开始处会调用规则类的begin()
        digester.addRule("Server/Service/Connector", new ConnectorCreateRule());
        // <Connector>在处理属性是也添加了名为SetAllPropertiesRule的规则，该规则接收了一个排除属性的数组，
        // 其中仅包含executor属性
        digester.addRule("Server/Service/Connector",
                new SetAllPropertiesRule(new String[]{"executor", "sslImplementationName", "protocol"}));
        //会设置调用的方法===》参数里面有文章
        digester.addSetNext("Server/Service/Connector",
                            "addConnector",
                            "org.apache.catalina.connector.Connector");

        digester.addRule("Server/Service/Connector", new AddPortOffsetRule());

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig",
                                 "org.apache.tomcat.util.net.SSLHostConfig");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig",
                "addSslHostConfig",
                "org.apache.tomcat.util.net.SSLHostConfig");

        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new CertificateCreateRule());
        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new SetAllPropertiesRule(new String[]{"type"}));
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/Certificate",
                            "addCertificate",
                            "org.apache.tomcat.util.net.SSLHostConfigCertificate");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConf");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                            "setOpenSslConf",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConf");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                            "addCmd",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");

        digester.addObjectCreate("Server/Service/Connector/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Connector/Listener");
        digester.addSetNext("Server/Service/Connector/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service/Connector/UpgradeProtocol",
                                  null, // MUST be specified in the element
                                  "className");
        digester.addSetProperties("Server/Service/Connector/UpgradeProtocol");
        digester.addSetNext("Server/Service/Connector/UpgradeProtocol",
                            "addUpgradeProtocol",
                            "org.apache.coyote.UpgradeProtocol");

        // Add RuleSets for nested elements
        digester.addRuleSet(new NamingRuleSet("Server/GlobalNamingResources/"));
        // 这里和上面有什么区别digester.addRule(),这里面有故事的
        digester.addRuleSet(new EngineRuleSet("Server/Service/"));
        //一个<Host>表示一个虚拟主机，其下可以存在多个<Context>标签，<Host>标签对应的规则定义如下
        digester.addRuleSet(new HostRuleSet("Server/Service/Engine/"));
        // 一个<Context>可以认为对应一个webapps下的目录，或者一个war包。代表虚拟主机的<Host>下可以存
        // 在多个<Context>标签，<Context>对应的解析规则也是继承RuleSetBase创建了自己的规则集合ContextRuleSet
        digester.addRuleSet(new ContextRuleSet("Server/Service/Engine/Host/"));
        addClusterRuleSet(digester, "Server/Service/Engine/Host/Cluster/");
        digester.addRuleSet(new NamingRuleSet("Server/Service/Engine/Host/Context/"));

        // When the 'engine' is found, set the parentClassLoader.
        digester.addRule("Server/Service/Engine",
                         new SetParentClassLoaderRule(parentClassLoader));
        addClusterRuleSet(digester, "Server/Service/Engine/Cluster/");

        long t2=System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Digester for server.xml created " + ( t2-t1 ));
        }
        return digester;

    }

    /**
     * Cluster support is optional. The JARs may have been removed.
     */
    private void addClusterRuleSet(Digester digester, String prefix) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName("org.apache.catalina.ha.ClusterRuleSet");
            constructor = clazz.getConstructor(String.class);
            RuleSet ruleSet = (RuleSet) constructor.newInstance(prefix);
            digester.addRuleSet(ruleSet);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()));
            }
        }
    }

    /**
     * Create and configure the Digester we will be using for shutdown.
     * @return the digester to process the stop operation
     */
    protected Digester createStopDigester() {

        // Initialize the digester
        Digester digester = new Digester();
        digester.setUseContextClassLoader(true);

        // Configure the rules we need for shutting down
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        return digester;

    }


    public void stopServer() {
        stopServer(null);
    }


    //关闭Tomcat通过执行shutdown.bat或shutdown.sh脚本最终会调用到这里
    public void stopServer(String[] arguments) {

        if (arguments != null) {
            arguments(arguments);
        }

        Server s = getServer();
        if (s == null) {
            // Create and execute our Digester
            //读取配置文件
            Digester digester = createStopDigester();
            File file = configFile();
            try (FileInputStream fis = new FileInputStream(file)) {
                InputSource is =
                    new InputSource(file.toURI().toURL().toString());
                is.setByteStream(fis);
                digester.push(this);
                digester.parse(is);
            } catch (Exception e) {
                log.error(sm.getString("catalina.stopError"), e);
                System.exit(1);
            }
        } else {
            // Server object already present. Must be running as a service
            try {
                s.stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("catalina.stopError"), e);
            }
            return;
        }

        // Stop the existing server
        s = getServer();
        if (s.getPortWithOffset() > 0) {
            // 即向localhost:8005发起一个Socket连接，并写入SHUTDOWN字符串。
            // 这样将会关闭Tomcat中的那唯一的一个用户线程，接着所有守护线程将会退出（由JVM保证），
            // 之后整个应用关闭。
            try (Socket socket = new Socket(s.getAddress(), s.getPortWithOffset());
                    OutputStream stream = socket.getOutputStream()) {
                String shutdown = s.getShutdown();
                for (int i = 0; i < shutdown.length(); i++) {
                    stream.write(shutdown.charAt(i));
                }
                stream.flush();
            } catch (ConnectException ce) {
                log.error(sm.getString("catalina.stopServer.connectException", s.getAddress(),
                        String.valueOf(s.getPortWithOffset()), String.valueOf(s.getPort()),
                        String.valueOf(s.getPortOffset())));
                log.error(sm.getString("catalina.stopError"), ce);
                System.exit(1);
            } catch (IOException e) {
                log.error(sm.getString("catalina.stopError"), e);
                System.exit(1);
            }
        } else {
            log.error(sm.getString("catalina.stopServer"));
            System.exit(1);
        }
    }

    //======================================
    // ---------------入口-------------------
    //======================================
    /**
     * Start a new server instance.
     *
     * load阶段主要是通过读取conf/server.xml或者server-embed.xml，实例化
     * Server、Service、Connector、Engine、Host等组件，并调用Lifecycle#init()
     * 完成初始化动作，以及发出INITIALIZING、INITIALIZED事件
     */

    /**
     *
     Digester---->利用jdk提供的sax解析功能，将server.xml的配置解析成对应的Bean，并完成注入，比如往Server中注入Service
     EngineConfig---->它是一个LifecycleListener实现，用于配置Engine，但是只会处理START_EVENT和STOP_EVENT事件
     Connector---->默认会有两种：HTTP/1.1、AJP，不同的Connector内部持有不同的CoyoteAdapter和ProtocolHandler，在Connector
                    初始化的时候，也会对ProtocolHandler进行初始化，完成端口的监听
     ProtocolHandler---->常用的实现有Http11NioProtocol、AjpNioProtocol，还有apr系列的Http11AprProtocol、AjpAprProtocol，
                        apr系列只有在使用apr包的时候才会使用到
     在ProtocolHandler------>调用init初始化的时候，还会去执行AbstractEndpoint的init方法，完成请求端口绑定、初始化NIO等操作，
                    在tomcat7中使用JIoEndpoint阻塞IO，而tomcat8中直接移除了JIoEndpoint，具体信息请查看
                    org.apache.tomcat.util.net这个包
     */
    public void load() {

        if (loaded) {
            return;
        }
        loaded = true;

        long t1 = System.nanoTime();

        initDirs();

        // Before digester - it may be needed
        //初始化jmx的环境变量
        initNaming();

        // Set configuration source
        // 定义解析server.xml的配置，告诉Digester哪个xml标签应该解析成什么类
        ConfigFileLoader.setSource(new CatalinaBaseConfigurationSource(Bootstrap.getCatalinaBaseFile(), getConfigFile()));
        File file = configFile();

        // Create and execute our Digester
        // 一是创建一个Digester对象，将当前对象压入Digester里的对象栈顶，
        // 根据inputSource里设置的文件xml路径及所创建的Digester对象所
        // 包含的解析规则生成相应对象，并调用相应方法将对象之间关联起来

        Digester digester = createStartDigester();

        // 首先尝试加载conf/server.xml，省略部分代码......
        // 如果不存在conf/server.xml，则加载server-embed.xml(该xml在catalina.jar中)，省略部分代码......
        // 如果还是加载不到xml，则直接return，省略部分代码......
        try (ConfigurationSource.Resource resource = ConfigFileLoader.getSource().getServerXml()) {
            InputStream inputStream = resource.getInputStream();
            InputSource inputSource = new InputSource(resource.getURI().toURL().toString());


            inputSource.setByteStream(inputStream);
            // 把Catalina作为一个顶级实例
            // (2)Digester做了一个类似压栈的操作，将当前的Catalina对象压入Catalina类中的
            // ArrayStack<Object> stack中，根据栈先进后出的特性可知该Catalina对象必定会
            // 最后一个弹栈，而栈中存放的其他对象实际上就是上面对应标签的java类实例，举个例
            // 子，如果server.xml中标签的结构为
            // <Server>
            //      <Service>
            //      </Service>
            // </Server>

            // 那么最后栈中的结构必然是先入栈Catalina实例，然后是<Server>标签对应类的实例，栈
            // 顶的是<Service>标签的实例。为什么要用这种设计思路存放标签对应的类实例，我理解可
            // 以想一想SAX方式解析xml文件的特点，SAX对xml文件边扫描边解析，自顶向下依次解析，
            // 可以看成是深度优先遍历的一种变体，该特性在数据结构的层面上正好用栈完美诠释，这里
            // 又为什么要将“自己”压入栈底，答案随着分析的深入自会揭晓，现在只需要记住
            digester.push(this);
            // 解析过程会实例化各个组件，比如Server、Container、Connector等

            //这样经过对xml文件的解析将会产生
            // org.apache.catalina.core.StandardServer、
            // org.apache.catalina.core.StandardService、
            // org.apache.catalina.connector.Connector、
            // org.apache.catalina.core.StandardEngine、
            // org.apache.catalina.core.StandardHost、
            // org.apache.catalina.core.StandardContext等等一系列对象，
            // 这些对象从前到后前一个包含后一个对象的引用（一对一或一对多的关系）。
            // (3)

            // Digester作为SAX的解析类，当解析到Docuemt开始会调用startDocument()方法，
            // 解析到Element开始会调用startElement()方法
            digester.parse(inputSource);
        } catch (Exception e) {
            if  (file == null) {
                log.warn(sm.getString("catalina.configFail", getConfigFile() + "] or [server-embed.xml"), e);
            } else {
                log.warn(sm.getString("catalina.configFail", file.getAbsolutePath()), e);
                if (file.exists() && !file.canRead()) {
                    log.warn(sm.getString("catalina.incorrectPermissions"));
                }
            }
            return;
        }

        // 给Server设置catalina信息
        getServer().setCatalina(this);
        getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
        getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

        // Stream redirection
        initStreams();

        // Start the new server
        try {
            // 调用Lifecycle的init阶段
            // 二是调用Server接口对象的init方法
            // (4)代码主要进行各个容器的初始化工作,但是有一个问题，就是这里的getServer()方法返回了Catalina类中的
            // protected Server server = null;，这个Server实际上就是上面创建的<Server>标签对应的实例StandardServer，
            // 问题是Tomcat是何时将这个初始值为null的Server赋值的呢？

            // 在Catalina类中确实存在setServer(Server)方法，但查询其调用链时发现该方法并没有被直接调用过，那这个Server
            // 是如何被赋值的呢？我们要重新看看在解析<Server>标签时Rule起了什么作用，ObjectCreateRule主要生成标签对应的
            // 类的实例，并将其压栈；SetPropertiesRule主要用于标签参数的解析；SetNextRule处理父子标签对应类方法的调用，
            // 建立标签实体之间的关联

            getServer().init();
        } catch (LifecycleException e) {
            if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE")) {
                throw new java.lang.Error(e);
            } else {
                log.error(sm.getString("catalina.initError"), e);
            }
        }

        long t2 = System.nanoTime();
        if(log.isInfoEnabled()) {
            log.info(sm.getString("catalina.init", Long.valueOf((t2 - t1) / 1000000)));
        }
    }


    /*
     * Load using arguments
     */
    public void load(String args[]) {

        try {
            if (arguments(args)) {
                load();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    /**
     * Start a new server instance.
     *  经过以上分析发现，在解析xml产生相应一系列对象后会顺序调用StandardServer对象的init、start方法，
     *  这里将会涉及到Tomcat的容器生命周期（Lifecycle）
     *  ----------------------------------------------
     * 主要分为以下三个步骤，其核心逻辑在于Server组件：
     * 1. 调用Server的start方法，启动Server组件
     * 2. 注册jvm关闭的勾子程序，用于安全地关闭Server组件，以及其它组件
     * 3. 开启shutdown端口的监听并阻塞，用于监听关闭指令
     */
    public void start() {

        if (getServer() == null) {
            load();
        }

        if (getServer() == null) {
            log.fatal(sm.getString("catalina.noServer"));
            return;
        }

        long t1 = System.nanoTime();

        // Start the new server
        try {
            getServer().start();
        } catch (LifecycleException e) {
            log.fatal(sm.getString("catalina.serverStartFail"), e);
            try {
                getServer().destroy();
            } catch (LifecycleException e1) {
                log.debug("destroy() failed for failed Server ", e1);
            }
            return;
        }

        long t2 = System.nanoTime();
        if(log.isInfoEnabled()) {
            log.info(sm.getString("catalina.startup", Long.valueOf((t2 - t1) / 1000000)));
        }

        // Register shutdown hook
        //// 注册勾子，用于安全关闭tomcat
        if (useShutdownHook) {
            if (shutdownHook == null) {
                shutdownHook = new CatalinaShutdownHook();
            }
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // If JULI is being used, disable JULI's shutdown hook since
            // shutdown hooks run in parallel and log messages may be lost
            // if JULI's hook completes before the CatalinaShutdownHook()
            LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof ClassLoaderLogManager) {
                ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                        false);
            }
        }
        //Bootstrap中会设置await为true，其目的在于让tomcat在shutdown端口阻塞监听关闭命令

        //BootStrap调用start()方法的时候已经将Catalina类的实例变量await设值为true，所以这里将会执行Catalina类的await方法
        if (await) {
            //如果没有shutdown命令方法会一直循环
            await();
            //如果到了一步，说明有停止的命令
            stop();
        }
    }


    /**
     * Stop an existing server instance.
     */
    public void stop() {

        try {
            // Remove the ShutdownHook first so that server.stop()
            // doesn't get invoked twice
            if (useShutdownHook) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);

                // If JULI is being used, re-enable JULI's shutdown to ensure
                // log messages are not lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                            true);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This will fail on JDK 1.2. Ignoring, as Tomcat can run
            // fine without the shutdown hook.
        }

        // Shut down the server
        try {
            Server s = getServer();
            LifecycleState state = s.getState();
            if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0
                    && LifecycleState.DESTROYED.compareTo(state) >= 0) {
                // Nothing to do. stop() was already called
            } else {
                s.stop();
                s.destroy();
            }
        } catch (LifecycleException e) {
            log.error(sm.getString("catalina.stopError"), e);
        }

    }


    /**
     * Await and shutdown.
     */
    public void await() {
        //调用org.apache.catalina.core.StandardServer类的await方法
        getServer().await();

    }


    /**
     * Print usage information for this application.
     */
    protected void usage() {

        System.out.println(sm.getString("catalina.usage"));

    }


    protected void initDirs() {
        String temp = System.getProperty("java.io.tmpdir");
        if (temp == null || (!(new File(temp)).isDirectory())) {
            log.error(sm.getString("embedded.notmp", temp));
        }
    }


    protected void initStreams() {
        // Replace System.out and System.err with a custom PrintStream
        System.setOut(new SystemLogHandler(System.out));
        System.setErr(new SystemLogHandler(System.err));
    }


    protected void initNaming() {
        // Setting additional variables
        if (!useNaming) {
            log.info(sm.getString("catalina.noNatming"));
            System.setProperty("catalina.useNaming", "false");
        } else {
            System.setProperty("catalina.useNaming", "true");
            String value = "org.apache.naming";
            String oldValue =
                System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
            if (oldValue != null) {
                value = value + ":" + oldValue;
            }
            System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
            if( log.isDebugEnabled() ) {
                log.debug("Setting naming prefix=" + value);
            }
            value = System.getProperty
                (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
            if (value == null) {
                System.setProperty
                    (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                     "org.apache.naming.java.javaURLContextFactory");
            } else {
                log.debug("INITIAL_CONTEXT_FACTORY already set " + value );
            }
        }
    }


    /**
     * Set the security package access/protection.
     */
    protected void setSecurityProtection(){
        SecurityConfig securityConfig = SecurityConfig.newInstance();
        securityConfig.setPackageDefinition();
        securityConfig.setPackageAccess();
    }


    // --------------------------------------- CatalinaShutdownHook Inner Class

    // XXX Should be moved to embedded !
    /**
     * Shutdown hook which will perform a clean shutdown of Catalina if needed.
     */
    protected class CatalinaShutdownHook extends Thread {

        @Override
        public void run() {
            try {
                if (getServer() != null) {
                    Catalina.this.stop();
                }
            } catch (Throwable ex) {
                ExceptionUtils.handleThrowable(ex);
                log.error(sm.getString("catalina.shutdownHookFail"), ex);
            } finally {
                // If JULI is used, shut JULI down *after* the server shuts down
                // so log messages aren't lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).shutdown();
                }
            }
        }
    }


    private static final Log log = LogFactory.getLog(Catalina.class);

}


// ------------------------------------------------------------ Private Classes


/**
 * Rule that sets the parent class loader for the top object on the stack,
 * which must be a <code>Container</code>.
 */

final class SetParentClassLoaderRule extends Rule {

    public SetParentClassLoaderRule(ClassLoader parentClassLoader) {

        this.parentClassLoader = parentClassLoader;

    }

    ClassLoader parentClassLoader = null;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug("Setting parent class loader");
        }

        Container top = (Container) digester.peek();
        top.setParentClassLoader(parentClassLoader);

    }


}
