package the.maxx.luaheadshot;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class Datahub extends Thread
{
	static final MpiNode[] AllNodes = new MpiNode[20];

	private final MpiNode mpiNode;
	private final int size;

	private final AtomicBoolean
			clientConnected[];
	private final AtomicBoolean isSynchronizing;

	private final Semaphore hasNewData;

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
		}
		Log.Debug("Datahub init'd");
	}

	public boolean hasPlayers() { return !TCPServer.Clients.isEmpty(); }

	public int getNextFreeSlotId()
	{
		for (int i = 1; i < size; i++)
			if (clientConnected[i].compareAndSet(false, true))
				return i;
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

	private static double[][] SpawnPointCoord = new double[][]{
		{-380, -2200, 70},
		{390, -10, 60},
		{-1337.82, 1215, 740},
		{-530, -1720, 750},
		{1360, -700, 740},
	};
	public void moveToRespawnPoint(int nodeid)
	{
		ClientState cs = new ClientState();
		int idx = MpiIntercom.Random.nextInt(5);
		cs.posX = SpawnPointCoord[idx][0];
		cs.posY = SpawnPointCoord[idx][1];
		cs.posZ = SpawnPointCoord[idx][2];
		cs.action = ClientState.Action.ALIVE;
		nextCommit[nodeid].replace(cs);
	}

	public void updateStateForId(int nodeid, ClientState st)
	{
		if (nodeid < 1) //is an observer
			return;
		if (!isSynchronizing.get())
		{
			if (st.action == ClientState.Action.OPONNENT_DEAD)
				synchronized (pronouncedDead)
				{
					pronouncedDead.add(st);
				}
			else
				synchronized (currentStates)
				{
					currentStates[nodeid].replace(st);
					hasNewData.release();
				}
		}
	}

	public void freeze()
	{
		synchronized (currentStates)
		{
			synchronized (pronouncedDead)
			{
				confirmedDead.clear();
				confirmedDead.addAll(pronouncedDead);
				pronouncedDead.clear();
			}
			for (int i = 1; i < size; i++)
				currentNegotiate[i].replace(currentStates[i]);
		}
	}

	public void commitAll()
	{
		isSynchronizing.set(true);
		synchronized (currentStates)
		{
			for (int n = 1; n < size; n++)
			{
				currentStates[n].replace(nextCommit[n]);
				currentNegotiate[n].replace(nextCommit[n]);
				lastNegotiated[n].replace(nextCommit[n]);
			}
			hasNewData.release();
		}
		isSynchronizing.set(false);
	}

	public void revokeAll()
	{
		isSynchronizing.set(true);
		synchronized (currentStates)
		{
			for (int n = 1; n < size; n++)
			{
				currentStates[n].replace(lastNegotiated[n]);
				currentNegotiate[n].replace(lastNegotiated[n]);
				nextCommit[n].replace(lastNegotiated[n]);
			}
			hasNewData.release();
		}
		isSynchronizing.set(false);
	}

	public void revoke(int node)
	{
		nextCommit[node] = lastNegotiated[node].copy();
	}

	@Override
	public void run()
	{
		while (!mpiNode.isFinishing())
		{
			try
			{
				hasNewData.acquire();
				synchronized (currentStates)
				{
					for (TCPServer.ClientHandler ch : TCPServer.Clients)
						if (ch.nodeId > 0)
							ch.sendMyUpdatedPositionToAll(currentStates[ch.nodeId]);
				}
			}
			catch (InterruptedException ignored) {}
		}
	}

}
