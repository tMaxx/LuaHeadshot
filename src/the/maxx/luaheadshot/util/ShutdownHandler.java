package the.maxx.luaheadshot.util;

import sun.misc.Signal;
import sun.misc.SignalHandler;
import the.maxx.luaheadshot.Log;
import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.MpiNode;

import java.util.Timer;
import java.util.TimerTask;

/** Ctrl+C handler */
@SuppressWarnings("ConstantConditions")
public class ShutdownHandler implements SignalHandler
{
	public static void Install()
	{
		Signal.handle(new Signal("INT"), new ShutdownHandler());
	}

	@Override
	public void handle(Signal signal)
	{
		try
		{
			if (MpiNode.Me() != null)
			{
				Log.Info("Quitting in at most 5 seconds...");
				MpiNode.Me().icomm.SendToAll(new MpiMessage(MpiMessage.Type.FINALIZE_EXECUTION));
				MpiNode.Me().icomm.BroadcastSend(new MpiMessage(MpiMessage.Type.CYCLE_NOOP));

				Timer tmr = new Timer();
				tmr.scheduleAtFixedRate(new TimerTask()
				{
					int countdown = 5;

					@Override
					public void run()
					{
						if (countdown-- < 0 || MpiNode.Me().isFinishing())
						{
							tmr.cancel();
						}
					}
				}, 0, 1000);

				if (MpiNode.Me().isFinishing())
				{
					Log.Info("MpiNode.Me reported finishing");
					return;
				}
			}
		}
		catch (Exception ignored) {} //MpiNode.Me() threw an error, Mpi thus unavailable

		Log.Error("Force quit, this is going to hurt...");
		//what a nice way to terminate this...
		Runtime.getRuntime().halt(500);
	}
}
