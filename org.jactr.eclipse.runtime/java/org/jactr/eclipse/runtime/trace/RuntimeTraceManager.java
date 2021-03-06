package org.jactr.eclipse.runtime.trace;

/*
 * default logging
 */
import java.util.Collection;
import java.util.concurrent.Executor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.jactr.eclipse.runtime.session.ISession;
import org.jactr.eclipse.runtime.trace.impl.GeneralEventManager;
import org.jactr.tools.tracer.transformer.ITransformedEvent;

public class RuntimeTraceManager
{

  private GeneralEventManager<IRuntimeTraceListener, Event> _eventManager;

  public RuntimeTraceManager()
  {
    _eventManager = new GeneralEventManager<IRuntimeTraceListener, Event>(
        new GeneralEventManager.INotifier<IRuntimeTraceListener, Event>() {

          public void notify(IRuntimeTraceListener listener, Event event)
          {
            if (listener.isInterestedIn(event.event, event.session))
              listener.eventFired(event.event, event.session);
          }
        });
  }

  public void clear()
  {
    _eventManager.clear();
  }

  public void addListener(IRuntimeTraceListener listener)
  {
    addListener(listener, null);
  }

  public void addListener(IRuntimeTraceListener listener, Executor executor)
  {
    _eventManager.addListener(listener, executor);
  }

  public void removeListener(final IRuntimeTraceListener listener)
  {
    _eventManager.removeListener(listener);
  }

  public void fireEvent(ITransformedEvent event, ISession session)
  {
    _eventManager.notify(new Event(event, session));
  }

  public void fireEvents(Collection<ITransformedEvent> events, ISession session)
  {
    for (ITransformedEvent event : events)
      _eventManager.notify(new Event(event, session));
  }

  public void fireEvents(IProgressMonitor monitor,
      Collection<ITransformedEvent> events, ISession session)
  {
    try
    {
      for (ITransformedEvent event : events)
      {
        if (monitor.isCanceled()) return;

        _eventManager.notify(new Event(event, session));
        monitor.worked(1);
      }
    }
    finally
    {
      // monitor.done();
    }

  }

  private class Event
  {
    final public ITransformedEvent event;

    final public ISession          session;

    public Event(ITransformedEvent event, ISession session)
    {
      this.event = event;
      this.session = session;
    }
  }
}
