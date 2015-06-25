package the.maxx.luaheadshot.action;

import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.ThePlayerSudo;
import the.maxx.luaheadshot.action.state.ESendReceive;

/** User data broadcaster */
public class SendReceiveSudo extends BaseSudo
{
	boolean[] gotMsg;
	int[] clientRanks, killRanks;
	ESendReceive status = ESendReceive.INIT;

	public SendReceiveSudo(ThePlayerSudo player)
	{
		super(player);
	}

	@Override
	public boolean processMessage(MpiMessage msg)
	{
		if (status == ESendReceive.ROOT_HANDOVER)
			if (msg.messageType == MpiMessage.Type.USER_ACCEPTANCE_STATE
				|| msg.messageType == MpiMessage.Type.USER_RESET_STATE)
			{
				gotMsg[msg.fromRank] = true;
				if (msg.messageType == MpiMessage.Type.USER_RESET_STATE)
				{
					player.node.datahub.moveToRespawnPoint(msg.fromRank);
					clientRanks[msg.fromRank] = 1337; // definitely accept
				}
				else
				{
					boolean[] recv = (boolean[])msg.data;
					for (int n = 1; n < player.node.size; n++)
						clientRanks[n] += (recv[n] ? 1 : -1);
					for (int n = player.node.size + 1; n < (2 * player.node.size); n++)
						killRanks[n] += (recv[n] ? 1 : -1);
				}

				for (int i = 1; i < gotMsg.length; i++)
					if (!gotMsg[i])
						return true;

				status = ESendReceive.INIT;

				//commit changes
				for (int n = 1, c = 0; n < clientRanks.length; n++)
				{
					if (killRanks[n] > 0)
						player.node.datahub.moveToRespawnPoint(n - player.node.size);
					if (clientRanks[n] < 0)
					{
						if (c++ <= (clientRanks.length / 2))
							player.node.datahub.revoke(n);
						else
						{
							player.node.datahub.revokeAll();
							break;
						}
					}
				}

				player.node.datahub.commitAll();

				status = ESendReceive.INIT;
				return true;
			}
		return false;
	}

	@Override
	public void run()
	{
		MpiMessage msg = new MpiMessage(MpiMessage.Type.CYCLE_NOOP);
		switch (status)
		{
			case INIT:
				msg = new MpiMessage(MpiMessage.Type.INITIALIZE_NEGOTIATIONS);
				gotMsg = new boolean[player.node.size];
				clientRanks = new int[player.node.size];
				killRanks = new int[player.node.size];
				status = ESendReceive.STATE_PREVIOUS;
				break;
			case STATE_PREVIOUS:
				player.node.datahub.freeze();
				msg = new MpiMessage(MpiMessage.Type.USER_STATE);
				msg.data = player.node.datahub.lastNegotiated;
				status = ESendReceive.STATE_PROPOSED;
				break;
			case STATE_PROPOSED:
				msg = new MpiMessage(MpiMessage.Type.USER_STATE);
				msg.data = player.node.datahub.currentNegotiate;
				status = ESendReceive.STATE_KILLED;
				break;
			case STATE_KILLED:
				msg = new MpiMessage(MpiMessage.Type.USER_STATE);
				msg.data = player.node.datahub.confirmedDead.toArray();
				status = ESendReceive.WAIT_FINISH;
				break;
			case WAIT_FINISH:
				status = ESendReceive.ROOT_HANDOVER;
			default:
				break;
		}
		player.node.icomm.BroadcastSend(msg);
	}
}
