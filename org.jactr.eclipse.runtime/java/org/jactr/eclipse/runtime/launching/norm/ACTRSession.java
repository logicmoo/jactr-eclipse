/*
 * Created on Jun 8, 2006 Copyright (C) 2001-5, Anthony Harrison anh23@pitt.edu
 * (jactr.org) This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the License,
 * or (at your option) any later version. This library is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU Lesser General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jactr.eclipse.runtime.launching.norm;

import java.net.InetSocketAddress;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.demux.ExceptionHandler;
import org.commonreality.mina.protocol.SerializingProtocol;
import org.commonreality.mina.service.ServerService;
import org.commonreality.mina.transport.NIOTransportProvider;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.jactr.eclipse.runtime.RuntimePlugin;
import org.jactr.eclipse.runtime.debug.ACTRDebugTarget;
import org.jactr.eclipse.runtime.handlers.TransformedEventMessageHandler;
import org.jactr.eclipse.runtime.launching.ACTRLaunchConstants;
import org.jactr.eclipse.runtime.launching.session.AbstractSession;
import org.jactr.eclipse.runtime.launching.session.SessionTracker;
import org.jactr.eclipse.runtime.preferences.RuntimePreferences;
import org.jactr.tools.async.credentials.ICredentials;
import org.jactr.tools.async.message.event.state.IModelStateEvent;
import org.jactr.tools.async.message.event.state.IRuntimeStateEvent;
import org.jactr.tools.async.shadow.ShadowController;
import org.jactr.tools.async.shadow.ShadowIOHandler;
import org.jactr.tools.async.shadow.handlers.ModelStateHandler;
import org.jactr.tools.async.shadow.handlers.RuntimeStateMessageHandler;
import org.jactr.tools.tracer.transformer.ITransformedEvent;

public class ACTRSession extends AbstractSession
{
  static public final String                 LAUNCH_TYPE     = "org.jactr.eclipse.runtime.launching.normal";

  static private SessionTracker<ACTRSession> _sessionTracker = new SessionTracker<ACTRSession>();

  static public SessionTracker<ACTRSession> getACTRSessionTracker()
  {
    return _sessionTracker;
  }

  /**
   * Logger definition
   */
  static private final transient Log       LOGGER              = LogFactory
                                                                   .getLog(ACTRSession.class);

  static public String                     ACTR_DEBUG_MODEL    = "org.jactr.eclipse.runtime.debug.DebugClient";

  protected ShadowController               _shadowController;

  protected boolean                        _suspendImmediately = false;

  protected TransformedEventMessageHandler _eventHandler;

  /**
   * @param launch
   * @param mode
   * @throws IllegalStateException
   *           if we are unable to open the service connection
   */
  public ACTRSession(ILaunch launch, ILaunchConfiguration configuration)
      throws IllegalStateException
  {
    super(launch, configuration);

    try
    {
      setSuspendImmediately(configuration.getAttribute(
          ACTRLaunchConstants.ATTR_SUSPEND, false));
    }
    catch (Exception e)
    {
    }

    _shadowController = new ShadowController();

    configureShadowControllerConnection(_shadowController, configuration);

    configureShadowController(_shadowController, launch);
  }

  public void setSuspendImmediately(boolean suspendImmediately)
  {
    _suspendImmediately = suspendImmediately;
  }

  @Override
  protected SessionTracker getSessionTracker()
  {
    return _sessionTracker;
  }

  protected void configureShadowControllerConnection(
      ShadowController controller, ILaunchConfiguration configuration)
  {
    controller.setService(new ServerService());
    controller.setTransportProvider(new NIOTransportProvider());
    _shadowController.setProtocol(new SerializingProtocol());

    /**
     * we need some credentials..
     */
    String credentials = null;
    // try
    // {
    // credentials = configuration.getAttribute(
    // ACTRLaunchConstants.ATTR_CREDENTIALS, (String) null);
    // }
    // catch (Exception e)
    // {
    // // noop
    // }

    if (credentials == null)
    {
      StringBuilder sb = new StringBuilder(System.getProperty("user.name"));
      sb.append(":").append(generateRandomPassword());
      credentials = sb.toString();
    }

    controller.setCredentialInformation(credentials);

    /*
     * now we just need to figure out where to attach..
     */
    controller.setAddressInfo("localhost:0");

    // controller.setExecutorService(Executors.newSingleThreadExecutor());
  }

  @SuppressWarnings("unchecked")
  protected void configureShadowController(ShadowController controller,
      ILaunch launch)
  {
    ShadowIOHandler handler = controller.getHandler();

    /*
     * we modify the model and runtime handlers..
     */
    handler.removeReceivedMessageHandler(IRuntimeStateEvent.class);
    handler.removeReceivedMessageHandler(IModelStateEvent.class);

    handler.addReceivedMessageHandler(IModelStateEvent.class,
        new ModelStateHandler() {
          @Override
          public void handleMessage(IoSession session, IModelStateEvent mse)
              throws Exception
          {
            super.handleMessage(session, mse);
            // log
            if (mse.getException() != null)
              RuntimePlugin.error(String.format(
                  "%s terminated abnormally due to %s", mse.getModelName(), mse
                      .getException()));
          }
        });

    handler.addReceivedMessageHandler(IRuntimeStateEvent.class,
        new RuntimeStateMessageHandler() {

          @Override
          public void handleMessage(IoSession session,
              IRuntimeStateEvent message) throws Exception
          {
            super.handleMessage(session, message);
            // log
            if (message.getException() != null)
              RuntimePlugin.error(String.format(
                  "Execution terminated abnormally due to %s", message
                      .getException()));
          }
        });

    handler.removeReceivedMessageHandler(ITransformedEvent.class);
    _eventHandler = new TransformedEventMessageHandler(this);
    handler.addReceivedMessageHandler(ITransformedEvent.class, _eventHandler);

    final ExceptionHandler<Throwable> eh = (ExceptionHandler<Throwable>) handler
        .getExceptionHandlerMap().get(Throwable.class);
    new ExceptionHandler<Throwable>() {

      public void exceptionCaught(IoSession arg0, Throwable arg1)
          throws Exception
      {
        RuntimePlugin.error(
            "Exception caught while listening to jACT-R execution ", arg1);
        if (eh != null) eh.exceptionCaught(arg0, arg1);
      }

    };
    handler.addExceptionHandler(Throwable.class, eh);
  }

  private String generateRandomPassword()
  {
    Random rand = new Random();
    StringBuilder sb = new StringBuilder();
    while (sb.length() < 16)
      sb.append(rand.nextInt());
    return sb.toString();
  }

  public ShadowController getShadowController()
  {
    return _shadowController;
  }

  @Override
  protected void connect() throws CoreException
  {
    _shadowController.attach();
  }

  @Override
  protected void disconnect(boolean force)
  {
    try
    {
      _shadowController.detach(force);
    }
    catch (Exception e)
    {
      RuntimePlugin.error("ACTRSession.disconnect threw Exception : ", e);
    }
  }

  @Override
  protected Job createSessionJob()
  {
    Job job = new ListenerJob(this);
    job.setPriority(Job.LONG);
    return job;
  }

  @Override
  public InetSocketAddress getConnectionAddress()
  {
    return (InetSocketAddress) _shadowController.getActualAddress();
  }

  @Override
  public ICredentials getCredentials()
  {
    return _shadowController.getActualCredentials();
  }

  private class ListenerJob extends Job
  {
    ACTRSession _master;

    public ListenerJob(ACTRSession master)
    {
      super(master.getConfiguration().getName());
      _master = master;
    }

    /**
     * wait at most maxTime to a connection to be established
     * 
     * @param maxTimeToWait
     * @return true if connection is established
     */
    protected boolean waitForConnection(IProgressMonitor monitor,
        long maxTimeToWait) throws Exception
    {
      boolean connected = false;
      monitor = new SubProgressMonitor(monitor, 1);
      monitor.beginTask("Waiting for connection", 1);
      try
      {
        long startTime = System.currentTimeMillis();

        /*
         * if the process is canceled, or the launch is terminated, we bail
         */
        while (System.currentTimeMillis() - startTime < maxTimeToWait
            && !monitor.isCanceled() && !connected)
        {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("Waiting for connection " + maxTimeToWait + " "
                + monitor.isCanceled() + " " + _launch.isTerminated());
          connected = _shadowController.waitForConnection(1000);
        }

        // RuntimePlugin.info("Connected:" + connected);

        monitor.worked(1);

        return connected;
      }
      finally
      {
        monitor.done();
      }

    }

    /**
     * wait for the debugger to finish its initialization (and startup)
     * 
     * @param monitor
     * @return
     */
    protected boolean waitForDebugger(IProgressMonitor monitor)
        throws Exception
    {
      monitor = new SubProgressMonitor(monitor, 2);
      monitor.beginTask("Waiting for debugger", 2);
      try
      {
        /*
         * if a debugger is attached, let's wait until it is ready to go
         */
        for (IDebugTarget debugger : _launch.getDebugTargets())
          if (debugger instanceof ACTRDebugTarget)
          {
            if (LOGGER.isDebugEnabled())
              LOGGER.debug("Waiting for debugger " + debugger);
            ((ACTRDebugTarget) debugger).installBreakpoints();
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Debugger is ready");
          }
        monitor.worked(1);

        monitor.setTaskName("Syncing communications");
        _shadowController.getHandler().waitForPendingWrites();
        monitor.worked(1);

        return true;
      }
      catch (Exception e)
      {
        RuntimePlugin.error("Failed waiting for debugger", e);
        throw e;
      }
      finally
      {
        monitor.done();
      }
    }

    /**
     * start the runtime, and wait for confirmation of the start
     * 
     * @param monitor
     * @return
     */
    protected boolean startRuntime(IProgressMonitor monitor, long maxTimeToWait)
        throws Exception
    {
      monitor = new SubProgressMonitor(monitor, 2);
      boolean running = false;
      try
      {
        monitor.beginTask("Starting runtime", 2);
        _shadowController.start(_suspendImmediately);
        monitor.worked(1);
        monitor.setTaskName("Waiting for confirmation");

        /*
         * wait at most max time for the connection to start, aborting it the
         * launch is terminated, or the process is canceled
         */
        running = _shadowController.isRunning();
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxTimeToWait
            && !monitor.isCanceled() && !_launch.isTerminated() && !running)
        {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("Waiting for running " + maxTimeToWait + " "
                + monitor.isCanceled() + " " + _launch.isTerminated());
          _shadowController.waitForStart(1000);
          running = _shadowController.isRunning();
        }

        // RuntimePlugin.info("Runtime is running : " + running);

        monitor.worked(1);
        return running;
      }
      finally
      {
        monitor.done();
      }
    }

    /**
     * wait until the runtime has stopped running.
     * 
     * @param monitor
     */
    protected void waitForCompletion(IProgressMonitor monitor) throws Exception
    {
      monitor = new SubProgressMonitor(monitor, 1);
      monitor.beginTask("Handling events", 1);
      try
      {
        while (!monitor.isCanceled()
            && _shadowController.getIOHandler().isConnected()
            && _shadowController.isRunning())
          _shadowController.waitForCompletion(1000);
      }
      finally
      {
        monitor.worked(1);
        monitor.done();
      }

      // RuntimePlugin.info("Completed. running:" +
      // _shadowController.isRunning()
      // + " connected:" + _shadowController.getIOHandler().isConnected());
    }

    @Override
    protected IStatus run(IProgressMonitor monitor)
    {
      monitor.beginTask("Listening to jACT-R Runtime", 14);
      IStatus rtn = Status.OK_STATUS;
      String label = "Connecting";

      monitor.setTaskName(label);
      long waitTime = 1000 * RuntimePlugin.getDefault().getPluginPreferences()
          .getInt(RuntimePreferences.NORMAL_START_WAIT_PREF);
      try
      {
        /*
         * we don't look at terminated since it may be true if the launch hasn't
         * started yet
         */
        boolean shouldContinue = !monitor.isCanceled()
            && waitForConnection(monitor, waitTime);

        label = "Configuring";
        monitor.worked(1);

        monitor.setTaskName(label);
        if (shouldContinue)
          shouldContinue = !monitor.isCanceled() && !_launch.isTerminated()
              && waitForDebugger(monitor);
        monitor.worked(1);

        label = "Starting";
        monitor.setTaskName(label);
        if (shouldContinue)
          shouldContinue = !monitor.isCanceled() && !_launch.isTerminated()
              && startRuntime(monitor, waitTime / 2);
        monitor.worked(1);

        label = "Listening";
        monitor.setTaskName(label);
        if (shouldContinue && !monitor.isCanceled() && !_launch.isTerminated())
          waitForCompletion(monitor);
        monitor.worked(1);

        /*
         * now just because it has completed, doesn't actually mean all the
         * messages have arrived..
         */
      }
      catch (Exception e)
      {
        rtn = new Status(IStatus.ERROR, RuntimePlugin.class.getName(),
            "Failed " + label + " launch", e);

        RuntimePlugin.error(rtn.getMessage(), e);
      }
      finally
      {
        monitor.done();
      }

      if (monitor.isCanceled()) rtn = Status.CANCEL_STATUS;

      return rtn;
    }
  }
}
