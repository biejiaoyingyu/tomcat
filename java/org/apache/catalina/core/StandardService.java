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
package org.apache.catalina.core;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;
import org.apache.catalina.mapper.MapperListener;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Standard implementation of the <code>Service</code> interface.  The
 * associated Container is generally an instance of Engine, but this is
 * not required.
 *
 * @author Craig R. McClanahan
 */

public class StandardService extends LifecycleMBeanBase implements Service {

    private static final Log log = LogFactory.getLog(StandardService.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * The name of this service.
     */
    private String name = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The <code>Server</code> that owns this Service, if any.
     */
    private Server server = null;

    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * The set of Connectors associated with this Service.
     */
    protected Connector connectors[] = new Connector[0];
    private final Object connectorsLock = new Object();

    /**
     * The list of executors held by the service.
     */
    protected final ArrayList<Executor> executors = new ArrayList<>();

    private Engine engine = null;

    private ClassLoader parentClassLoader = null;

    /**
     * Mapper.
     */
    protected final Mapper mapper = new Mapper();


    /**
     * Mapper listener.
     */
    protected final MapperListener mapperListener = new MapperListener(this);


    // ------------------------------------------------------------- Properties

    @Override
    public Mapper getMapper() {
        return mapper;
    }


    @Override
    public Engine getContainer() {
        return engine;
    }


    @Override
    public void setContainer(Engine engine) {
        Engine oldEngine = this.engine;
        if (oldEngine != null) {
            oldEngine.setService(null);
        }
        this.engine = engine;
        if (this.engine != null) {
            this.engine.setService(this);
        }
        if (getState().isAvailable()) {
            if (this.engine != null) {
                try {
                    this.engine.start();
                } catch (LifecycleException e) {
                    log.warn(sm.getString("standardService.engine.startFailed"), e);
                }
            }
            // Restart MapperListener to pick up new engine.
            try {
                mapperListener.stop();
            } catch (LifecycleException e) {
                log.warn(sm.getString("standardService.mapperListener.stopFailed"), e);
            }
            try {
                mapperListener.start();
            } catch (LifecycleException e) {
                log.warn(sm.getString("standardService.mapperListener.startFailed"), e);
            }
            if (oldEngine != null) {
                try {
                    oldEngine.stop();
                } catch (LifecycleException e) {
                    log.warn(sm.getString("standardService.engine.stopFailed"), e);
                }
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("container", oldEngine, this.engine);
    }


    /**
     * Return the name of this Service.
     */
    @Override
    public String getName() {
        return name;
    }


    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }


    /**
     * Return the <code>Server</code> with which we are associated (if any).
     */
    @Override
    public Server getServer() {
        return this.server;
    }


    /**
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    @Override
    public void setServer(Server server) {
        this.server = server;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     *
     * @param connector The Connector to be added
     */
    @Override
    public void addConnector(Connector connector) {

        synchronized (connectorsLock) {
            connector.setService(this);
            Connector results[] = new Connector[connectors.length + 1];
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            results[connectors.length] = connector;
            connectors = results;

            if (getState().isAvailable()) {
                try {
                    connector.start();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("connector", null, connector);
        }

    }


    public ObjectName[] getConnectorNames() {
        ObjectName results[] = new ObjectName[connectors.length];
        for (int i=0; i<results.length; i++) {
            results[i] = connectors[i].getObjectName();
        }
        return results;
    }


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * Find and return the set of Connectors associated with this Service.
     */
    @Override
    public Connector[] findConnectors() {
        return connectors;
    }


    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * @param connector The Connector to be removed
     */
    @Override
    public void removeConnector(Connector connector) {

        synchronized (connectorsLock) {
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            if (connectors[j].getState().isAvailable()) {
                try {
                    connectors[j].stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connectors[j]), e);
                }
            }
            connector.setService(null);
            int k = 0;
            Connector results[] = new Connector[connectors.length - 1];
            for (int i = 0; i < connectors.length; i++) {
                if (i != j)
                    results[k++] = connectors[i];
            }
            connectors = results;

            // Report this property change to interested listeners
            support.firePropertyChange("connector", connector, null);
        }
    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StandardService[");
        sb.append(getName());
        sb.append("]");
        return sb.toString();
    }


    /**
     * Adds a named executor to the service
     * @param ex Executor
     */
    @Override
    public void addExecutor(Executor ex) {
        synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);
                if (getState().isAvailable()) {
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        log.error(sm.getString("standardService.executor.start"), x);
                    }
                }
            }
        }
    }


    /**
     * Retrieves all executors
     * @return Executor[]
     */
    @Override
    public Executor[] findExecutors() {
        synchronized (executors) {
            Executor[] arr = new Executor[executors.size()];
            executors.toArray(arr);
            return arr;
        }
    }


    /**
     * Retrieves executor by name, null if not found
     * @param executorName String
     * @return Executor
     */
    @Override
    public Executor getExecutor(String executorName) {
        synchronized (executors) {
            for (Executor executor: executors) {
                if (executorName.equals(executor.getName()))
                    return executor;
            }
        }
        return null;
    }


    /**
     * Removes an executor from the service
     * @param ex Executor
     */
    @Override
    public void removeExecutor(Executor ex) {
        synchronized (executors) {
            if ( executors.remove(ex) && getState().isAvailable() ) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardService.executor.stop"), e);
                }
            }
        }
    }


    /**
     * Start nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.start.name", this.name));
        setState(LifecycleState.STARTING);

        // Start our defined Container first
        // 启动Engine，Engine的child容器都会被启动，webapp的部署会在这个步骤完成；
        /**
         * 1.StandardEngine、StandardHost、StandardContext、StandardWrapper各个容器存在父子关系，
         * 一个父容器包含多个子容器，并且一个子容器对应一个父容器。Engine是顶层父容器，它不存在父容器，
         * 关于各个组件的详细介绍，请参考《tomcat框架设计》。默认情况下，StandardEngine只有一个子容
         * 器StandardHost，一个StandardContext对应一个webapp应用，而一个StandardWrapper对应一个
         * webapp里面的一个 Servlet
         *
         * 2.StandardEngine、StandardHost、StandardContext、StandardWrapper都是继承至ContainerBase，
         * 各个容器的启动，都是由父容器调用子容器的start方法，也就是说由StandardEngine启动StandardHost，
         * 再StandardHost启动StandardContext，以此类推。
         *
         * 3.由于它们都是继续至ContainerBase，当调用 start 启动Container容器时，首先会执行 ContainerBase
         * 的 start 方法，它会寻找子容器，并且在线程池中启动子容器，StandardEngine也不例外。
         */
        if (engine != null) {
            synchronized (engine) {
                engine.start();
            }
        }
        //启动Executor，这是tomcat用Lifecycle封装的线程池，继承至
        // java.util.concurrent.Executor以及tomcat的Lifecycle接口
        synchronized (executors) {
            for (Executor executor: executors) {
                executor.start();
            }
        }

        // 启动MapperListener
        mapperListener.start();

        // Start our defined Connectors second
        //启动Connector组件，由Connector完成Endpoint的启动，这个时候意味着tomcat可以对外提供请求服务了
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                // If it has already failed, don't try and start it
                if (connector.getState() != LifecycleState.FAILED) {
                    connector.start();
                }
            }
        }
    }


    /**
     * Stop nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        // Pause connectors first
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                connector.pause();
                // Close server socket if bound on start
                // Note: test is in AbstractEndpoint
                connector.getProtocolHandler().closeServerSocketGraceful();
            }
        }

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.stop.name", this.name));
        setState(LifecycleState.STOPPING);

        // Stop our defined Container second
        if (engine != null) {
            synchronized (engine) {
                engine.stop();
            }
        }

        // Now stop the connectors
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                if (!LifecycleState.STARTED.equals(
                        connector.getState())) {
                    // Connectors only need stopping if they are currently
                    // started. They may have failed to start or may have been
                    // stopped (e.g. via a JMX call)
                    continue;
                }
                connector.stop();
            }
        }

        // If the Server failed to start, the mapperListener won't have been
        // started
        if (mapperListener.getState() != LifecycleState.INITIALIZED) {
            mapperListener.stop();
        }

        synchronized (executors) {
            for (Executor executor: executors) {
                executor.stop();
            }
        }

    }


    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     *
     * server.xml中的标签和java类并不是完全一一对应的关系，这里的Container实际上就是将<Engine>、<Host>
     * 等标签对应类的共性抽取的父接口。因此，StandardService类的初始化过程依然是将初始化的过程“下放”给子容
     * 器初始化的过程。这就引出了第二个设计模式：责任链设计模式，在这种模式中，通常每个接收者都包含对另一个接
     * 收者的引用。如果一个对象不能处理该请求，那么它会把相同的请求传给下一个接收者，依此类推。至此，实际上组
     * 件的初始化流程就分析完了，因为后面组件的初始化也会像上面分析的一样“父传子，子传孙”
     */
    @Override
    protected void initInternal() throws LifecycleException {
        //往jmx中注册自己
        super.initInternal();
        //// 初始化Engine
        if (engine != null) {
            //进入
            engine.init();
        }

        // Initialize any Executors
        // 存在Executor线程池，则进行初始化，默认是没有的
        for (Executor executor : findExecutors()) {
            if (executor instanceof JmxEnabled) {
                ((JmxEnabled) executor).setDomain(getDomain());
            }
            executor.init();
        }

        // Initialize mapper listener
        // 暂时不知道这个MapperListener的作用
        mapperListener.init();

        // Initialize our defined Connectors
        // 初始化Connector，而Connector又会对ProtocolHandler进行初始化，开启应用端口的监听
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                connector.init();
            }
        }
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        mapperListener.destroy();

        // Destroy our defined Connectors
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                connector.destroy();
            }
        }

        // Destroy any Executors
        for (Executor executor : findExecutors()) {
            executor.destroy();
        }

        if (engine != null) {
            engine.destroy();
        }

        super.destroyInternal();
    }


    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (server != null) {
            return server.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }


    /**
     * Set the parent class loader for this server.
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);
    }


    @Override
    protected String getDomainInternal() {
        String domain = null;
        Container engine = getContainer();

        // Use the engine name first
        if (engine != null) {
            domain = engine.getName();
        }

        // No engine or no engine name, use the service name
        if (domain == null) {
            domain = getName();
        }

        // No service name, return null which will trigger the use of the
        // default
        return domain;
    }


    @Override
    public final String getObjectNameKeyProperties() {
        return "type=Service";
    }

}
