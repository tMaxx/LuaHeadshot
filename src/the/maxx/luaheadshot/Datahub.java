package the.maxx.luaheadshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class Datahub extends Thread
{
	static final MpiNode[] AllNodes = new MpiNode[20];

	private final MpiNode mpiNode;
	private final int size;

	private final AtomicBoolean
		clientConnected[],
		hasNewClientData[];
	private final AtomicBoolean isSynchronizing;

	final Semaphore hasNewData;
	final ReentrantLock readWriteSync;

	public final ClientState[]
		lastNegotiated,
		currentStates,
		currentNegotiate,
		nextCommit;

	public final ArrayList<ClientState>
		pronouncedDead,
		confirmedDead;

	public Datahub(MpiNode node)
	{
		super();
		this.mpiNode = node;
		this.size = node.size;

		hasNewData = new Semaphore(1, true);
		hasNewClientData = new AtomicBoolean[size];
		readWriteSync = new ReentrantLock(true);
		isSynchronizing = new AtomicBoolean(false);
		lastNegotiated = new ClientState[size];
		currentStates = new ClientState[size];
		currentNegotiate = new ClientState[size];
		nextCommit = new ClientState[size];
		clientConnected = new AtomicBoolean[size];
		pronouncedDead = new ArrayList<>();
		confirmedDead = new ArrayList<>();

		for (int i = 1; i < size; i++)
		{
			lastNegotiated[i] = new ClientState(i);
			currentStates[i] = new ClientState(i);
			currentNegotiate[i] = new ClientState(i);
			nextCommit[i] = new ClientState(i);
			clientConnected[i] = new AtomicBoolean(false);
			hasNewClientData[i] = new AtomicBoolean(false);
		}

		Log.Debug("Datahub init'd");
	}

	//region player id manage
	public boolean hasPlayers() { return !TCPServer.Clients.isEmpty(); }

	public int getNextFreeSlotId()
	{
		for (int i = 1; i < size; i++)
			if (clientConnected[i].compareAndSet(false, true))
			{
				currentStates[i].action = ClientState.Action.CONNECTED;
				return i;
			}
		return -1;
	}

	public void setSlotAsFree(int id)
	{
		if (id < 1)
			throw new IllegalArgumentException("Invalid node id");
		if (id >= size) //player is an observer
			return;

		clientConnected[id].compareAndSet(true, false);
	}
	//endregion

	private static double[][] SpawnPointCoord = new double[][]{
		{-380.0, -2200.0, 108.0},
		{390.0, -10.0, 108.0},
//		{-1337.82, 1215, 740},
//		{-530, -1720, 750},
//		{1360, -700, 740},
	};
	public ClientState getRespawnPoint()
	{
		ClientState ret = new ClientState();
		ret.action = ClientState.Action.ALIVE;
		int idx = MpiIntercom.Random.nextInt(SpawnPointCoord.length);
		ret.posX = SpawnPointCoord[idx][0];
		ret.posY = SpawnPointCoord[idx][1];
		ret.posZ = SpawnPointCoord[idx][2];
		return ret;
	}

	public ClientState moveToRespawnPoint(int nodeid)
	{
		nextCommit[nodeid].replace(getRespawnPoint());
		hasNewClientData[nodeid].set(true);
		return nextCommit[nodeid].copy();
	}

	public void newStateFromId(int nodeid, ClientState st)
	{
		if (nodeid < 1) //is an observer
			return;
		if (!isSynchronizing.get())
		{
			readWriteSync.lock();
			if (st.action == ClientState.Action.OPONNENT_DEAD)
				pronouncedDead.add(st);
			else
			{
				currentStates[nodeid].replace(st);
				hasNewClientData[nodeid].set(true);
				hasNewData.release();
			}
			readWriteSync.unlock();
		}
	}

	public void announceNewPlayer(TCPServer.ClientHandler player)
	{
		ClientState state = currentStates[player.nodeId].replace(getRespawnPoint());
		for (TCPServer.ClientHandler client : TCPServer.Clients)
		{
			try
			{
				if (player.nodeId > 0 && player.nodeId != client.nodeId)
					client.sendStatus(state, ClientState.Action.ENEMY_CONNECTED);
				else
					client.sendStatus(state, ClientState.Action.CONNECTED);
			}
			catch (IOException e)
			{
				Log.Exception("New client announce failed: my id:" + player.nodeId + ", their:" + client.nodeId, e);
			}
		}
	}

	//resync all client data to all clients
	public void setAllAsNew()
	{
		for (int i = 0; i < size; i++)
			hasNewClientData[i].set(true);
		hasNewData.release();
	}

	//region commit-revoke-freeze
	public void freeze()
	{
		readWriteSync.lock();
			confirmedDead.clear();
			confirmedDead.addAll(pronouncedDead);
			pronouncedDead.clear();

			for (int i = 1; i < size; i++)
				currentNegotiate[i].replace(currentStates[i]);
		readWriteSync.unlock();
	}

	//move data from negotiated to
	public void commitAll()
	{
		isSynchronizing.set(true);
		readWriteSync.lock();

		for (int n = 1; n < size; n++)
		{
			currentStates[n].replace(nextCommit[n]);
			currentNegotiate[n].replace(nextCommit[n]);
			lastNegotiated[n].replace(nextCommit[n]);
			hasNewClientData[n].set(true);
		}
		hasNewData.release();

		readWriteSync.unlock();
		isSynchronizing.set(false);
	}

	public void revokeAll()
	{
		isSynchronizing.set(true);
		readWriteSync.lock();
			for (int n = 1; n < size; n++)
			{
				currentStates[n].replace(lastNegotiated[n]);
				currentNegotiate[n].replace(lastNegotiated[n]);
				nextCommit[n].replace(lastNegotiated[n]);
				hasNewClientData[n].set(true);
			}
			hasNewData.release();
		readWriteSync.unlock();
		isSynchronizing.set(false);
	}

	public void revoke(int node)
	{
		readWriteSync.lock();
		nextCommit[node] = lastNegotiated[node].copy();
		readWriteSync.unlock();
	}
	//endregion

	@Override
	public void run()
	{
		while (!mpiNode.isFinishing())
		{
			try
			{
				hasNewData.acquire();
				readWriteSync.lock();
				//iterate over all states, send to all clients
				for (int i = 1; i < size; i++)
					if (hasNewClientData[i].getAndSet(false))
						for (TCPServer.ClientHandler ch : TCPServer.Clients)
							try
							{
								ch.sendStatus(currentStates[i]);
							}
							catch (IOException e)
							{
								Log.Exception("Send failed", e);
							}
				readWriteSync.unlock();
			}
			catch (InterruptedException ignored) {}
		}
	}

}
