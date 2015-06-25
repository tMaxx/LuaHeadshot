package the.maxx.luaheadshot;

import mpi.MPIException;
import the.maxx.luaheadshot.action.Base;
import the.maxx.luaheadshot.action.PingPong;
import the.maxx.luaheadshot.action.SendReceive;

/** Handles lua compiler and actions in executed scripts */
public class ThePlayer
{
	public final MpiNode node;

	public ThePlayer(MpiNode node)
	{
		this.node = node;
	}

	void reset()
	{
		node.icomm.SendToRoot(MpiMessage.Type.NODE_RESET);
		state = PlayerState.IDLE;
		action = null;
		Log.Debug("ThePlayer reset");
	}

	public boolean processMessage(MpiMessage m)
	{
		if (m == null) return false;
		switch (m.messageType)
		{
			case ABORT_EXECUTION:
				Log.Error("Aborting: " + m.data.toString());
			case FINALIZE_EXECUTION:
				Log.Info("Finishing execution");
				node.setFinishing();
				break;
			case NODE_ALL_RESET:
				reset();
				break;
			case INITIALIZE_NEGOTIATIONS:
				//switch state
				state = PlayerState.SEND_RECEIVE;
				action = new SendReceive(this);
				break;
			case TEST_INIT_PINGPONG:
				state = PlayerState.PINGPONG;
				action = new PingPong(this);
				break;
			default:
				return false;
		}
		return true;
	}

	protected enum PlayerState
	{
		IDLE, PINGPONG, SEND_RECEIVE
	}
	PlayerState state = PlayerState.IDLE;
	Base action = null;

	public void run()
	{
		MpiMessage recv;

		playerloop:
		while (!node.isFinishing())
		{
			//recv all floating messages
			for (MpiMessage m : node.icomm.ReceiveAll())
			{
				if (processMessage(m))
					break playerloop;
				if (state != PlayerState.IDLE)
					action.processMessage(m);
			}

			if (state != PlayerState.IDLE)
			{
				action.run();

				if (action.isFinished())
				{ //reset
					state = PlayerState.IDLE;
					action = null;
				}
			}
			else //is idle, next command
			{
				recv = node.icomm.BroadcastRecv();
				if (!processMessage(recv) && state != PlayerState.IDLE)
				{
					state = PlayerState.IDLE;
					action = null;
				}
			}
		} //playerloop
	}

	public void finish()
	{
		//clear all logs before quitting
		node.icomm.ReceiveAll();
		Log.Info("ThePlayer finished");
	}
}
