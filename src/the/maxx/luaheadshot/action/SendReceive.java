package the.maxx.luaheadshot.action;

import the.maxx.luaheadshot.ClientState;
import the.maxx.luaheadshot.ClientState.Action;
import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.ThePlayer;
import the.maxx.luaheadshot.action.state.ESendReceive;

/** Node's send-receive worker */
public class SendReceive extends Base
{
	ESendReceive state = ESendReceive.INIT;
	ClientState[] last, proposed, killed;

	public SendReceive(ThePlayer player)
	{
		super(player);
	}

	private boolean checkDistance(ClientState c1, ClientState c2, double less)
	{
		return c1.distanceDiff(c2) < less;
	}

	private void processData()
	{
		//bottom half - last action, upper half - DOA
		boolean[] ret = new boolean[2 * player.node.size];
		//check distances travelled
		for (int i = 1; i < player.node.size; i++)
			if (checkDistance(last[i], proposed[i], 50.0))
				ret[i] = true;
		for (int i = player.node.size + 1; i < (2 * player.node.size); i++)
			ret[i] = (proposed[i - player.node.size].action != Action.DEAD
						|| proposed[i - player.node.size].action != Action.STUCK);

		//check last kills
		if (killed != null)
			for (ClientState cs : killed)
			{
				if (checkDistance(cs, proposed[cs.userId], 10.0)
					|| checkDistance(cs, last[cs.userId], 10.0))
					ret[player.node.size + cs.userId] = false;
			}
		MpiMessage msg = new MpiMessage(MpiMessage.Type.USER_ACCEPTANCE_STATE);
		msg.data = ret;
		player.node.icomm.SendToRoot(msg);
	}

	@Override
	public void run()
	{
		MpiMessage msg;
		msg = player.node.icomm.BroadcastRecv();
		if (msg != null)
		switch (state)
		{
			case INIT:
			case STATE_PREVIOUS:
				last = (ClientState[])msg.data;
				state = ESendReceive.STATE_PROPOSED;
				break;
			case STATE_PROPOSED:
				proposed = (ClientState[])msg.data;
				state = ESendReceive.STATE_PROPOSED;
				break;
			case STATE_KILLED:
				if (msg.data != null)
					killed = (ClientState[])msg.data;
				else
					killed = null;
				processData();
				state = ESendReceive.WAIT_FINISH;
				break;
			case WAIT_FINISH:
			default:
				finished = true;
				break;
		}
	}
}
