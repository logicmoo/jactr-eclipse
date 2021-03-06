package org.jactr.eclipse.runtime.visual;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jactr.eclipse.runtime.launching.norm.ACTRSession;
import org.jactr.eclipse.runtime.session.ISession;
import org.jactr.eclipse.runtime.session.impl.Session2SessionAdapter;
import org.jactr.eclipse.runtime.trace.IRuntimeTraceListener;
import org.jactr.eclipse.runtime.trace.impl.GeneralEventManager;
import org.jactr.eclipse.runtime.trace.impl.RuntimeTraceDataManager;
import org.jactr.tools.tracer.transformer.ITransformedEvent;
import org.jactr.tools.tracer.transformer.visual.TransformedVisualEvent;

public class VisualTraceCenter extends
    RuntimeTraceDataManager<VisualDescriptor>
{
  static private final Log         LOGGER   = LogFactory
                                                .getLog(VisualTraceCenter.class);

  static private VisualTraceCenter _default = new VisualTraceCenter();

  static public VisualTraceCenter get()
  {
    return _default;
  }

  private final GeneralEventManager<IVisualTraceCenterListener, VisualTraceCenterEvent> _listenerList;

  private IRuntimeTraceListener                                                         _runtimeListener;

  protected VisualTraceCenter()
  {
    _listenerList = new GeneralEventManager<IVisualTraceCenterListener, VisualTraceCenterEvent>(
        new GeneralEventManager.INotifier<IVisualTraceCenterListener, VisualTraceCenterEvent>() {

          public void notify(IVisualTraceCenterListener listener,
              VisualTraceCenterEvent event)
          {
            if (event._isAdd)
              listener.modelAdded(event._modelName, event._descriptor);
            else
              listener.modelRemoved(event._modelName, event._descriptor);
          }
        });

    _runtimeListener = new IRuntimeTraceListener() {

      public void eventFired(ITransformedEvent traceEvent, ISession session)
      {
        // short term patch
        Session2SessionAdapter nsw = (Session2SessionAdapter) session;

        VisualTraceCenter.get().process(traceEvent,
            (ACTRSession) nsw.getOldSession());
      }

      public boolean isInterestedIn(ITransformedEvent traceEvent,
          ISession session)
      {
        return traceEvent instanceof TransformedVisualEvent;
      }

    };
  }

  public IRuntimeTraceListener getRuntimeListener()
  {
    return _runtimeListener;
  }

  public void add(IVisualTraceCenterListener listener)
  {
    _listenerList.addListener(listener);
  }

  public void remove(IVisualTraceCenterListener listener)
  {
    _listenerList.removeListener(listener);
  }

  @Override
  protected VisualDescriptor createRuntimeTraceData(ACTRSession session,
      String commonName,
      String modelName)
  {
    return new VisualDescriptor(commonName, modelName, session);
  }

  @Override
  protected void modelAdded(ACTRSession session, VisualDescriptor data,
      String modelName)
  {
    super.modelAdded(session, data, modelName);
    _listenerList.notify(new VisualTraceCenterEvent(modelName, data, true));
  }

  @Override
  protected void disposeRuntimeTraceData(ACTRSession session, String modelName,
      VisualDescriptor data)
  {
    // noop

  }

  @Override
  protected void modelRemoved(ACTRSession session, VisualDescriptor data,
      String modelName)
  {
    super.modelRemoved(session, data, modelName);
    _listenerList.notify(new VisualTraceCenterEvent(modelName, data, false));
  }

  @Override
  protected void process(ACTRSession session, String modelName,
      VisualDescriptor data, ITransformedEvent event)
  {
    try
    {
      data.process((TransformedVisualEvent) event);
    }
    catch (Exception e)
    {
      LOGGER.error("Failed to process visual event for " + modelName, e);
    }
  }

  private class VisualTraceCenterEvent
  {
    public boolean          _isAdd;

    public String           _modelName;

    public VisualDescriptor _descriptor;

    public VisualTraceCenterEvent(String modelName, VisualDescriptor data,
        boolean isAdd)
    {
      _isAdd = isAdd;
      _modelName = modelName;
      _descriptor = data;
    }
  }

}
