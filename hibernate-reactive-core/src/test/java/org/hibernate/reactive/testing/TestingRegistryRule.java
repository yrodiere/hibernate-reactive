/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.testing;

import io.vertx.core.Vertx;
import org.hibernate.reactive.vertx.VertxInstance;
import org.hibernate.reactive.vertx.impl.ProvidedVertxInstance;
import org.hibernate.service.Service;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceBinding;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.rules.ExternalResource;

public class TestingRegistryRule extends ExternalResource {

    private Vertx vertx;
    private Registry registry;

    @Override
    protected void before() throws Throwable {
        this.vertx = Vertx.vertx();
        this.registry = new Registry( vertx );
    }

    @Override
    protected void after() {
        if (vertx != null) {
            vertx.close();
        }
        this.registry = null;
    }

    public ServiceRegistryImplementor getServiceRegistry() {
        return registry;
    }

    //TODO extract into its own class and evolve as necessary?
    private static class Registry implements ServiceRegistryImplementor {

        private final ProvidedVertxInstance vertxService;

        public Registry(Vertx vertx) {
            this.vertxService = new ProvidedVertxInstance( vertx );
        }

        @Override
        public <R extends Service> ServiceBinding<R> locateServiceBinding(Class<R> serviceRole) {
            return null;
        }

        @Override
        public void destroy() {

        }

        @Override
        public void registerChild(ServiceRegistryImplementor child) {

        }

        @Override
        public void deRegisterChild(ServiceRegistryImplementor child) {

        }

        @Override
        public ServiceRegistry getParentServiceRegistry() {
            return null;
        }

        @Override
        public <R extends Service> R getService(Class<R> serviceRole) {
            if ( serviceRole == VertxInstance.class )
                return (R) vertxService;
            else {
                throw new IllegalArgumentException( "This is a mock service - need to explicitly handle any service we might need during testing" );
            }
        }
    }

}
