package org.hibernate.rx.impl;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.hibernate.FlushMode;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.ExceptionConverter;
import org.hibernate.engine.spi.SessionDelegatorBaseImpl;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.transaction.spi.TransactionImplementor;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.internal.ExceptionMapperStandardImpl;
import org.hibernate.resource.transaction.backend.jta.internal.synchronization.ExceptionMapper;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.rx.RxHibernateSession;
import org.hibernate.rx.RxSession;
import org.hibernate.rx.engine.spi.RxActionQueue;
import org.hibernate.rx.engine.spi.RxHibernateSessionFactoryImplementor;
import org.hibernate.rx.event.spi.RxFlushEventListener;
import org.hibernate.rx.util.RxUtil;

public class RxHibernateSessionImpl extends SessionDelegatorBaseImpl implements RxHibernateSession, EventSource {

	private final RxHibernateSessionFactoryImplementor factory;
	private final ExceptionConverter exceptionConverter;
	private final ExceptionMapper exceptionMapper = ExceptionMapperStandardImpl.INSTANCE;
	private transient RxActionQueue rxActionQueue;


	public RxHibernateSessionImpl(RxHibernateSessionFactoryImplementor factory, SessionImplementor delegate) {
		super( delegate );
		this.factory = factory;
		this.exceptionConverter = delegate.getExceptionConverter();
		this.rxActionQueue = new RxActionQueue( this );
	}

	@Override
	public TransactionCoordinator getTransactionCoordinator() {
		return super.getTransactionCoordinator();
	}

	@Override
	public RxHibernateSessionFactoryImplementor getSessionFactory() {
		return factory;
	}

	@Override
	public RxActionQueue getRxActionQueue() {
		return rxActionQueue;
	}

	@Override
	public SessionImplementor getSession() {
		return this;
	}

//	private void firePersist(PersistEvent event) {
//		try {
////			checkTransactionSynchStatus();
////			checkNoUnresolvedActionsBeforeOperation();
//
//			for ( PersistEventListener listener : listeners( EventType.PERSIST ) ) {
//				listener.rxOnPersist( event );
//			}
//		}
//		catch (MappingException e) {
//			throw exceptionConverter.convert( new IllegalArgumentException( e.getMessage() ) );
//		}
//		catch (RuntimeException e) {
//			throw exceptionConverter.convert( e );
//		}
//		finally {
////			try {
////				checkNoUnresolvedActionsAfterOperation();
////			}
////			catch (RuntimeException e) {
////				throw exceptionConverter.convert( e );
////			}
//		}
//	}

	@Override
	public void flush() {
		checkOpen();
		doFlush();
	}

	@Override
	public CompletionStage<Void> rxFlush() {
	    checkOpen();
	    return doFlush();
	}

	private CompletionStage<Void> doFlush() {
//		checkTransactionNeeded();
//		checkTransactionSynchStatus();

//			if ( persistenceContext.getCascadeLevel() > 0 ) {
//				throw new HibernateException( "Flush during cascade is dangerous" );
//			}

		    CompletionStage<Void> ret = RxUtil.nullFuture();
			FlushEvent flushEvent = new FlushEvent( this );
			for ( FlushEventListener listener : listeners( EventType.FLUSH ) ) {
				ret = ret.thenCompose(v -> ((RxFlushEventListener)listener).rxOnFlush( flushEvent ));
			}

//			delayedAfterCompletion();
			return ret.exceptionally(x -> {
			    if(x instanceof RuntimeException)
			        throw exceptionConverter.convert( (RuntimeException)x );
			    else
			        return RxUtil.rethrow(x);
			});
	}

	private <T> Iterable<T> listeners(EventType<T> type) {
		return eventListenerGroup( type ).listeners();
	}

	private <T> EventListenerGroup<T> eventListenerGroup(EventType<T> type) {
		return getFactory().getServiceRegistry().getService( EventListenerRegistry.class ).getEventListenerGroup( type );
	}

	private void managedFlush() {
		if ( isClosed() ) {// && !waitingForAutoClose ) {
//			log.trace( "Skipping auto-flush due to session closed" );
			return;
		}
//		log.trace( "Automatically flushing session" );
		doFlush();
	}

	@Override
	public void flushBeforeTransactionCompletion() {
		final boolean doFlush = isTransactionFlushable()
				&& getHibernateFlushMode() != FlushMode.MANUAL;

		try {
			if ( doFlush ) {
				managedFlush();
			}
		}
		catch (RuntimeException re) {
			throw exceptionMapper.mapManagedFlushFailure( "error during managed flush", re, this );
		}
	}

	private boolean isTransactionFlushable() {
		if ( getCurrentTransaction() == null ) {
			// assume it is flushable - CMT, auto-commit, etc
			return true;
		}
		final TransactionStatus status = getCurrentTransaction().getStatus();
		return status == TransactionStatus.ACTIVE || status == TransactionStatus.COMMITTING;
	}

	// SessionImpl
	protected TransactionImplementor getCurrentTransaction() {
		return (TransactionImplementor) getTransaction();
	}

	@Override
	public RxSession reactive() {
		return new RxSessionImpl( factory, this );
	}

	@Override
	public void reactive(Consumer<RxSession> consumer) {
		consumer.accept( new RxSessionImpl( factory, this ) );
	}

	@Override
	public <T> T unwrap(Class<T> clazz) {
		if ( RxHibernateSession.class.isAssignableFrom( clazz ) ) {
			return (T) this;
		}
		return super.unwrap( clazz );
	}
}
